package dev.eministar.modules.ticket;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TicketService {
    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);
    private static final Path DATA_PATH = Paths.get("data/tickets.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, Map<String, Ticket>> tickets = new ConcurrentHashMap<>();
    // guildId -> ticketId -> Ticket

    private static int ticketCounter = 1000;

    static {
        load();
    }

    private static void load() {
        try {
            if (!Files.exists(DATA_PATH)) {
                Files.createDirectories(DATA_PATH.getParent());
                save();
                return;
            }

            try (Reader r = Files.newBufferedReader(DATA_PATH, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                if (root == null) return;

                if (root.has("ticketCounter")) {
                    ticketCounter = root.get("ticketCounter").getAsInt();
                }

                if (root.has("tickets")) {
                    JsonObject ticketsObj = root.getAsJsonObject("tickets");
                    for (Map.Entry<String, JsonElement> guildEntry : ticketsObj.entrySet()) {
                        String guildId = guildEntry.getKey();
                        JsonObject guildTickets = guildEntry.getValue().getAsJsonObject();

                        Map<String, Ticket> guildMap = new ConcurrentHashMap<>();
                        for (Map.Entry<String, JsonElement> ticketEntry : guildTickets.entrySet()) {
                            Ticket ticket = GSON.fromJson(ticketEntry.getValue(), Ticket.class);
                            guildMap.put(ticketEntry.getKey(), ticket);
                        }
                        tickets.put(guildId, guildMap);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load tickets.json", e);
        }
    }

    private static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("ticketCounter", ticketCounter);

            JsonObject ticketsObj = new JsonObject();
            for (Map.Entry<String, Map<String, Ticket>> guildEntry : tickets.entrySet()) {
                JsonObject guildTickets = new JsonObject();
                for (Map.Entry<String, Ticket> ticketEntry : guildEntry.getValue().entrySet()) {
                    guildTickets.add(ticketEntry.getKey(), GSON.toJsonTree(ticketEntry.getValue()));
                }
                ticketsObj.add(guildEntry.getKey(), guildTickets);
            }
            root.add("tickets", ticketsObj);

            try (Writer w = Files.newBufferedWriter(DATA_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
        } catch (IOException e) {
            logger.error("Failed to save tickets.json", e);
        }
    }

    public static String generateTicketId() {
        return String.format("TICKET-%04d", ticketCounter++);
    }

    public static Ticket createTicket(String guildId, String userId, TicketCategory category) {
        String ticketId = generateTicketId();
        Ticket ticket = new Ticket(ticketId, userId, guildId, category);

        tickets.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>()).put(ticketId, ticket);
        save();

        return ticket;
    }

    public static Optional<Ticket> getTicket(String guildId, String ticketId) {
        return Optional.ofNullable(tickets.getOrDefault(guildId, new ConcurrentHashMap<>()).get(ticketId));
    }

    public static Optional<Ticket> getTicketByChannel(String guildId, String channelId) {
        return tickets.getOrDefault(guildId, new ConcurrentHashMap<>()).values().stream()
                .filter(t -> channelId.equals(t.getChannelId()))
                .findFirst();
    }

    public static void updateTicket(Ticket ticket) {
        tickets.computeIfAbsent(ticket.getGuildId(), k -> new ConcurrentHashMap<>())
                .put(ticket.getTicketId(), ticket);
        save();
    }

    public static List<Ticket> getOpenTickets(String guildId) {
        return tickets.getOrDefault(guildId, new ConcurrentHashMap<>()).values().stream()
                .filter(t -> t.getStatus() == Ticket.TicketStatus.OPEN || t.getStatus() == Ticket.TicketStatus.CLAIMED)
                .toList();
    }

    public static List<Ticket> getUserTickets(String guildId, String userId) {
        return tickets.getOrDefault(guildId, new ConcurrentHashMap<>()).values().stream()
                .filter(t -> t.getUserId().equals(userId))
                .toList();
    }

    public static List<Ticket> getAllTickets(String guildId) {
        return new ArrayList<>(tickets.getOrDefault(guildId, new ConcurrentHashMap<>()).values());
    }

    public static List<Ticket> getClosedTickets(String guildId) {
        return tickets.getOrDefault(guildId, new ConcurrentHashMap<>()).values().stream()
                .filter(t -> t.getStatus() == Ticket.TicketStatus.CLOSED)
                .toList();
    }

    /**
     * Get count of tickets closed today
     */
    public static int getTicketsClosedToday(String guildId) {
        long startOfDay = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Berlin"))
                .atStartOfDay(java.time.ZoneId.of("Europe/Berlin"))
                .toInstant().toEpochMilli();

        return (int) tickets.getOrDefault(guildId, new ConcurrentHashMap<>()).values().stream()
                .filter(t -> t.getStatus() == Ticket.TicketStatus.CLOSED)
                .filter(t -> t.getClosedAt() >= startOfDay)
                .count();
    }

    /**
     * Get total count of all closed tickets (alltime)
     */
    public static int getTicketsClosedAllTime(String guildId) {
        return (int) tickets.getOrDefault(guildId, new ConcurrentHashMap<>()).values().stream()
                .filter(t -> t.getStatus() == Ticket.TicketStatus.CLOSED)
                .count();
    }

    /**
     * Get count of tickets claimed by a specific user today
     */
    public static int getTicketsClaimedTodayByUser(String guildId, String userId) {
        long startOfDay = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Berlin"))
                .atStartOfDay(java.time.ZoneId.of("Europe/Berlin"))
                .toInstant().toEpochMilli();

        return (int) tickets.getOrDefault(guildId, new ConcurrentHashMap<>()).values().stream()
                .filter(t -> t.getStatus() == Ticket.TicketStatus.CLOSED)
                .filter(t -> userId.equals(t.getClaimedBy()))
                .filter(t -> t.getClosedAt() >= startOfDay)
                .count();
    }

    /**
     * Get total count of tickets claimed by a specific user (alltime)
     */
    public static int getTicketsClaimedAllTimeByUser(String guildId, String userId) {
        return (int) tickets.getOrDefault(guildId, new ConcurrentHashMap<>()).values().stream()
                .filter(t -> t.getStatus() == Ticket.TicketStatus.CLOSED)
                .filter(t -> userId.equals(t.getClaimedBy()))
                .count();
    }

    /**
     * Get average ticket close time in minutes (alltime)
     */
    public static long getAverageCloseTimeMinutes(String guildId) {
        var closedTickets = tickets.getOrDefault(guildId, new ConcurrentHashMap<>()).values().stream()
                .filter(t -> t.getStatus() == Ticket.TicketStatus.CLOSED && t.getClosedAt() > 0)
                .toList();

        if (closedTickets.isEmpty()) return 0;

        long totalMinutes = closedTickets.stream()
                .mapToLong(t -> (t.getClosedAt() - t.getCreatedAt()) / (1000 * 60))
                .sum();

        return totalMinutes / closedTickets.size();
    }
}
