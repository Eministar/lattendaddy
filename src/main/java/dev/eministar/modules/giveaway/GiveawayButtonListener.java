package dev.eministar.modules.giveaway;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;

public class GiveawayButtonListener extends ListenerAdapter {
    private final GiveawayStore store;

    public GiveawayButtonListener(GiveawayStore store) {
        this.store = store;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!event.isFromGuild()) return;
        String id = event.getComponentId();
        if (!id.startsWith("gaw:enter:")) return;

        String giveawayId = id.substring("gaw:enter:".length());
        Member member = event.getMember();
        if (member == null) {
            event.reply("❌ Kein Member gefunden.").setEphemeral(true).queue();
            return;
        }

        // Giveaway suchen
        GiveawayData data = null;
        String keyForStore = null;
        for (Map.Entry<String, GiveawayData> entry : store.getAll().entrySet()) {
            if (entry.getValue() != null && giveawayId.equals(entry.getValue().id)) {
                data = entry.getValue();
                keyForStore = entry.getKey();
                break;
            }
        }

        if (data == null) {
            event.reply("❌ Dieses Giveaway existiert nicht mehr.").setEphemeral(true).queue();
            return;
        }

        if (!data.guildId.equals(event.getGuild().getId())) {
            event.reply("❌ Falscher Server für dieses Giveaway.").setEphemeral(true).queue();
            return;
        }

        if (!data.isActive()) {
            event.reply("❌ Dieses Giveaway ist bereits beendet oder pausiert.").setEphemeral(true).queue();
            return;
        }

        // Requirements prüfen
        GiveawayData.Requirements req = data.requirements != null ? data.requirements : new GiveawayData.Requirements();
        RequirementsChecker.CheckResult res = RequirementsChecker.check(member, req);
        if (!res.passed) {
            event.reply("❌ Teilnahmebedingungen nicht erfüllt: " + res.message).setEphemeral(true).queue();
            return;
        }

        if (data.entrants == null) data.entrants = new java.util.HashMap<>();
        String userId = member.getId();
        if (data.entrants.containsKey(userId)) {
            event.reply("🎫 Du nimmst bereits an diesem Giveaway teil!\n\n*Deine Teilnahme wurde bereits registriert. Viel Glück!*").setEphemeral(true).queue();
            return;
        }

        int entries = RequirementsChecker.calculateEntries(member, data.entriesConfig != null ? data.entriesConfig : new GiveawayData.EntriesConfig());
        GiveawayData.Entrant entrant = new GiveawayData.Entrant();
        entrant.entries = entries;
        entrant.joinedAt = Instant.now().toString();
        data.entrants.put(userId, entrant);
        data.lastEditAt = Instant.now().toString();

        if (keyForStore != null) {
            store.put(keyForStore, data);
        }

        int totalEntrants = data.entrants.size();
        double chance = (double) data.winnersCount / totalEntrants * 100;

        StringBuilder response = new StringBuilder();
        response.append("🎉 **Du nimmst jetzt am Giveaway teil!**\n\n");
        response.append("━━━━━━━━━━━━━━━━━━━━\n");
        response.append("🎁 **Preis:** ").append(data.prize).append("\n");
        response.append("🎫 **Deine Lose:** ").append(entries).append("\n");
        response.append("👥 **Teilnehmer:** ").append(totalEntrants).append("\n");
        response.append("🎯 **Gewinnchance:** ").append(String.format("%.1f%%", Math.min(chance, 100))).append("\n");
        response.append("━━━━━━━━━━━━━━━━━━━━\n\n");
        response.append("*Viel Glück! 🍀*");

        event.reply(response.toString()).setEphemeral(true).queue();

        // Versuch, die Teilnehmerzahl im Embed zu aktualisieren (optional)
        TextChannel ch = event.getGuild().getTextChannelById(data.channelId);
        if (ch != null && data.messageId != null) {
            final GiveawayData dataFinal = data;
            ch.retrieveMessageById(data.messageId).queue(msg -> {
                GiveawayModuleV2.updateGiveawayEmbedInMessage(msg, dataFinal);
            }, failure -> {
                // ignore, z.B. wenn Nachricht gelöscht wurde
            });
        }
    }
}
