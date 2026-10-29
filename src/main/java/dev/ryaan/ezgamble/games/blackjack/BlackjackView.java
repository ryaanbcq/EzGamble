package dev.ryaan.ezgamble.games.blackjack;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.games.Chips;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.games.blackjack.BlackjackGame.Card;
import dev.ryaan.ezgamble.games.blackjack.BlackjackGame.Hand;
import dev.ryaan.ezgamble.games.blackjack.BlackjackGame.Result;
import dev.ryaan.ezgamble.games.blackjack.BlackjackGame.Value;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.GlassButton;
import dev.ryaan.ezgamble.proto.GlassField;
import dev.ryaan.ezgamble.proto.Key;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Widget;
import dev.ryaan.ezgamble.ui.GambleScreen;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.ryaan.ezgamble.games.blackjack.BlackjackGame.handValue;
import static dev.ryaan.ezgamble.games.blackjack.BlackjackTable.*;

/** The table drawn inside GambleScreen: layout, animation, input (prototype BlackjackView). */
public final class BlackjackView implements GameView {
    private static final Map<String, String> WORDS = Map.of("blackjack", "Blackjack!", "win", "{p} wins", "dealerbust", "{p} wins", "push", "Push", "bust", "{p} busts", "lose", "House wins");   // {p} = the backer
    private static final Map<String, String> SHORT = Map.of("blackjack", "Blackjack", "win", "Win", "dealerbust", "Win", "push", "Push", "bust", "Bust", "lose", "Lose");

    private final GambleScreen screen;
    private String uiKey = "";
    private GlassButton[] actBtns;
    private String[] actNames;
    private GlassButton dealBtn, payBtn;
    private Widgets.RefundPill refund;

    public BlackjackView(GambleScreen s) { this.screen = s; }

    private static String me() { return MinecraftClient.getInstance().getSession().getUsername(); }

    /** The player search capsule, top left of the felt. */
    private double[] pickerBox() { GambleScreen.Geom g = screen.geom(); return new double[]{g.lx() + 8, g.fy() + 8, 168}; }

    /** What the widget set depends on; a change rebuilds the screen. */
    private String uiState() {
        BlackjackTable T = BJ;
        BlackjackGame gm = T.game;
        return String.join("|", T.stage, String.valueOf(T.backer), gm != null ? gm.phase : "", gm != null && T.resultAt > 0 && P.now() >= T.resultAt ? "r" : "", String.valueOf(T.backers().size()), String.valueOf(T.page));
    }

    @Override
    public void init() {
        GambleScreen s = screen;
        BlackjackTable T = BJ;
        GambleScreen.Geom G0 = s.geom();
        double lx = G0.lx(), rx = G0.rx(), hy = G0.hy(), fy = G0.fy(), by = G0.by(), cx = G0.cx();
        uiKey = uiState(); actBtns = null; dealBtn = null; payBtn = null; refund = null;
        s.add(new GlassButton(lx, hy, 18, 16, "‹", "GHOST", () -> { T.apply(); GambleScreen.sub = null; s.rebuild(); }));
        if (T.stage.equals("bet")) {
            List<Payouts.Row> list = T.backers();
            if (list.stream().noneMatch(r -> r.name().equals(T.backer))) { T.backer = list.isEmpty() ? null : list.get(0).name(); T.clearBet(); }
            if (!list.isEmpty()) {
                int n = Chips.CHIPS.length, step = 44;
                double x0 = cx - ((n * step - (step - 30)) >> 1);
                for (int i = 0; i < n; i++) s.add(new Widgets.ChipButton(x0 + i * step, fy + 116, Chips.CHIPS[n - 1 - i], T));
                s.add(Widgets.area(cx - 80, fy + 44, 160, 68, T::undoChip));   // the bet stack: click = take the last chip back
                if (T.field == null) T.field = new GlassField(0, 0, 110, 22, "", "Amount", 10);
                T.field.x = lx; T.field.y = by + 1; T.field.focused = false;
                s.add(T.field);
                s.add(new GlassButton(lx + 116, by, 56, 24, "Clear", "GHOST", T::clearBet));
            }
            dealBtn = s.add(new GlassButton(rx - 96, by, 96, 24, "Deal", "PRIMARY", T::deal));
            dealBtn.active = GambleData.mode && !list.isEmpty();
            if (!list.isEmpty()) {   // added last: drawn over the felt and the chips, and gets the clicks first while its list is open
                double[] pb = pickerBox();
                if (T.picker == null) T.picker = new Widgets.BackerPicker(pb[0], pb[1], pb[2], T);
                T.picker.place(pb[0], pb[1], pb[2]);
                s.add(T.picker);
            }
            return;
        }
        BlackjackGame gm = T.game;
        boolean done = gm.phase.equals("done") && T.resultAt > 0 && P.now() >= T.resultAt;
        if (done) {
            s.add(new GlassButton(rx - 96, by, 96, 24, "Rebet", T.payState() != null ? "GHOST" : "PRIMARY", T::rebet)).active = T.balance() > 0;   // a won hand: Pay is the one tinted button
            s.add(new GlassButton(rx - 96 - 6 - 80, by, 80, 24, "Change bet", "GHOST", () -> { T.apply(); T.stage = "bet"; }));
            if (T.refundPill == null || T.refundPill.round != T.game) { T.refundPill = new Widgets.RefundPill(T, T::apply); T.refundPill.round = T.game; }   // one per hand: survives the rebuilds
            refund = s.add(T.refundPill);
            if (T.payState() != null) {   // the backer won: one click sends him the hand's payout
                payBtn = s.add(new GlassButton(lx, by, 150, 24, "", "PRIMARY", T::pay) {
                    @Override public void click(double mx, double my) { if (active) action.run(); }   // no click sound of its own: pay() plays it once, only when it sends
                });
                syncPay();
            }
            return;
        }
        String[][] acts = {{"hit", "Hit", "PRIMARY"}, {"stand", "Stand", "GHOST"}, {"double", "Double", "GHOST"}, {"split", "Split", "GHOST"}};
        int bw = 70, gap = 6;
        double x0 = cx - ((acts.length * bw + (acts.length - 1) * gap) >> 1);
        actBtns = new GlassButton[acts.length]; actNames = new String[acts.length];
        for (int i = 0; i < acts.length; i++) {
            String a = acts[i][0];
            actBtns[i] = s.add(new GlassButton(x0 + i * (bw + gap), by, bw, 24, acts[i][1], acts[i][2], () -> T.act(a)));
            actNames[i] = a;
        }
    }

    @Override
    public void tick() {
        BJ.tick();
        if (!uiState().equals(uiKey)) { screen.rebuild(); return; }
        if (actBtns != null) for (int i = 0; i < actBtns.length; i++) actBtns[i].active = BJ.canDo(actNames[i]);
        if (dealBtn != null) dealBtn.active = GambleData.mode && !BJ.backers().isEmpty();
        if (payBtn != null) syncPay();
    }

    private void syncPay() {
        Widgets.PayState st = BJ.payState();
        GlassButton b = payBtn;
        if (st == null) { b.visible = false; return; }
        b.label = st.label(); b.active = st.active(); b.w = Math.max(120, F.width(st.label()) + 20);
    }

    /** true = the key was used (K then types or acts instead of closing the menu). */
    @Override
    public boolean key(Key e) {
        BlackjackTable T = BJ;
        String k = e.key().toLowerCase();
        if (T.stage.equals("bet")) {
            if (T.picker != null && T.picker.key(e)) return true;
            if (T.field == null) return false;
            if (T.field.focused) {
                if (e.is("Enter")) T.deal();
                else if (e.is("Escape")) T.field.focused = false;
                else if (T.field.type(e)) { T.bet = Math.max(0, Money.parse(T.field.value)); T.betChips.clear(); }
                return true;
            }
            if (e.is("Enter")) { T.deal(); return true; }
            return false;
        }
        String act = switch (k) { case "h" -> "hit"; case "s" -> "stand"; case "d" -> "double"; case "p" -> "split"; default -> null; };
        if (act != null) { T.act(act); return true; }
        if ((e.is("Enter") || e.key().equals(" ")) && T.game.phase.equals("done") && T.resultAt > 0 && P.now() >= T.resultAt) { T.rebet(); return true; }
        return false;
    }

    /** After a click anywhere: a click elsewhere leaves the amount field, closes the player search and folds Refund all. */
    @Override
    public void clicked(Widget hit) {
        BlackjackTable T = BJ;
        if (T.field != null && hit != T.field) T.field.focused = false;
        if (T.picker != null && hit != T.picker) T.picker.close();
        if (refund != null && hit != refund) refund.fold();
    }

    @Override public void leave() { BJ.apply(); }   // a finished hand is settled at once

    private void felt(G g, double x, double y, double w, double h) {
        P.smooth(g, x, y, w, h, 10, P.v(0xFF2A1B44, 0xFF1A1030), 0x1FFFFFFF);   // one surface, a hairline rim: content, not glass
    }

    @Override
    public void render(G g, double mx, double my) {
        BlackjackTable T = BJ;
        GambleScreen.Geom G0 = screen.geom();
        double lx = G0.lx(), rx = G0.rx(), hy = G0.hy(), fy = G0.fy(), fh = G0.fh();
        g.drawText("Blackjack", lx + 24, hy + 4, P.WHITE, true);
        List<Object[]> pills = new ArrayList<>();
        if (T.backer != null && !T.stage.equals("play")) pills.add(new Object[]{"Chips", Money.format(T.balance())});   // in a hand, his name and chips sit on his seat
        pills.add(T.stage.equals("play") ? new Object[]{"Bet", Money.format(T.committed())} : new Object[]{"Shoe", T.shoe.remaining() + " cards"});
        P.pillsRight(g, rx, hy, pills.toArray(new Object[0][]));
        felt(g, lx, G0.ft(), rx - lx, G0.fth());
        if (T.stage.equals("bet")) renderBet(g); else renderPlay(g);
        if (T.stage.equals("play") && T.warn != null && P.now() < T.warn.until()) {
            String w = T.warn.text();
            double cx = lx + ((rx - lx) / 2);
            g.drawText(w, cx - (F.width(w) >> 1), fy + 84, P.RED, true);
        }
    }

    private void renderBet(G g) {
        BlackjackTable T = BJ;
        GambleScreen.Geom G0 = screen.geom();
        double lx = G0.lx(), fy = G0.fy(), cx = G0.cx();
        List<Payouts.Row> list = T.backers();
        if (list.isEmpty()) {
            g.drawText("No chips on the table yet.", lx + 8, fy + 44, P.TEXT, false);
            g.drawText("Players who /pay " + me() + " during a session become your backers:", lx + 8, fy + 58, P.MUTE, false);
            g.drawText("you play here with their chips, and the result is theirs.", lx + 8, fy + 69, P.MUTE, false);
            if (!GambleData.mode) g.drawText("Turn gambling mode on to start a session.", lx + 8, fy + 86, P.RED, false);
            return;
        }
        // the bet, the hero of this screen: stacks side by side in the middle of the felt, the amount x2 under them;
        // no bet yet = a pale betting spot on the felt. Errors show on the amount line, never over a control.
        List<Chips.Stack> all = Chips.chipsFor(T.bet).stacks();
        List<Chips.Stack> stacks = all.subList(0, Math.min(4, all.size()));
        int span = (stacks.size() - 1) * 34;
        if (stacks.isEmpty()) {   // the betting spot
            P.smooth(g, cx - 26, fy + 60, 52, 26, 13, 0x14A18CD1);
            P.ring(g, cx - 26, fy + 60, 52, 26, 13, 0x80A18CD1, 1.5);
        }
        for (int i = 0; i < stacks.size(); i++) Chips.chipStack(g, cx - (span >> 1) + i * 34, fy + 88, 14, stacks.get(i).chip(), stacks.get(i).n());
        String warn = T.warn != null && P.now() < T.warn.until() ? T.warn.text() : !GambleData.mode ? "Gambling mode is off" : null;
        if (warn != null) g.drawText(warn, cx - (F.width(warn) >> 1), fy + 98, P.RED, true);
        else if (T.bet > 0) {
            String v = Money.format(T.bet);
            g.push(); g.translate(cx - F.width(v), fy + 94); g.scale(2, 2); P.gradientText(g, v, 0, 0, true); g.pop();
        }
    }

    /** Draws one card on its timeline: nothing before its time, a slide from the shoe, a flip when revealed. */
    private String card(G g, Card c, double x, double y, double shoeX, double shoeY, long t) {
        Timing tm = BJ.times.get(c.id);
        if (tm == null || t < tm.dealAt) return "none";
        double p = Math.min(1, (double) (t - tm.dealAt) / BJ_DEAL_MS);
        boolean hidden = c.hidden || (tm.revealAt > 0 && t < tm.revealAt);
        if (p < 1) {
            double e = P.easeOut(p);
            Widgets.drawCard(g, Math.round(shoeX + (x - shoeX) * e), Math.round(shoeY + (y - shoeY) * e), c.r, c.s, hidden, true);
            return "moving";
        }
        if (tm.revealAt > 0 && t >= tm.revealAt && t < tm.revealAt + BJ_FLIP_MS) {   // flip: X scale to 0 as the back, back to 1 as the face
            double f = (double) (t - tm.revealAt) / BJ_FLIP_MS, sx = Math.max(0.06, Math.abs(1 - 2 * f)), mid = x + CARD_W / 2.0;
            g.push(); g.translate(mid, 0); g.scale(sx, 1); g.translate(-mid, 0);
            Widgets.drawCard(g, x, y, c.r, c.s, f < 0.5, true);
            g.pop();
            return "moving";
        }
        Widgets.drawCard(g, x, y, c.r, c.s, hidden, true);
        return hidden ? "down" : "up";
    }

    /** x of the first card of a centred hand of n cards, eased. */
    private int centre(String key, int n, double cx) {
        BlackjackTable T = BJ;
        double target = cx - ((CARD_W + Math.max(0, n - 1) * CARD_STEP) >> 1);
        T.off.putIfAbsent(key, target);
        double v = T.off.get(key);
        v += (target - v) * 0.22;
        T.off.put(key, v);
        return (int) Math.round(v);
    }

    private void renderPlay(G g) {
        BlackjackTable T = BJ;
        BlackjackGame gm = T.game;
        GambleScreen.Geom G0 = screen.geom();
        double lx = G0.lx(), rx = G0.rx(), fy = G0.fy(), fh = G0.fh(), cx = G0.cx();
        long t = P.now();
        double shoeX = rx - 34, shoeY = fy + 8, bankX = rx - 24, bankY = fy + 72;
        for (int i = 2; i >= 0; i--) Widgets.drawCard(g, shoeX + i, shoeY - i, "", "", true, i == 2);   // the shoe: a few backs
        if (gm.reshuffled && t < T.lastEnd + 800) P.pill(g, shoeX + CARD_W - P.pillW("", "Shuffled"), shoeY + CARD_H + 6, "", "Shuffled", P.TEXT);
        List<Result> res = gm.results != null && T.resultAt > 0 && t >= T.resultAt ? gm.results : null;
        // A mirror: the dealer's hand at the top with its tag right under it, the player's hand at the bottom with its tag
        // right above it, the verdict in between. Both hands are centred and glide to their new centre when a card lands.
        if (T.offFor != gm) { T.offFor = gm; T.off.clear(); }
        // dealer = the gambler: his head and name on the house hand
        double dy = fy + 8;
        int dx0 = centre("d", dealt(gm.dealer, t), cx);
        List<Card> dUp = new ArrayList<>();
        for (int i = 0; i < gm.dealer.size(); i++) { Card c = gm.dealer.get(i); if (card(g, c, dx0 + i * CARD_STEP, dy, shoeX, shoeY, t).equals("up")) dUp.add(c); }
        Value dv = handValue(dUp);
        String meName = GambleData.name != null ? GambleData.name : me();
        P.seatTag(g, cx, dy + CARD_H + 5, P.skinOf(me()), F.trimToWidth(meName, 70), dUp.isEmpty() ? "-" : String.valueOf(dv.total()), !dUp.isEmpty() && dv.total() > 21 ? P.RED : P.WHITE, "ghost");
        // the player's hand(s); after a split the two halves slide apart
        double py = fy + fh - 8 - CARD_H;
        boolean two = gm.hands.size() == 2;
        double sp = two ? P.easeOut(Math.min(1, Math.max(0, (double) (t - T.splitAt) / BJ_SPLIT_MS))) : 1;
        double[] hx = two
                ? new double[]{cx - 40 + (cx - 130 - (cx - 40)) * sp, cx - 40 + CARD_STEP + (cx + 30 - (cx - 40 + CARD_STEP)) * sp}
                : new double[]{centre("p", dealt(gm.hands.get(0).cards, t), cx)};
        // the backer's chips, live: they move when the result lands (count down in red, up in green), so viewers can check
        long real = T.balance();
        if (T.balShown == null || !java.util.Objects.equals(T.balFor, T.backer)) { T.balFor = T.backer; T.balShown = real; T.balAnim = null; }
        if (real != (T.balAnim != null ? T.balAnim.to : T.balShown)) T.balAnim = new BalAnim(T.balShown, real, t);
        if (T.balAnim != null) { double k = P.easeOut(Math.min(1, (double) (t - T.balAnim.at) / 700)); T.balShown = Math.round(T.balAnim.from + (T.balAnim.to - T.balAnim.from) * k); }
        boolean moving = T.balAnim != null && t - T.balAnim.at < 1800, upBal = T.balAnim != null && T.balAnim.to > T.balAnim.from;
        String bv = Money.format(T.balShown);
        int chipsCol = moving ? (upBal ? P.GREEN : P.RED) : P.WHITE;
        double ty = py - 20;   // the player's tag row
        for (int i = 0; i < gm.hands.size(); i++) {
            Hand h = gm.hands.get(i);
            int x0 = (int) Math.round(hx[i]);
            Result r = res != null ? res.get(i) : null;
            List<Card> up = new ArrayList<>();
            for (int j = 0; j < h.cards.size(); j++) { Card c = h.cards.get(j); if (card(g, c, x0 + j * CARD_STEP, py, shoeX, shoeY, t).equals("up")) up.add(c); }
            betStack(g, x0 - 12, py + 20, h.bet, r, bankX, bankY, t);   // his chips in front of his cards
            if (two && sp < 1) continue;
            Value v = handValue(up);
            String label = up.isEmpty() ? "-" : v.soft() && v.total() < 21 ? (v.total() - 10) + "/" + v.total() : String.valueOf(v.total());
            boolean active = gm.phase.equals("player") && gm.active == i && !T.locked(), bust = !up.isEmpty() && v.total() > 21;
            if (r != null && two) P.pill(g, x0, ty, SHORT.get(r.outcome()), Money.signed(r.delta()), r.delta() > 0 ? P.GREEN : r.delta() < 0 ? P.RED : P.WHITE);
            else if (two) P.pill(g, x0, ty, "Hand " + (i + 1), label, bust ? P.RED : P.WHITE, active ? "selected" : "ghost");
            else {   // one tag: head, name, total, and his chips glued to it
                String name = F.trimToWidth(T.backer, 70);
                int tw = P.seatTagW(name, label), cw = P.pillW("Chips", bv);
                double x = cx - ((tw + 4 + cw) >> 1);
                P.seatTag(g, x + (tw >> 1), ty, P.skinOf(T.backer), name, label, bust ? P.RED : P.WHITE, active ? "selected" : "ghost");
                if (refund != null) refund.place(x + tw + 4, ty, chipsCol); else P.pill(g, x + tw + 4, ty, "Chips", bv, chipsCol);   // after the hand: the pill can refund
            }
        }
        if (two) {   // two hand tags take the row: his seat (head, name, chips) goes to the top left corner, facing the shoe
            String name = F.trimToWidth(T.backer, 70);
            int tw = P.seatTagW(name, bv);
            P.seatTag(g, lx + 8 + (tw >> 1), fy + 8, P.skinOf(T.backer), name, bv, chipsCol);
        }
        // middle: the house rule, then the verdict
        if (res == null) { String s = "Blackjack pays 3 to 2"; g.drawText(s, cx - (F.width(s) >> 1), fy + 74, P.DIM, false); return; }
        double e = P.easeOut(Math.min(1, (double) (t - T.resultAt) / 220)), sc = 2 * (0.6 + 0.4 * e);
        boolean one = gm.results.size() == 1;
        long total = gm.total();   // the backer's result; the house's is the opposite
        String who = F.trimToWidth(T.backer, 80);
        String word = one ? WORDS.get(gm.results.get(0).outcome()).replace("{p}", who) : total > 0 ? who + " wins" : total < 0 ? "House wins" : "Push";
        boolean grad = one && gm.results.get(0).outcome().equals("blackjack");
        int col = total > 0 ? P.GREEN : total < 0 ? P.RED : P.WHITE;   // the verdict in the backer's colours (a bust is red); the line under it is the gambler's
        g.push(); g.translate(cx, fy + 70); g.scale(sc, sc);
        if (grad) P.gradientText(g, word, -(F.width(word) >> 1), -4, true, e); else g.drawText(word, -(F.width(word) >> 1), -4, P.alpha(col, e), true);
        g.pop();
        String sub = total != 0 ? Money.signed(total) : "Bet returned";   // the backer's result, as the viewers see it (the gambler's own is on the HUD)
        g.drawText(sub, cx - (F.width(sub) >> 1), fy + 84, P.alpha(total > 0 ? P.GREEN : total < 0 ? P.RED : P.MUTE, e), false);
    }

    private static int dealt(List<Card> cards, long t) {
        int n = 0;
        for (Card c : cards) { Timing tm = BJ.times.get(c.id); if (tm != null && t >= tm.dealAt) n++; }
        return n;
    }

    /** The chips of one hand: static on the felt, then flying to the bank (loss) or joined by the payout from the bank (win). */
    private void betStack(G g, double x, double y, long bet, Result r, double bankX, double bankY, long t) {
        if (r == null) { Chips.drawBet(g, x, y, bet); return; }
        double f = Math.min(1, (double) (t - BJ.resultAt) / BJ_FLY_MS), e = P.easeOut(f);
        if (r.delta() < 0) { if (f < 1) Chips.drawBet(g, Math.round(x + (bankX - x) * e), Math.round(y + (bankY - y) * e), bet); return; }
        if (r.delta() == 0) { Chips.drawBet(g, x, y, bet); return; }
        if (f < 1) { Chips.drawBet(g, x, y, bet); Chips.drawBet(g, Math.round(bankX + (x - 10 - bankX) * e), Math.round(bankY + (y - 3 - bankY) * e), r.delta()); }
        else Chips.drawBet(g, x, y, bet + r.delta());
    }
}
