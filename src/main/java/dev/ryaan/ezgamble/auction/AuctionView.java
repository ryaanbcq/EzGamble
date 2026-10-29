package dev.ryaan.ezgamble.auction;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.proto.*;
import dev.ryaan.ezgamble.ui.GambleScreen;
import dev.ryaan.ezgamble.ui.ProfitChart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * Games > Auction (sub-view): the form (idle) with its item picker and history, the live ranking (running), the
 * winner (ended). The picker and the history are views of it too.
 */
public final class AuctionView implements GameView {
    private static final int PAD = GambleScreen.PAD, W = GambleScreen.W, H = GambleScreen.H;

    /** A form field with its commit (read on Enter / click elsewhere / Start). */
    private static final class AucField extends GlassField {
        Predicate<AucField> commit;
        AucField(double w, String value, String ph, int max) { super(0, 0, w, 18, value, ph, max); focused = false; }
    }
    private record Bad(AucField f, String msg, long at) {}

    private final GambleScreen s;
    private String view = "form";   // form | picker | history
    private final Map<String, AucField> fields = new HashMap<>();
    private List<AucField> live = new ArrayList<>();   // the fields of the current build
    private Bad bad;
    private long armed;
    private GlassButton cancelBtn;
    private Widget rewardBtn;
    private GlassSegmented dur;
    private Auction.State builtState = Auction.State.IDLE;
    private String lastQ; private List<Auction.ItemEntry> lastItems;

    public AuctionView(GambleScreen s) { this.s = s; }

    // ---- build

    @Override
    public void init() {
        // fields that were being typed in when the view was rebuilt: read them (a click elsewhere reads the field)
        for (AucField f : live) if (f.focused && f.commit != null) { f.focused = false; f.commit.test(f); }
        double lx = s.x0 + PAD, rx = s.x0 + W - PAD, y0 = s.y0;
        builtState = Auction.state; live = new ArrayList<>();
        if (Auction.state != Auction.State.IDLE && view.equals("picker")) view = "form";
        cancelBtn = null; rewardBtn = null;
        if (view.equals("picker")) {   // ItemPickerScreen: every item of the game, filtered by the search field
            s.add(new GlassButton(lx, y0 + 80, 18, 16, "‹", "GHOST", () -> { view = "form"; s.rebuild(); }));
            AucField f = fields.computeIfAbsent("search", k -> new AucField(10, "", "Search an item", 40));
            f.x = lx + 24; f.y = y0 + 79; f.w = rx - lx - 24; f.focused = true;
            s.add(f); live.add(f);
            double[] c = pickClip();
            s.add(new Widget(c[0], c[1], c[2], c[3]) {
                @Override public void click(double mx, double my) {
                    String id = pickHit(mx, my);
                    if (id != null) { P.play("click"); Auction.item = id; Auction.save(); view = "form"; s.rebuild(); }
                }
            });
            return;
        }
        if (view.equals("history")) { s.add(new GlassButton(lx, y0 + 80, 18, 16, "‹", "GHOST", () -> { view = "form"; s.rebuild(); })); return; }
        s.add(new GlassButton(lx, y0 + 80, 18, 16, "‹", "GHOST", s::back));
        double by = y0 + H - PAD - 24;
        if (Auction.state == Auction.State.IDLE) {
            s.add(new GlassButton(rx - 54, y0 + 80, 54, 16, "History", "GHOST", () -> { view = "history"; s.chartAt = P.now(); s.rebuild(); }));
            // the reward card opens the picker; the stepper on its right sets the amount
            rewardBtn = s.add(new Widget(lx, y0 + 102, rx - lx - 90, 40) {
                @Override public void click(double mx, double my) { P.play("click"); view = "picker"; fields.remove("search"); s.rebuild(); }
            });
            s.add(new GlassButton(rx - 10 - 16 - 30 - 16, y0 + 114, 16, 16, "‹", "GHOST", () -> { Auction.count = Math.max(1, Auction.count - 1); Auction.save(); }));
            s.add(new GlassButton(rx - 10 - 16, y0 + 114, 16, 16, "›", "GHOST", () -> { Auction.count = Math.min(64, Auction.count + 1); Auction.save(); }));
            // fields survive rebuilds (what was typed stays), commit = read on Enter / click elsewhere / Start
            double half = (int) ((rx - lx - 6) / 2);
            fld("min", lx + 8, y0 + 168, 90, Money.format(Auction.min).substring(1), "Amount", 10, f -> {
                long v = Money.parse(f.value); if (v < 0) return bad(f, "Not an amount");
                Auction.min = v; f.value = Money.format(v).substring(1); Auction.save(); return true;
            });
            AucField secsF = fld("secs", lx + half + 6 + 8, y0 + 168, 44, String.valueOf(Auction.secs), "120", 5, f -> {
                int v = jsParseInt(f.value); if (!(v >= 15 && v <= 86400)) return bad(f, "15 to 86400 s");   // 15 s at least (Ryan)
                Auction.secs = v; f.value = String.valueOf(v); Auction.save(); return true;
            });
            if (dur == null) dur = new GlassSegmented(0, 0, 18, new String[][]{{"15", "15s"}, {"60", "1m"}, {"120", "2m"}, {"300", "5m"}},
                    () -> String.valueOf(Auction.secs), v -> { Auction.secs = Integer.parseInt(v); fields.get("secs").value = v; Auction.save(); });
            dur.place(lx + half + 6 + 8 + 44 + 4, y0 + 168);
            s.add(dur);
            fld("ot", lx + 8, y0 + 220, 44, String.valueOf(Auction.overtime), "10", 4, f -> {
                int v = jsParseInt(f.value.isEmpty() ? "0" : f.value); if (!(v >= 0 && v <= 600)) return bad(f, "0 to 600 s");
                Auction.overtime = v; f.value = String.valueOf(v); Auction.save(); return true;
            });
            fld("sd", lx + half + 6 + 8, y0 + 220, 44, String.valueOf(Auction.streamDelay), "0", 4, f -> {
                int v = jsParseInt(f.value.isEmpty() ? "0" : f.value); if (!(v >= 0 && v <= 600)) return bad(f, "0 to 600 s");
                Auction.streamDelay = v; f.value = String.valueOf(v); Auction.save(); return true;
            });
            s.add(new GlassButton(lx, by, rx - lx, 24, "Start auction", "PRIMARY", this::startAuction));
        }
        if (Auction.state == Auction.State.RUNNING) {
            GlassButton cancel = s.add(new GlassButton(lx, by + 3, 70, 18, "Cancel", "DANGER", null));
            cancel.action = () -> {
                if (armed != 0 && P.now() < armed) { armed = 0; Auction.cancel(); }
                else { armed = P.now() + 3000; cancel.label = "Sure?"; }
            };
            cancelBtn = cancel;
            s.add(new GlassButton(rx - 70, by + 3, 70, 18, "End now", "GHOST", Auction::finish));
        }
        if (Auction.state == Auction.State.ENDED) {
            s.add(new GlassButton(lx, by, rx - lx - 100, 24, "New auction", "PRIMARY", () -> { Auction.reset(); s.rebuild(); }));
            s.add(new GlassButton(rx - 94, by, 94, 24, "Run it back", "GHOST", () -> { Auction.reset(); Auction.start(); s.close(); }));
        }
    }

    private AucField fld(String key, double x, double y, double w, String value, String ph, int max, Predicate<AucField> commit) {
        AucField f = fields.computeIfAbsent(key, k -> new AucField(w, value, ph, max));
        f.x = x; f.y = y; f.w = w; f.commit = commit;
        s.add(f); live.add(f);
        return f;
    }

    private boolean bad(AucField f, String msg) { bad = new Bad(f, msg, P.now()); P.play("fail", 1.4, 0.4); return false; }

    /** JavaScript's parseInt: leading digits, else -1 (NaN: every range test fails). */
    private static int jsParseInt(String v) {
        String t = v.trim();
        int i = 0;
        while (i < t.length() && Character.isDigit(t.charAt(i))) i++;
        if (i == 0 || i > 9) return -1;
        return Integer.parseInt(t.substring(0, i));
    }

    /** Every field read; the menu folds (pop), the reward pops like a totem, the HUD takes over. */
    private void startAuction() {
        for (AucField f : live) { f.focused = false; if (f.commit != null && !f.commit.test(f)) return; }
        Auction.start();
        s.close();
    }

    private double[] pickClip() { double top = s.y0 + 102; return new double[]{s.x0 + PAD, top, W - 2 * PAD, s.y0 + H - PAD - top}; }

    private List<Auction.ItemEntry> pickItems() {
        AucField f = fields.get("search");
        String q = f == null ? "" : f.value.trim().toLowerCase();
        if (lastItems != null && q.equals(lastQ)) return lastItems;
        List<Auction.ItemEntry> all = Auction.itemList(), out = all;
        if (!q.isEmpty()) { out = new ArrayList<>(); for (var it : all) if (it.key().contains(q) || it.id().contains(q)) out.add(it); }
        lastQ = q; lastItems = out;
        return out;
    }

    /** {x, y, w, h, cols, gx} */
    private double[] pickGeom() {
        double[] c = pickClip();
        int cols = Math.max(1, (int) Math.floor(c[2] / 20));
        return new double[]{c[0], c[1], c[2], c[3], cols, c[0] + ((int) (c[2] - cols * 20) >> 1)};
    }

    private String pickHit(double mx, double my) {
        double[] G = pickGeom();
        var items = pickItems();
        long off = Math.round(s.sc("picker").pos);
        int cols = (int) G[4];
        int col = (int) Math.floor((mx - G[5]) / 20), row = (int) Math.floor((my - G[1] + off) / 20);
        if (col < 0 || col >= cols || row < 0) return null;
        int i = row * cols + col;
        return i < items.size() ? items.get(i).id() : null;
    }

    // ---- logic

    @Override
    public void tick() {
        if (builtState != Auction.state) s.rebuild();
        if (cancelBtn != null && cancelBtn.label.equals("Sure?") && P.now() >= armed) cancelBtn.label = "Cancel";
    }

    @Override
    public void leave() {
        view = "form"; fields.remove("search");
        for (AucField f : fields.values()) f.focused = false;
    }

    @Override
    public boolean key(Key e) {
        // typing in the auction form or the item search (K types a K there)
        for (AucField f : live) if (f.focused) {
            if (e.is("Enter") || e.is("Escape")) { f.focused = false; if (f.commit != null) f.commit.test(f); }
            else f.type(e);
            return true;
        }
        return false;
    }

    /** A click elsewhere reads the field. */
    @Override
    public void clicked(Widget hit) {
        for (AucField f : new ArrayList<>(live)) if (f.focused && hit != f && f.commit != null) { f.focused = false; f.commit.test(f); }
    }

    @Override
    public boolean scrolled(double mx, double my, double dy, double dx) { return false; }

    // ---- render

    @Override
    public void render(G g, double mx, double my) {
        double lx = s.x0 + PAD, rx = s.x0 + W - PAD;
        long t = P.now();
        double y = s.y0 + 80;
        if (view.equals("picker")) { renderPicker(g, mx, my); return; }
        if (view.equals("history")) { renderHistory(g, mx, my); return; }
        g.drawText("Auction", lx + 24, y + 4, WHITE, true);
        if (Auction.state == Auction.State.IDLE) {
            // reward: the item big, its name, the amount; the whole card is the picker's button
            y = s.y0 + 102;
            boolean hov = rewardBtn != null && rewardBtn.over(mx, my);
            card(g, lx, y, rx - lx, 40, "soft");
            if (hov) smooth(g, lx, y, rx - lx, 40, 7, 0x0FFFFFFF, 0x40FFFFFF);
            g.push(); g.translate(lx + 8, y + 4); g.scale(2, 2); g.drawItem(Auction.item, 0, 0, true); g.pop();
            g.drawText("Reward", lx + 46, y + 8, MUTE, false);
            g.drawText(F.trimToWidth(Auction.itemName(Auction.item), rx - lx - 90 - 50), lx + 46, y + 20, hov ? WHITE : TEXT, false);
            g.drawText("›", rx - 90 - 10, y + 16, hov ? WHITE : DIM, false);
            String c = String.valueOf(Auction.count);
            g.drawText(c, rx - 10 - 16 - 15 - (F.width(c) >> 1), y + 18, WHITE, true);
            g.drawText("Amount", rx - 10 - 16 - 15 - (F.width("Amount") >> 1), y + 5, MUTE, false);
            // minimum bid / timer, then overtime / stream delay: four cards, label row + control row
            double half = (int) ((rx - lx - 6) / 2);
            Bad bd = bad != null && t - bad.at() < 2500 ? bad : null;
            y = s.y0 + 148;
            AucField fm = fields.get("min"), fs = fields.get("secs");
            long mv = fm != null && fm.focused ? Money.parse(fm.value) : Auction.min;
            box(g, lx, y, half, "Minimum bid", fm, bd, mv >= 0 ? Money.full(mv) : "", DIM);
            int sv = fs != null && fs.focused ? jsParseInt(fs.value) : Auction.secs;
            box(g, lx + half + 6, y, half, "Timer", fs, bd, sv >= 5 && sv <= 86400 ? P.clock(sv * 1000L) : "", DIM);
            y = s.y0 + 200;
            box(g, lx, y, half, "Overtime", fields.get("ot"), bd, Auction.overtime != 0 ? "" : "Off", MUTE);
            g.drawText("s", lx + 8 + 44 + 4, y + 25, TEXT, false);
            box(g, lx + half + 6, y, half, "Stream delay", fields.get("sd"), bd, Auction.streamDelay != 0 ? "" : "Off", MUTE);
            g.drawText("s", lx + half + 6 + 8 + 44 + 4, y + 25, TEXT, false);
            return;
        }
        // live: reward, pot, timer
        y = s.y0 + 100;
        card(g, lx, y, rx - lx, 48, "accent");
        g.push(); g.translate(lx + 8, y + 8); g.scale(2, 2); g.drawItem(Auction.item, 0, 0, true); g.pop();
        g.drawText(F.trimToWidth(Auction.label(), 150), lx + 48, y + 9, WHITE, true);
        String[][] pills = {{"Min", Money.format(Auction.min)}, {"Pot", Money.format(Auction.pot())}};
        double px = lx + 48;
        for (String[] p : pills) { pill(g, px, y + 24, p[0], p[1]); px += pillW(p[0], p[1]) + 4; }
        if (Auction.state == Auction.State.RUNNING) {
            boolean grace = Auction.inGrace();
            long left = Auction.remaining();
            boolean last10 = !grace && left < 10000;
            String clk = P.clock(left);
            double cw = F.width(clk) * 2;
            g.push(); g.translate(rx - 10 - cw, y + 10); g.scale(2, 2);
            if (grace) gradientText(g, clk, 0, 0, true, 0.6 + 0.4 * pulse(t * 2));
            else if (last10) g.drawText(clk, 0, 0, (t / 250) % 2 != 0 ? RED : WHITE, true);
            else gradientText(g, clk, 0, 0, true);
            g.pop();
            boolean ext = Auction.extendedAt != 0 && t - Auction.extendedAt < 1500;
            String f = grace ? "And the winner is" + ".".repeat((int) (t / 300 % 4)) : ext ? "Overtime +" + Math.max(1, Math.round(Auction.extendedBy / 1000.0)) + "s" : "";
            if (!f.isEmpty()) {
                double fw = F.width(grace ? "And the winner is..." : f) + 12, fx = rx - 10 - fw;
                capsule(g, fx, y + 29, fw, 13, "ghost", false); g.drawText(f, fx + 6, y + 32, PINK, false);
            }
        } else {
            String done = Auction.winner != null ? "Sold" : "Closed";
            g.push(); g.translate(rx - 10 - F.width(done) * 2, y + 10); g.scale(2, 2); gradientText(g, done, 0, 0, true); g.pop();
        }
        y += 54;
        if (Auction.state == Auction.State.ENDED) {   // winner banner
            Auction.Bid w = Auction.winner;
            card(g, lx, y, rx - lx, 30, w != null ? "accent" : "soft");
            if (w != null) {
                g.drawHead(skinOf(w.name), lx + 8, y + 7, 16);
                g.drawText("Winner", lx + 30, y + 5, MUTE, false);
                gradientText(g, w.name, lx + 30, y + 16, true);
                String v = Money.format(w.total);
                g.drawText(v, rx - 10 - F.width(v), y + 11, GREEN, true);
            } else {
                g.drawText("No winner", lx + 10, y + 5, WHITE, false);
                g.drawText(!Auction.rows().isEmpty() ? "Nobody reached " + Money.format(Auction.min) : "No bids", lx + 10, y + 16, MUTE, false);
            }
            y += 36;
        }
        // ranking (scrolls): rank, head, name, payments, total; the eligible leader in the accent
        List<Auction.Bid> rows = Auction.rows();
        Auction.Bid lead = Auction.leader();
        double bottom = s.y0 + H - PAD - 28, lh = bottom - y;
        if (rows.isEmpty() && Auction.state == Auction.State.RUNNING) {
            String a1 = "Waiting for the first bid", a2 = "/pay " + AuctionHud.me();
            g.drawText(a1, lx + (int) ((rx - lx - F.width(a1)) / 2), y + 20, TEXT, false);
            g.drawText(a2, lx + (int) ((rx - lx - F.width(a2)) / 2), y + 32, MUTE, false);
            return;
        }
        String id = "auction";
        double off = s.beginList(g, id, lx, y, rx - lx, lh, rows.size() * 24 - 2);
        for (int i = 0; i < rows.size(); i++) {
            Auction.Bid b = rows.get(i);
            double ry = y + i * 24 - off;
            if (ry + 22 < y || ry > y + lh) continue;
            boolean isLead = b == lead, under = b.total < Auction.min;
            if (isLead) smooth(g, lx, ry, rx - lx, 22, 6, 0x1FFBC2EB, 0x80FBC2EB); else card(g, lx, ry, rx - lx, 22, "row");
            g.drawText("#" + (i + 1), lx + 6, ry + 7, isLead ? PINK : DIM, false);
            g.drawHead(skinOf(b.name), lx + 24, ry + 3, 16);
            g.drawText(F.trimToWidth(b.name, 100), lx + 45, ry + 7, under ? MUTE : WHITE, false);
            double tx = lx + 45 + F.width(F.trimToWidth(b.name, 100)) + 6;
            if (b.payments > 1) { String x = "×" + b.payments; g.drawText(x, tx, ry + 7, MUTE, false); tx += F.width(x) + 6; }
            if (under) g.drawText("Under min", tx, ry + 7, DIM, false);
            String v = Money.format(b.total);
            if (isLead) gradientText(g, v, rx - 8 - F.width(v), ry + 7, true); else g.drawText(v, rx - 8 - F.width(v), ry + 7, under ? DIM : TEXT, false);
            if (b.lastAt != 0 && t - b.lastAt < 1500) {   // the bid that just landed
                String plus = "+" + Money.format(b.last);
                double pw = F.width(plus) + 10, pxx = rx - 8 - F.width(v) - 6 - pw;
                double a = 1 - Math.max(0, (t - b.lastAt - 900) / 600.0);
                capsule(g, pxx, ry + 4, pw, 13, "ghost", false, a); g.drawText(plus, pxx + 5, ry + 7, alpha(GREEN, a), false);
            }
        }
        s.endList(g, id, rows.size() * 24 - 2);
    }

    private void box(G g, double x, double yy, double half, String label, AucField f, Bad bd, String right, int rc) {
        card(g, x, yy, half, 46, "soft");
        g.drawText(label, x + 8, yy + 7, MUTE, false);
        if (bd != null && bd.f() == f) g.drawText(bd.msg(), x + half - 8 - F.width(bd.msg()), yy + 7, RED, false);
        else if (!right.isEmpty()) g.drawText(right, x + half - 8 - F.width(right), yy + 7, rc, false);
    }

    /** ItemPickerScreen: a grid of every item (20 px cells, scrolls), the search field in the header, a name tooltip. */
    private void renderPicker(G g, double mx, double my) {
        double[] G = pickGeom();
        var items = pickItems();
        int cols = (int) G[4];
        int rowsN = (items.size() + cols - 1) / cols;
        String id = "picker";
        double off = s.beginList(g, id, G[0], G[1], G[2], G[3], rowsN * 20);
        int first = Math.max(0, (int) Math.floor(off / 20)), lastRow = Math.min(rowsN, first + (int) Math.ceil(G[3] / 20) + 1);
        boolean inGrid = mx >= G[0] && mx < G[0] + G[2] && my >= G[1] && my < G[1] + G[3];
        Auction.ItemEntry hovered = null; double hx = 0, hy = 0;
        for (int r = first; r < lastRow; r++) for (int c = 0; c < cols; c++) {
            int i = r * cols + c;
            if (i >= items.size()) break;
            var it = items.get(i);
            double x = G[5] + c * 20, y = G[1] + r * 20 - off;
            boolean sel = it.id().equals(Auction.item), hov = inGrid && mx >= x && mx < x + 20 && my >= y && my < y + 20;
            if (sel) smooth(g, x, y, 20, 20, 5, 0x33FBC2EB, 0xCCFBC2EB);
            else if (hov) smooth(g, x, y, 20, 20, 5, 0x1FFFFFFF, 0x40FFFFFF);
            g.drawItem(it.id(), x + 2, y + 2, true);   // every reward is shown enchanted (EzAuction's glint override)
            if (hov) { hovered = it; hx = x; hy = y; }
        }
        if (items.isEmpty()) g.drawText("No item matches.", G[0] + 4, G[1] + 8, DIM, false);
        s.endList(g, id, rowsN * 20);
        if (hovered != null) {   // name above the cell (a tooltip, kept inside the panel)
            double tw = F.width(hovered.name()) + 12, tx = Math.max(G[0], Math.min(G[0] + G[2] - tw, hx + 10 - ((int) tw >> 1))), ty = hy - 18 < G[1] - 4 ? hy + 22 : hy - 18;
            glass(g, tx, ty, tw, 16, 7, 0.8, 0.8);
            g.drawText(hovered.name(), tx + 6, ty + 4, WHITE, false);
        }
    }

    /** HistoryScreen + ProfitScreen, revenue only (Ryan: no reward cost): tiles, the revenue curve, one row per auction. */
    private void renderHistory(G g, double mx, double my) {
        double lx = s.x0 + PAD, rx = s.x0 + W - PAD;
        var list = Auction.history;
        double y = s.y0 + 80;
        g.drawText("Auctions", lx + 24, y + 4, WHITE, true);
        long sold = list.stream().filter(h -> h.winner != null).count(), rev = list.stream().mapToLong(h -> h.revenue).sum();
        pillsRight(g, rx, y, new Object[][]{{"Auctions", String.valueOf(list.size())}, {"Sold", String.valueOf(sold)}});
        y = s.y0 + 102;
        card(g, lx, y, rx - lx, 34, "accent");
        double third = (int) ((rx - lx - 10) / 3);
        long best = list.stream().mapToLong(h -> h.revenue).max().orElse(0);
        tile(g, lx + 10, y + 6, "Revenue", Money.format(rev), rev != 0 ? GREEN : DIM);
        tile(g, lx + 10 + third, y + 6, "Best sale", best != 0 ? Money.format(best) : "-", best != 0 ? WHITE : DIM);
        tile(g, lx + 10 + 2 * third, y + 6, "Average", sold != 0 ? Money.format(Math.round(rev / (double) sold)) : "-", sold != 0 ? WHITE : DIM);
        y += 38;
        double ch = 54;
        card(g, lx, y, rx - lx, ch, "chart");
        if (list.isEmpty()) g.drawText("No auction yet.", lx + 8, y + (int) (ch / 2) - 4, DIM, false);
        else {
            long[] deltas = new long[list.size()];
            for (int i = 0; i < deltas.length; i++) deltas[i] = list.get(i).revenue;
            float reveal = (float) easeOut((P.now() - s.chartAt) / 600.0);
            var r = ProfitChart.draw(g.d, deltas, (int) lx, (int) y, (int) (rx - lx), (int) ch, (int) mx, (int) my, false, 0, true, reveal);
            if (r.hover() > 0) {
                var h = list.get(r.hover() - 1);
                ProfitChart.tooltip(g.d, h.label, "+" + Money.format(h.revenue), h.revenue != 0 ? GREEN : DIM, r.px()[r.hover()], (int) y, (int) lx, (int) rx, DATE(h.at), h.winner != null ? h.winner.name : "No winner");
            }
        }
        y += ch + 4;
        List<Auction.Hist> rows = new ArrayList<>(list);
        java.util.Collections.reverse(rows);
        String id = "auction-history";
        double lh = s.y0 + H - PAD - y;
        double off = s.beginList(g, id, lx, y, rx - lx, lh, rows.size() * 30 - 3);
        for (int i = 0; i < rows.size(); i++) {
            var h = rows.get(i);
            double ry = y + i * 30 - off;
            if (ry + 27 < y || ry > y + lh) continue;
            card(g, lx, ry, rx - lx, 27, "row");
            g.drawItem(h.item, lx + 5, ry + 5, true);
            g.drawText(F.trimToWidth(h.label, 150), lx + 26, ry + 4, WHITE, false);
            if (h.winner != null) { g.drawHead(skinOf(h.winner.name), lx + 26, ry + 15, 8); g.drawText(F.trimToWidth(h.winner.name, 120), lx + 37, ry + 15, MUTE, false); }
            else g.drawText("No winner", lx + 26, ry + 15, DIM, false);
            String v = h.revenue != 0 ? "+" + Money.format(h.revenue) : "$0";
            g.drawText(v, rx - 6 - F.width(v), ry + 4, h.revenue != 0 ? GREEN : DIM, false);
            String d = DATE(h.at);
            g.drawText(d, rx - 6 - F.width(d), ry + 15, DIM, false);
        }
        s.endList(g, id, rows.size() * 30 - 3);
    }
}
