package dev.ryaan.ezgamble.data;

import dev.ryaan.ezgamble.Money;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your own balance, never asked for: the server's sidebar line "$ 9.8B" (rounded, read every second) and the exact
 * figure of a /bal you typed ("You have $ 9,898,239,382", "Tu possèdes $ ..."). The exact one is kept while the sidebar
 * still agrees with it; once the sidebar moves away (a bet, a payment), the sidebar's value takes over until the next /bal.
 */
public final class Balance {
    private Balance() {}

    private static final Pattern BOARD = Pattern.compile("^\\$\\s?([0-9][0-9.,]*\\s?[KMBTkmbt]?)$");
    private static final Pattern BAL = Pattern.compile(
            "^(?:you have|your balance(?: is)?:?|balance:?|tu poss[eè]des|du hast|tienes|voc[eê] tem|je hebt)\\s*\\$\\s?([0-9][0-9,.]*\\s?[KMBTkmbt]?)\\s*[.!]?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CODES = Pattern.compile("§.");

    private static long exact = -1, board = -1;
    private static int ticks;

    /** The balance to show, or -1 while we have not seen it. */
    public static long value() {
        if (exact >= 0 && (board < 0 || Math.abs(exact - board) <= Math.max(1, board * 0.01))) return exact;
        return board;
    }

    public static void tick(MinecraftClient c) {
        if (c.world == null) { board = -1; return; }
        if (++ticks % 20 != 0) return;
        var sb = c.world.getScoreboard();
        var obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (obj == null) return;
        for (var e : sb.getScoreboardEntries(obj)) {
            Text name = e.display() != null ? e.display() : Team.decorateName(sb.getScoreHolderTeam(e.owner()), Text.literal(e.owner()));
            Matcher m = BOARD.matcher(CODES.matcher(name.getString()).replaceAll("").trim());
            if (m.matches()) { long v = Money.parse(m.group(1)); if (v >= 0) board = v; return; }
        }
    }

    /** A /bal reply: the exact figure. Never swallows the line. */
    public static void onChat(String line) {
        Matcher m = BAL.matcher(CODES.matcher(line).replaceAll("").trim());
        if (m.matches()) { long v = Money.parse(m.group(1)); if (v >= 0) exact = v; }
    }

    /** Debug: as if the sidebar or /bal said so. */
    public static void debug(long sidebar, long bal) { if (sidebar >= 0) board = sidebar; if (bal >= 0) exact = bal; }
}
