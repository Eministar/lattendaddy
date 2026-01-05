package dev.eministar.modules.poll;

import dev.eministar.command.Command;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class PollModule extends ListenerAdapter implements Command {
    private final PollStore store;
    private final Map<String, Long> updateDebounce;
    private static final long DEBOUNCE_MS = 2000;

    public PollModule() {
        this.store = new PollStore("./data/polls.json");
        this.updateDebounce = new HashMap<>();
    }

    @Override
    public String name() {
        return "poll";
    }

    @Override
    public String description() {
        return "Fortgeschrittenes Umfrage-System mit Live-Stats";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        event.getChannel().sendMessage("❌ Nutze `/poll` Slash-Commands!").queue();
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
            case "close":
                handleClose(event);
                break;
            case "results":
                handleResults(event);
                break;
            default:
                event.reply("❌ Unbekannter Command").setEphemeral(true).queue();
        }
    }

    private void handleCreate(SlashCommandInteraction event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ Du benötigst `Server verwalten` Berechtigung!").setEphemeral(true).queue();
            return;
        }

        String title = event.getOption("title").getAsString();
        String optionsStr = event.getOption("options").getAsString();

        if (title.length() > 120) {
            event.reply("❌ Titel zu lang! Max: 120 Zeichen").setEphemeral(true).queue();
            return;
        }

        String[] optionArray = optionsStr.split("[|,]");
        List<String> optionsList = Arrays.stream(optionArray)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .limit(10)
            .collect(Collectors.toList());

        if (optionsList.size() < 2) {
            event.reply("❌ Mindestens 2 Optionen erforderlich!").setEphemeral(true).queue();
            return;
        }

        String description = event.getOption("description") != null ?
            event.getOption("description").getAsString() : null;

        String durationStr = event.getOption("duration") != null ?
            event.getOption("duration").getAsString() : "60m";

        boolean multi = event.getOption("multi") != null &&
            event.getOption("multi").getAsBoolean();

        int maxChoices = event.getOption("max-choices") != null ?
            event.getOption("max-choices").getAsInt() : 1;

        boolean anonymous = event.getOption("anonymous") != null ?
            event.getOption("anonymous").getAsBoolean() : true;

        String visibility = event.getOption("visibility") != null ?
            event.getOption("visibility").getAsString() : "live";

        Duration duration;
        try {
            duration = PollTimeParser.parse(durationStr);
        } catch (Exception e) {
            event.reply("❌ Ungültiges Format! Nutze: 45m, 2h, 1d").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        PollData poll = new PollData();
        poll.id = store.generateId();
        poll.guildId = event.getGuild().getId();
        poll.channelId = event.getChannel().getId();
        poll.creatorId = event.getUser().getId();
        poll.title = title;
        poll.description = description;
        poll.anonymous = anonymous;
        poll.allowVoteChange = true;
        poll.visibility = visibility;
        poll.startedAt = Instant.now().toString();
        poll.endsAt = Instant.now().plus(duration).toString();
        poll.status = "open";
        poll.multi.enabled = multi;
        if (multi) {
            poll.multi.maxChoices = Math.min(maxChoices, optionsList.size());
        }

        char optionId = 'A';
        for (String optionLabel : optionsList) {
            if (optionLabel.length() > 80) {
                optionLabel = optionLabel.substring(0, 80);
            }
            poll.options.add(new PollData.PollOption(String.valueOf(optionId), optionLabel));
            poll.totals.put(String.valueOf(optionId), 0);
            optionId++;
        }

        EmbedBuilder embed = buildPollEmbed(poll);

        List<Button> buttons = new ArrayList<>();
        for (PollData.PollOption option : poll.options) {
            String label = option.label.length() > 20 ? option.label.substring(0, 20) + "..." : option.label;
            buttons.add(Button.primary("poll:vote:" + poll.id + ":" + option.id, option.id + ": " + label));
        }
        buttons.add(Button.secondary("poll:myvote:" + poll.id, "🗳️ Meine Stimme"));
        buttons.add(Button.danger("poll:end:" + poll.id, "🛑 Beenden"));

        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 5) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
        }

        TextChannel channel = event.getGuild().getTextChannelById(poll.channelId);
        if (channel == null) {
            event.getHook().editOriginal("❌ Kanal nicht gefunden!").queue();
            return;
        }

        channel.sendMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue(message -> {
                poll.messageId = message.getId();
                store.put(poll.guildId + ":" + poll.messageId, poll);

                message.pin().queue(null, e -> {});

                message.createThreadChannel(title + " - Diskussion")
                    .queue(thread -> {
                        poll.threadId = thread.getId();
                        store.put(poll.guildId + ":" + poll.messageId, poll);
                    }, e -> {});

                event.getHook().editOriginal("✅ Umfrage erstellt: " + message.getJumpUrl()).queue();
            });
    }

    private EmbedBuilder buildPollEmbed(PollData poll) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📊 " + poll.title);

        // Dynamic color based on status
        if (poll.isClosed()) {
            embed.setColor(new Color(0x57F287)); // Green for closed
        } else {
            embed.setColor(new Color(0x5865F2)); // Blue for active
        }

        StringBuilder desc = new StringBuilder();

        // Header separator
        desc.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        if (poll.description != null && !poll.description.isEmpty()) {
            desc.append("*").append(poll.description).append("*\n\n");
        }

        // Poll mode info
        desc.append("📋 **Modus:** ");
        if (poll.multi.enabled) {
            desc.append("Multi-Choice (max ").append(poll.multi.maxChoices).append(")");
        } else {
            desc.append("Single-Choice");
        }
        desc.append(poll.anonymous ? " • 🔒 Anonym" : " • 👁️ Öffentlich");
        desc.append("\n\n");

        boolean showStats = "live".equals(poll.visibility) || poll.isClosed();
        int totalVotes = poll.getTotalVotes();

        // Options with progress bars
        desc.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        for (PollData.PollOption option : poll.options) {
            int votes = poll.totals.getOrDefault(option.id, 0);
            double percent = totalVotes > 0 ? (double) votes / totalVotes * 100 : 0;

            // Option header
            desc.append("**").append(option.id).append(": ").append(option.label).append("**\n");

            if (showStats) {
                // Progress bar
                int filledBlocks = (int) Math.round(percent / 10);
                desc.append("```\n");
                desc.append("[");
                for (int i = 0; i < 10; i++) {
                    desc.append(i < filledBlocks ? "█" : "░");
                }
                desc.append("] ").append(String.format("%5.1f%%", percent));
                desc.append(" (").append(votes).append(")\n");
                desc.append("```\n");
            } else {
                desc.append("```\n[░░░░░░░░░░] ???%\n```\n");
            }
        }

        // Footer stats
        desc.append("━━━━━━━━━━━━━━━━━━━━\n");
        desc.append("📊 **Gesamt:** ").append(totalVotes).append(" Stimmen\n");

        if (!poll.isClosed()) {
            Duration remaining = Duration.between(Instant.now(), poll.getEndsAtInstant());
            desc.append("⏰ **Endet:** <t:").append(poll.getEndsAtInstant().getEpochSecond()).append(":R>");
        } else {
            desc.append("✅ **Status:** Beendet");
        }

        embed.setDescription(desc.toString());
        embed.setFooter("Poll-ID: " + poll.id + " • Erstellt von User", null);
        embed.setTimestamp(poll.getEndsAtInstant());

        return embed;
    }

    private void handleClose(SlashCommandInteraction event) {
        String pollId = event.getOption("id").getAsString();
        PollData poll = findPollById(event.getGuild().getId(), pollId);

        if (poll == null) {
            event.reply("❌ Umfrage nicht gefunden!").setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        boolean canClose = poll.creatorId.equals(event.getUser().getId()) ||
                          (member != null && member.hasPermission(Permission.MANAGE_SERVER));

        if (!canClose) {
            event.reply("❌ Nur der Ersteller oder Moderatoren können die Umfrage beenden!").setEphemeral(true).queue();
            return;
        }

        poll.status = "closed";
        store.put(poll.guildId + ":" + poll.messageId, poll);

        event.getGuild().getTextChannelById(poll.channelId)
            .retrieveMessageById(poll.messageId)
            .queue(msg -> msg.editMessageEmbeds(buildPollEmbed(poll).build()).queue());

        event.reply("✅ Umfrage beendet!").queue();
    }

    private void handleResults(SlashCommandInteraction event) {
        String pollId = event.getOption("id").getAsString();
        PollData poll = findPollById(event.getGuild().getId(), pollId);

        if (poll == null) {
            event.reply("❌ Umfrage nicht gefunden!").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📊 Ergebnisse: " + poll.title);
        embed.setColor(Color.decode("#57F287"));

        int totalVotes = poll.getTotalVotes();

        List<Map.Entry<String, Integer>> sorted = poll.totals.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .collect(Collectors.toList());

        StringBuilder desc = new StringBuilder();
        desc.append("**Gesamt: ").append(totalVotes).append(" Stimmen**\n\n");

        int rank = 1;
        for (Map.Entry<String, Integer> entry : sorted) {
            PollData.PollOption option = poll.getOptionById(entry.getKey());
            double percent = PercentBarRenderer.calculatePercent(entry.getValue(), totalVotes);

            String medal = rank == 1 ? "🥇 " : rank == 2 ? "🥈 " : rank == 3 ? "🥉 " : "";
            desc.append(medal).append("**").append(option.label).append("**\n");
            desc.append(String.format("└ %.1f%% (%d Stimmen)\n\n", percent, entry.getValue()));
            rank++;
        }

        embed.setDescription(desc.toString());
        event.replyEmbeds(embed.build()).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        if (parts.length < 3 || !"poll".equals(parts[0])) return;

        String action = parts[1];
        String pollId = parts[2];

        PollData poll = findPollById(event.getGuild().getId(), pollId);
        if (poll == null) {
            event.reply("❌ Umfrage nicht gefunden!").setEphemeral(true).queue();
            return;
        }

        switch (action) {
            case "vote":
                if (parts.length < 4) return;
                handleVote(event, poll, parts[3]);
                break;
            case "myvote":
                handleMyVote(event, poll);
                break;
            case "end":
                handleEndButton(event, poll);
                break;
        }
    }

    private void handleVote(ButtonInteractionEvent event, PollData poll, String optionId) {
        if (!poll.isOpen()) {
            event.reply("❌ Diese Umfrage ist geschlossen!").setEphemeral(true).queue();
            return;
        }

        String userId = event.getUser().getId();
        List<String> currentVotes = poll.votes.getOrDefault(userId, new ArrayList<>());

        if (!poll.multi.enabled) {
            for (String oldOption : currentVotes) {
                poll.totals.put(oldOption, poll.totals.getOrDefault(oldOption, 1) - 1);
            }
            currentVotes.clear();
            currentVotes.add(optionId);
            poll.totals.put(optionId, poll.totals.getOrDefault(optionId, 0) + 1);
        } else {
            if (currentVotes.contains(optionId)) {
                currentVotes.remove(optionId);
                poll.totals.put(optionId, poll.totals.getOrDefault(optionId, 1) - 1);
            } else {
                if (currentVotes.size() >= poll.multi.maxChoices) {
                    event.reply("❌ Max " + poll.multi.maxChoices + " Optionen!").setEphemeral(true).queue();
                    return;
                }
                currentVotes.add(optionId);
                poll.totals.put(optionId, poll.totals.getOrDefault(optionId, 0) + 1);
            }
        }

        poll.votes.put(userId, currentVotes);
        store.put(poll.guildId + ":" + poll.messageId, poll);

        event.getMessage().editMessageEmbeds(buildPollEmbed(poll).build()).queue();

        String labels = currentVotes.stream()
            .map(id -> poll.getOptionById(id).label)
            .collect(Collectors.joining(", "));
        event.reply("✅ Gespeichert: " + labels).setEphemeral(true).queue();
    }

    private void handleMyVote(ButtonInteractionEvent event, PollData poll) {
        List<String> votes = poll.votes.getOrDefault(event.getUser().getId(), new ArrayList<>());

        if (votes.isEmpty()) {
            event.reply("❌ Du hast noch nicht abgestimmt!").setEphemeral(true).queue();
            return;
        }

        String labels = votes.stream()
            .map(id -> poll.getOptionById(id).label)
            .collect(Collectors.joining(", "));

        event.reply("🗳️ **Deine Stimme(n):**\n" + labels).setEphemeral(true).queue();
    }

    private void handleEndButton(ButtonInteractionEvent event, PollData poll) {
        Member member = event.getMember();
        boolean canEnd = poll.creatorId.equals(event.getUser().getId()) ||
                        (member != null && member.hasPermission(Permission.MANAGE_SERVER));

        if (!canEnd) {
            event.reply("❌ Nur Ersteller/Mods!").setEphemeral(true).queue();
            return;
        }

        poll.status = "closed";
        store.put(poll.guildId + ":" + poll.messageId, poll);
        event.getMessage().editMessageEmbeds(buildPollEmbed(poll).build()).queue();
        event.reply("✅ Umfrage beendet!").setEphemeral(true).queue();
    }

    private PollData findPollById(String guildId, String pollId) {
        return store.getByGuild(guildId).values().stream()
            .filter(p -> p.id.equals(pollId) || pollId.equals(p.messageId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public CommandData getSlashCommandData() {
        return Commands.slash("poll", "Umfrage-System")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .setGuildOnly(true)
                .addSubcommands(
                        new SubcommandData("create", "Erstelle eine Umfrage")
                                .addOption(OptionType.STRING, "title", "Titel", true)
                                .addOption(OptionType.STRING, "options", "Optionen (| oder , getrennt)", true)
                                .addOption(OptionType.STRING, "description", "Beschreibung", false)
                                .addOption(OptionType.STRING, "duration", "Dauer (z.B. 45m, 2h, 1d)", false)
                                .addOption(OptionType.BOOLEAN, "multi", "Multi-Choice", false)
                                .addOption(OptionType.INTEGER, "max-choices", "Max Auswahlen", false)
                                .addOption(OptionType.BOOLEAN, "anonymous", "Anonym", false)
                                .addOption(OptionType.STRING, "visibility", "live/final", false),
                        new SubcommandData("close", "Beende Umfrage")
                                .addOption(OptionType.STRING, "id", "Poll-ID", true),
                        new SubcommandData("results", "Zeige Ergebnisse")
                                .addOption(OptionType.STRING, "id", "Poll-ID", true)
                );
    }
}

