package dev.eministar.modules.giveaway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Debouncer {
    private final Map<String, Long> lastUpdate;
    private final long delayMs;

    public Debouncer(long delayMs) {
        this.delayMs = delayMs;
        this.lastUpdate = new ConcurrentHashMap<>();
    }

    public boolean shouldExecute(String key) {
        long now = System.currentTimeMillis();
        Long last = lastUpdate.get(key);

        if (last == null || (now - last) >= delayMs) {
            lastUpdate.put(key, now);
            return true;
        }

        return false;
    }

    public void reset(String key) {
        lastUpdate.remove(key);
    }

    public void clear() {
        lastUpdate.clear();
    }
}
