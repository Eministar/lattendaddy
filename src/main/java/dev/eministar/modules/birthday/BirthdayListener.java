package dev.eministar.modules.birthday;

import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public class BirthdayListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(BirthdayListener.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void onReady(ReadyEvent event) {
        // start the scheduler using the JDA instance
        start(event.getJDA());

        // Update/create birthday lists for all guilds on startup
        logger.info("Aktualisiere Geburtstagslisten für alle Server...");
        for (var guild : event.getJDA().getGuilds()) {
            try {
                updateListEmbed(guild.getId(), event.getJDA());
            } catch (Exception e) {
                logger.error("Fehler beim Aktualisieren der Geburtstagsliste für Guild {}", guild.getId(), e);
            }
        }
    }

    // Timezone for birthday checks - Europe/Berlin for German server
    private static final java.time.ZoneId TIMEZONE = java.time.ZoneId.of("Europe/Berlin");

    // Public start so we can call it if needed
    public void start(JDA jda) {
        // Schedule to run exactly at midnight in configured timezone, and every 24h after
        long initialDelay = computeSecondsUntilMidnight();
        scheduler.scheduleAtFixedRate(() -> runDaily(jda), initialDelay, 24 * 60 * 60, TimeUnit.SECONDS);
        logger.info("Geburtstags-Checker gestartet. Nächste Prüfung um Mitternacht ({}), in {} Sekunden.", TIMEZONE, initialDelay);
    }

    private long computeSecondsUntilMidnight() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(TIMEZONE);
        java.time.ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(TIMEZONE);
        long secondsUntilMidnight = java.time.Duration.between(now, nextMidnight).getSeconds();
        return Math.max(5, secondsUntilMidnight);
    }

    private void runDaily(JDA jda) {
        try {
            for (var guild : jda.getGuilds()) {
                String guildId = guild.getId();
                // First remove birthday role from everyone who had it yesterday
                removeBirthdayRoleFromAll(guild);
                // Then check and assign for today's birthdays
                checkGuildBirthdays(guildId, jda);
            }
        } catch (Exception e) {
            logger.error("Fehler beim Überprüfen der Geburtstage", e);
        }
    }

    private void removeBirthdayRoleFromAll(Guild guild) {
        String roleId = Config.getBirthdayRoleId();
        if (roleId.isEmpty()) return;

        Role birthdayRole = guild.getRoleById(roleId);
        if (birthdayRole == null) {
            logger.warn("Birthday role not found: {}", roleId);
            return;
        }

        // Remove role from all members who have it
        guild.findMembersWithRoles(birthdayRole).onSuccess(members -> {
            for (Member member : members) {
                guild.removeRoleFromMember(member, birthdayRole).queue(
                    success -> logger.debug("Removed birthday role from {}", member.getEffectiveName()),
                    error -> logger.warn("Failed to remove birthday role from {}: {}", member.getEffectiveName(), error.getMessage())
                );
            }
        });
    }

    private void checkGuildBirthdays(String guildId, JDA jda) {
        Map<String, BirthdayService.BirthdayEntry> all = BirthdayService.getAllBirthdays(guildId);
        if (all.isEmpty()) return;
        LocalDate today = LocalDate.now(TIMEZONE);
        String isoToday = today.toString(); // yyyy-MM-dd

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;

        // Get birthday role if configured
        String roleId = Config.getBirthdayRoleId();
        Role birthdayRole = roleId.isEmpty() ? null : guild.getRoleById(roleId);

        for (var e : all.entrySet()) {
            String userId = e.getKey();
            BirthdayService.BirthdayEntry b = e.getValue();
            if (b.day == today.getDayOfMonth() && b.month == today.getMonthValue()) {
                // skip if already sent today
                if (b.lastCongratsDate.equals(isoToday)) continue;
                TextChannel ch = resolveCongratsChannel(jda, guildId);
                if (ch == null) continue;
                final String finalGuildId = guildId;
                final Role finalBirthdayRole = birthdayRole;
                jda.retrieveUserById(userId).queue(user -> {
                    String mention = user.getAsMention();
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle(EmojiUtil.wrap("🎂") + " Alles Gute zum Geburtstag! " + EmojiUtil.wrap("🎉"));

                    String ageText = "";
                    if (b.year != null) {
                        int age = today.getYear() - b.year;
                        ageText = "\n" + EmojiUtil.wrap("🎈") + " Du wirst heute **" + age + " Jahre** alt!";
                    }

                    eb.setDescription(
                        EmojiUtil.wrap("🎊") + " **Happy Birthday, " + mention + "!** " + EmojiUtil.wrap("🎊") +
                        ageText +
                        "\n\n" + EmojiUtil.wrap("🎁") + " Wir wünschen dir einen wundervollen Tag voller Freude, Glück und natürlich ganz viel Kaffee! " + EmojiUtil.wrap("☕") +
                        "\n\n" + EmojiUtil.wrap("✨") + " Genieße deinen besonderen Tag! " + EmojiUtil.wrap("🥳")
                    );

                    if (user.getAvatarUrl() != null) eb.setThumbnail(user.getAvatarUrl());
                    eb.setColor(new Color(0xFFD1DC));
                    eb.setFooter("🎂 Geburtstags-Bot • Lattendaddy", null);

                    ch.sendMessageEmbeds(eb.build()).queue(msg -> {
                        // store last sent id + date
                        BirthdayService.setLastCongrats(finalGuildId, userId, msg.getId(), isoToday);
                    });

                    // Assign birthday role if configured
                    if (finalBirthdayRole != null) {
                        guild.retrieveMemberById(userId).queue(member -> {
                            guild.addRoleToMember(member, finalBirthdayRole).queue(
                                success -> logger.info("Assigned birthday role to {} for their birthday", member.getEffectiveName()),
                                error -> logger.warn("Failed to assign birthday role to {}: {}", member.getEffectiveName(), error.getMessage())
                            );
                        }, error -> logger.warn("Could not find member {} for birthday role", userId));
                    }
                });
            }
        }
    }

    private static TextChannel resolveCongratsChannel(JDA jda, String guildId) {
        String cfg = Config.getBirthdayCongratsChannelId();
        if (!cfg.isEmpty()) {
            var g = jda.getGuildById(guildId);
            if (g != null) {
                var ch = g.getGuildChannelById(cfg);
                if (ch instanceof TextChannel tc) return tc;
            }
        }
        var g = jda.getGuildById(guildId);
        if (g == null) return null;
        var defaultCh = g.getDefaultChannel();
        if (defaultCh instanceof TextChannel tc) return tc;
        return null;
    }

    // Public helpers to update or send the list embed
    public static void updateListEmbed(String guildId, JDA jda) {
        TextChannel ch = resolveListChannel(jda, guildId);
        if (ch == null) {
            logger.debug("Kein Channel für Geburtstagsliste konfiguriert für Guild {}", guildId);
            return;
        }

        try {
            Map<String, BirthdayService.BirthdayEntry> all = BirthdayService.getAllBirthdays(guildId);
            EmbedBuilder eb = buildListEmbed(jda, guildId, all);
            String messageId = BirthdayService.getListMessageId(guildId);

            if (messageId != null && !messageId.isEmpty()) {
                // Try to edit existing message
                ch.retrieveMessageById(messageId).queue(
                    msg -> msg.editMessageEmbeds(eb.build()).queue(
                        success -> logger.debug("Geburtstagsliste aktualisiert für Guild {}", guildId),
                        err -> logger.warn("Fehler beim Bearbeiten der Geburtstagsliste, sende neue: {}", err.getMessage())
                    ),
                    // If message not found, send new one
                    err -> ch.sendMessageEmbeds(eb.build()).queue(
                        sent -> {
                            BirthdayService.setListMessageId(guildId, sent.getId());
                            logger.info("Neue Geburtstagsliste gesendet für Guild {} (Message-ID: {})", guildId, sent.getId());
                        },
                        sendErr -> logger.error("Fehler beim Senden der Geburtstagsliste: {}", sendErr.getMessage())
                    )
                );
            } else {
                // No message ID stored, send new one
                ch.sendMessageEmbeds(eb.build()).queue(
                    sent -> {
                        BirthdayService.setListMessageId(guildId, sent.getId());
                        logger.info("Geburtstagsliste erstellt für Guild {} (Message-ID: {})", guildId, sent.getId());
                    },
                    err -> logger.error("Fehler beim Erstellen der Geburtstagsliste: {}", err.getMessage())
                );
            }
        } catch (Exception e) {
            logger.error("Fehler beim Aktualisieren der Geburtstagsliste für Guild {}", guildId, e);
        }
    }

    public static void sendListEmbed(String guildId, TextChannel channel) {
        Map<String, BirthdayService.BirthdayEntry> all = BirthdayService.getAllBirthdays(guildId);
        EmbedBuilder eb = buildListEmbed(channel.getJDA(), guildId, all);
        channel.sendMessageEmbeds(eb.build()).queue(sent -> BirthdayService.setListMessageId(guildId, sent.getId()));
    }

    public static void sendListEmbedToHook(String guildId, net.dv8tion.jda.api.interactions.InteractionHook hook, JDA jda) {
        Map<String, BirthdayService.BirthdayEntry> all = BirthdayService.getAllBirthdays(guildId);
        EmbedBuilder eb = buildListEmbed(jda, guildId, all);
        hook.editOriginalEmbeds(eb.build()).queue();
    }

    private static TextChannel resolveListChannel(JDA jda, String guildId) {
        String cfg = Config.getBirthdayListChannelId();
        var g = jda.getGuildById(guildId);
        if (g == null) return null;
        if (!cfg.isEmpty()) {
            var ch = g.getGuildChannelById(cfg);
            if (ch instanceof TextChannel tc) return tc;
        }
        var defaultCh = g.getDefaultChannel();
        if (defaultCh instanceof TextChannel tc) return tc;
        return null;
    }

    private static EmbedBuilder buildListEmbed(JDA jda, String guildId, Map<String, BirthdayService.BirthdayEntry> all) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(EmojiUtil.wrap("🎂") + " Geburtstagsliste " + EmojiUtil.wrap("🎉"));
        eb.setColor(new Color(0xFFB6C1));
        eb.setDescription(EmojiUtil.wrap("🎈") + " Hier sind alle Geburtstage aufgelistet! " + EmojiUtil.wrap("✨"));

        // group by month
        Map<Integer, List<String>> byMonth = new TreeMap<>();
        for (var e : all.entrySet()) {
            String userId = e.getKey();
            BirthdayService.BirthdayEntry b = e.getValue();
            byMonth.computeIfAbsent(b.month, k -> new ArrayList<>()).add(formatEntry(jda, guildId, userId, b));
        }

        for (int m = 1; m <= 12; m++) {
            List<String> list = byMonth.getOrDefault(m, Collections.emptyList());
            String monthEmoji = getMonthEmoji(m);
            if (list.isEmpty()) {
                eb.addField(monthEmoji + " " + monthName(m), EmojiUtil.wrap("💤") + " Keine Einträge", false);
            } else {
                eb.addField(monthEmoji + " " + monthName(m), String.join("\n", list), false);
            }
        }

        eb.setFooter("Nutze /birthday set <Tag> <Monat> [Jahr] um deinen Geburtstag hinzuzufügen " + EmojiUtil.wrap("🎁"));
        return eb;
    }

    private static String formatEntry(JDA jda, String guildId, String userId, BirthdayService.BirthdayEntry b) {
        String name = "<@" + userId + ">";
        try {
            var guild = jda.getGuildById(guildId);
            if (guild != null) {
                var member = guild.getMemberById(userId);
                if (member != null) name = member.getEffectiveName();
            }
        } catch (Exception ignored) {}
        return EmojiUtil.wrap("🎈") + name + " — " + b.pretty();
    }

    private static String monthName(int m) {
        return switch (m) {
            case 1 -> "Januar";
            case 2 -> "Februar";
            case 3 -> "März";
            case 4 -> "April";
            case 5 -> "Mai";
            case 6 -> "Juni";
            case 7 -> "Juli";
            case 8 -> "August";
            case 9 -> "September";
            case 10 -> "Oktober";
            case 11 -> "November";
            case 12 -> "Dezember";
            default -> "Unbekannt";
        };
    }

    private static String getMonthEmoji(int m) {
        return switch (m) {
            case 1 -> "❄️"; // Winter
            case 2 -> "💝"; // Valentine
            case 3 -> "🌸"; // Frühling
            case 4 -> "🐰"; // Ostern
            case 5 -> "🌺"; // Frühling
            case 6 -> "☀️"; // Sommer
            case 7 -> "🏖️"; // Sommer
            case 8 -> "🌻"; // Sommer
            case 9 -> "🍂"; // Herbst
            case 10 -> "🎃"; // Halloween
            case 11 -> "🍁"; // Herbst
            case 12 -> "🎄"; // Weihnachten
            default -> "📅";
        };
    }
}
