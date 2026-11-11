package dev.veryue.vuequests.util;

public class TextUtil {

    public static String abbreviateCount(int count) {
        long magnitude = Math.abs((long) count);
        if (magnitude < 1_000) {
            return Integer.toString(count);
        }
        int unit = magnitude < 1_000_000 ? 1_000 : magnitude < 1_000_000_000 ? 1_000_000 : 1_000_000_000;
        long tenths = Math.round(magnitude * 10D / unit);
        if (tenths >= 10_000 && unit < 1_000_000_000) {
            unit *= 1_000;
            tenths = 10;
        }
        String suffix = unit == 1_000 ? "k" : unit == 1_000_000 ? "m" : "b";
        return (count < 0 ? "-" : "") + tenths / 10 + (tenths % 10 == 0 ? "" : "." + tenths % 10) + suffix;
    }
}
