package dev.ryaan.ezgamble.books;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.*;
import dev.ryaan.ezgamble.recap.RecapScreen;
import dev.ryaan.ezgamble.ui.GambleScreen;
import dev.ryaan.ezgamble.ui.Icons;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * Games > Books: the accounts. Tables (one row per game with the realised return against the table's theoretical
 * RTP), Hours (24 bars of activity), Days (a 7 x 5 calendar heatmap of the last five weeks) and Sessions (every
 * session, a click replays its recap). The footer exports CSV files and copies the totals. Drawn under its widgets
 * like the other non-game sub-views; the selected tab and the last export are kept in config/ezgamble/books.json.
 */
public final class BooksView implements GameView {
    private static final int GOLD = 0xFFF2C14E;
    private static final double ROW_H = 30, TABLE_ROW = 20;

    // ---- persistence: the tab and the last export (config/ezgamble/books.json); declared first, `tab` reads it below

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ezgamble").resolve("books.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final class State { String tab = "tables"; long lastExportAt; int lastExportRows; }
    private static State st;

    /** tables | hours | days | sessions */
    public static String tab = st().tab;
    private static GlassSegmented tabSeg;

    private final GambleScreen s;
    private GlassButton exportBtn, copyBtn;
    private long exportedAt = -1, copiedAt = -1, failedAt = -1;
    private int exportedRows, builtSessions = -1;

    public BooksView(GambleScreen s) { this.s = s; }

    private double lx() { return s.x0 + GambleScreen.PAD; }
    private double rx() { return s.x0 + GambleScreen.W - GambleScreen.PAD; }
    /** The content: under the title row, over the footer buttons. */
    private double top() { return s.y0 + 102; }
    private double bottom() { return s.y0 + GambleScreen.H - GambleScreen.PAD - 22; }
    private double footY() { return s.y0 + GambleScreen.H - GambleScreen.PAD - 18; }
    private Widget.Clip clip() { return new Widget.Clip(lx(), top(), rx() - lx(), bottom() - top()); }

    private static State st() {
        if (st != null) return st;
        try { if (Files.exists(FILE)) st = GSON.fromJson(Files.readString(FILE), State.class); } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] books.json unreadable: {}", e.toString()); }
        if (st == null) st = new State();
        if (st.tab == null || !List.of("tables", "hours", "days", "sessions").contains(st.tab)) st.tab = "tables";
        return st;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(st()), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] books.json write failed: {}", e.toString()); }
    }

    /** Switches the tab (the segmented control and DebugRun bookstab). */
    public static void setTab(String t) {
        if (!List.of("tables", "hours", "days", "sessions").contains(t)) return;
        tab = t; st().tab = t; save();
        GambleScreen cur = GambleScreen.current();
        if (cur != null) { cur.chartAt = P.now(); cur.rebuild(); }
    }

    // ---- widgets

    @Override
    public void init() {
        double lx = lx(), rx = rx(), y = s.y0 + 80;
        // the Tables / Hours / Days / Log control, top right like the chips' Session / All players; it survives rebuilds so the pill slides.
        // "Log" rather than "Sessions": the control must end short of the main tab bar on the same row (it ends at x0 + 204)
        if (tabSeg == null) tabSeg = new GlassSegmented(0, 0, 18, new String[][]{{"tables", "Tables"}, {"hours", "Hours"}, {"days", "Days"}, {"sessions", "Log"}}, () -> tab, BooksView::setTab);
        tabSeg.place(rx - tabSeg.w, s.y0 + 33);
        s.add(tabSeg);
        s.add(new GlassButton(lx, y, 18, 16, "‹", "GHOST", s::back));
        double ew = F.width("Export CSV") + 16, cw = F.width("Copy totals") + 16;
        exportBtn = s.add(new GlassButton(lx, footY(), ew, 16, "Export CSV", "GHOST", this::export));
        copyBtn = s.add(new GlassButton(lx + ew + 6, footY(), cw, 16, "Copy totals", "GHOST", this::copy));
        Books b = Books.get();
        builtSessions = rows(b);
        if (tab.equals("sessions") && b.bets > 0) {   // every row opens that session's recap; none while render shows "The books are empty"
            Widget.Clip clip = clip();
            for (int i = 0; i < b.sessions.size(); i++) {
                Session ses = b.sessions.get(i).session();
                Widget w = s.add(Widgets.area(lx, clip.y() + i * ROW_H, rx - lx, ROW_H - 3, () -> open(ses)));
                w.list = new Widget.ListPos("books-sessions", w.y); w.clip = clip;
            }
        }
    }

    private void open(Session ses) {
        P.play("click");
        MinecraftClient.getInstance().setScreen(new RecapScreen(ses));   // the full-screen story of THAT session; K brings the menu back on Books
    }

    private void export() {
        Books.Export ex = Books.export();
        if (ex == null) { failedAt = P.now(); P.play("fail", 1, 0.5); return; }
        exportedRows = ex.rows(); exportedAt = P.now();
        st().lastExportAt = System.currentTimeMillis(); st().lastExportRows = ex.rows(); save();
        P.play("cash", 1, 0.6);
    }

    private void copy() {
        MinecraftClient.getInstance().keyboard.setClipboard(Books.get().summary());
        copiedAt = P.now();
    }

    /** The session rows the page shows: none until the first bet (render says the books are empty instead). */
    private static int rows(Books b) { return b.bets == 0 ? 0 : b.sessions.size(); }
    @Override public void tick() { if (tab.equals("sessions") && rows(Books.get()) != builtSessions) s.rebuild(); }   // a session opened or closed, or the first bet: new rows
    @Override public void leave() {}

    // ---- drawing

    @Override
    public void render(G g, double mx, double my) {
        double lx = lx(), rx = rx(), y = s.y0 + 80;
        Books b = Books.get();
        g.drawText("Books", lx + 24, y + 4, WHITE, true);
        pillsRight(g, rx, y, pills(b));
        if (b.bets == 0) {
            String a = "The books are empty", c = "Every bet of every session is tallied here.";
            g.drawText(a, lx + (rx - lx - F.width(a)) / 2, y + 60, TEXT, false);
            g.drawText(c, lx + (rx - lx - F.width(c)) / 2, y + 72, MUTE, false);
        } else switch (tab) {
            case "hours" -> renderHours(g, mx, my, b);
            case "days" -> renderDays(g, mx, my, b);
            case "sessions" -> renderSessions(g, mx, my, b);
            default -> renderTables(g, mx, my, b);
        }
        renderFooter(g);
    }

    /** The header pills say what the tab is about: all-time totals, the busiest hour, the rolling week, best and worst. */
    private Object[][] pills(Books b) {
        return switch (tab) {
            case "hours" -> b.bestHour < 0 ? new Object[][]{{"Bets", String.valueOf(b.bets)}}
                    : new Object[][]{{"Busiest", P.pad2(b.bestHour) + "h"}, {"Bets", String.valueOf(b.hourBets[b.bestHour]), WHITE}};
            case "days" -> new Object[][]{{"Days played", String.valueOf(b.daysPlayed)}, {"7 days", Money.signed(b.week), b.week >= 0 ? GREEN : RED}};
            case "sessions" -> new Object[][]{{"Sessions", String.valueOf(b.sessions.size())},
                    {"Best", b.best == null ? "-" : Money.signed(b.best.profit()), b.best == null ? DIM : GREEN}, {"Worst", b.worst == null ? "-" : Money.signed(b.worst.profit()), b.worst == null ? DIM : RED}};
            default -> new Object[][]{{"Bets", String.valueOf(b.bets)}, {"Volume", Money.format(b.volume)}, {"Profit", Money.signed(b.profit), b.profit >= 0 ? GREEN : RED}};
        };
    }

    /** Realised against theory: within a point white, under it green (the house did better than its tables promise), over it red. */
    private static int rtpColor(Books.GameRow r) {
        double v = r.realised();
        if (Double.isNaN(v) || r.theory() == null) return WHITE;
        double gap = v - r.theory();
        return Math.abs(gap) <= 0.01 ? WHITE : gap < 0 ? GREEN : RED;
    }

    private void renderTables(G g, double mx, double my, Books b) {
        double lx = lx(), rx = rx();
        Widget.Clip clip = clip();
        // column heads, then one row per table: icon and name, bets, volume, house profit, realised / theoretical RTP
        double cBets = lx + 150, cVol = lx + 212, cProf = lx + 268, cRtp = rx - 6, hy = clip.y();   // "104.2% / 99.5%" is 72 px, it starts at lx + 274
        g.drawText("Table", lx + 6, hy, DIM, false);
        String[] heads = {"Bets", "Volume", "House", "RTP / theory"}; double[] right = {cBets, cVol, cProf, cRtp};
        for (int i = 0; i < heads.length; i++) g.drawText(heads[i], right[i] - F.width(heads[i]), hy, DIM, false);
        double top = hy + 11, lh = clip.y() + clip.h() - top, ch = b.games.size() * TABLE_ROW - 2;
        double off = s.beginList(g, "books-tables", lx, top, rx - lx, lh, ch);
        boolean inList = my >= top && my < top + lh;
        for (int i = 0; i < b.games.size(); i++) {
            Books.GameRow r = b.games.get(i);
            double ry = top + i * TABLE_ROW - off;
            if (ry + TABLE_ROW < top || ry > top + lh) continue;
            card(g, lx, ry, rx - lx, TABLE_ROW - 2, "row");
            if (inList && mx >= lx && mx < rx && my >= ry && my < ry + TABLE_ROW - 2) smooth(g, lx, ry, rx - lx, TABLE_ROW - 2, 6, 0x0FFFFFFF, 0x30FFFFFF);
            Icons.drawGameIcon(g, r.game(), lx + 4, ry + 2, 14);
            g.drawText(F.trimToWidth(r.game(), 84), lx + 22, ry + 5, WHITE, false);
            String n = String.valueOf(r.bets()), v = Money.format(r.volume()), p = Money.signed(r.profit());
            g.drawText(n, cBets - F.width(n), ry + 5, MUTE, false);
            g.drawText(v, cVol - F.width(v), ry + 5, WHITE, false);
            g.drawText(p, cProf - F.width(p), ry + 5, r.profit() >= 0 ? GREEN : RED, false);
            String th = " / " + Books.pct(r.theory()), re = Books.pct(r.realised());
            g.drawText(th, cRtp - F.width(th), ry + 5, MUTE, false);
            g.drawText(re, cRtp - F.width(th) - F.width(re), ry + 5, rtpColor(r), false);
        }
        s.endList(g, "books-tables", ch);
    }

    /** 24 thin bars, the height is the bets, the colour the sign of the house's profit there; labels every 6 h; the busiest hour named. */
    private void renderHours(G g, double mx, double my, Books b) {
        double lx = lx(), rx = rx();
        Widget.Clip clip = clip();
        double bw = 12, gap = 2, gw = 24 * bw + 23 * gap, x0 = lx + Math.floor((rx - lx - gw) / 2);
        double base = clip.y() + clip.h() - 12, topY = clip.y() + 16, maxH = base - topY;
        if (b.bestHour >= 0) {
            String a = "Busiest hour " + Books.hourText(b.bestHour) + ": " + b.hourBets[b.bestHour] + (b.hourBets[b.bestHour] == 1 ? " bet, " : " bets, ");
            g.drawText(a, lx + 4, clip.y() + 2, MUTE, false);
            g.drawText(Money.signed(b.hourProfit[b.bestHour]), lx + 4 + F.width(a), clip.y() + 2, b.hourProfit[b.bestHour] >= 0 ? GREEN : RED, false);
        }
        g.fill(x0, base, x0 + gw, base + 1, 0x24FFFFFF);   // the floor
        int hov = -1;
        for (int h = 0; h < 24; h++) {
            double x = x0 + h * (bw + gap);
            boolean over = mx >= x && mx < x + bw && my >= topY && my < base;
            if (over) hov = h;
            if (b.hourBets[h] == 0) { smooth(g, x, base - 2, bw, 2, 1, 0x1AFFFFFF); }
            else {
                double hh = Math.max(3, Math.round(maxH * b.hourBets[h] / (double) b.hourMax));
                int c = b.hourProfit[h] >= 0 ? GREEN : RED;
                smooth(g, x, base - hh, bw, hh, 3, v(alpha(c, over ? 1 : 0.85), alpha(c, over ? 0.6 : 0.35)), h == b.bestHour ? 0xB0FBC2EB : over ? 0x60FFFFFF : 0);
            }
            if (h % 6 == 0) { String l = h + "h"; g.drawText(l, x + (bw - F.width(l)) / 2, base + 3, DIM, false); }
        }
        if (hov >= 0) {   // the hour under the pointer, over everything
            String t = Books.hourText(hov) + " · " + b.hourBets[hov] + (b.hourBets[hov] == 1 ? " bet" : " bets") + (b.hourBets[hov] == 0 ? "" : " · " + Money.signed(b.hourProfit[hov]));
            tip(g, x0 + hov * (bw + gap) + bw / 2, topY - 2, t, b.hourBets[hov] == 0 ? MUTE : b.hourProfit[hov] >= 0 ? GREEN : RED);
        }
    }

    /** The last five weeks as a 7 x 5 heatmap, Monday at the top, the current week on the right; a summary column beside it. */
    private void renderDays(G g, double mx, double my, Books b) {
        double lx = lx(), rx = rx();
        Widget.Clip clip = clip();
        double cw = 44, chh = 18, gap = 3, gx = lx + 16, gy = clip.y() + 11;
        String[] wd = {"M", "T", "W", "T", "F", "S", "S"};
        for (int r = 0; r < 7; r++) g.drawText(wd[r], lx + 4, gy + r * (chh + gap) + 5, DIM, false);
        int hov = -1;
        long todayIdx = b.today.toEpochDay() - b.firstDay.toEpochDay();
        for (int w = 0; w < Books.WEEKS; w++) {
            LocalDate mon = b.firstDay.plusWeeks(w);
            String ml = P.pad2(mon.getDayOfMonth()) + "/" + P.pad2(mon.getMonthValue());   // the Monday of the column
            g.drawText(ml, gx + w * (cw + gap) + (cw - F.width(ml)) / 2, clip.y(), DIM, false);
            for (int r = 0; r < 7; r++) {
                int i = w * 7 + r;
                double x = gx + w * (cw + gap), y = gy + r * (chh + gap);
                boolean future = i > todayIdx, over = !future && mx >= x && mx < x + cw && my >= y && my < y + chh;
                if (over) hov = i;
                int fill;
                if (future) fill = 0x05FFFFFF;
                else if (b.dayBets[i] == 0) fill = 0x0CFFFFFF;
                else { int c = b.dayProfit[i] >= 0 ? GREEN : RED; fill = alpha(c, 0.18 + 0.62 * (b.dayMax == 0 ? 1 : Math.abs(b.dayProfit[i]) / (double) b.dayMax)); }   // the tint is the sign, the intensity the amount
                smooth(g, x, y, cw, chh, 4, fill, i == todayIdx ? 0x90FBC2EB : over ? 0x60FFFFFF : 0);
                if (!future && b.dayBets[i] > 0) { String t = Money.signed(b.dayProfit[i]); if (F.width(t) <= cw - 6) g.drawText(t, x + (cw - F.width(t)) / 2, y + 5, alpha(WHITE, 0.9), false); }
            }
        }
        // the summary column: the five weeks, the best day, the rolling week
        double sx = gx + Books.WEEKS * (cw + gap) + 8, sw = rx - sx;
        long five = 0; for (long v : b.dayProfit) five += v;
        tile(g, sx, clip.y() + 2, "5 weeks", F.trimToWidth(Money.signed(five), sw), five >= 0 ? GREEN : RED);
        tile(g, sx, clip.y() + 30, "Best day", b.bestDay < 0 ? "-" : F.trimToWidth(Books.dayText(b.firstDay.plusDays(b.bestDay)), sw), b.bestDay < 0 ? DIM : WHITE);
        if (b.bestDay >= 0) g.drawText(F.trimToWidth(Money.signed(b.dayProfit[b.bestDay]), sw), sx, clip.y() + 54, b.dayProfit[b.bestDay] >= 0 ? GREEN : RED, false);
        tile(g, sx, clip.y() + 72, "Last 7 days", F.trimToWidth(Money.signed(b.week), sw), b.week >= 0 ? GREEN : RED);
        tile(g, sx, clip.y() + 100, "Days played", b.daysPlayed + " of " + Books.DAYS, WHITE);
        if (hov >= 0) {
            String t = Books.dayText(b.firstDay.plusDays(hov)) + " · " + (b.dayBets[hov] == 0 ? "no bet" : b.dayBets[hov] + (b.dayBets[hov] == 1 ? " bet · " : " bets · ") + Money.signed(b.dayProfit[hov]));
            tip(g, gx + (hov / 7) * (cw + gap) + cw / 2, gy + (hov % 7) * (chh + gap) - 2, t, b.dayBets[hov] == 0 ? MUTE : b.dayProfit[hov] >= 0 ? GREEN : RED);
        }
    }

    /** Every session, two lines each: date with its tag (live, best, worst) and the profit; duration, bets and players, then Recap on hover. */
    private void renderSessions(G g, double mx, double my, Books b) {
        double lx = lx(), rx = rx();
        Widget.Clip clip = clip();
        double ch = b.sessions.size() * ROW_H - 3;
        double off = s.beginList(g, "books-sessions", clip.x(), clip.y(), clip.w(), clip.h(), ch);
        boolean inList = my >= clip.y() && my < clip.y() + clip.h();
        for (int i = 0; i < b.sessions.size(); i++) {
            Books.SessionRow r = b.sessions.get(i);
            double ry = clip.y() + i * ROW_H - off;
            if (ry + ROW_H < clip.y() || ry > clip.y() + clip.h()) continue;
            boolean hov = inList && mx >= lx && mx < rx && my >= ry && my < ry + ROW_H - 3;
            card(g, lx, ry, rx - lx, ROW_H - 3, "row");
            if (hov) smooth(g, lx, ry, rx - lx, ROW_H - 3, 6, 0x0FFFFFFF, 0x30FFFFFF);   // the row is a link
            String d = P.DATE(r.start());
            g.drawText(d, lx + 8, ry + 4, WHITE, false);
            double tx = lx + 8 + F.width(d) + 8;
            if (r.live()) { dot(g, tx, ry + 6, alpha(GREEN, 0.55 + 0.45 * pulse(now()))); g.drawText("Live", tx + 8, ry + 4, GREEN, false); }
            else if (r == b.best && r.bets() > 0) g.drawText("Best", tx, ry + 4, GOLD, false);
            else if (r == b.worst && r.bets() > 0) g.drawText("Worst", tx, ry + 4, RED, false);
            String l2 = P.clock(r.duration()) + " · " + r.bets() + (r.bets() == 1 ? " bet · " : " bets · ") + r.players() + (r.players() == 1 ? " player" : " players");
            g.drawText(F.trimToWidth(l2, rx - 60 - (lx + 8)), lx + 8, ry + 15, MUTE, false);
            String p = r.bets() == 0 ? "-" : Money.signed(r.profit());
            g.drawText(p, rx - 8 - F.width(p), ry + 4, r.bets() == 0 ? DIM : r.profit() >= 0 ? GREEN : RED, false);
            if (hov) { String rc = "Recap ›"; g.drawText(rc, rx - 8 - F.width(rc), ry + 15, PINK, false); }
            else g.drawText("›", rx - 12, ry + 15, DIM, false);
        }
        s.endList(g, "books-sessions", ch);
    }

    /** Export CSV, Copy totals, and what just happened (3 s), else when the books were last exported. */
    private void renderFooter(G g) {
        double y = footY() + 4, x = copyBtn.x + copyBtn.w + 8;
        copyBtn.label = P.now() - copiedAt < 3000 ? "✔ Copied" : "Copy totals";
        if (P.now() - exportedAt < 3000) g.drawText("Exported " + exportedRows + (exportedRows == 1 ? " row" : " rows"), x, y, GREEN, false);
        else if (P.now() - failedAt < 3000) g.drawText("Export failed, see the log", x, y, RED, false);
        else if (st().lastExportAt > 0) g.drawText(F.trimToWidth("Last export " + P.DATE(st().lastExportAt) + ", " + st().lastExportRows + (st().lastExportRows == 1 ? " row" : " rows"), rx() - x), x, y, DIM, false);
    }

    /** A glass label centred over (cx, bottom), kept inside the panel. */
    private void tip(G g, double cx, double bottom, String t, int color) {
        double tw = F.width(t) + 12, tx = Math.max(lx(), Math.min(cx - tw / 2, rx() - tw)), ty = bottom - 15;
        smooth(g, tx, ty, tw, 15, 7.5, v(0xF0291C42, 0xF01C1230), 0x40FFFFFF);
        g.drawText(t, tx + 6, ty + 4, color, false);
    }
}
