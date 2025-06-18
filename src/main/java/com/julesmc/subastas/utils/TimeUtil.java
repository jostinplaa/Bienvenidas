package com.julesmc.subastas.utils;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhd])"); // s, m, h, d

    /**
     * Parses a duration string like "10s", "5m", "1h", "2d" into seconds.
     *
     * @param durationString The string to parse.
     * @return The duration in seconds, or 0 if parsing fails.
     */
    public static int parseDuration(String durationString) {
        if (durationString == null || durationString.isEmpty()) {
            return 0;
        }
        Matcher matcher = DURATION_PATTERN.matcher(durationString.toLowerCase());
        int totalSeconds = 0;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            switch (unit) {
                case "s":
                    totalSeconds += value;
                    break;
                case "m":
                    totalSeconds += value * 60;
                    break;
                case "h":
                    totalSeconds += value * 60 * 60;
                    break;
                case "d":
                    totalSeconds += value * 24 * 60 * 60;
                    break;
            }
        }
        return totalSeconds;
    }

    /**
     * Formats a duration in milliseconds into a human-readable string like "1d 2h 30m 5s".
     *
     * @param millis The duration in milliseconds.
     * @return A formatted string, or "N/A" if millis is negative.
     */
    public static String formatDuration(long millis) {
        if (millis < 0) {
            return "N/A";
        }
        if (millis == 0) {
            return "0s";
        }

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0) { // Always show seconds if no other unit or if it's the only unit
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }

    /**
     * Formats a duration in seconds into a human-readable string like "1d 2h 30m 5s".
     * @param totalSeconds The duration in seconds.
     * @return A formatted string.
     */
    public static String formatDurationFromSeconds(long totalSeconds) {
        if (totalSeconds < 0) {
            return "N/A";
        }
        if (totalSeconds == 0) {
            return "0s";
        }
        return formatDuration(totalSeconds * 1000);
    }
}
