package dev.eministar.modules.poll;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler that automatically closes polls when their time is up
 */
public class PollScheduler {
    private static final Logger logger = LoggerFactory.getLogger(PollScheduler.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final PollStore store;
    private JDA jda;

    public PollScheduler(PollStore store) {
        this.store = store;
    }

    public void start(JDA jda) {
        this.jda = jda;
        // Check every 30 seconds for ended polls
        scheduler.scheduleAtFixedRate(this::checkPolls, 10, 30, TimeUnit.SECONDS);
        logger.info("PollScheduler started");
    }

    private void checkPolls() {
        try {
            Instant now = Instant.now();

            for (Map.Entry<String, PollData> entry : store.getAll().entrySet()) {
                PollData data = entry.getValue();
                if (data == null || !data.isOpen()) continue;

                Instant endsAt = data.getEndsAtInstant();
                if (endsAt.isBefore(now)) {
                    endPoll(entry.getKey(), data);
                }
            }
        } catch (Exception e) {
            logger.error("Error checking polls", e);
        }
    }

    private void endPoll(String key, PollData poll) {
        logger.info("Auto-closing poll: {}", poll.id);

        poll.status = "closed";
        store.put(key, poll);

        // Update embed
        if (jda != null && poll.channelId != null && poll.messageId != null) {
            TextChannel channel = jda.getTextChannelById(poll.channelId);
            if (channel != null) {
                channel.retrieveMessageById(poll.messageId).queue(message -> {
                    EmbedBuilder embed = buildClosedPollEmbed(poll);
                    message.editMessageEmbeds(embed.build()).queue(
                        success -> logger.debug("Updated closed poll embed: {}", poll.id),
                        error -> logger.warn("Failed to update poll embed: {}", error.getMessage())
                    );
                }, error -> logger.warn("Failed to retrieve poll message: {}", error.getMessage()));
            }
        }
    }

    private EmbedBuilder buildClosedPollEmbed(PollData poll) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📊 " + poll.title + " ✅");
        embed.setColor(new Color(0x57F287)); // Green for closed

        StringBuilder desc = new StringBuilder();
        desc.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        if (poll.description != null && !poll.description.isEmpty()) {
            desc.append("*").append(poll.description).append("*\n\n");
        }

        int totalVotes = poll.getTotalVotes();

        // Sort options by votes
        poll.options.stream()
            .sorted((a, b) -> poll.totals.getOrDefault(b.id, 0).compareTo(poll.totals.getOrDefault(a.id, 0)))
            .forEach(option -> {
                int votes = poll.totals.getOrDefault(option.id, 0);
                double percent = totalVotes > 0 ? (double) votes / totalVotes * 100 : 0;

                // Medal for top 3
                int rank = 1;
                for (PollData.PollOption o : poll.options) {
                    if (poll.totals.getOrDefault(o.id, 0) > votes) rank++;
                }
                String medal = rank == 1 ? "🥇 " : rank == 2 ? "🥈 " : rank == 3 ? "🥉 " : "";

                desc.append(medal).append("**").append(option.id).append(": ").append(option.label).append("**\n");

                // Progress bar
                int filledBlocks = (int) Math.round(percent / 10);
                desc.append("```\n[");
                for (int i = 0; i < 10; i++) {
                    desc.append(i < filledBlocks ? "█" : "░");
                }
                desc.append("] ").append(String.format("%5.1f%%", percent));
                desc.append(" (").append(votes).append(")\n```\n");
            });

        desc.append("━━━━━━━━━━━━━━━━━━━━\n");
        desc.append("📊 **Gesamt:** ").append(totalVotes).append(" Stimmen\n");
        desc.append("✅ **Status:** Beendet");

        embed.setDescription(desc.toString());
        embed.setFooter("Poll-ID: " + poll.id + " • Abgeschlossen", null);
        embed.setTimestamp(Instant.now());

        return embed;
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}

