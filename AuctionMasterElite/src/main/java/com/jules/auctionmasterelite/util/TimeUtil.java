package com.jules.auctionmasterelite.util;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smhd])");

    /**
     * Parses a time string like "1d12h30m15s" into milliseconds.
     * @param timeString The string to parse.
     * @return The total time in milliseconds, or -1 if the string is invalid.
     */
    public static long parseTime(String timeString) {
        long totalMillis = 0;
        Matcher matcher = TIME_PATTERN.matcher(timeString.toLowerCase());
        boolean matchFound = false;

        while (matcher.find()) {
            matchFound = true;
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            switch (unit) {
                case "s":
                    totalMillis += TimeUnit.SECONDS.toMillis(value);
                    break;
                case "m":
                    totalMillis += TimeUnit.MINUTES.toMillis(value);
                    break;
                case "h":
                    totalMillis += TimeUnit.HOURS.toMillis(value);
                    break;
                case "d":
                    totalMillis += TimeUnit.DAYS.toMillis(value);
                    break;
            }
        }

        return matchFound ? totalMillis : -1;
    }

    /**
     * Formats a duration in milliseconds into a human-readable string (e.g., "1d 12h 30m 15s").
     * @param millis The duration in milliseconds.
     * @return The formatted string.
     */
    public static String formatDuration(long millis) {
        if (millis < 0) {
            return "N/A";
        }

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}
