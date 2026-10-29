package dev.ryaan.ezgamble;
import dev.ryaan.ezgamble.ui.Glass;

import java.util.Locale;

/** DonutSMP amounts: "50M", "1.5k", "1,000,000", "12B". Stored in whole dollars. */
public final class Money {
    private Money() {}

    /** @return the amount in dollars, or -1 when the text is not an amount. */
    public static long parse(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().replace("$", "").replace(",", "").replace(" ", "").toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return -1;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') mult = 1e3; else if (last == 'm') mult = 1e6; else if (last == 'b') mult = 1e9;
        if (mult != 1) s = s.substring(0, s.length() - 1);
        try {
            double v = Double.parseDouble(s);
            if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) return -1;
            return Math.round(v * mult);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 1500000 -> "$1.5M", 12000 -> "$12K", 950 -> "$950". */
    public static String format(long v) {
        if (v >= 1_000_000_000L) return "$" + trim(v / 1e9) + "B";
        if (v >= 1_000_000L) return "$" + trim(v / 1e6) + "M";
        if (v >= 1_000L) return "$" + trim(v / 1e3) + "K";
        return "$" + v;
    }

    /** 1500000 -> "$1 500 000". */
    public static String full(long v) {
        return "$" + String.format(Locale.ROOT, "%,d", Math.abs(v)).replace(',', ' ');
    }

    /** +$1.5M / -$300K. */
    public static String signed(long v) { return (v >= 0 ? "+" : "-") + format(Math.abs(v)); }

    private static String trim(double d) {
        String s = String.format(Locale.ROOT, "%.2f", d);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }
}
