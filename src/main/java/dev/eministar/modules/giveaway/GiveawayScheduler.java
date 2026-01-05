package dev.eministar.modules.giveaway;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler that automatically ends giveaways when their time is up
 */
public class GiveawayScheduler {
    private static final Logger logger = LoggerFactory.getLogger(GiveawayScheduler.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final GiveawayStore store;
    private JDA jda;

    public GiveawayScheduler(GiveawayStore store) {
        this.store = store;
    }

    public void start(JDA jda) {
        this.jda = jda;
        // Check every 30 seconds for ended giveaways
        scheduler.scheduleAtFixedRate(this::checkGiveaways, 10, 30, TimeUnit.SECONDS);
        logger.info("GiveawayScheduler started");
    }

    private void checkGiveaways() {
        try {
            Instant now = Instant.now();

            for (Map.Entry<String, GiveawayData> entry : store.getAll().entrySet()) {
                try {
                    GiveawayData data = entry.getValue();
                    if (data == null || !data.isActive()) continue;

                    Instant endsAt = data.getEndsAtInstant();
                    if (endsAt.isBefore(now)) {
                        endGiveaway(entry.getKey(), data);
                    }
                } catch (Exception e) {
                    logger.warn("Error processing giveaway {}: {}", entry.getKey(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error checking giveaways", e);
        }
    }

    private void endGiveaway(String key, GiveawayData data) {
        logger.info("Auto-ending giveaway: {}", data.id);

        // Pick winners
        Map<String, Integer> weights = new HashMap<>();
        if (data.entrants != null) {
            for (Map.Entry<String, GiveawayData.Entrant> e : data.entrants.entrySet()) {
                int entries = e.getValue() != null ? e.getValue().entries : 1;
                if (entries <= 0) entries = 1;
                weights.put(e.getKey(), entries);
            }
        }

        List<String> winners = WeightedPicker.pickWeighted(weights, data.winnersCount);
        data.winners = winners;
        data.status = "ended";
        data.visibility = "final";
        data.lastEditAt = Instant.now().toString();
        store.put(key, data);

        // Update embed and announce winners
        if (jda != null) {
            TextChannel channel = jda.getTextChannelById(data.channelId);
            if (channel != null && data.messageId != null) {
                // Check if bot can access the channel
                if (!channel.canTalk()) {
                    logger.warn("Cannot access channel {} for giveaway {} - missing permissions", data.channelId, data.id);
                    return;
                }

                channel.retrieveMessageById(data.messageId).queue(message -> {
                    // Build final embed
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setColor(new Color(0x57F287));
                    eb.setTitle("🎉 GIVEAWAY BEENDET 🎉");

                    StringBuilder desc = new StringBuilder();
                    desc.append("━━━━━━━━━━━━━━━━━━━━\n\n");
                    desc.append("🎁 **").append(data.prize).append("**\n\n");
                    desc.append("━━━━━━━━━━━━━━━━━━━━\n\n");

                    int totalEntrants = data.entrants != null ? data.entrants.size() : 0;
                    desc.append("📊 **Statistiken:**\n");
                    desc.append("• 👥 Teilnehmer: **").append(totalEntrants).append("**\n");
                    desc.append("• 🏆 Gewinner: **").append(data.winnersCount).append("**\n\n");

                    if (winners.isEmpty()) {
                        desc.append("❌ **Keine Gewinner** - Niemand hat teilgenommen.\n");
                    } else {
                        desc.append("🎊 **Gewinner:**\n");
                        for (String userId : winners) {
                            desc.append("• <@").append(userId).append(">\n");
                        }
                    }

                    desc.append("\n━━━━━━━━━━━━━━━━━━━━");

                    eb.setDescription(desc.toString());
                    eb.setFooter("🎉 Giveaway-ID: " + data.id + " • Beendet", null);
                    eb.setTimestamp(Instant.now());

                    message.editMessageEmbeds(eb.build())
                            .setActionRow(net.dv8tion.jda.api.interactions.components.buttons.Button.primary("gaw:enter:" + data.id, "🎁 Beendet").asDisabled())
                            .queue();

                    // Announce winners
                    if (!winners.isEmpty()) {
                        StringBuilder ping = new StringBuilder();
                        ping.append("🎉 **Herzlichen Glückwunsch!**\n\n");
                        ping.append("Die Gewinner von **").append(data.prize).append("** sind:\n");
                        for (String userId : winners) {
                            ping.append("• <@").append(userId).append(">\n");
                        }
                        ping.append("\n*Bitte meldet euch beim Host um euren Preis zu erhalten!*");
                        channel.sendMessage(ping.toString()).queue();
                    }
                }, error -> logger.warn("Failed to update ended giveaway message: {}", error.getMessage()));
            }
        }
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

