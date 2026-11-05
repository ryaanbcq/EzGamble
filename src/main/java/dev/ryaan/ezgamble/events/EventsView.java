package dev.ryaan.ezgamble.events;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.proto.*;
import dev.ryaan.ezgamble.ui.GambleScreen;
import dev.ryaan.ezgamble.vip.VipBadge;

import java.util.List;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * Games > Events. Nothing running: the form (kind, duration, the race's prize pool, Start) over the past events, each
 * race with a Pay button per winner the host has not paid yet. Running: the countdown x2, the live leaderboard (top 8
 * with a bar relative to the leader), the prize split and a Cancel button armed by a first click for 3 s. Drawn under
 * its widgets like the other non-game sub-views; the form's state lives in statics so it survives rebuilds.
 */
public final class EventsView implements GameView {
    public static String kind = "race", minutes = "15";
    private static GlassSegmented kindSeg, durSeg;
    private static GlassField prizeField;
    private static long armedUntil, warnUntil;
    private static String warn;
    private static final double ROW = 15, FORM_X = 44;
    private static final String LIST = "events-past";

    private final GambleScreen s;
    private int ver = -1, payVer = -1;
    private boolean armedShown;

    public EventsView(GambleScreen s) { this.s = s; }

    private double lx() { return s.x0 + GambleScreen.PAD; }
    private double rx() { return s.x0 + GambleScreen.W - GambleScreen.PAD; }
    private double by() { return s.y0 + GambleScreen.H - GambleScreen.PAD - 24; }
    private Widget.Clip pastClip() { double top = s.y0 + 182; return new Widget.Clip(lx(), top, GambleScreen.W - 2 * GambleScreen.PAD, s.y0 + GambleScreen.H - GambleScreen.PAD - top); }
    /** A race with winners lists them under its line; anything else is one line. */
    private static double rowH(Events.Event p) { return p.kind() == Events.Kind.RACE && !p.cancelled && !p.top.isEmpty() ? 24 + 16 * p.top.size() : 27; }

    @Override
    public void init() {
        double lx = lx(), rx = rx(), y0 = s.y0;
        ver = Events.ver; payVer = Payouts.ver;
        s.add(new GlassButton(lx, y0 + 80, 18, 16, "‹", "GHOST", s::back));
        if (Events.current() != null) {   // running: Cancel asks first, a stray click never kills an event
            boolean armed = P.now() < armedUntil; armedShown = armed;
            s.add(new GlassButton(rx - 62, by(), 62, 24, armed ? "Sure?" : "Cancel", "DANGER", () -> {
                if (P.now() < armedUntil) { armedUntil = 0; Events.cancel(); } else armedUntil = P.now() + 3000;
                s.rebuild();
            }));
            return;
        }
        if (kindSeg == null) kindSeg = new GlassSegmented(0, 0, 18, new String[][]{{"race", "Race"}, {"xp", "Double XP"}, {"hour", "Happy hour"}}, () -> kind,
                v -> { kind = v; GambleScreen cur = GambleScreen.current(); if (cur != null) cur.rebuild(); });
        kindSeg.place(lx + FORM_X, y0 + 102); s.add(kindSeg);
        if (durSeg == null) durSeg = new GlassSegmented(0, 0, 18, new String[][]{{"5", "5"}, {"15", "15"}, {"30", "30"}, {"60", "60"}}, () -> minutes, v -> minutes = v);
        durSeg.place(lx + FORM_X, y0 + 124); s.add(durSeg);
        if (prizeField == null) { prizeField = new GlassField(0, 0, 90, 18, "", "Prize pool", 12); prizeField.focused = false; prizeField.allowed = "[0-9.,kKmMbB$]"; }   // DonutSMP amounts: 100M, 1.5B
        prizeField.x = lx + FORM_X; prizeField.y = y0 + 146;
        if (kind.equals("race")) s.add(prizeField); else prizeField.focused = false;
        GlassButton start = s.add(new GlassButton(rx - 62, y0 + 146, 62, 18, "Start", "PRIMARY", this::start));
        start.active = GambleData.mode;   // wagers are only recorded while gambling mode is on
        // the past events: one Pay button per race winner not paid yet, scrolling with its row
        Widget.Clip clip = pastClip();
        double y = clip.y();
        for (Events.Event p : Events.past()) {
            if (p.kind() == Events.Kind.RACE && !p.cancelled) for (int i = 0; i < p.top.size(); i++) {
                Events.Winner w = p.top.get(i);
                Events.PayState ps = Events.payState(w);
                if (ps == null || ps.done()) continue;
                double bw = F.width(ps.label()) + 14;
                GlassButton b = s.add(new GlassButton(rx - 6 - bw, y + 22 + i * 16, bw, 14, ps.label(), ps.retry() ? "DANGER" : "PRIMARY", () -> { if (!Events.pay(w)) P.play("fail", 1, 0.5); s.rebuild(); }));
                b.active = ps.active();
                b.list = new Widget.ListPos(LIST, b.y); b.clip = clip;
            }
            y += rowH(p) + 3;
        }
    }

    private void start() {
        Events.Kind k = Events.Kind.of(kind);
        long prize = k == Events.Kind.RACE ? Money.parse(prizeField.value) : 0;
        if (k == Events.Kind.RACE && prize <= 0) { fail("Enter a prize pool, like 100M"); return; }
        if (!GambleData.mode) { fail("Turn gambling mode on first"); return; }
        if (!Events.start(k, Integer.parseInt(minutes), prize)) { fail("An event is already running"); return; }
        prizeField.focused = false;
        s.rebuild();
    }

    private void fail(String text) { warn = text; warnUntil = P.now() + 2200; P.play("fail", 1, 0.5); }

    @Override
    public void tick() {
        if (ver != Events.ver || payVer != Payouts.ver) { ver = Events.ver; payVer = Payouts.ver; s.rebuild(); }   // the event ended, a Pay went out or came back
        if (Events.current() != null && armedShown != P.now() < armedUntil) s.rebuild();   // "Sure?" goes back to "Cancel" after 3 s
    }

    @Override public void leave() { if (prizeField != null) prizeField.focused = false; }

    @Override
    public boolean key(Key e) {
        if (prizeField == null || !prizeField.focused || Events.current() != null || !kind.equals("race")) return false;
        if (e.is("Enter")) { start(); return true; }
        if (e.is("Escape")) { prizeField.focused = false; return true; }
        return prizeField.type(e) || e.isChar();   // a letter typed in the field never closes the menu
    }

    @Override public void clicked(Widget hit) { if (prizeField != null && hit != prizeField) prizeField.focused = false; }

    private static String mmss(long ms) { long sec = Math.max(0, ms) / 1000; return String.format("%02d:%02d", sec / 60, sec % 60); }
    private static String hhmm(long t) { String d = DATE(t); return d.substring(d.indexOf(' ') + 1); }

    @Override
    public void render(G g, double mx, double my) {
        Events.Event e = Events.current();
        if (e != null) { renderLive(g, e, mx, my); return; }
        double lx = lx(), rx = rx(), y = s.y0 + 80, y0 = s.y0;
        g.drawText("Events", lx + 24, y + 4, WHITE, true);
        List<Events.Event> past = Events.past();
        long unpaid = Events.unpaid();
        pillsRight(g, rx, y, unpaid > 0 ? new Object[][]{{"Past", String.valueOf(past.size())}, {"Unpaid", Money.format(unpaid), RED}} : new Object[][]{{"Past", String.valueOf(past.size())}});
        // the form's labels (the controls are widgets)
        Events.Kind k = Events.Kind.of(kind);
        g.drawText("Kind", lx, y0 + 107, MUTE, false);
        g.drawText("Lasts", lx, y0 + 129, MUTE, false);
        if (durSeg != null) g.drawText("min", lx + FORM_X + durSeg.w + 6, y0 + 129, DIM, false);
        String desc = k == Events.Kind.RACE ? "Top 3 split 50 / 30 / 20" : k == Events.Kind.XP ? "VIP XP counts twice while it runs" : "+1% cashback on every tier while it runs";
        if (k == Events.Kind.RACE) { g.drawText("Prize", lx, y0 + 151, MUTE, false); g.drawText(F.trimToWidth(desc, rx - 68 - (lx + FORM_X + 96)), lx + FORM_X + 96, y0 + 151, DIM, false); }
        else g.drawText(F.trimToWidth(desc, rx - 68 - (lx + FORM_X)), lx + FORM_X, y0 + 151, TEXT, false);
        g.drawText("Past events", lx, y0 + 170, MUTE, false);
        String hint = P.now() < warnUntil ? warn : !GambleData.mode ? "Gambling mode is off" : null;
        if (hint != null) g.drawText(hint, rx - F.width(hint), y0 + 170, RED, false);
        // the past events, latest first
        Widget.Clip clip = pastClip();
        if (past.isEmpty()) { g.drawText("No event yet, start one above", lx, clip.y() + 4, DIM, false); }
        double ch = -3;
        for (Events.Event p : past) ch += rowH(p) + 3;
        double off = s.beginList(g, LIST, clip.x(), clip.y(), clip.w(), clip.h(), Math.max(0, ch));
        double ry = clip.y() - off;
        for (Events.Event p : past) {
            double h = rowH(p);
            if (ry + h >= clip.y() && ry <= clip.y() + clip.h()) {
                Events.Kind pk = p.kind();
                card(g, lx, ry, rx - lx, h, "row");
                smooth(g, lx + 6, ry + 5, 7, 7, 3.5, pk.color);
                g.drawText(pk.label, lx + 17, ry + 4, pk.color, false);
                String when = DATE(p.start) + " · " + p.minutes() + " min" + (p.cancelled ? " · cancelled" : "");
                g.drawText(when, lx + 17 + F.width(pk.label) + 6, ry + 4, MUTE, false);
                if (pk == Events.Kind.RACE) { String pr = "Pool " + Money.format(p.prize); g.drawText(pr, rx - 6 - F.width(pr), ry + 4, WHITE, false); }
                if (pk != Events.Kind.RACE) g.drawText(pk == Events.Kind.XP ? "XP counted twice" : "+1% cashback", lx + 17, ry + 15, DIM, false);
                else if (p.cancelled) g.drawText("No winner", lx + 17, ry + 15, DIM, false);
                else if (p.top.isEmpty()) g.drawText("Nobody wagered", lx + 17, ry + 15, DIM, false);
                for (int i = 0; i < p.top.size() && pk == Events.Kind.RACE && !p.cancelled; i++) {
                    Events.Winner w = p.top.get(i);
                    double wy = ry + 22 + i * 16;
                    g.drawText(String.valueOf(i + 1), lx + 8, wy + 3, Events.MEDAL[i], false);
                    g.drawHead(w.name, lx + 18, wy + 2, 9);
                    int bw = VipBadge.w(w.name); VipBadge.draw(g, w.name, lx + 31, wy + 3);
                    g.drawText(F.trimToWidth(w.name, 84 - bw), lx + 31 + bw, wy + 3, TEXT, false);
                    g.drawText(Money.format(w.wagered), lx + 120, wy + 3, MUTE, false);
                    Events.PayState ps = Events.payState(w);
                    if (ps != null && ps.done()) g.drawText(ps.label(), rx - 6 - F.width(ps.label()), wy + 3, GREEN, false);   // else the Pay button sits there
                }
            }
            ry += h + 3;
        }
        s.endList(g, LIST, Math.max(0, ch));
    }

    /** The running event: countdown, leaderboard, the split (race) and the Cancel button's row. */
    private void renderLive(G g, Events.Event e, double mx, double my) {
        double lx = lx(), rx = rx(), y = s.y0 + 80, y0 = s.y0, cx = s.x0 + GambleScreen.W / 2.0;
        Events.Kind k = e.kind();
        long left = Math.max(0, e.end - System.currentTimeMillis());
        boolean lastTen = left <= 10_000;
        gradientText(g, k.label, lx + 24, y + 4, true);
        List<Events.Entry> b = Events.board();
        int bets = 0;
        for (Events.Entry x : b) bets += x.bets();
        Object[][] pills = k == Events.Kind.RACE
                ? new Object[][]{{"Pool", Money.format(e.prize), Events.GOLD}, {"Players", String.valueOf(b.size())}, {"Bets", String.valueOf(bets)}}
                : new Object[][]{{"Players", String.valueOf(b.size())}, {"Bets", String.valueOf(bets)}};
        pillsRight(g, rx, y, pills);
        // the countdown x2, red and pulsing in the last 10 s
        String cd = mmss(left);
        int col = lastTen ? alpha(RED, 0.6 + 0.4 * pulse(P.now() * 2.4)) : WHITE;
        g.push(); g.translate(cx - F.width(cd), y0 + 100); g.scale(2, 2); g.drawText(cd, 0, 0, col, true); g.pop();
        String sub = "Ends at " + hhmm(e.end) + " · started " + hhmm(e.start) + " · " + e.minutes() + " min";
        g.drawText(sub, cx - F.width(sub) / 2.0, y0 + 118, MUTE, false);
        // the leaderboard: top 8, a bar behind each line relative to the leader
        double ly = y0 + 132;
        if (b.isEmpty()) {
            String a = "No wager yet", c = "Bets placed with a player's chips during the event count";
            g.drawText(a, cx - F.width(a) / 2.0, ly + 30, TEXT, false);
            g.drawText(c, cx - F.width(c) / 2.0, ly + 42, MUTE, false);
        }
        int n = Math.min(8, b.size());
        long lead = b.isEmpty() ? 1 : Math.max(1, b.get(0).wagered());
        for (int i = 0; i < n; i++) {
            Events.Entry en = b.get(i);
            double ry = ly + i * ROW, bw = (rx - lx) * en.wagered() / (double) lead;
            int m = i < 3 ? Events.MEDAL[i] : LAV;
            smooth(g, lx, ry, Math.max(14, bw), ROW - 2, 4, alpha(m, i == 0 ? 0.16 : 0.09));
            g.drawText("#" + (i + 1), lx + 5, ry + 3, i < 3 ? m : DIM, false);
            g.drawHead(en.name(), lx + 24, ry + 2, 9);
            int vb = VipBadge.w(en.name()); VipBadge.draw(g, en.name(), lx + 37, ry + 3);
            g.drawText(F.trimToWidth(en.name(), 120 - vb), lx + 37 + vb, ry + 3, i == 0 ? WHITE : TEXT, i == 0);
            String v = Money.format(en.wagered()), bt = en.bets() + (en.bets() == 1 ? " bet" : " bets");
            g.drawText(v, rx - 6 - F.width(v), ry + 3, i == 0 ? Events.GOLD : WHITE, false);
            g.drawText(bt, rx - 6 - F.width(v) - 8 - F.width(bt), ry + 3, DIM, false);
        }
        if (b.size() > n) g.drawText("+" + (b.size() - n) + " more", lx + 5, ly + n * ROW + 1, DIM, false);
        // the bottom row: the split (race) or what the event does, the Cancel button on the right (a widget)
        double by = by();
        if (k == Events.Kind.RACE) {
            String[] lbl = {"1st", "2nd", "3rd"};
            double x = lx;
            for (int i = 0; i < 3; i++) { String v = Money.format((long) Math.floor(e.prize * Events.SPLIT[i])); pill(g, x, by + 4, lbl[i], v, Events.MEDAL[i]); x += pillW(lbl[i], v) + 4; }   // 15 high, centred on the 24 px button
        } else g.drawText(k == Events.Kind.XP ? "VIP XP counts twice until it ends" : "+1% cashback on every tier until it ends", lx, by + 8, TEXT, false);
    }
}
