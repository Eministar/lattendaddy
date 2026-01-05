package dev.eministar.modules.giveaway;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GiveawayStore {
    private final String filePath;
    private final Gson gson;
    private final Map<String, GiveawayData> giveaways;
    private int sequence;

    public GiveawayStore(String filePath) {
        this.filePath = filePath;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.giveaways = new ConcurrentHashMap<>();
        this.sequence = 0;
        load();
    }

    public synchronized void load() {
        File file = new File(filePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            save();
            return;
        }

        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<StoreData>() {}.getType();
            StoreData data = gson.fromJson(reader, type);
            if (data != null) {
                this.sequence = data.seq;
                if (data.gaws != null) {
                    this.giveaways.putAll(data.gaws);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load giveaways: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try (Writer writer = new FileWriter(filePath)) {
            StoreData data = new StoreData();
            data.seq = sequence;
            data.gaws = new HashMap<>(giveaways);
            gson.toJson(data, writer);
        } catch (Exception e) {
            System.err.println("Failed to save giveaways: " + e.getMessage());
        }
    }

    public String generateId() {
        sequence++;
        save();
        return String.format("GA-%s-%03d",
                Instant.now().toString().substring(0, 10).replace("-", ""),
                sequence % 1000);
    }

    public void put(String key, GiveawayData data) {
        giveaways.put(key, data);
        save();
    }

    public GiveawayData get(String key) {
        return giveaways.get(key);
    }

    public void remove(String key) {
        giveaways.remove(key);
        save();
    }

    public Map<String, GiveawayData> getAll() {
        return new HashMap<>(giveaways);
    }

    public Map<String, GiveawayData> getByGuild(String guildId) {
        Map<String, GiveawayData> result = new HashMap<>();
        giveaways.forEach((key, data) -> {
            if (data.guildId.equals(guildId)) {
                result.put(key, data);
            }
        });
        return result;
    }

    private static class StoreData {
        int seq;
        Map<String, GiveawayData> gaws;
    }
}

