package ru.tehkode.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateUtils {
    private static final Pattern INTERVAL_PATTERN = Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s*(seconds?|minutes?|hours?|days?|weeks?|months?|years?|s|m|h|d|w)",
        Pattern.CASE_INSENSITIVE
    );

    public static int parseInterval(String arg) {
        if (arg.matches("^\\d+$")) {
            return Integer.parseInt(arg);
        }

        Matcher match = INTERVAL_PATTERN.matcher(arg);
        int interval = 0;

        while (match.find()) {
            float value = Float.parseFloat(match.group(1));
            int seconds = getSecondsIn(match.group(2));
            interval += Math.round(value * seconds);
        }

        return interval;
    }

    public static int getSecondsIn(String type) {
        switch (type.toLowerCase()) {
            case "second": case "seconds": case "s": return 1;
            case "minute": case "minutes": case "m": return 60;
            case "hour": case "hours": case "h": return 3600;
            case "day": case "days": case "d": return 86400;
            case "week": case "weeks": case "w": return 604800;
            case "month": case "months": return 2592000;
            case "year": case "years": return 31104000;
            default:
                throw new IllegalArgumentException("Unknown time unit: " + type);
        }
    }
}
