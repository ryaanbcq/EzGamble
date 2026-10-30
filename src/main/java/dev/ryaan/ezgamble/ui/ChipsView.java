package dev.ryaan.ezgamble.ui;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.PlayerStats;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Chips view (Games > Manage): one row per player who paid, his chips, and the ways to send them back; plus the
 * "All players" list. Port of the prototype's initChips / renderChips / renderAllPlayers (state kept in statics like
 * the prototype kept it on the screen).
 */
public final class ChipsView implements GameView {
    /** session | all */
    public static String who = "session";
    public static GlassField search, amountField;
    /** Name of the row whose amount field is open. */
    public static String editing;
    private static GlassSegmented whoSeg;
    private static boolean backing;
    private static int payVer = Payouts.ver;

    private final GambleScreen s;

    public ChipsView(GambleScreen s) { this.s = s; }

    private static int lx(GambleScreen s) { return (int) (s.x0 + GambleScreen.PAD); }
    private static int rx(GambleScreen s) { return (int) (s.x0 + GambleScreen.W - GambleScreen.PAD); }

    @Override
    public void init() {
        double lx = lx(s), rx = rx(s), y0 = s.y0;
        // the Session / All players control (prototype: rightGroup in init); it survives rebuilds so the pill keeps sliding
        if (whoSeg == null) whoSeg = new GlassSegmented(0, 0, 18, new String[][]{{"session", "Session"}, {"all", "All players"}},
                () -> who, v -> { who = v; GambleScreen cur = GambleScreen.current(); if (cur != null) { cur.chartAt = P.now(); cur.rebuild(); } });
        whoSeg.place(rx - whoSeg.w, y0 + 33);
        s.add(whoSeg);
        payVer = Payouts.ver;
        s.add(new GlassButton(lx, y0 + 80, 18, 16, "‹", "GHOST", () -> { editing = null; backing = true; s.back(); backing = false; }));
        // liquid glass search capsule, as wide as the header leaves
        double pw = 0;
        for (Object[] p : pills()) pw += P.pillW((String) p[0], (String) p[1]) + 4;
        if (search == null) {
            search = new GlassField(0, 0, 100, 16, "", "Search players", 16);
            search.search = true; search.focused = false;
            search.onChange = () -> { GambleScreen cur = GambleScreen.current(); if (cur == null) return; cur.sc("chips-" + who).target = 0; cur.rebuild(); };
        }
        search.x = lx + 24; search.y = y0 + 80; search.w = Math.max(70, rx - pw - 6 - (lx + 24));
        s.add(search);
        List<Payouts.Row> rows = who.equals("all") ? null : chipRows();
        String id = "chips-" + who;
        Widget.Clip clip = clip();
        if (rows == null) {   // all players: the whole row opens the profile
            List<PlayerStats> all = allRows();
            for (int i = 0; i < all.size(); i++) {
                final String name = all.get(i).name;
                double ry = clip.y() + i * 30;
                row(Widgets.area(lx, ry, rx - lx, 27, () -> openPlayer(name)), id, clip);
            }
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Payouts.Row r = rows.get(i);
            double ry = clip.y() + i * 30;
            // the whole row opens the player's profile; the buttons, added after, take their clicks first
            row(Widgets.area(lx, ry, rx - lx, 27, () -> openPlayer(r.name())), id, clip);
            if (r.pend() != null) continue;   // "Sending..." text only
            if (r.name().equals(editing)) {
                if (amountField == null) amountField = new GlassField(0, 0, 86, 18, "", "Amount", 10);
                amountField.x = rx - 6 - 18 - 4 - 40 - 4 - 86; amountField.y = ry + 4; amountField.focused = true;
                row(amountField, id, clip);
                row(new GlassButton(rx - 6 - 18 - 4 - 40, ry + 5, 40, 16, "Send", "PRIMARY", this::sendAmount), id, clip);
                row(new GlassButton(rx - 6 - 18, ry + 5, 18, 16, "×", "GHOST", () -> { editing = null; s.rebuild(); }), id, clip);
                continue;
            }
            // ONE button per row: Send opens the amount, filled with everything he has; Send again pays
            if (r.bal() <= 0) continue;   // nothing left: "Settled" is drawn instead
            row(new GlassButton(rx - 6 - 46, ry + 5, 46, 16, "Send", "GHOST", () -> {
                editing = r.name();
                amountField = new GlassField(0, 0, 86, 18, Money.format(r.bal()).replace("$", ""), "Amount", 10);
                amountField.selectAll = true;
                s.rebuild();
            }), id, clip);
        }
    }

    /** Every widget of a row scrolls with it. */
    private <T extends Widget> T row(T w, String id, Widget.Clip clip) {
        s.add(w);
        w.list = new Widget.ListPos(id, w.y); w.clip = clip;
        return w;
    }

    /** Where the Chips / All players list scrolls: under the title row, down to the footer pill (or the bottom). */
    private Widget.Clip clip() {
        double top = s.y0 + 102;
        return new Widget.Clip(lx(s), top, GambleScreen.W - 2 * GambleScreen.PAD, s.y0 + GambleScreen.H - GambleScreen.PAD - (who.equals("all") ? 0 : 18) - top);
    }

    /** Also used by the duel requests list (prototype chipsClip). */
    public static Widget.Clip listClip(GambleScreen s, boolean all) {
        double top = s.y0 + 102;
        return new Widget.Clip(lx(s), top, GambleScreen.W - 2 * GambleScreen.PAD, s.y0 + GambleScreen.H - GambleScreen.PAD - (all ? 0 : 18) - top);
    }

    private static String q() { return search == null ? "" : search.value.trim().toLowerCase().replaceFirst("^\\.", ""); }

    private static boolean matches(String name) {
        String q = q();
        return q.isEmpty() || name.toLowerCase().replaceFirst("^\\.", "").contains(q);
    }

    private List<Payouts.Row> chipRows() {
        List<Payouts.Row> out = new ArrayList<>();
        for (Payouts.Row r : Payouts.rows()) if (matches(r.name())) out.add(r);
        return out;
    }

    private List<PlayerStats> allRows() {
        List<PlayerStats> out = new ArrayList<>();
        for (PlayerStats p : PlayerStats.all()) if (matches(p.name)) out.add(p);
        return out;
    }

    /** The pills of the Chips header (totals over everybody, not just the search results). */
    private Object[][] pills() {
        if (who.equals("all")) {
            List<PlayerStats> rows = PlayerStats.all();
            long made = 0;
            for (PlayerStats p : rows) made += p.made;
            return new Object[][]{{"Players", String.valueOf(rows.size())}, {"You made", Money.signed(made), made >= 0 ? P.GREEN : P.RED}};
        }
        long held = 0, sent = 0;
        for (Payouts.Row r : Payouts.rows()) { held += r.bal(); sent += r.sent(); }
        return new Object[][]{{"Held", Money.format(held)}, {"Sent back", Money.format(sent)}};
    }

    private void openPlayer(String name) {
        P.play("click");
        PlayerView.player = name;
        editing = null;
        GambleScreen.sub = "player";
        s.chartAt = P.now();
        s.rebuild();
    }

    private void sendAmount() {
        long v = Money.parse(amountField != null ? amountField.value : "");
        if (v <= 0) { P.play("fail", 1, 0.5); return; }
        Payouts.send(editing, v);
        editing = null;
        s.rebuild();
    }

    @Override
    public void tick() {
        if (payVer != Payouts.ver) { payVer = Payouts.ver; s.rebuild(); }
    }

    @Override
    public void render(G g, double mx, double my) {
        if (who.equals("all")) { renderAll(g, mx, my); return; }
        int lx = lx(s), rx = rx(s); double y = s.y0 + 80;
        List<Payouts.Row> rows = chipRows();
        P.pillsRight(g, rx, y, pills());
        if (rows.isEmpty() && search != null && !search.value.isEmpty()) {
            String nm = "No player matches";
            g.drawText(nm, lx + ((rx - lx - F.width(nm)) >> 1), y + 34, P.MUTE, false);
        } else if (rows.isEmpty()) {
            g.drawText("No chips yet.", lx, y + 30, P.TEXT, false);
            g.drawText("Players who /pay you during a session show up here.", lx, y + 42, P.MUTE, false);
        }
        Widget.Clip clip = clip();
        String id = "chips-session";
        double ch = rows.size() * 30 - 3;
        double off = s.beginList(g, id, clip.x(), clip.y(), clip.w(), clip.h(), ch);
        boolean inList = my >= clip.y() && my < clip.y() + clip.h();
        for (int i = 0; i < rows.size(); i++) {
            Payouts.Row r = rows.get(i);
            double ry = clip.y() + i * 30 - off;
            if (ry + 27 < clip.y() || ry > clip.y() + clip.h()) continue;   // off screen
            P.card(g, lx, ry, rx - lx, 27, "row");
            if (inList && mx >= lx && mx < rx && my >= ry && my < ry + 27 && !r.name().equals(editing)) P.smooth(g, lx, ry, rx - lx, 27, 6, 0x0FFFFFFF, 0x30FFFFFF);   // hover: the row is a link
            g.drawHead(P.skinOf(r.name()), lx + 5, ry + 5, 16);
            g.drawText(F.trimToWidth(r.name(), 90), lx + 26, ry + 4, P.WHITE, false);
            String paid = "Paid " + Money.format(r.recv());
            g.drawText(paid, lx + 26, ry + 15, P.MUTE, false);
            if (r.won() != 0) g.drawText(Money.signed(r.won()), lx + 26 + F.width(paid) + 6, ry + 15, r.won() > 0 ? P.GREEN : P.RED, false);   // won or lost at the tables
            if (r.name().equals(editing)) continue;   // the field and its buttons take the right side
            double bx = rx - 6 - 52 - 4 - 40;   // left edge of the buttons
            String v = Money.format(r.bal());
            if (r.bal() > 0) P.gradientText(g, v, bx - 8 - F.width(v), ry + 10, true); else g.drawText(v, bx - 8 - F.width(v), ry + 10, P.DIM, false);
            if (r.bal() <= 0 && r.pend() == null) g.drawText("Settled", bx + 20 - (F.width("Settled") >> 1), ry + 10, P.GREEN, false);
            Payouts.Recent rec = r.recent() != null && P.now() - r.recent().at() < 4000 ? r.recent() : null;
            if (r.pend() != null) { String t = "Sending " + Money.format(r.pend().amount()) + "..."; g.drawText(t, rx - 6 - F.width(t), ry + 10, P.MUTE, false); }
            if (r.pend() == null && rec != null) {   // a short verdict over the row's left side, under the name
                String t = rec.ok() ? "✔ Sent " + Money.format(rec.amount()) : "✖ " + (rec.why() != null ? rec.why() : "Not confirmed");
                double tw = F.width(t) + 12, tx = bx - 8 - F.width(v) - 8 - tw;
                if (tx > lx + 26 + F.width(F.trimToWidth(r.name(), 90)) + 6) { P.capsule(g, tx, ry + 6, tw, 15, "ghost", false); g.drawText(t, tx + 6, ry + 10, rec.ok() ? P.GREEN : P.RED, false); }
            }
        }
        s.endList(g, id, ch);
        Session ses = Payouts.session();
        int n = ses != null && ses.payments != null ? ses.payments.size() : 0;
        double fy = s.y0 + GambleScreen.H - GambleScreen.PAD - 10;
        P.pill(g, lx, fy - 4, "Payments", String.valueOf(n));
    }

    /** All players ever: what each one paid in total, and what you made with him. */
    private void renderAll(G g, double mx, double my) {
        int lx = lx(s), rx = rx(s); double y = s.y0 + 80;
        List<PlayerStats> rows = allRows();
        P.pillsRight(g, rx, y, pills());
        if (rows.isEmpty()) { String nm = search != null && !search.value.isEmpty() ? "No player matches" : "Nobody has paid you yet."; g.drawText(nm, lx + ((rx - lx - F.width(nm)) >> 1), y + 34, P.MUTE, false); }
        Widget.Clip clip = clip();
        String id = "chips-all";
        double ch = rows.size() * 30 - 3;
        double off = s.beginList(g, id, clip.x(), clip.y(), clip.w(), clip.h(), ch);
        boolean inList = my >= clip.y() && my < clip.y() + clip.h();
        for (int i = 0; i < rows.size(); i++) {
            PlayerStats r = rows.get(i);
            double ry = clip.y() + i * 30 - off;
            if (ry + 27 < clip.y() || ry > clip.y() + clip.h()) continue;
            P.card(g, lx, ry, rx - lx, 27, "row");
            if (inList && mx >= lx && mx < rx && my >= ry && my < ry + 27) P.smooth(g, lx, ry, rx - lx, 27, 6, 0x0FFFFFFF, 0x30FFFFFF);
            g.drawHead(P.skinOf(r.name), lx + 5, ry + 5, 16);
            g.drawText(F.trimToWidth(r.name, 110), lx + 26, ry + 4, P.WHITE, false);
            g.drawText("Paid " + Money.format(r.paid), lx + 26, ry + 15, P.MUTE, false);
            String v = Money.signed(r.made), lbl = "You made";
            g.drawText(lbl, rx - 18 - F.width(lbl), ry + 4, P.MUTE, false);
            g.drawText(v, rx - 18 - F.width(v), ry + 15, r.made >= 0 ? P.GREEN : P.RED, false);
            g.drawText("›", rx - 10, ry + 10, P.DIM, false);
        }
        s.endList(g, id, ch);
    }

    @Override
    public boolean key(Key e) {
        if (search != null && search.focused) {   // typing in the player search
            if (e.is("Escape")) { search.value = ""; search.focused = false; search.onChange.run(); }
            else if (e.is("Enter")) search.focused = false;
            else if (search.type(e)) { /* onChange already ran */ }
            return true;
        }
        if (editing != null) {   // typing an amount: 5M, 250k...
            if (e.is("Enter")) sendAmount();
            else if (e.is("Escape")) { editing = null; s.rebuild(); }
            else if (amountField != null) amountField.type(e);
            return true;
        }
        return false;
    }

    @Override
    public void clicked(Widget hit) {
        if (search != null && hit != search) search.focused = false;
    }

    @Override
    public void leave() {
        editing = null;
        if (!backing && !"player".equals(GambleScreen.sub)) who = "session";   // a tab change resets Session / All players
    }
}
