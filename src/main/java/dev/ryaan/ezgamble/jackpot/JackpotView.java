package dev.ryaan.ezgamble.jackpot;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.*;
import dev.ryaan.ezgamble.ui.GambleScreen;
import dev.ryaan.ezgamble.ui.Icons;
import dev.ryaan.ezgamble.ui.PlayerView;

import java.util.List;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * Games > Jackpot: the pot big in the accent card with the odds line, the slice (0.5 / 1 / 2 percent) and the seed
 * (what the pot restarts at after a hit) with Apply, the Enabled switch in the header, the hits history (head, name,
 * amount, date, a row opens the profile) and the provably fair note. Drawn under its widgets like the other
 * non-game sub-views, the back button at (x0 + PAD, y0 + 80).
 */
public final class JackpotView implements GameView {
    private static final double ROW_H = 16;
    /** Kept between rebuilds so the segmented pill keeps sliding and the field keeps its text. */
    private static GlassSegmented sliceSeg;
    private static GlassField seedField;
    private static long savedAt;

    private final GambleScreen s;
    private GlassButton apply;
    private int builtVer = -1;

    public JackpotView(GambleScreen s) { this.s = s; }

    private double lx() { return s.x0 + GambleScreen.PAD; }
    private double rx() { return s.x0 + GambleScreen.W - GambleScreen.PAD; }
    private double y() { return s.y0 + 80; }
    /** The settings card: slice on the left, seed on the right. */
    private double setY() { return y() + 72; }
    private Widget.Clip listClip() { double top = y() + 136; return new Widget.Clip(lx(), top, rx() - lx(), s.y0 + GambleScreen.H - GambleScreen.PAD - 14 - top); }

    private static String sliceId(double v) { return v < 0.0075 ? "half" : v < 0.015 ? "one" : "two"; }
    private static double sliceOf(String id) { return id.equals("half") ? 0.005 : id.equals("two") ? 0.02 : 0.01; }

    @Override
    public void init() {
        double lx = lx(), rx = rx(), y = y(), sy = setY();
        builtVer = Jackpot.ver;
        s.add(new GlassButton(lx, y, 18, 16, "‹", "GHOST", () -> { if (seedField != null) seedField.focused = false; s.back(); }));
        // the Enabled switch, right of the title row
        s.add(new GlassSwitch(rx - 28, y + 1, 28, 14, Jackpot::enabled, on -> { Jackpot.setEnabled(on); s.rebuild(); }));
        // the slice: a segmented control, the change is immediate
        if (sliceSeg == null) sliceSeg = new GlassSegmented(0, 0, 18, new String[][]{{"half", "0.5%"}, {"one", "1%"}, {"two", "2%"}},
                () -> sliceId(Jackpot.slice()), v -> { Jackpot.setSlice(sliceOf(v)); GambleScreen cur = GambleScreen.current(); if (cur != null) cur.rebuild(); });
        sliceSeg.place(lx + 8, sy + 19);
        s.add(sliceSeg);
        // the seed: a field and Apply (Enter applies too)
        double fw = 64, aw = F.width("Apply") + 16;
        if (seedField == null) { seedField = new GlassField(0, 0, fw, 18, Money.format(Jackpot.seed()).replace("$", ""), "Seed", 8); seedField.allowed = "[0-9.KMBkmb]"; seedField.focused = false; }
        seedField.x = rx - 8 - aw - 4 - fw; seedField.y = sy + 19; seedField.w = fw;
        s.add(seedField);
        apply = s.add(new GlassButton(rx - 8 - aw, sy + 20, aw, 16, "Apply", "PRIMARY", this::applySeed));
        // the hits, newest first: a row opens the player's profile
        List<Jackpot.Hit> hits = Jackpot.hits();
        Widget.Clip clip = listClip();
        for (int i = 0; i < hits.size(); i++) {
            String name = hits.get(hits.size() - 1 - i).name();
            Widget w = s.add(Widgets.area(lx, clip.y() + i * ROW_H, rx - lx, ROW_H - 1, () -> {
                P.play("click"); PlayerView.player = name; PlayerView.backTo = "jackpot"; GambleScreen.sub = "player"; s.chartAt = P.now(); s.rebuild(); }));
            w.list = new Widget.ListPos("jackpot", w.y); w.clip = clip;
        }
    }

    private void applySeed() {
        long v = Money.parse(seedField.value);
        if (v <= 0) { P.play("fail", 1, 0.5); return; }
        Jackpot.setSeed(v);
        seedField.value = Money.format(Jackpot.seed()).replace("$", "");
        seedField.focused = false;
        savedAt = P.now();
        P.play("pay", 1.1, 0.5);
    }

    @Override public void tick() { if (Jackpot.ver != builtVer) s.rebuild(); }
    @Override public void leave() { if (seedField != null) seedField.focused = false; }

    @Override
    public boolean key(Key e) {
        if (seedField != null && seedField.focused) {
            if (e.is("Enter")) { applySeed(); return true; }
            if (e.is("Escape")) { seedField.focused = false; return true; }
            return seedField.type(e);
        }
        return false;
    }

    @Override
    public void clicked(Widget hit) { if (seedField != null) seedField.focused = hit == seedField; }

    @Override
    public void render(G g, double mx, double my) {
        double lx = lx(), rx = rx(), y = y(), sy = setY();
        long t = P.now();
        boolean on = Jackpot.enabled();
        g.drawText("Jackpot", lx + 24, y + 4, WHITE, true);
        String en = "Enabled";
        g.drawText(en, rx - 28 - 6 - F.width(en), y + 4, on ? TEXT : MUTE, false);

        // the pot, big, with the odds on its right
        card(g, lx, y + 22, rx - lx, 44, "accent");
        Icons.drawGameIcon(g, "jackpot", lx + 8, y + 28, 32);
        g.drawText("Progressive pot", lx + 48, y + 28, MUTE, false);
        String pot = Money.format(Jackpot.pot());
        g.push(); g.translate(lx + 48, y + 40); g.scale(2, 2);
        if (on) gradientText(g, pot, 0, 0, true); else g.drawText(pot, 0, 0, DIM, true);
        g.pop();
        if (!on) g.drawText("off", lx + 48 + F.width(pot) * 2 + 6, y + 48, RED, false);
        // the odds line: in two lines on the right, never under the amount
        String o1 = "Each $1M bet has a " + Jackpot.oddsText(1_000_000L) + " chance", o2 = "the house pays " + Jackpot.sliceText() + " of every bet";
        double left = lx + 48 + F.width(pot) * 2 + (on ? 14 : 14 + F.width("off") + 6);
        g.drawText(F.trimToWidth(o1, rx - 10 - left), rx - 10 - F.width(F.trimToWidth(o1, rx - 10 - left)), y + 36, TEXT, false);
        g.drawText(F.trimToWidth(o2, rx - 10 - left), rx - 10 - F.width(F.trimToWidth(o2, rx - 10 - left)), y + 48, MUTE, false);

        // the settings: slice, then seed
        card(g, lx, sy, rx - lx, 44, "soft");
        g.drawText("House slice", lx + 8, sy + 7, MUTE, false);
        String sh = "of every bet feeds the pot";
        g.drawText(sh, lx + 8 + F.width("House slice") + 6, sy + 7, DIM, false);
        String seedL = "Seed", seedH = "the pot restarts here after a hit";
        double sx = seedField.x;
        g.drawText(seedL, sx, sy + 7, MUTE, false);
        if (F.width(seedH) <= rx - 8 - (sx + F.width(seedL) + 6)) g.drawText(seedH, sx + F.width(seedL) + 6, sy + 7, DIM, false);
        if (apply != null) apply.label = t - savedAt < 1500 ? "✔ Saved" : "Apply";

        // the hits history
        List<Jackpot.Hit> hits = Jackpot.hits();
        Widget.Clip clip = listClip();
        double titleY = clip.y() - 14;
        g.drawText("Hits", lx, titleY, WHITE, false);
        long total = 0;
        for (Jackpot.Hit h : hits) total += h.amount();
        pillsRight(g, rx, titleY - 4, new Object[][]{{"Hits", String.valueOf(hits.size())}, {"Paid out", Money.format(total)}});
        if (hits.isEmpty()) {
            String a = "No hit yet. Every bet placed with a player's chips has a shot at the pot.";
            g.drawText(F.trimToWidth(a, rx - lx), lx + (rx - lx - F.width(F.trimToWidth(a, rx - lx))) / 2, clip.y() + 12, DIM, false);
        } else {
            double hh = hits.size() * ROW_H, off = s.beginList(g, "jackpot", clip.x(), clip.y(), clip.w(), clip.h(), hh);
            for (int i = 0; i < hits.size(); i++) {
                Jackpot.Hit h = hits.get(hits.size() - 1 - i);
                double ry = clip.y() + i * ROW_H - off;
                if (ry + ROW_H < clip.y() || ry > clip.y() + clip.h()) continue;
                boolean hov = mx >= lx && mx < rx && my >= ry && my < ry + ROW_H - 1 && my >= clip.y() && my < clip.y() + clip.h();
                if (hov) smooth(g, lx, ry, rx - lx, ROW_H - 1, 5, 0x14FFFFFF);
                else if (i % 2 == 0) smooth(g, lx, ry, rx - lx, ROW_H - 1, 4, 0x08FFFFFF);
                g.drawHead(skinOf(h.name()), lx + 5, ry + 3, 9);
                int bw = dev.ryaan.ezgamble.vip.VipBadge.w(h.name()); dev.ryaan.ezgamble.vip.VipBadge.draw(g, h.name(), lx + 19, ry + 4);
                g.drawText(F.trimToWidth(h.name(), 110 - bw), lx + 19 + bw, ry + 4, hov ? WHITE : TEXT, false);
                String d = DATE(h.t()), amt = Money.format(h.amount()), rn = "#" + h.nonce();
                g.drawText(d, rx - 6 - F.width(d), ry + 4, DIM, false);
                gradientText(g, amt, rx - 6 - F.width(d) - 10 - F.width(amt), ry + 4, false);
                g.drawText(rn, rx - 6 - F.width(d) - 10 - F.width(amt) - 8 - F.width(rn), ry + 4, DIM, false);   // the Fair round that drew it
            }
            s.endList(g, "jackpot", hh);
        }
        // the provably fair note, one line at the bottom
        String note = "Provably fair: every draw is a round of the Fair log, the reveal lets anyone recompute it.";
        double ny = s.y0 + GambleScreen.H - GambleScreen.PAD - 10;
        g.drawText(F.trimToWidth(note, rx - lx), lx, ny, DIM, false);
    }
}
