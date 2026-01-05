package dev.eministar.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Config {
    private static JsonObject root;
    private static final Path CONFIG_PATH = Paths.get("config/config.json");

    static {
        loadConfig();
    }

    private static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                createDefaultConfig();
            }
            try (InputStreamReader r = new InputStreamReader(Files.newInputStream(CONFIG_PATH), StandardCharsets.UTF_8)) {
                root = new Gson().fromJson(r, JsonObject.class);
            }

            // Ensure any newly introduced keys are present in existing configs (merge defaults)
            ensureDefaults();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.json", e);
        }
    }

    private static void createDefaultConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("token", "PUT_YOUR_TOKEN_HERE");
        config.addProperty("prefix", "!");
        config.addProperty("ownerId", "");

        JsonObject db = new JsonObject();
        db.addProperty("host", "localhost");
        db.addProperty("port", 3306);
        db.addProperty("database", "lattendaddy");
        db.addProperty("user", "root");
        db.addProperty("password", "");
        config.add("database", db);

        // new optional fields
        config.addProperty("guildId", "");
        config.addProperty("welcomeChannelId", "");
        config.addProperty("goodbyeChannelId", "");
        // birthday channels: where to post congratulations and where the list embed lives
        config.addProperty("birthdayCongratsChannelId", "");
        config.addProperty("birthdayListChannelId", "");

        // channel counters defaults
        JsonObject channelCounts = new JsonObject();
        channelCounts.addProperty("enabled", false);
        channelCounts.addProperty("onlineChannelId", "");
        channelCounts.addProperty("memberChannelId", "");
        channelCounts.addProperty("includeBots", false);
        config.add("channelCounts", channelCounts);

        // flag quiz defaults
        JsonObject flagQuiz = new JsonObject();
        flagQuiz.addProperty("enabled", true);
        config.add("flagQuiz", flagQuiz);

        // defaults for roles arrays
        config.add("joinRoleIds", new JsonArray());
        config.add("ticketClaimRoleIds", new JsonArray());

        // DPQ defaults
        config.addProperty("dpqChannelId", "");
        config.addProperty("dpqCategoryId", "");
        config.addProperty("dpqPingRoleId", "");
        config.addProperty("dpqTeamRoleId", "");
        config.addProperty("dpqQaUrl", "");
        // new: role for current DPQ leaders
        config.addProperty("dpqWinnerRoleId", "");

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(config, w);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create default config.json", e);
        }
    }

    public static void runSetupWizard() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Lattendaddy Setup Wizard ===");
        System.out.println("Willkommen! Lass uns deinen Discord-Bot konfigurieren.");
        System.out.println("Du kannst jederzeit 'skip' eingeben, um einen Schritt zu überspringen.\n");

        // Bot Token
        String token = "";
        while (token.isEmpty() || token.equals("PUT_YOUR_TOKEN_HERE")) {
            System.out.print("Bot Token (erforderlich, von https://discord.com/developers/applications): ");
            token = scanner.nextLine().trim();
            if (token.equalsIgnoreCase("skip")) {
                System.out.println("Token ist erforderlich. Bitte gib ihn ein.");
                token = "";
            }
        }

        // Prefix
        System.out.print("Command-Prefix (Standard: !): ");
        String prefix = scanner.nextLine().trim();
        if (prefix.isEmpty() || prefix.equalsIgnoreCase("skip")) prefix = "!";

        // Owner ID
        System.out.print("Owner ID (optional, für Admin-Commands): ");
        String ownerId = scanner.nextLine().trim();
        if (ownerId.equalsIgnoreCase("skip")) ownerId = "";

        // Guild ID
        System.out.print("Guild ID für Slash-Commands (optional, für schnelle Updates): ");
        String guildId = scanner.nextLine().trim();
        if (guildId.equalsIgnoreCase("skip")) guildId = "";

        // Welcome channel
        System.out.print("Welcome-Channel ID (optional, Kanal für Begrüßungen): ");
        String welcomeChannelId = scanner.nextLine().trim();
        if (welcomeChannelId.equalsIgnoreCase("skip")) welcomeChannelId = "";

        // Goodbye channel
        System.out.print("Goodbye-Channel ID (optional, Kanal für Verabschiedungen): ");
        String goodbyeChannelId = scanner.nextLine().trim();
        if (goodbyeChannelId.equalsIgnoreCase("skip")) goodbyeChannelId = "";

        // Database Setup
        System.out.println("\n--- Datenbank-Konfiguration ---");
        System.out.print("Datenbank-Host (Standard: localhost): ");
        String dbHost = scanner.nextLine().trim();
        if (dbHost.isEmpty() || dbHost.equalsIgnoreCase("skip")) dbHost = "localhost";

        System.out.print("Datenbank-Port (Standard: 3306): ");
        String portInput = scanner.nextLine().trim();
        int dbPort = 3306;
        if (!portInput.isEmpty() && !portInput.equalsIgnoreCase("skip")) {
            try {
                dbPort = Integer.parseInt(portInput);
            } catch (NumberFormatException e) {
                System.out.println("Ungültiger Port, verwende Standard 3306.");
            }
        }

        System.out.print("Datenbank-Name (Standard: lattendaddy): ");
        String dbName = scanner.nextLine().trim();
        if (dbName.isEmpty() || dbName.equalsIgnoreCase("skip")) dbName = "lattendaddy";

        System.out.print("Datenbank-User (Standard: root): ");
        String dbUser = scanner.nextLine().trim();
        if (dbUser.isEmpty() || dbUser.equalsIgnoreCase("skip")) dbUser = "root";

        System.out.print("Datenbank-Password (optional): ");
        String dbPassword = scanner.nextLine().trim();
        if (dbPassword.equalsIgnoreCase("skip")) dbPassword = "";

        // Zusammenfassung
        System.out.println("\n--- Zusammenfassung ---");
        System.out.println("Token: " + (token.length() > 10 ? token.substring(0, 10) + "..." : token));
        System.out.println("Prefix: " + prefix);
        System.out.println("Owner ID: " + (ownerId.isEmpty() ? "Nicht gesetzt" : ownerId));
        System.out.println("Guild ID: " + (guildId.isEmpty() ? "Nicht gesetzt" : guildId));
        System.out.println("Welcome Channel ID: " + (welcomeChannelId.isEmpty() ? "Nicht gesetzt" : welcomeChannelId));
        System.out.println("Goodbye Channel ID: " + (goodbyeChannelId.isEmpty() ? "Nicht gesetzt" : goodbyeChannelId));
        System.out.println("DB Host: " + dbHost);
        System.out.println("DB Port: " + dbPort);
        System.out.println("DB Name: " + dbName);
        System.out.println("DB User: " + dbUser);
        System.out.println("DB Password: " + (dbPassword.isEmpty() ? "Nicht gesetzt" : "***"));
        System.out.print("Ist das korrekt? (y/n): ");
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y") && !confirm.equalsIgnoreCase("yes")) {
            System.out.println("Setup abgebrochen. Starte den Bot neu, um es erneut zu versuchen.");
            return;
        }

        // Update config
        root.addProperty("token", token);
        root.addProperty("prefix", prefix);
        if (!ownerId.isEmpty()) root.addProperty("ownerId", ownerId);
        if (!guildId.isEmpty()) root.addProperty("guildId", guildId);
        if (!welcomeChannelId.isEmpty()) root.addProperty("welcomeChannelId", welcomeChannelId);
        if (!goodbyeChannelId.isEmpty()) root.addProperty("goodbyeChannelId", goodbyeChannelId);

        JsonObject db = new JsonObject();
        db.addProperty("host", dbHost);
        db.addProperty("port", dbPort);
        db.addProperty("database", dbName);
        db.addProperty("user", dbUser);
        db.addProperty("password", dbPassword);
        root.add("database", db);

        // Save
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
        } catch (IOException e) {
            System.err.println("Fehler beim Speichern der Config: " + e.getMessage());
            return;
        }

        System.out.println("✅ Config erfolgreich gespeichert!");
        System.out.println("Starte den Bot neu, um die Änderungen zu übernehmen.");
    }

    // Ensure that missing keys are added to an existing config.json without overwriting existing values
    private static void ensureDefaults() {
        boolean changed = false;

        if (root == null) root = new JsonObject();

        if (!root.has("token")) { root.addProperty("token", "PUT_YOUR_TOKEN_HERE"); changed = true; }
        if (!root.has("prefix")) { root.addProperty("prefix", "!"); changed = true; }
        if (!root.has("ownerId")) { root.addProperty("ownerId", ""); changed = true; }
        if (!root.has("guildId")) { root.addProperty("guildId", ""); changed = true; }
        if (!root.has("welcomeChannelId")) { root.addProperty("welcomeChannelId", ""); changed = true; }
        if (!root.has("goodbyeChannelId")) { root.addProperty("goodbyeChannelId", ""); changed = true; }
        if (!root.has("birthdayCongratsChannelId")) { root.addProperty("birthdayCongratsChannelId", ""); changed = true; }
        if (!root.has("birthdayListChannelId")) { root.addProperty("birthdayListChannelId", ""); changed = true; }
        if (!root.has("birthdayRoleId")) { root.addProperty("birthdayRoleId", ""); changed = true; }
        if (!root.has("ticketLogChannelId")) { root.addProperty("ticketLogChannelId", ""); changed = true; }
        if (!root.has("suggestionChannelId")) { root.addProperty("suggestionChannelId", ""); changed = true; }

        // TempVoice defaults
        if (!root.has("tempVoice") || root.get("tempVoice").isJsonNull()) {
            JsonObject tempVoice = new JsonObject();
            tempVoice.addProperty("enabled", false);
            tempVoice.addProperty("sourceChannelId", "");
            tempVoice.addProperty("fallbackCategoryId", "");
            tempVoice.addProperty("deleteGraceSeconds", 10);
            tempVoice.addProperty("defaultMaxMembers", 5);
            tempVoice.addProperty("defaultBitrateKbps", 64);
            root.add("tempVoice", tempVoice);
            changed = true;
        } else {
            JsonObject tv = root.getAsJsonObject("tempVoice");
            if (!tv.has("enabled")) { tv.addProperty("enabled", false); changed = true; }
            if (!tv.has("sourceChannelId")) { tv.addProperty("sourceChannelId", ""); changed = true; }
            if (!tv.has("fallbackCategoryId")) { tv.addProperty("fallbackCategoryId", ""); changed = true; }
            if (!tv.has("deleteGraceSeconds")) { tv.addProperty("deleteGraceSeconds", 10); changed = true; }
            if (!tv.has("defaultMaxMembers")) { tv.addProperty("defaultMaxMembers", 5); changed = true; }
            if (!tv.has("defaultBitrateKbps")) { tv.addProperty("defaultBitrateKbps", 64); changed = true; }
        }

        // Hymn defaults
        if (!root.has("hymn") || root.get("hymn").isJsonNull()) {
            JsonObject hymn = new JsonObject();
            hymn.addProperty("enabled", false);
            hymn.addProperty("channelId", "");
            root.add("hymn", hymn);
            changed = true;
        } else {
            JsonObject hymn = root.getAsJsonObject("hymn");
            if (!hymn.has("enabled")) { hymn.addProperty("enabled", false); changed = true; }
            if (!hymn.has("channelId")) { hymn.addProperty("channelId", ""); changed = true; }
        }

        // Channel counts defaults
        if (!root.has("channelCounts") || root.get("channelCounts").isJsonNull()) {
            JsonObject cc = new JsonObject();
            cc.addProperty("enabled", false);
            cc.addProperty("onlineChannelId", "");
            cc.addProperty("memberChannelId", "");
            cc.addProperty("includeBots", false);
            root.add("channelCounts", cc);
            changed = true;
        } else {
            JsonObject cc = root.getAsJsonObject("channelCounts");
            if (!cc.has("enabled")) { cc.addProperty("enabled", false); changed = true; }
            if (!cc.has("onlineChannelId")) { cc.addProperty("onlineChannelId", ""); changed = true; }
            if (!cc.has("memberChannelId")) { cc.addProperty("memberChannelId", ""); changed = true; }
            if (!cc.has("includeBots")) { cc.addProperty("includeBots", false); changed = true; }
        }

        // FlagQuiz defaults
        if (!root.has("flagQuiz") || root.get("flagQuiz").isJsonNull()) {
            JsonObject fq = new JsonObject();
            fq.addProperty("enabled", true);
            root.add("flagQuiz", fq);
            changed = true;
        } else {
            JsonObject fq = root.getAsJsonObject("flagQuiz");
            if (!fq.has("enabled")) { fq.addProperty("enabled", true); changed = true; }
        }

        // Roles arrays defaults
        if (!root.has("joinRoleIds")) { root.add("joinRoleIds", new JsonArray()); changed = true; }
        if (!root.has("ticketClaimRoleIds")) { root.add("ticketClaimRoleIds", new JsonArray()); changed = true; }

        // DPQ defaults
        if (!root.has("dpqChannelId")) { root.addProperty("dpqChannelId", ""); changed = true; }
        if (!root.has("dpqCategoryId")) { root.addProperty("dpqCategoryId", ""); changed = true; }
        if (!root.has("dpqPingRoleId")) { root.addProperty("dpqPingRoleId", ""); changed = true; }
        if (!root.has("dpqTeamRoleId")) { root.addProperty("dpqTeamRoleId", ""); changed = true; }
        if (!root.has("dpqQaUrl")) { root.addProperty("dpqQaUrl", ""); changed = true; }
        if (!root.has("dpqWinnerRoleId")) { root.addProperty("dpqWinnerRoleId", ""); changed = true; }

        if (!root.has("database") || root.get("database").isJsonNull()) {
            JsonObject db = new JsonObject();
            db.addProperty("host", "localhost");
            db.addProperty("port", 3306);
            db.addProperty("database", "lattendaddy");
            db.addProperty("user", "root");
            db.addProperty("password", "");
            root.add("database", db);
            changed = true;
        } else {
            JsonObject db = root.getAsJsonObject("database");
            if (!db.has("host")) { db.addProperty("host", "localhost"); changed = true; }
            if (!db.has("port")) { db.addProperty("port", 3306); changed = true; }
            if (!db.has("database")) { db.addProperty("database", "lattendaddy"); changed = true; }
            if (!db.has("user")) { db.addProperty("user", "root"); changed = true; }
            if (!db.has("password")) { db.addProperty("password", ""); changed = true; }
        }

        if (changed) {
            try {
                // create a backup of the existing config before overwriting
                if (Files.exists(CONFIG_PATH)) {
                    Path backup = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName().toString() + ".bak");
                    Files.copy(CONFIG_PATH, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                try (Writer w = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                    new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to update config.json with defaults", e);
            }
        }
    }

    public static boolean needsSetup() {
        return getToken().equals("PUT_YOUR_TOKEN_HERE") || getPrefix().isEmpty();
    }

    public static String getToken() {
        return root.get("token").getAsString();
    }

    public static String getPrefix() {
        return root.get("prefix").getAsString();
    }

    public static String getOwnerId() {
        return root.has("ownerId") ? root.get("ownerId").getAsString() : "";
    }

    public static String getGuildId() {
        return root.has("guildId") ? root.get("guildId").getAsString() : "";
    }

    public static String getWelcomeChannelId() {
        return root.has("welcomeChannelId") ? root.get("welcomeChannelId").getAsString() : "";
    }

    public static String getGoodbyeChannelId() {
        return root.has("goodbyeChannelId") ? root.get("goodbyeChannelId").getAsString() : "";
    }

    public static String getBirthdayCongratsChannelId() {
        return root.has("birthdayCongratsChannelId") ? root.get("birthdayCongratsChannelId").getAsString() : "";
    }

    public static String getBirthdayListChannelId() {
        return root.has("birthdayListChannelId") ? root.get("birthdayListChannelId").getAsString() : "";
    }

    public static String getBirthdayRoleId() {
        return root.has("birthdayRoleId") ? root.get("birthdayRoleId").getAsString() : "";
    }

    public static String getTicketLogChannelId() {
        return root.has("ticketLogChannelId") ? root.get("ticketLogChannelId").getAsString() : "";
    }

    public static String getSuggestionChannelId() {
        return root.has("suggestionChannelId") ? root.get("suggestionChannelId").getAsString() : "";
    }

    // TempVoice getters
    public static boolean getTempVoiceEnabled() {
        if (!root.has("tempVoice")) return false;
        JsonObject tv = root.getAsJsonObject("tempVoice");
        return tv.has("enabled") && tv.get("enabled").getAsBoolean();
    }

    public static String getTempVoiceSourceChannelId() {
        if (!root.has("tempVoice")) return "";
        JsonObject tv = root.getAsJsonObject("tempVoice");
        return tv.has("sourceChannelId") ? tv.get("sourceChannelId").getAsString() : "";
    }

    public static String getTempVoiceFallbackCategoryId() {
        if (!root.has("tempVoice")) return "";
        JsonObject tv = root.getAsJsonObject("tempVoice");
        return tv.has("fallbackCategoryId") ? tv.get("fallbackCategoryId").getAsString() : "";
    }

    public static int getTempVoiceDeleteGraceSeconds() {
        if (!root.has("tempVoice")) return 10;
        JsonObject tv = root.getAsJsonObject("tempVoice");
        return tv.has("deleteGraceSeconds") ? tv.get("deleteGraceSeconds").getAsInt() : 10;
    }
    // Hymn getters
    public static boolean getHymnEnabled() {
        if (!root.has("hymn")) return false;
        JsonObject hymn = root.getAsJsonObject("hymn");
        return hymn.has("enabled") && hymn.get("enabled").getAsBoolean();
    }

    public static String getHymnChannelId() {
        if (!root.has("hymn")) return "";
        JsonObject hymn = root.getAsJsonObject("hymn");
        return hymn.has("channelId") ? hymn.get("channelId").getAsString() : "";
    }

    // Counting getters
    public static boolean getCountingEnabled() {
        if (!root.has("counting")) return false;
        JsonObject counting = root.getAsJsonObject("counting");
        return counting.has("enabled") && counting.get("enabled").getAsBoolean();
    }

    public static String getCountingChannelId() {
        if (!root.has("counting")) return "";
        JsonObject counting = root.getAsJsonObject("counting");
        return counting.has("channelId") ? counting.get("channelId").getAsString() : "";
    }

    // Database getters
    public static int getTempVoiceDefaultMaxMembers() {
        if (!root.has("tempVoice")) return 5;
        JsonObject tv = root.getAsJsonObject("tempVoice");
        return tv.has("defaultMaxMembers") ? tv.get("defaultMaxMembers").getAsInt() : 5;
    }

    public static int getTempVoiceDefaultBitrateKbps() {
        if (!root.has("tempVoice")) return 64;
        JsonObject tv = root.getAsJsonObject("tempVoice");
        return tv.has("defaultBitrateKbps") ? tv.get("defaultBitrateKbps").getAsInt() : 64;
    }

    // Database getters
    public static String getDbHost() {
        return root.getAsJsonObject("database").get("host").getAsString();
    }

    public static int getDbPort() {
        return root.getAsJsonObject("database").get("port").getAsInt();
    }

    public static String getDbDatabase() {
        return root.getAsJsonObject("database").get("database").getAsString();
    }

    public static String getDbUser() {
        return root.getAsJsonObject("database").get("user").getAsString();
    }

    public static String getDbPassword() {
        return root.getAsJsonObject("database").get("password").getAsString();
    }

    // Channel Counts getters
    public static boolean getChannelCountsEnabled() {
        if (!root.has("channelCounts")) return false;
        JsonObject cc = root.getAsJsonObject("channelCounts");
        return cc.has("enabled") && cc.get("enabled").getAsBoolean();
    }

    public static String getChannelCountsOnlineChannelId() {
        if (!root.has("channelCounts")) return "";
        JsonObject cc = root.getAsJsonObject("channelCounts");
        return cc.has("onlineChannelId") ? cc.get("onlineChannelId").getAsString() : "";
    }

    public static String getChannelCountsMemberChannelId() {
        if (!root.has("channelCounts")) return "";
        JsonObject cc = root.getAsJsonObject("channelCounts");
        return cc.has("memberChannelId") ? cc.get("memberChannelId").getAsString() : "";
    }

    public static boolean getChannelCountsIncludeBots() {
        if (!root.has("channelCounts")) return false;
        JsonObject cc = root.getAsJsonObject("channelCounts");
        return cc.has("includeBots") && cc.get("includeBots").getAsBoolean();
    }

    // FlagQuiz getters
    public static boolean getFlagQuizEnabled() {
        if (!root.has("flagQuiz")) return true;
        JsonObject fq = root.getAsJsonObject("flagQuiz");
        return fq.has("enabled") && fq.get("enabled").getAsBoolean();
    }

    // Roles getters
    public static List<String> getJoinRoleIds() {
        List<String> out = new ArrayList<>();
        if (root.has("joinRoleIds") && root.get("joinRoleIds").isJsonArray()) {
            root.getAsJsonArray("joinRoleIds").forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }

    public static List<String> getTicketClaimRoleIds() {
        List<String> out = new ArrayList<>();
        if (root.has("ticketClaimRoleIds") && root.get("ticketClaimRoleIds").isJsonArray()) {
            root.getAsJsonArray("ticketClaimRoleIds").forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }

    // DPQ getters
    public static String getDpqChannelId() { return root.has("dpqChannelId") ? root.get("dpqChannelId").getAsString() : ""; }
    public static String getDpqCategoryId() { return root.has("dpqCategoryId") ? root.get("dpqCategoryId").getAsString() : ""; }
    public static String getDpqPingRoleId() { return root.has("dpqPingRoleId") ? root.get("dpqPingRoleId").getAsString() : ""; }
    public static String getDpqTeamRoleId() { return root.has("dpqTeamRoleId") ? root.get("dpqTeamRoleId").getAsString() : ""; }
    public static String getDpqQaUrl() { return root.has("dpqQaUrl") ? root.get("dpqQaUrl").getAsString() : ""; }
    public static String getDpqWinnerRoleId() { return root.has("dpqWinnerRoleId") ? root.get("dpqWinnerRoleId").getAsString() : ""; }
}
