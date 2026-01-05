package dev.eministar.modules.giveaway;

import dev.eministar.command.Command;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Professional Giveaway Module V2
 * Features: Button-based entry, live stats, requirements, bonus entries, claim system, pause/resume
 */
public class GiveawayModuleV2 implements Command {
    private final GiveawayStore store;
    private final Debouncer updateDebouncer;
    private final Map<String, Long> userCooldowns;

    public GiveawayModuleV2() {
        this.store = new GiveawayStore("./data/giveaways.json");
        this.updateDebouncer = new Debouncer(2000); // 2s debounce for UI updates
        this.userCooldowns = new HashMap<>();
    }

    @Override
    public String name() {
        return "gaw";
    }

    @Override
    public String description() {
        return "Professionelles Giveaway-System mit Button-Teilnahme und Live-Stats";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        event.getChannel().sendMessage("❌ Nutze `/gaw` Commands!").queue();
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (!event.isFromGuild()) {
            event.reply("❌ Nur in Servern verfügbar!").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        switch (subcommand) {
            case "create":
                handleCreate(event);
                break;
            case "end":
                handleEnd(event);
                break;
            case "list":
                handleList(event);
                break;
            default:
                event.reply("❌ Unbekannter Command").setEphemeral(true).queue();
        }
    }

    private void handleCreate(SlashCommandInteraction event) {
        // Permission check
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ Du benötigst `Server verwalten` Berechtigung!").setEphemeral(true).queue();
            return;
        }

        // Parse parameters
        String title = event.getOption("title").getAsString();
        String prize = event.getOption("prize").getAsString();
        String durationStr = event.getOption("duration").getAsString();
        int winners = event.getOption("winners") != null ?
            event.getOption("winners").getAsInt() : 1;

        // Validate
        if (winners < 1 || winners > 20) {
            event.reply("❌ Gewinner muss zwischen 1-20 liegen!").setEphemeral(true).queue();
            return;
        }

        Duration duration;
        try {
            duration = TimeParser.parse(durationStr);
        } catch (Exception e) {
            event.reply("❌ Ungültiges Format! Nutze: 45m, 2h, 1d").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        // Create giveaway data
        GiveawayData data = new GiveawayData();
        data.id = store.generateId();
        data.guildId = event.getGuild().getId();
        data.channelId = event.getChannel().getId();
        data.hostId = event.getUser().getId();
        data.title = title;
        data.prize = prize;
        data.winnersCount = winners;
        data.startedAt = Instant.now().toString();
        data.endsAt = Instant.now().plus(duration).toString();
        data.status = "running";
        data.visibility = "live";

        // Create embed
        EmbedBuilder embed = createGiveawayEmbed(data);

        // Send message
        TextChannel channel = event.getGuild().getTextChannelById(data.channelId);
        if (channel == null) {
            event.getHook().editOriginal("❌ Kanal nicht gefunden!").queue();
            return;
        }

        channel.sendMessageEmbeds(embed.build())
            .setActionRow(Button.primary("gaw:enter:" + data.id, "🎁 Teilnehmen"))
            .queue(message -> {
                data.messageId = message.getId();
                String key = data.guildId + ":" + data.messageId;
                store.put(key, data);

                event.getHook().editOriginal("✅ Giveaway erstellt: " + message.getJumpUrl()).queue();
            });
    }

    private EmbedBuilder createGiveawayEmbed(GiveawayData data) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎉 GIVEAWAY 🎉");
        embed.setColor(new Color(0xFF69B4));

        int totalEntrants = data.entrants != null ? data.entrants.size() : 0;
        long endsAtEpoch = data.getEndsAtInstant().getEpochSecond();

        StringBuilder desc = new StringBuilder();
        desc.append("━━━━━━━━━━━━━━━━━━━━\n\n");
        desc.append("🎁 **").append(data.prize).append("**\n\n");
        desc.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        desc.append("📋 **Details:**\n");
        desc.append("• 👑 Host: <@").append(data.hostId).append(">\n");
        desc.append("• 🏆 Gewinner: **").append(data.winnersCount).append("**\n");
        desc.append("• ⏰ Endet: <t:").append(endsAtEpoch).append(":R>\n\n");

        if ("live".equals(data.visibility)) {
            desc.append("📊 **Live-Statistiken:**\n");
            desc.append("```ansi\n");
            desc.append("\u001b[1;35m┌─────────────────────────┐\u001b[0m\n");
            desc.append("\u001b[1;35m│\u001b[0m  👥 Teilnehmer: \u001b[1;32m").append(String.format("%-7d", totalEntrants)).append("\u001b[1;35m│\u001b[0m\n");
            desc.append("\u001b[1;35m│\u001b[0m  🎯 Gewinnchance: ");
            if (totalEntrants > 0) {
                double chance = (double) data.winnersCount / totalEntrants * 100;
                desc.append("\u001b[1;33m").append(String.format("%-5.1f%%", Math.min(chance, 100))).append("\u001b[0m");
            } else {
                desc.append("\u001b[1;33m100% \u001b[0m");
            }
            desc.append(" \u001b[1;35m│\u001b[0m\n");
            desc.append("\u001b[1;35m└─────────────────────────┘\u001b[0m\n");
            desc.append("```\n");
        }

        desc.append("\n🎮 **Klicke auf den Button um teilzunehmen!**\n");
        desc.append("━━━━━━━━━━━━━━━━━━━━");

        embed.setDescription(desc.toString());
        embed.setFooter("🎉 Giveaway-ID: " + data.id + " • Viel Glück!", null);
        embed.setTimestamp(data.getEndsAtInstant());

        return embed;
    }

    private void handleEnd(SlashCommandInteraction event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ Du brauchst die Berechtigung `Server verwalten`.").setEphemeral(true).queue();
            return;
        }
        var opt = event.getOption("id");
        if (opt == null) {
            event.reply("❌ Bitte gib eine Giveaway-ID an.").setEphemeral(true).queue();
            return;
        }
        String id = opt.getAsString();

        GiveawayData data = null;
        String keyForStore = null;
        for (Map.Entry<String, GiveawayData> entry : store.getAll().entrySet()) {
            if (entry.getValue() != null && id.equals(entry.getValue().id)) {
                data = entry.getValue();
                keyForStore = entry.getKey();
                break;
            }
        }
        if (data == null) {
            event.reply("❌ Kein Giveaway mit dieser ID gefunden.").setEphemeral(true).queue();
            return;
        }
        if (!data.guildId.equals(event.getGuild().getId())) {
            event.reply("❌ Dieses Giveaway gehört zu einem anderen Server.").setEphemeral(true).queue();
            return;
        }
        if (data.isEnded()) {
            event.reply("ℹ️ Dieses Giveaway ist bereits beendet.").setEphemeral(true).queue();
            return;
        }

        // Gewinner ziehen
        java.util.Map<String, Integer> weights = new java.util.HashMap<>();
        if (data.entrants != null) {
            for (Map.Entry<String, GiveawayData.Entrant> e : data.entrants.entrySet()) {
                int entries = e.getValue() != null ? e.getValue().entries : 1;
                if (entries <= 0) entries = 1;
                weights.put(e.getKey(), entries);
            }
        }

        java.util.List<String> winners = WeightedPicker.pickWeighted(weights, data.winnersCount);
        data.winners = winners;
        data.status = "ended";
        data.visibility = "final";
        data.lastEditAt = Instant.now().toString();

        if (keyForStore != null) store.put(keyForStore, data);

        TextChannel ch = event.getGuild().getTextChannelById(data.channelId);
        if (ch == null || data.messageId == null) {
            event.reply("✅ Giveaway beendet, aber die ursprüngliche Nachricht wurde nicht gefunden.").setEphemeral(true).queue();
            return;
        }

        // lokale, final Variablen für das Lambda
        final GiveawayData dataFinal = data;
        final java.util.List<String> winnersFinal = new java.util.ArrayList<>(winners);

        ch.retrieveMessageById(data.messageId).queue(message -> {
            // Finales Embed bauen
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(new Color(0x57F287));
            eb.setTitle("🎉 Giveaway beendet: " + dataFinal.title);

            StringBuilder desc = new StringBuilder();
            desc.append("**🎁 Preis:** ").append(dataFinal.prize).append("\n");
            desc.append("**👥 Gewinner:** ").append(dataFinal.winnersCount).append("\n");
            desc.append("**⏰ Beendet:** <t:").append(Instant.now().getEpochSecond()).append(":R>\n\n");

            int totalEntrants = dataFinal.entrants != null ? dataFinal.entrants.size() : 0;
            desc.append("**📊 Teilnehmer:** ").append(totalEntrants).append("\n\n");

            if (winnersFinal.isEmpty()) {
                desc.append("Leider hat niemand teilgenommen – keine Gewinner.");
            } else {
                desc.append("**Gewinner:**\n");
                for (String userId : winnersFinal) {
                    desc.append("• <@").append(userId).append(">\n");
                }
            }

            eb.setDescription(desc.toString());
            eb.setFooter("Giveaway-ID: " + dataFinal.id, null);
            eb.setTimestamp(Instant.now());

            message.editMessageEmbeds(eb.build())
                    .setActionRow(Button.primary("gaw:enter:" + dataFinal.id, "🎁 Beendet").asDisabled())
                    .queue();

            if (!winnersFinal.isEmpty()) {
                StringBuilder ping = new StringBuilder("🎉 Glückwunsch an die Gewinner: ");
                for (String userId : winnersFinal) {
                    ping.append("<@").append(userId).append("> ");
                }
                ch.sendMessage(ping.toString()).queue();
            }

            event.reply("✅ Giveaway wurde beendet.").setEphemeral(true).queue();
        }, failure -> {
            event.reply("✅ Giveaway-Daten beendet, aber Nachricht konnte nicht geladen werden.").setEphemeral(true).queue();
        });
    }

    private void handleList(SlashCommandInteraction event) {
        Map<String, GiveawayData> gaws = store.getByGuild(event.getGuild().getId());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📋 Aktive Giveaways");
        embed.setColor(Color.decode("#5865F2"));

        if (gaws.isEmpty()) {
            embed.setDescription("Keine aktiven Giveaways vorhanden.");
        } else {
            StringBuilder desc = new StringBuilder();
            gaws.values().stream()
                .filter(GiveawayData::isActive)
                .forEach(gaw -> {
                    desc.append("**").append(gaw.title).append("**\n");
                    desc.append("└ Status: ").append(gaw.status).append("\n");
                    desc.append("└ ID: `").append(gaw.id).append("`\n\n");
                });
            embed.setDescription(desc.toString());
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    @Override
    public CommandData getSlashCommandData() {
        return Commands.slash("gaw", "Giveaway-System")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .setGuildOnly(true)
                .addSubcommands(
                        new SubcommandData("create", "Erstelle ein Giveaway")
                                .addOption(OptionType.STRING, "title", "Titel des Giveaways", true)
                                .addOption(OptionType.STRING, "prize", "Der Preis", true)
                                .addOption(OptionType.STRING, "duration", "Dauer (z.B. 45m, 2h, 1d)", true)
                                .addOption(OptionType.INTEGER, "winners", "Anzahl Gewinner (1-20)", false),
                        new SubcommandData("end", "Beende ein Giveaway")
                                .addOption(OptionType.STRING, "id", "Giveaway-ID", true),
                        new SubcommandData("list", "Liste alle aktiven Giveaways")
                );
    }

    public static void updateGiveawayEmbedInMessage(Message message, GiveawayData data) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎉 GIVEAWAY 🎉");
        embed.setColor(new Color(0xFF69B4));

        int totalEntrants = data.entrants != null ? data.entrants.size() : 0;
        long endsAtEpoch = data.getEndsAtInstant().getEpochSecond();

        StringBuilder desc = new StringBuilder();
        desc.append("━━━━━━━━━━━━━━━━━━━━\n\n");
        desc.append("🎁 **").append(data.prize).append("**\n\n");
        desc.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        desc.append("📋 **Details:**\n");
        desc.append("• 👑 Host: <@").append(data.hostId).append(">\n");
        desc.append("• 🏆 Gewinner: **").append(data.winnersCount).append("**\n");
        desc.append("• ⏰ Endet: <t:").append(endsAtEpoch).append(":R>\n\n");

        desc.append("📊 **Live-Statistiken:**\n");
        desc.append("```ansi\n");
        desc.append("\u001b[1;35m┌─────────────────────────┐\u001b[0m\n");
        desc.append("\u001b[1;35m│\u001b[0m  👥 Teilnehmer: \u001b[1;32m").append(String.format("%-7d", totalEntrants)).append("\u001b[1;35m│\u001b[0m\n");
        desc.append("\u001b[1;35m│\u001b[0m  🎯 Gewinnchance: ");
        if (totalEntrants > 0) {
            double chance = (double) data.winnersCount / totalEntrants * 100;
            desc.append("\u001b[1;33m").append(String.format("%-5.1f%%", Math.min(chance, 100))).append("\u001b[0m");
        } else {
            desc.append("\u001b[1;33m100% \u001b[0m");
        }
        desc.append(" \u001b[1;35m│\u001b[0m\n");
        desc.append("\u001b[1;35m└─────────────────────────┘\u001b[0m\n");
        desc.append("```\n");

        desc.append("\n🎮 **Klicke auf den Button um teilzunehmen!**\n");
        desc.append("━━━━━━━━━━━━━━━━━━━━");

        embed.setDescription(desc.toString());
        embed.setFooter("🎉 Giveaway-ID: " + data.id + " • Viel Glück!", null);
        embed.setTimestamp(data.getEndsAtInstant());

        message.editMessageEmbeds(embed.build()).queue();
    }
}
