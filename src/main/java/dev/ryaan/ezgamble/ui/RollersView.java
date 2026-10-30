package dev.ryaan.ezgamble.ui;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.PlayerStats;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.*;

import java.util.List;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * Games > High rollers: the biggest wagers placed with players' chips. The top three stand on a podium as pixel-art
 * chibis of their skins (gold in the middle, crowned and hopping), the others are listed under it. Session / All time.
 * A click on anyone opens his profile.
 */
public final class RollersView implements GameView {
    public static String range = "all";
    private static GlassSegmented rangeSeg;
    /** Podium order left to right: 2nd, 1st, 3rd; step heights and medal colours by rank. */
    private static final int[] ORDER = {1, 0, 2}, MEDAL = {0xFFF2C14E, 0xFFC7CCD6, 0xFFCD8B5A};
    /** Step heights: with the list under the podium, and with the podium alone on the page. */
    private static final int[] STEP_LIST = {58, 46, 36}, STEP_TALL = {64, 50, 40};
    private static final double STEP_W = 100, GAP = 6, ROW_H = 16;

    private final GambleScreen s;

    public RollersView(GambleScreen s) { this.s = s; }

    /** With nobody listed under it the podium fills the page (chibis at 3 px per texel), else 2 px. */
    private static boolean list(int n) { return n > 3; }
    private double base(int n) { return s.y0 + (list(n) ? 232 : 272); }
    private static int[] stepH(int n) { return list(n) ? STEP_LIST : STEP_TALL; }
    private static double px(int n) { return list(n) ? 2 : 3; }
    private Widget.Clip listClip() { double top = base(4) + 6; return new Widget.Clip(s.x0 + GambleScreen.PAD, top, GambleScreen.W - 2 * GambleScreen.PAD, s.y0 + GambleScreen.H - GambleScreen.PAD - top); }
    private double stepX(int col) { double cx = s.x0 + GambleScreen.W / 2.0; return cx - STEP_W * 1.5 - GAP + col * (STEP_W + GAP); }

    @Override
    public void init() {
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD;
        if (rangeSeg == null) rangeSeg = new GlassSegmented(0, 0, 18, new String[][]{{"session", "Session"}, {"all", "All time"}},
                () -> range, v -> { range = v; GambleScreen cur = GambleScreen.current(); if (cur != null) { cur.chartAt = P.now(); cur.rebuild(); } });
        rangeSeg.place(rx - rangeSeg.w, s.y0 + 33);
        s.add(rangeSeg);
        s.add(new GlassButton(lx, s.y0 + 80, 18, 16, "‹", "GHOST", s::back));
        List<PlayerStats.Roller> rows = PlayerStats.highRollers(range);
        dev.ryaan.ezgamble.proto.Faces.prefetch(rows.stream().map(PlayerStats.Roller::name).toList());
        for (int col = 0; col < 3; col++) {   // the podium: step and chibi open the profile
            int rank = ORDER[col];
            if (rank >= rows.size()) continue;
            String name = rows.get(rank).name();
            int n = rows.size();
            double sh = stepH(n)[rank], top = base(n) - sh;
            s.add(Widgets.area(stepX(col), top - (CHIBI_H + 4) * px(n), STEP_W, sh + (CHIBI_H + 4) * px(n), () -> open(name)));
        }
        Widget.Clip clip = listClip();
        for (int i = 3; i < rows.size(); i++) {
            String name = rows.get(i).name();
            Widget w = s.add(Widgets.area(lx, clip.y() + (i - 3) * ROW_H, rx - lx, ROW_H - 1, () -> open(name)));
            w.list = new Widget.ListPos("rollers", w.y); w.clip = clip;
        }
    }

    private void open(String name) {
        P.play("click");
        PlayerView.player = name;
        PlayerView.backTo = "rollers";
        GambleScreen.sub = "player";
        s.chartAt = P.now();
        s.rebuild();
    }

    @Override public void tick() {}
    @Override public void leave() {}

    @Override
    public void render(G g, double mx, double my) {
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD, y = s.y0 + 80;
        long t = now();
        List<PlayerStats.Roller> rows = PlayerStats.highRollers(range);
        g.drawText("High rollers", lx + 24, y + 4, WHITE, true);
        long total = 0;
        for (PlayerStats.Roller r : rows) total += r.wagered();
        pillsRight(g, rx, y, new Object[][]{{"Players", String.valueOf(rows.size())}, {"Wagered", Money.format(total)}});
        if (rows.isEmpty()) {
            String a = "No wagers yet", b = "Bets placed with a player's chips land here.";
            g.drawText(a, lx + (rx - lx - F.width(a)) / 2, y + 60, TEXT, false);
            g.drawText(b, lx + (rx - lx - F.width(b)) / 2, y + 72, MUTE, false);
            return;
        }
        int n = rows.size();
        double base = base(n), p = px(n);
        for (int col = 0; col < 3; col++) {
            int rank = ORDER[col], m = MEDAL[rank];
            double sx = stepX(col), sh = stepH(n)[rank], top = base - sh, cx = sx + STEP_W / 2;
            boolean has = rank < n;
            boolean hov = has && mx >= sx && mx < sx + STEP_W && my >= top - (CHIBI_H + 4) * p && my < base;
            if (has) {   // a soft halo of stacked ovals behind whoever stands there, the winner's the brightest
                double k = rank == 0 ? 1 : 0.55, hy = top - 1 - CHIBI_H / 2.0 * p;
                for (int r : new int[]{15, 11, 7}) smooth(g, cx - r * p, hy - r * p * 1.2, 2 * r * p, 2.4 * r * p, r * p * 0.9, alpha(m, 0.06 * k));
                smooth(g, cx - 8 * p, top - 2, 16 * p, 4, 2, alpha(m, 0.35 * k));
            }
            // the step: glass tinted with its medal, a lip of the medal along the top
            if (has) smooth(g, sx, top, STEP_W, sh, 6, v(alpha(m, hov ? 0.26 : 0.18), alpha(m, 0.03)), alpha(m, hov ? 0.7 : 0.45));
            else smooth(g, sx, top, STEP_W, sh, 6, 0x08FFFFFF, 0x12FFFFFF);
            g.fill(sx + 8, top + 1, sx + STEP_W - 8, top + 2, has ? alpha(m, 0.9) : 0x16FFFFFF);
            String num = String.valueOf(rank + 1);
            g.push(); g.translate(cx - F.width(num), top + 6); g.scale(2, 2);
            g.drawText(num, 0, 0, has ? m : DIM, true);
            g.pop();
            if (!has) {   // an open seat: a ghost waiting for its high roller
                chibiGhost(g, cx, top - 1, p);
                String o = "Open seat";
                g.drawText(o, cx - F.width(o) / 2.0, top + 25, DIM, false);
                continue;
            }
            PlayerStats.Roller r = rows.get(rank);
            String v = Money.format(r.wagered());
            if (rank == 0) gradientText(g, v, cx - F.width(v) / 2.0, top + 25, true);
            else g.drawText(v, cx - F.width(v) / 2.0, top + 25, WHITE, true);
            String bets = r.bets() + (r.bets() == 1 ? " bet" : " bets");
            if (sh >= 46) g.drawText(bets, cx - F.width(bets) / 2.0, top + 36, MUTE, false);
            // the chibi on top, his name over his head
            chibi(g, r.name(), cx, top - 1, p, t, rank == 0, rank == 0);
            if (rank == 0) sparkles(g, cx, top - 1, p, t);
            String nm = F.trimToWidth(r.name(), STEP_W - 8);
            double ny = top - 1 - CHIBI_H * p - (rank == 0 ? 3 * p : 0) - 12;
            if (hov || rank == 0) gradientText(g, nm, cx - F.width(nm) / 2.0, ny, true);
            else g.drawText(nm, cx - F.width(nm) / 2.0, ny, TEXT, true);
        }
        // everybody else, under the podium
        if (rows.size() <= 3) return;
        Widget.Clip clip = listClip();
        double h = (rows.size() - 3) * ROW_H - 1;
        double off = s.beginList(g, "rollers", clip.x(), clip.y(), clip.w(), clip.h(), h);
        for (int i = 3; i < rows.size(); i++) {
            PlayerStats.Roller r = rows.get(i);
            double ry = clip.y() + (i - 3) * ROW_H - off;
            if (ry + ROW_H < clip.y() || ry > clip.y() + clip.h()) continue;
            boolean hov = mx >= lx && mx < rx && my >= ry && my < ry + ROW_H - 1 && my >= clip.y() && my < clip.y() + clip.h();
            if (hov) smooth(g, lx, ry, rx - lx, ROW_H - 1, 5, 0x14FFFFFF);
            g.drawText("#" + (i + 1), lx + 6, ry + 4, DIM, false);
            g.drawHead(skinOf(r.name()), lx + 28, ry + 3, 9);
            g.drawText(F.trimToWidth(r.name(), 140), lx + 41, ry + 4, TEXT, false);
            String v = Money.format(r.wagered()), bets = r.bets() + (r.bets() == 1 ? " bet" : " bets");
            g.drawText(v, rx - 6 - F.width(v), ry + 4, WHITE, false);
            g.drawText(bets, rx - 6 - F.width(v) - 8 - F.width(bets), ry + 4, MUTE, false);
        }
        s.endList(g, "rollers", h);
    }
}
