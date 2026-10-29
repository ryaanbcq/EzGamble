package dev.ryaan.ezgamble.games;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.GlassField;
import dev.ryaan.ezgamble.proto.Key;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Widget;

import java.util.List;
import java.util.function.Supplier;

/** Widgets and helpers shared by the game tables (prototype: BackerPicker, ChipButton, RefundPill, payStateOf, payOut, drawCard). */
public final class Widgets {
    private Widgets() {}

    // ---- the Pay button of a won round

    /** A round that can be paid once: paid = the first click (amount, at). */
    public static class Round { public Paid paid; }
    public record Paid(long at, long amount) {}
    public record PayState(String label, boolean active, long amount, boolean retry, boolean done) {}

    /**
     * One /pay per round, ever: round.paid is set on the first click, so a double click or a second click while it
     * is sending does nothing. Only a payment the server did NOT confirm (15 s) opens a retry. Armed 0.6 s after the
     * result shows.
     */
    public static PayState payStateOf(Round round, String backer, long amount, long shownAt) {
        if (round == null || (amount <= 0 && round.paid == null)) return null;
        if (round.paid == null) return new PayState("Pay " + F.trimToWidth(backer, 70) + " " + Money.format(amount), P.now() >= shownAt + 600, amount, false, false);
        if (Payouts.pending.stream().anyMatch(p -> p.name().equals(backer))) return new PayState("Sending...", false, 0, false, false);
        Payouts.Recent r = Payouts.recent.get(backer);
        if (r != null && r.at() >= round.paid.at() && !r.ok()) return new PayState("Not confirmed, retry", P.now() >= r.at() + 600, round.paid.amount(), true, false);
        if (r != null && r.at() >= round.paid.at() && r.ok()) return new PayState("Paid " + Money.format(round.paid.amount()), false, 0, false, true);
        return new PayState("Sending...", false, 0, false, false);
    }

    public static void payOut(Round round, String backer, PayState st, Runnable settle) {
        if (st == null || !st.active() || (round.paid != null && !st.retry())) return;
        settle.run();
        long amount = st.retry() ? round.paid.amount() : st.amount();
        round.paid = new Paid(P.now(), amount);
        Payouts.send(backer, amount);
        P.play("click");
    }

    // ---- cards (Blackjack, and any game that shows one)

    public static final int CARD_W = 24, CARD_H = 32, CARD_RED = 0xFFD8445C;

    /** One card: the blank sprite, rank + small suit top left, the suit x2 bottom right. */
    public static void drawCard(G g, double x, double y, String rank, String suit, boolean back, boolean shadow) {
        if (shadow) P.smooth(g, x + 1, y + 2, CARD_W, CARD_H, 3, 0x40000000);
        g.drawSprite(back ? "card/back" : "card/face", x, y, CARD_W, CARD_H);
        if (back) return;
        int col = suit.equals("♥") || suit.equals("♦") ? CARD_RED : P.INK;
        g.drawText(rank, x + 3, y + 3, col, false);
        g.drawText(suit, x + 3, y + 12, col, false);
        g.push(); g.translate(x + 9, y + 15); g.scale(2, 2); g.drawText(suit, 0, 0, col, false); g.pop();
    }

    // ---- "Play for": the player search capsule

    /** Closed: the picked player's head, name and chips. Clicked: a search field and a glass list of matching players. */
    public static class BackerPicker extends Widget {
        public final Host host;
        public final GlassField field;
        public int sel;

        public BackerPicker(double x, double y, double w, Host host) {
            super(x, y, w, 20); this.host = host;
            field = new GlassField(x, y, w, 20, "", "Search player", 16);
            field.search = true; field.focused = false;
        }

        public void place(double x, double y, double w) { this.x = x; this.y = y; this.w = w; field.x = x; field.y = y; field.w = w; }
        public boolean open() { return field.focused; }

        public List<Payouts.Row> matches() {
            String q = Payouts.norm(field.value);
            return host.backers().stream().filter(r -> Payouts.norm(r.name()).contains(q)).limit(5).toList();
        }

        double listY() { return y + h + 4; }
        double listH() { return Math.max(1, matches().size()) * 18 + 6; }

        int rowAt(double mx, double my) {
            if (!open() || mx < x || mx >= x + w) return -1;
            int i = (int) Math.floor((my - listY() - 3) / 18);
            return my >= listY() + 3 && i >= 0 && i < matches().size() ? i : -1;
        }

        @Override
        public boolean over(double mx, double my) {
            if (!visible) return false;
            if (mx >= x && mx < x + w && my >= y && my < y + h) return true;
            return open() && mx >= x && mx < x + w && my >= listY() && my < listY() + listH();
        }

        @Override
        public void render(G g, double mx, double my) {
            boolean hovBar = mx >= x && mx < x + w && my >= y && my < y + h;
            if (open()) field.render(g, mx, my);
            else {
                P.capsule(g, x, y, w, h, "ghost", hovBar);
                double ty = y + 6;
                int c = hovBar ? P.WHITE : P.MUTE;
                g.fill(x + 9, ty, x + 12, ty + 1, c); g.fill(x + 9, ty + 4, x + 12, ty + 5, c); g.fill(x + 8, ty + 1, x + 9, ty + 4, c); g.fill(x + 12, ty + 1, x + 13, ty + 4, c);
                g.fill(x + 13, ty + 5, x + 14, ty + 6, c); g.fill(x + 14, ty + 6, x + 15, ty + 7, c);
                if (host.backer != null) {
                    String v = Money.format(host.balance());
                    int vw = F.width(v);
                    g.drawHead(host.backer, x + 20, y + 6, 8);
                    g.drawText(F.trimToWidth(host.backer, w - 44 - vw), x + 32, ty, P.WHITE, true);
                    P.gradientText(g, v, x + w - 9 - vw, ty, false);
                } else g.drawText("Search player", x + 20, ty, P.DIM, false);
            }
            if (!open()) return;
            double ly = listY(), lh = listH();
            List<Payouts.Row> list = matches();
            int hov = rowAt(mx, my);
            P.smooth(g, x, ly, w, lh, 8, P.v(0xF0291C42, 0xF01C1230), 0x40FFFFFF);
            if (list.isEmpty()) { String t = "No player matches"; g.drawText(t, x + (int) ((w - F.width(t)) / 2), ly + 8, P.MUTE, false); return; }
            for (int i = 0; i < list.size(); i++) {
                Payouts.Row r = list.get(i);
                double ry = ly + 3 + i * 18;
                boolean on = i == (hov >= 0 ? hov : sel), picked = r.name().equals(host.backer);
                if (on) P.smooth(g, x + 3, ry, w - 6, 18, 5, P.h(0x33A18CD1, 0x26FBC2EB));
                g.drawHead(r.name(), x + 9, ry + 5, 8);
                String v = Money.format(r.bal());
                int vw = F.width(v);
                g.drawText(F.trimToWidth(r.name(), w - 40 - vw), x + 21, ry + 5, on || picked ? P.WHITE : P.TEXT, picked);
                g.drawText(v, x + w - 9 - vw, ry + 5, picked ? P.WHITE : P.MUTE, false);
            }
        }

        @Override
        public void click(double mx, double my) {
            int i = rowAt(mx, my);
            if (i >= 0) { pick(matches().get(i)); return; }
            if (open()) { field.click(mx, my); sel = 0; return; }
            P.play("click"); field.value = ""; field.focused = true; field.typedAt = P.now(); sel = 0;
        }

        public void pick(Payouts.Row r) {
            field.focused = false; field.value = "";
            if (r == null || r.name().equals(host.backer)) return;
            P.play("click"); host.backer = r.name(); Host.lastBacker = r.name(); if (host.bet > r.bal()) host.clearBet();
        }

        public void close() { field.focused = false; field.value = ""; }

        /** true if the key was used (only while open). */
        @Override
        public boolean key(Key e) {
            if (!open()) return false;
            List<Payouts.Row> list = matches();
            if (e.is("Escape")) close();
            else if (e.is("Enter")) pick(list.isEmpty() ? null : list.get(Math.min(sel, list.size() - 1)));
            else if (e.is("ArrowDown")) sel = Math.min(sel + 1, Math.max(0, list.size() - 1));
            else if (e.is("ArrowUp")) sel = Math.max(0, sel - 1);
            else if (field.type(e)) sel = 0;
            return true;
        }
    }

    /** A casino chip to click: adds its value to the bet. Faded when the backer cannot cover it. */
    public static class ChipButton extends Widget {
        public final Chips.Chip chip;
        public final Host host;

        public ChipButton(double x, double y, Chips.Chip chip, Host host) { super(x, y, 30, 32); this.chip = chip; this.host = host; }
        boolean ok() { return host.backer != null && host.bet + chip.v() <= host.balance(); }

        @Override
        public void render(G g, double mx, double my) {
            boolean ok = ok(), hov = ok && over(mx, my);
            active = ok;
            Chips.chipStack(g, x + 15, y + 20 - (hov ? 2 : 0), 9, ok ? chip : chip.faded(0.3), 1);
            g.drawText(chip.label(), x + 15 - (F.width(chip.label()) >> 1), y + 24, ok ? P.TEXT : P.DIM, false);
        }

        @Override
        public void click(double mx, double my) { host.addChip(chip); }
    }

    /**
     * The player's "Chips $X" pill at the end of a round: hover lights it, a click turns it into "Refund all $X",
     * a second click sends him ALL his chips (/pay, confirmed like every payout). Armed 0.4 s after it opens.
     */
    public static class RefundPill extends Widget {
        public final Host host;
        public final Runnable settle;
        public boolean open;
        public long openAt;
        public Paid sent;
        public Object round;
        public int color = P.WHITE;

        public RefundPill(Host host, Runnable settle) { super(0, 0, 0, 15); this.host = host; this.settle = settle; }

        record St(String label, String kind, boolean busy, boolean done, boolean retry, int color) {}

        St state() {
            String n = host.backer;
            boolean pend = Payouts.pending.stream().anyMatch(p -> p.name().equals(n));
            Payouts.Recent r = Payouts.recent.get(n);
            if (pend) return new St("Sending...", "ghost", true, false, false, 0);
            if (sent != null && r != null && r.at() >= sent.at())
                return r.ok() ? new St("Refunded " + Money.format(sent.amount()), "ghost", false, true, false, P.GREEN) : new St("Not confirmed, retry", "danger", false, false, true, 0);
            if (open) return new St("Refund all " + Money.format(host.balance()), "primary", false, false, false, 0);
            return null;
        }

        /** Left edge x, top y; the width follows its text. */
        public void place(double x, double y, int color) {
            this.x = x; this.y = y; this.color = color;
            St st = state();
            w = st != null ? F.width(st.label) + 14 : P.pillW("Chips", Money.format(host.balance()));
        }

        @Override
        public void render(G g, double mx, double my) {
            St st = state();
            boolean hov = over(mx, my) && !(st != null && (st.busy || st.done));
            if (st == null) {
                String v = Money.format(host.balance());
                P.capsule(g, x, y, w, 15, hov ? "selected" : "ghost", hov);
                g.drawText("Chips", x + 7, y + 4, hov ? P.TEXT : P.MUTE, false);
                g.drawText(v, x + 7 + F.width("Chips") + 4, y + 4, hov ? P.WHITE : color, false);
                return;
            }
            boolean armed = !open || P.now() - openAt >= 400;
            P.capsule(g, x, y, w, 15, st.kind, hov && armed, st.kind.equals("primary") && !armed ? 0.6 : 1);
            g.drawText(st.label, x + 7, y + 4, st.kind.equals("primary") ? P.INK : st.color != 0 ? st.color : st.kind.equals("danger") ? P.RED : P.TEXT, !st.kind.equals("primary"));
        }

        @Override
        public void click(double mx, double my) {
            St st = state();
            if (st != null && (st.busy || st.done)) return;
            if (!open && !(st != null && st.retry)) { if (host.balance() <= 0) return; P.play("click"); open = true; openAt = P.now(); return; }
            if (P.now() - openAt < 400) return;
            settle.run();
            long amount = st != null && st.retry ? sent.amount() : host.balance();
            if (amount <= 0) return;
            open = false; sent = new Paid(P.now(), amount);
            Payouts.send(host.backer, amount);
            P.play("click");
        }

        public void fold() { open = false; }
    }

    /** One uniform draw in [0, 1) from the OS random source. */
    private static final java.security.SecureRandom RNG = new java.security.SecureRandom();
    public static double rand01() { return RNG.nextDouble(); }

    /** x2, x1.5, x0.4. */
    public static String multText(double m) { return "x" + (m == Math.rint(m) ? String.valueOf((long) m) : String.format(java.util.Locale.ROOT, "%.1f", m).replaceAll("\\.0$", "")); }

    /** Simple clickable area (the prototype's `new Widget(...)` + `w.click = () => ...`). */
    public static Widget area(double x, double y, double w, double h, Runnable onClick) {
        return new Widget(x, y, w, h) { @Override public void click(double mx, double my) { onClick.run(); } };
    }

    /** A Supplier-typed alias kept for ported lambdas. */
    public interface S<T> extends Supplier<T> {}
}
