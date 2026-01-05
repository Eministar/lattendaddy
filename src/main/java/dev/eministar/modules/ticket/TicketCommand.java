package dev.eministar.modules.ticket;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class TicketCommand implements Command {

    @Override
    public String name() {
        return "ticket";
    }

    @Override
    public String description() {
        return "Ticket-System verwalten";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        event.getChannel().sendMessage(EmojiUtil.wrap("ℹ️") + " Bitte nutze `/ticket` für das Ticket-System!").queue();
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (event.getGuild() == null) {
            event.reply(EmojiUtil.wrap("❌") + " Dieser Command funktioniert nur auf Servern!").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply(EmojiUtil.wrap("❌") + " Fehler: Kein Subcommand gefunden!").setEphemeral(true).queue();
            return;
        }

        switch (subcommand) {
            case "setup" -> handleSetup(event);
            case "panel" -> handlePanel(event);
            case "stats" -> handleStats(event);
            case "list" -> handleList(event);
            case "info" -> handleInfo(event);
            case "add" -> handleAddUser(event);
            case "remove" -> handleRemoveUser(event);
            case "close" -> handleClose(event);
            case "claim" -> handleClaim(event);
            case "unclaim" -> handleUnclaim(event);
            case "rename" -> handleRename(event);
            case "leaderboard" -> handleLeaderboard(event);
            default -> event.reply(EmojiUtil.wrap("❌") + " Unbekannter Befehl!").setEphemeral(true).queue();
        }
    }

    private void handleSetup(SlashCommandInteraction event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply(EmojiUtil.wrap("❌") + " Du benötigst Administrator-Rechte!").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue(hook -> {
            hook.editOriginal(EmojiUtil.wrap("✅") + " Ticket-System wurde eingerichtet!\n" +
                    "Nutze `/ticket panel` um das Ticket-Panel zu senden.").queue();
        });
    }

    private void handlePanel(SlashCommandInteraction event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply(EmojiUtil.wrap("❌") + " Du benötigst Administrator-Rechte!").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        int todayCount = TicketService.getTicketsClosedToday(guildId);
        int alltimeCount = TicketService.getTicketsClosedAllTime(guildId);
        int openCount = TicketService.getOpenTickets(guildId).size();
        long avgTime = TicketService.getAverageCloseTimeMinutes(guildId);

        event.deferReply(true).queue(hook -> {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(EmojiUtil.wrap("🎫") + " Support Ticket System");
            embed.setDescription(
                    EmojiUtil.wrap("👋") + " **Willkommen beim Support!**\n\n" +
                    "Benötigst du Hilfe oder möchtest dich bewerben?\n" +
                    "Wähle einfach eine Kategorie aus dem Menü unten!\n\n" +
                    "━━━━━━━━━━━━━━━━━━━━\n" +
                    EmojiUtil.wrap("🎫") + " **Support** - Allgemeine Hilfe & Fragen\n" +
                    EmojiUtil.wrap("📝") + " **Bewerbung** - Bewirb dich im Team\n" +
                    EmojiUtil.wrap("⚠️") + " **Report** - Melde Regelverstöße\n" +
                    EmojiUtil.wrap("🎉") + " **Event** - Event-Ideen & Fragen\n\n" +
                    "━━━━━━━━━━━━━━━━━━━━\n" +
                    EmojiUtil.wrap("📊") + " **Statistiken:**\n" +
                    "• " + EmojiUtil.wrap("📂") + " Offene Tickets: **" + openCount + "**\n" +
                    "• " + EmojiUtil.wrap("✅") + " Heute bearbeitet: **" + todayCount + "**\n" +
                    "• " + EmojiUtil.wrap("📈") + " Alltime bearbeitet: **" + alltimeCount + "**\n" +
                    "• " + EmojiUtil.wrap("⏱️") + " Ø Bearbeitungszeit: **" + formatTime(avgTime) + "**\n\n" +
                    EmojiUtil.wrap("✨") + " *Unser Team hilft dir gerne weiter!*"
            );
            embed.setColor(new Color(0x5865F2));
            embed.setFooter("Lattendaddy Ticket System • Zuletzt aktualisiert", null);
            embed.setTimestamp(Instant.now());
            if (event.getGuild() != null && event.getGuild().getIconUrl() != null) {
                embed.setThumbnail(event.getGuild().getIconUrl());
            }

            StringSelectMenu menu = StringSelectMenu.create("ticket:create")
                    .setPlaceholder("🎫 Wähle eine Kategorie...")
                    .addOption(TicketCategory.SUPPORT.getDisplayName(), "SUPPORT",
                            TicketCategory.SUPPORT.getDescription(),
                            net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🎫"))
                    .addOption(TicketCategory.BEWERBUNG.getDisplayName(), "BEWERBUNG",
                            TicketCategory.BEWERBUNG.getDescription(),
                            net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("📝"))
                    .addOption(TicketCategory.REPORT.getDisplayName(), "REPORT",
                            TicketCategory.REPORT.getDescription(),
                            net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("⚠️"))
                    .addOption(TicketCategory.EVENT.getDisplayName(), "EVENT",
                            TicketCategory.EVENT.getDescription(),
                            net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🎉"))
                    .build();

            event.getChannel().sendMessageEmbeds(embed.build())
                    .setActionRow(menu)
                    .queue(
                        success -> hook.editOriginal(EmojiUtil.wrap("✅") + " Ticket-Panel wurde gesendet!").queue(),
                        error -> hook.editOriginal(EmojiUtil.wrap("❌") + " Fehler beim Senden des Panels!").queue()
                    );
        });
    }

    private void handleStats(SlashCommandInteraction event) {
        String guildId = event.getGuild().getId();

        int todayCount = TicketService.getTicketsClosedToday(guildId);
        int alltimeCount = TicketService.getTicketsClosedAllTime(guildId);
        int openCount = TicketService.getOpenTickets(guildId).size();
        long avgTime = TicketService.getAverageCloseTimeMinutes(guildId);
        int totalTickets = TicketService.getAllTickets(guildId).size();

        // Personal stats for the user
        String userId = event.getUser().getId();
        int userTodayCount = TicketService.getTicketsClaimedTodayByUser(guildId, userId);
        int userAlltimeCount = TicketService.getTicketsClaimedAllTimeByUser(guildId, userId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("📊") + " Ticket-Statistiken");
        embed.setColor(new Color(0x5865F2));

        StringBuilder desc = new StringBuilder();
        desc.append("**").append(EmojiUtil.wrap("🏠")).append(" Server-Statistiken:**\n");
        desc.append("━━━━━━━━━━━━━━━━━━━━\n");
        desc.append(EmojiUtil.wrap("📂")).append(" Offene Tickets: **").append(openCount).append("**\n");
        desc.append(EmojiUtil.wrap("✅")).append(" Heute geschlossen: **").append(todayCount).append("**\n");
        desc.append(EmojiUtil.wrap("📈")).append(" Alltime geschlossen: **").append(alltimeCount).append("**\n");
        desc.append(EmojiUtil.wrap("🎫")).append(" Gesamte Tickets: **").append(totalTickets).append("**\n");
        desc.append(EmojiUtil.wrap("⏱️")).append(" Ø Bearbeitungszeit: **").append(formatTime(avgTime)).append("**\n\n");

        desc.append("**").append(EmojiUtil.wrap("👤")).append(" Deine Statistiken:**\n");
        desc.append("━━━━━━━━━━━━━━━━━━━━\n");
        desc.append(EmojiUtil.wrap("✅")).append(" Heute bearbeitet: **").append(userTodayCount).append("**\n");
        desc.append(EmojiUtil.wrap("📈")).append(" Alltime bearbeitet: **").append(userAlltimeCount).append("**\n");

        embed.setDescription(desc.toString());
        embed.setFooter("Ticket-Statistiken • " + event.getGuild().getName(), event.getGuild().getIconUrl());
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleList(SlashCommandInteraction event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply(EmojiUtil.wrap("❌") + " Du benötigst die Berechtigung Kanäle zu verwalten!").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        List<Ticket> openTickets = TicketService.getOpenTickets(guildId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("📋") + " Offene Tickets");
        embed.setColor(new Color(0x5865F2));

        if (openTickets.isEmpty()) {
            embed.setDescription(EmojiUtil.wrap("✨") + " Keine offenen Tickets vorhanden!");
        } else {
            StringBuilder desc = new StringBuilder();
            for (Ticket ticket : openTickets) {
                String status = ticket.getStatus() == Ticket.TicketStatus.CLAIMED ?
                        EmojiUtil.wrap("✋") + " Claimed" : EmojiUtil.wrap("📂") + " Offen";
                String prio = switch (ticket.getPriority()) {
                    case HIGH -> EmojiUtil.wrap("🔴") + " HIGH";
                    case LOW -> EmojiUtil.wrap("🟢") + " LOW";
                    default -> EmojiUtil.wrap("🟡") + " NORMAL";
                };

                desc.append("**").append(ticket.getTicketId()).append("**\n");
                desc.append("└ ").append(ticket.getCategory().getFormattedName()).append(" | ").append(status).append(" | ").append(prio).append("\n");
                if (ticket.getClaimedBy() != null) {
                    desc.append("└ Claimed by: <@").append(ticket.getClaimedBy()).append(">\n");
                }
                desc.append("\n");
            }
            embed.setDescription(desc.toString());
        }

        embed.setFooter("Offene Tickets: " + openTickets.size(), null);
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleInfo(SlashCommandInteraction event) {
        String ticketId = event.getOption("ticket-id") != null ?
                event.getOption("ticket-id").getAsString() : null;

        String guildId = event.getGuild().getId();
        Ticket ticket;

        if (ticketId != null) {
            ticket = TicketService.getTicket(guildId, ticketId).orElse(null);
        } else {
            // Try to find ticket by current channel
            ticket = TicketService.getTicketByChannel(guildId, event.getChannel().getId()).orElse(null);
        }

        if (ticket == null) {
            event.reply(EmojiUtil.wrap("❌") + " Ticket nicht gefunden!").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("🎫") + " Ticket Info: " + ticket.getTicketId());
        embed.setColor(switch (ticket.getStatus()) {
            case OPEN -> new Color(0x57F287);
            case CLAIMED -> new Color(0xFEE75C);
            case CLOSED -> new Color(0xED4245);
        });

        StringBuilder desc = new StringBuilder();
        desc.append("**").append(EmojiUtil.wrap("📋")).append(" Details:**\n");
        desc.append("━━━━━━━━━━━━━━━━━━━━\n");
        desc.append(EmojiUtil.wrap("🏷️")).append(" **ID:** `").append(ticket.getTicketId()).append("`\n");
        desc.append(EmojiUtil.wrap("📁")).append(" **Kategorie:** ").append(ticket.getCategory().getFormattedName()).append("\n");
        desc.append(EmojiUtil.wrap("👤")).append(" **Ersteller:** <@").append(ticket.getUserId()).append(">\n");
        desc.append(EmojiUtil.wrap("📊")).append(" **Status:** ").append(formatStatus(ticket.getStatus())).append("\n");
        desc.append(EmojiUtil.wrap("⚡")).append(" **Priorität:** ").append(formatPriority(ticket.getPriority())).append("\n");

        if (ticket.getClaimedBy() != null) {
            desc.append(EmojiUtil.wrap("✋")).append(" **Bearbeitet von:** <@").append(ticket.getClaimedBy()).append(">\n");
        }

        desc.append("\n**").append(EmojiUtil.wrap("📝")).append(" Grund:**\n```").append(ticket.getReason()).append("```\n");

        // Timestamps
        desc.append("\n**").append(EmojiUtil.wrap("⏰")).append(" Zeitstempel:**\n");
        desc.append("• Erstellt: <t:").append(ticket.getCreatedAt() / 1000).append(":R>\n");
        if (ticket.getClosedAt() > 0) {
            desc.append("• Geschlossen: <t:").append(ticket.getClosedAt() / 1000).append(":R>\n");
            long durationMinutes = (ticket.getClosedAt() - ticket.getCreatedAt()) / (1000 * 60);
            desc.append("• Bearbeitungszeit: ").append(formatTime(durationMinutes)).append("\n");
        }

        // Notes
        if (!ticket.getNotes().isEmpty()) {
            desc.append("\n**").append(EmojiUtil.wrap("📌")).append(" Notizen (").append(ticket.getNotes().size()).append("):**\n");
            for (Ticket.TicketNote note : ticket.getNotes()) {
                desc.append("• <@").append(note.getAuthorId()).append(">: ").append(note.getContent()).append("\n");
            }
        }

        embed.setDescription(desc.toString());
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleAddUser(SlashCommandInteraction event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply(EmojiUtil.wrap("❌") + " Du benötigst die Berechtigung!").setEphemeral(true).queue();
            return;
        }

        Member target = event.getOption("user").getAsMember();
        if (target == null) {
            event.reply(EmojiUtil.wrap("❌") + " Benutzer nicht gefunden!").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        Ticket ticket = TicketService.getTicketByChannel(guildId, event.getChannel().getId()).orElse(null);

        if (ticket == null) {
            event.reply(EmojiUtil.wrap("❌") + " Dies ist kein Ticket-Kanal!").setEphemeral(true).queue();
            return;
        }

        event.getChannel().asTextChannel().getManager()
                .putMemberPermissionOverride(target.getIdLong(),
                        java.util.EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null)
                .queue(
                        success -> event.reply(EmojiUtil.wrap("✅") + " " + target.getAsMention() + " wurde zum Ticket hinzugefügt!").queue(),
                        error -> event.reply(EmojiUtil.wrap("❌") + " Fehler beim Hinzufügen!").setEphemeral(true).queue()
                );
    }

    private void handleRemoveUser(SlashCommandInteraction event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply(EmojiUtil.wrap("❌") + " Du benötigst die Berechtigung!").setEphemeral(true).queue();
            return;
        }

        Member target = event.getOption("user").getAsMember();
        if (target == null) {
            event.reply(EmojiUtil.wrap("❌") + " Benutzer nicht gefunden!").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        Ticket ticket = TicketService.getTicketByChannel(guildId, event.getChannel().getId()).orElse(null);

        if (ticket == null) {
            event.reply(EmojiUtil.wrap("❌") + " Dies ist kein Ticket-Kanal!").setEphemeral(true).queue();
            return;
        }

        event.getChannel().asTextChannel().getManager()
                .removePermissionOverride(target.getIdLong())
                .queue(
                        success -> event.reply(EmojiUtil.wrap("✅") + " " + target.getAsMention() + " wurde aus dem Ticket entfernt!").queue(),
                        error -> event.reply(EmojiUtil.wrap("❌") + " Fehler beim Entfernen!").setEphemeral(true).queue()
                );
    }

    private void handleClose(SlashCommandInteraction event) {
        String guildId = event.getGuild().getId();
        Ticket ticket = TicketService.getTicketByChannel(guildId, event.getChannel().getId()).orElse(null);

        if (ticket == null) {
            event.reply(EmojiUtil.wrap("❌") + " Dies ist kein Ticket-Kanal!").setEphemeral(true).queue();
            return;
        }

        if (ticket.getStatus() == Ticket.TicketStatus.CLOSED) {
            event.reply(EmojiUtil.wrap("❌") + " Dieses Ticket ist bereits geschlossen!").setEphemeral(true).queue();
            return;
        }

        ticket.setStatus(Ticket.TicketStatus.CLOSED);
        ticket.setClosedAt(System.currentTimeMillis());
        TicketService.updateTicket(ticket);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("🔒") + " Ticket geschlossen");
        embed.setDescription("Dieses Ticket wird in 5 Sekunden gelöscht.\n\n" +
                "**Geschlossen von:** " + event.getUser().getAsMention());
        embed.setColor(new Color(0xED4245));
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue(hook -> {
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    event.getChannel().delete().queue();
                } catch (Exception ignored) {}
            }).start();
        });
    }

    private void handleClaim(SlashCommandInteraction event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply(EmojiUtil.wrap("❌") + " Du benötigst die Berechtigung!").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        Ticket ticket = TicketService.getTicketByChannel(guildId, event.getChannel().getId()).orElse(null);

        if (ticket == null) {
            event.reply(EmojiUtil.wrap("❌") + " Dies ist kein Ticket-Kanal!").setEphemeral(true).queue();
            return;
        }

        if (ticket.getClaimedBy() != null) {
            event.reply(EmojiUtil.wrap("❌") + " Dieses Ticket wurde bereits von <@" + ticket.getClaimedBy() + "> geclaimed!").setEphemeral(true).queue();
            return;
        }

        ticket.setClaimedBy(event.getUser().getId());
        ticket.setStatus(Ticket.TicketStatus.CLAIMED);
        TicketService.updateTicket(ticket);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setDescription(EmojiUtil.wrap("✋") + " **Ticket geclaimed!**\n\n" +
                event.getUser().getAsMention() + " kümmert sich jetzt um dieses Ticket.");
        embed.setColor(new Color(0xFEE75C));
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleUnclaim(SlashCommandInteraction event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply(EmojiUtil.wrap("❌") + " Du benötigst die Berechtigung!").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        Ticket ticket = TicketService.getTicketByChannel(guildId, event.getChannel().getId()).orElse(null);

        if (ticket == null) {
            event.reply(EmojiUtil.wrap("❌") + " Dies ist kein Ticket-Kanal!").setEphemeral(true).queue();
            return;
        }

        if (ticket.getClaimedBy() == null) {
            event.reply(EmojiUtil.wrap("❌") + " Dieses Ticket ist nicht geclaimed!").setEphemeral(true).queue();
            return;
        }

        // Only the claimer or admins can unclaim
        if (!ticket.getClaimedBy().equals(event.getUser().getId()) &&
            !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply(EmojiUtil.wrap("❌") + " Nur der Bearbeiter oder Admins können das Ticket freigeben!").setEphemeral(true).queue();
            return;
        }

        String previousClaimer = ticket.getClaimedBy();
        ticket.setClaimedBy(null);
        ticket.setStatus(Ticket.TicketStatus.OPEN);
        TicketService.updateTicket(ticket);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setDescription(EmojiUtil.wrap("🔓") + " **Ticket freigegeben!**\n\n" +
                "Das Ticket ist jetzt wieder offen und kann von anderen Team-Mitgliedern übernommen werden.\n" +
                "**Vorher bearbeitet von:** <@" + previousClaimer + ">");
        embed.setColor(new Color(0x57F287));
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleRename(SlashCommandInteraction event) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply(EmojiUtil.wrap("❌") + " Du benötigst die Berechtigung!").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        Ticket ticket = TicketService.getTicketByChannel(guildId, event.getChannel().getId()).orElse(null);

        if (ticket == null) {
            event.reply(EmojiUtil.wrap("❌") + " Dies ist kein Ticket-Kanal!").setEphemeral(true).queue();
            return;
        }

        String newName = event.getOption("name").getAsString();
        String safeName = newName.toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        if (safeName.length() > 100) safeName = safeName.substring(0, 100);

        final String finalName = safeName;
        event.getChannel().asTextChannel().getManager().setName(safeName).queue(
            success -> event.reply(EmojiUtil.wrap("✅") + " Kanal wurde zu `" + finalName + "` umbenannt!").setEphemeral(true).queue(),
            error -> event.reply(EmojiUtil.wrap("❌") + " Fehler beim Umbenennen!").setEphemeral(true).queue()
        );
    }

    private void handleLeaderboard(SlashCommandInteraction event) {
        String guildId = event.getGuild().getId();
        List<Ticket> allTickets = TicketService.getAllTickets(guildId);

        // Count closed tickets per claimer
        Map<String, Integer> claimerCounts = new java.util.HashMap<>();
        for (Ticket t : allTickets) {
            if (t.getStatus() == Ticket.TicketStatus.CLOSED && t.getClaimedBy() != null) {
                claimerCounts.merge(t.getClaimedBy(), 1, Integer::sum);
            }
        }

        if (claimerCounts.isEmpty()) {
            event.reply(EmojiUtil.wrap("📊") + " Noch keine Ticket-Statistiken vorhanden!").setEphemeral(true).queue();
            return;
        }

        // Sort by count descending
        List<Map.Entry<String, Integer>> sorted = claimerCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .toList();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("🏆") + " Ticket-Leaderboard");
        embed.setColor(new Color(0xFFD700));

        StringBuilder desc = new StringBuilder();
        desc.append("**Top 10 Ticket-Bearbeiter (Alltime)**\n");
        desc.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        int rank = 1;
        for (Map.Entry<String, Integer> entry : sorted) {
            String medal = switch (rank) {
                case 1 -> "🥇";
                case 2 -> "🥈";
                case 3 -> "🥉";
                default -> "**#" + rank + "**";
            };
            desc.append(medal).append(" <@").append(entry.getKey()).append("> — **")
                    .append(entry.getValue()).append("** Tickets\n");
            rank++;
        }

        embed.setDescription(desc.toString());
        embed.setFooter("Ticket-Leaderboard • " + event.getGuild().getName(), event.getGuild().getIconUrl());
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private String formatTime(long minutes) {
        if (minutes < 60) return minutes + " Min";
        long hours = minutes / 60;
        long mins = minutes % 60;
        if (hours < 24) return hours + "h " + mins + "m";
        long days = hours / 24;
        hours = hours % 24;
        return days + "d " + hours + "h";
    }

    private String formatStatus(Ticket.TicketStatus status) {
        return switch (status) {
            case OPEN -> EmojiUtil.wrap("🟢") + " Offen";
            case CLAIMED -> EmojiUtil.wrap("🟡") + " In Bearbeitung";
            case CLOSED -> EmojiUtil.wrap("🔴") + " Geschlossen";
        };
    }

    private String formatPriority(Ticket.TicketPriority priority) {
        return switch (priority) {
            case HIGH -> EmojiUtil.wrap("🔴") + " Hoch";
            case NORMAL -> EmojiUtil.wrap("🟡") + " Normal";
            case LOW -> EmojiUtil.wrap("🟢") + " Niedrig";
        };
    }

    @Override
    public net.dv8tion.jda.api.interactions.commands.build.CommandData getSlashCommandData() {
        return Commands.slash("ticket", description())
                .addSubcommands(
                        new SubcommandData("setup", "Richte das Ticket-System ein"),
                        new SubcommandData("panel", "Sende das Ticket-Panel"),
                        new SubcommandData("stats", "Zeige Ticket-Statistiken"),
                        new SubcommandData("list", "Zeige alle offenen Tickets"),
                        new SubcommandData("info", "Zeige Info zu einem Ticket")
                                .addOption(OptionType.STRING, "ticket-id", "Ticket-ID (optional)", false),
                        new SubcommandData("add", "Füge einen Benutzer zum Ticket hinzu")
                                .addOption(OptionType.USER, "user", "Der Benutzer", true),
                        new SubcommandData("remove", "Entferne einen Benutzer aus dem Ticket")
                                .addOption(OptionType.USER, "user", "Der Benutzer", true),
                        new SubcommandData("close", "Schließe das aktuelle Ticket"),
                        new SubcommandData("claim", "Übernimm das aktuelle Ticket"),
                        new SubcommandData("unclaim", "Gib das aktuelle Ticket wieder frei"),
                        new SubcommandData("rename", "Benenne den Ticket-Kanal um")
                                .addOption(OptionType.STRING, "name", "Neuer Name", true),
                        new SubcommandData("leaderboard", "Zeige Top Ticket-Bearbeiter")
                );
    }
}


