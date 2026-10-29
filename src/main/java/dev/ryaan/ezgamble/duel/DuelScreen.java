package dev.ryaan.ezgamble.duel;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.proto.*;
import net.minecraft.client.gui.screen.Screen;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The answer: the world blurred behind, one glass card, Later / Accept. Enter or Y accepts; Later, N, Esc or 20 s
 * without an answer keep it in the pending requests.
 */
public final class DuelScreen extends ProtoScreen {
    static final int W = 210, H = 150;
    private final Duel.Req d;
    private final long openedAt = now();
    private long closingAt; private Screen closingNext; private boolean closing;
    private String err; private long errAt;
    private double x0, y0;

    public DuelScreen(Duel.Req d) { super("Duel"); this.d = d; }

    private double pop() {
        long t = now();
        if (closing) return 1 - easeIn(Math.min(1, (t - closingAt) / 200.0));
        return easeOut(Math.min(1, (t - openedAt) / 260.0));
    }

    @Override
    protected void init2() {
        x0 = (int) (((int) SW - W) >> 1); y0 = (int) (((int) SH - H) >> 1);
        int bw = (W - 28 - 6) >> 1; double by = y0 + H - 38;
        add(new GlassButton(x0 + 14, by, bw, 24, "Later", "GHOST", this::later));
        add(new GlassButton(x0 + 14 + bw + 6, by, bw, 24, "Accept", "PRIMARY", this::accept));
    }

    private double left() { return Math.max(0, 1 - (now() - openedAt) / (double) Duel.DUEL_MS); }

    @Override
    public void tick() {
        if (closing) {
            if (now() - closingAt >= 200) { Duel.done(); if (client != null) client.setScreen(closingNext); }
            return;
        }
        if (left() <= 0) later();   // no answer: kept for later, never lost
        for (Widget c : children) c.active = !closing;
    }

    private void leave(Screen next) { if (!closing) { closing = true; closingAt = now(); closingNext = next; } }

    private void later() {
        if (closing) return;
        Duel.later(); play("mc_sweep2", 0.7, 0.5); leave(null);
    }

    private void accept() {
        String r = Duel.accept(d);
        if (r != null) { err = r; errAt = now(); play("fail", 1.3, 0.4); }
    }

    @Override
    public void key(Key e) {
        String k = e.key().toLowerCase();
        if (k.equals("enter") || k.equals("y")) accept();
        else if (k.equals("n") || k.equals("escape")) later();
    }

    @Override
    public void close() { later(); }

    @Override
    protected void renderBg(G g, double mx, double my) {
        double p = pop();
        if (client != null && client.world != null) g.d.applyBlur();   // vanilla 1.21 menu blur (the player's "Menu Background Blurriness")
        g.fill(0, 0, SW, SH, ((int) (0x60 * p) << 24) | 0x0A0710);
    }

    @Override
    protected void renderFg(G g, double mx, double my) {
        double p = pop(), cx = x0 + (W >> 1); long t = now();
        g.push();
        double sc = 0.85 + 0.15 * p; g.translate(cx, y0 + H / 2.0); g.scale(sc, sc); g.translate(-cx, -(y0 + H / 2.0));
        glass(g, x0, y0, W, H, 12, 0.84);
        ring(g, x0, y0, W, H, 12, alpha(PINK, 0.3 + 0.2 * Math.sin(t / 300.0)), 1.2);
        // the game he challenges you to (its own icon), his head on its corner
        String gi = switch (d.game) { case "Crates" -> "game/crates"; case "Mines" -> "game/mines"; case "Crash" -> "game/crash"; case "Coinflip" -> "game/coinflip"; default -> "game/blackjack"; };
        g.drawSprite(gi, cx - 18, y0 + 10, 36, 36);
        g.drawHead(skinOf(d.name), cx + 8, y0 + 32, 14);
        // who, what, how much
        String n = F.trimToWidth(d.name, 95); double nw = F.width(n) * 2;
        g.push(); g.translate(cx - nw / 2, y0 + 52); g.scale(2, 2); gradientText(g, n, 0, 0, true); g.pop();
        String line = "challenges you to " + d.game;
        g.drawText(line, cx - (F.width(line) >> 1), y0 + 72, TEXT, true);
        String amt = Money.format(d.amount); double aw = F.width(amt) * 2;
        g.push(); g.translate(cx - aw / 2, y0 + 86); g.scale(2, 2); g.drawText(amt, 0, 0, WHITE, true); g.pop();
        if (err != null && t - errAt < 2500) g.drawText(err, cx - (F.width(err) >> 1), y0 + H - 50, RED, true);
        // time to answer, under the buttons
        double left = left(), bw = Math.round((W - 28) * left);
        g.fill(x0 + 14, y0 + H - 9, x0 + 14 + bw, y0 + H - 8, left < 0.25 ? RED : alpha(LAV, 0.8));
        g.pop();
        renderChildren(g, mx, my);
    }
}
