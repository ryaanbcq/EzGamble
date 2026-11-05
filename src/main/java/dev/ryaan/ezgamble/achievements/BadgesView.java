package dev.ryaan.ezgamble.achievements;

import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.GlassButton;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.ui.GambleScreen;
import dev.ryaan.ezgamble.ui.PlayerView;

import java.util.List;

/**
 * Games > player profile > Badges: the whole catalogue for PlayerView.player as a 4 x 6 grid of tiles (icon x2, name,
 * rarity, a rarity-coloured border), earned ones lit with the unlock date, locked ones dim with the description; a header
 * with his head, name, count and the rarest earned; ‹ goes back to the profile. The grid scrolls (list "badges").
 */
public final class BadgesView implements GameView {
    private static final int COLS = 4, GAP = 4, TILE_H = 40, STRIDE = TILE_H + GAP;

    private final GambleScreen s;

    public BadgesView(GambleScreen s) { this.s = s; }

    private double lx() { return s.x0 + GambleScreen.PAD; }
    private double rx() { return s.x0 + GambleScreen.W - GambleScreen.PAD; }
    private double top() { return s.y0 + 102; }
    private double tileW() { return Math.floor((rx() - lx() - (COLS - 1) * GAP) / COLS); }

    @Override
    public void init() {
        s.add(new GlassButton(lx(), s.y0 + 80, 18, 16, "‹", "GHOST", () -> { GambleScreen.sub = "player"; s.rebuild(); }));   // back to the profile, the player kept
    }

    @Override public void tick() {}
    @Override public void leave() {}

    @Override
    public void render(G g, double mx, double my) {
        String name = PlayerView.player;
        if (name == null) return;
        double lx = lx(), rx = rx(), y = s.y0 + 80;
        List<Achievements.Earned> earned = Achievements.of(name);
        Achievement rarest = Achievements.rarest(name);
        g.drawHead(P.skinOf(name), lx + 24, y, 16);
        P.gradientText(g, name, lx + 45, y + 4, true);
        P.pillsRight(g, rx, y, rarest == null ? new Object[][]{{"Badges", earned.size() + " / " + Achievements.TOTAL}}
                : new Object[][]{{"Badges", earned.size() + " / " + Achievements.TOTAL}, {"Rarest", rarest.name(), rarest.color()}});
        List<Achievement> cat = Achievements.catalogue();
        double top = top(), lh = s.y0 + GambleScreen.H - GambleScreen.PAD - top, tw = tileW();
        int rows = (cat.size() + COLS - 1) / COLS;
        double content = rows * STRIDE - GAP;
        double off = s.beginList(g, "badges", lx, top, rx - lx, lh, content);
        boolean inList = my >= top && my < top + lh;
        Achievement hover = null; Long hoverAt = null;
        for (int i = 0; i < cat.size(); i++) {
            Achievement a = cat.get(i);
            double x = lx + (i % COLS) * (tw + GAP), ty = top + (i / COLS) * STRIDE - off;
            if (ty + TILE_H < top || ty > top + lh) continue;   // off screen
            Long at = BadgeIcons.earnedAt(name, a);
            boolean lit = at != null, over = inList && mx >= x && mx < x + tw && my >= ty && my < ty + TILE_H;
            int c = a.color();
            // earned: a tint of its rarity behind a bright border; locked: plain glass, a faint border
            P.smooth(g, x, ty, tw, TILE_H, 6, lit ? P.alpha(c, over ? 0.2 : 0.12) : (over ? 0x14FFFFFF : 0x0AFFFFFF), P.alpha(c, lit ? 0.75 : 0.22));
            BadgeIcons.draw(g, a, x + 5, ty + (TILE_H - 18) / 2.0, 2, lit);
            double tx = x + 27, maxW = tw - 31;
            g.drawText(F.trimToWidth(a.name(), maxW), tx, ty + 6, lit ? P.WHITE : P.DIM, false);
            g.drawText(a.rarityName(), tx, ty + 16, lit ? c : P.alpha(c, 0.55), false);
            g.drawText(F.trimToWidth(lit ? P.DATE(at) : a.desc(), maxW), tx, ty + 26, lit ? P.MUTE : P.alpha(P.DIM, 0.7), false);
            if (over) { hover = a; hoverAt = at; }
        }
        s.endList(g, "badges", content);
        if (hover != null) BadgeIcons.tooltip(g, hover, hoverAt, mx, my, lx, rx, s.y0 + 30);   // the full description, on top of everything
    }
}
