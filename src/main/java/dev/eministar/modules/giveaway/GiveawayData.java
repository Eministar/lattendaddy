package dev.eministar.modules.giveaway;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GiveawayData {
    public String id;
    public String guildId;
    public String channelId;
    public String messageId;
    public String threadId;
    public String hostId;
    public String title;
    public String prize;
    public int winnersCount;
    public String description;
    public String startedAt;
    public String endsAt;
    public String pausedAt;
    public String status; // scheduled, running, paused, ended, cancelled
    public String visibility; // live, final
    public Requirements requirements;
    public EntriesConfig entriesConfig;
    public Map<String, Entrant> entrants;
    public List<String> winners;
    public Map<String, ClaimData> claimed;
    public String lastEditAt;

    public GiveawayData() {
        this.entrants = new HashMap<>();
        this.claimed = new HashMap<>();
        this.requirements = new Requirements();
        this.entriesConfig = new EntriesConfig();
    }

    public static class Requirements {
        public int minAccountAgeHours;
        public int minGuildJoinHours;
        public List<String> requireRoleIds;
        public List<String> denyRoleIds;
    }

    public static class EntriesConfig {
        public int base = 1;
        public Map<String, Integer> bonusByRole = new HashMap<>();
    }

    public static class Entrant {
        public int entries;
        public String joinedAt;
    }

    public static class ClaimData {
        public String at;
        public String method; // dm, thread
    }

    public boolean isActive() {
        return "running".equals(status) || "scheduled".equals(status);
    }

    public boolean isPaused() {
        return "paused".equals(status);
    }

    public boolean isEnded() {
        return "ended".equals(status) || "cancelled".equals(status);
    }

    public Instant getEndsAtInstant() {
        return Instant.parse(endsAt);
    }

    public Instant getStartedAtInstant() {
        return Instant.parse(startedAt);
    }

    public Instant getPausedAtInstant() {
        return pausedAt != null ? Instant.parse(pausedAt) : null;
    }
}

