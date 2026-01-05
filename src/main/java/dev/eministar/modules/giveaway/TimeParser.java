package dev.eministar.modules.giveaway;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeParser {
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([mhd])");

    public static Duration parse(String input) throws IllegalArgumentException {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration cannot be empty");
        }

        input = input.toLowerCase().trim();
        Matcher matcher = DURATION_PATTERN.matcher(input);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration format. Use: 45m, 2h, 1d");
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        switch (unit) {
            case "m":
                return Duration.ofMinutes(amount);
            case "h":
                return Duration.ofHours(amount);
            case "d":
                return Duration.ofDays(amount);
            default:
                throw new IllegalArgumentException("Unknown time unit: " + unit);
        }
    }

    public static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    public static String formatRelative(Duration duration) {
        long seconds = duration.getSeconds();

        if (seconds < 60) {
            return "weniger als 1 Minute";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + (minutes == 1 ? " Minute" : " Minuten");
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + (hours == 1 ? " Stunde" : " Stunden");
        } else {
            long days = seconds / 86400;
            return days + (days == 1 ? " Tag" : " Tagen");
        }
    }
}

