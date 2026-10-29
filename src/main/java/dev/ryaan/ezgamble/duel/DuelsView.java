package dev.ryaan.ezgamble.duel;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.proto.*;
import dev.ryaan.ezgamble.ui.ChipsView;
import dev.ryaan.ezgamble.ui.GambleScreen;

import java.util.List;

import static dev.ryaan.ezgamble.proto.P.*;

/** Games > Requests: the duels answered "Later". Accept plays it now (if his chips still cover it), x drops it. */
public final class DuelsView implements GameView {
    private final GambleScreen s;
    private Duel.Req errReq; private String errText; private long errAt;

    public DuelsView(GambleScreen s) { this.s = s; }

    @Override
    public void init() {
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD, y0 = s.y0;
        s.add(new GlassButton(lx, y0 + 80, 18, 16, "‹", "GHOST", () -> { GambleScreen.sub = null; s.rebuild(); }));
        Widget.Clip clip = ChipsView.listClip(s, ChipsView.who.equals("all"));
        List<Duel.Req> rows = Duel.pending;
        for (int i = 0; i < rows.size(); i++) {
            Duel.Req d = rows.get(i);
            double ry = clip.y() + i * 30;
            GlassButton acc = new GlassButton(rx - 6 - 18 - 4 - 50, ry + 5, 50, 16, "Accept", "PRIMARY", () -> {
                String r = Duel.accept(d);
                if (r != null) { errReq = d; errText = r; errAt = now(); play("fail", 1.3, 0.4); }
            });
            acc.active = Duel.chips(d.name) >= d.amount && GambleData.mode;
            GlassButton x = new GlassButton(rx - 6 - 18, ry + 5, 18, 16, "×", "GHOST", () -> { play("click"); Duel.remove(d); });
            for (GlassButton b : new GlassButton[]{acc, x}) { s.add(b); b.list = new Widget.ListPos("duels", b.y); b.clip = clip; }
        }
    }

    @Override public void tick() {}

    @Override
    public void render(G g, double mx, double my) {
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD, y = s.y0 + 80;
        g.drawText("Duel requests", lx + 24, y + 4, WHITE, true);
        List<Duel.Req> rows = Duel.pending;
        if (rows.isEmpty()) {
            String a = "No pending requests", b = "A duel you answer Later waits here.";
            g.drawText(a, lx + ((rx - lx - F.width(a)) / 2), y + 50, TEXT, false);
            g.drawText(b, lx + ((rx - lx - F.width(b)) / 2), y + 62, MUTE, false);
            return;
        }
        Widget.Clip clip = ChipsView.listClip(s, ChipsView.who.equals("all"));
        double off = s.beginList(g, "duels", clip.x(), clip.y(), clip.w(), clip.h(), rows.size() * 30 - 3);
        for (int i = 0; i < rows.size(); i++) {
            Duel.Req d = rows.get(i);
            double ry = clip.y() + i * 30 - off;
            if (ry + 27 < clip.y() || ry > clip.y() + clip.h()) continue;
            card(g, lx, ry, rx - lx, 27, "row");
            g.drawHead(skinOf(d.name), lx + 5, ry + 5, 16);
            g.drawText(F.trimToWidth(d.name, 90), lx + 26, ry + 4, WHITE, false);
            String err = errReq == d && now() - errAt < 2500 ? errText : null;
            boolean ok = Duel.chips(d.name) >= d.amount; long mins = (now() - d.at) / 60000;
            String sub = err != null ? err : (!ok ? "Not enough chips now" : d.game + ", " + (mins < 1 ? "just now" : mins + " min ago"));
            g.drawText(sub, lx + 26, ry + 15, err != null || !ok ? RED : MUTE, false);
            String v = Money.format(d.amount); double vx = rx - 6 - 18 - 4 - 50 - 8 - F.width(v);
            if (ok) gradientText(g, v, vx, ry + 10, true); else g.drawText(v, vx, ry + 10, DIM, false);
        }
        s.endList(g, "duels", rows.size() * 30 - 3);
    }

    @Override public void leave() {}
}
