package dev.ryaan.ezgamble.lobby;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.proto.*;
import dev.ryaan.ezgamble.ui.ChipsView;
import dev.ryaan.ezgamble.ui.GambleScreen;
import dev.ryaan.ezgamble.ui.Icons;
import dev.ryaan.ezgamble.vip.VipBadge;

import java.util.List;

import static dev.ryaan.ezgamble.proto.P.*;

/** Games > Lobby: the waiting line, one row per player (rank, head, gem, name, game, amount, waited), Seat plays him now, x drops him. Rows like ChipsView. */
public final class LobbyView implements GameView {
    private final GambleScreen s;
    private int ver = -1;

    public LobbyView(GambleScreen s) { this.s = s; }

    @Override
    public void init() {
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD, y0 = s.y0;
        ver = Lobby.ver();
        s.add(new GlassButton(lx, y0 + 80, 18, 16, "‹", "GHOST", s::back));
        Widget.Clip clip = ChipsView.listClip(s, false);
        List<Lobby.Seat> rows = Lobby.seats();
        for (int i = 0; i < rows.size(); i++) {
            Lobby.Seat d = rows.get(i);
            double ry = clip.y() + i * 30;
            GlassButton seat = new GlassButton(rx - 6 - 18 - 4 - 40, ry + 5, 40, 16, "Seat", "PRIMARY", () -> Lobby.seat(d)) {
                // short of chips: he waits, greyed, until he pays again; decided every frame, his chips move without the line changing
                @Override public void render(G g, double mx, double my) { active = GambleData.mode && !Lobby.isShort(d); super.render(g, mx, my); }
            };
            GlassButton x = new GlassButton(rx - 6 - 18, ry + 5, 18, 16, "×", "GHOST", () -> Lobby.remove(d));
            for (GlassButton b : new GlassButton[]{seat, x}) { s.add(b); b.list = new Widget.ListPos("lobby", b.y); b.clip = clip; }
        }
    }

    @Override public void tick() { if (ver != Lobby.ver()) { ver = Lobby.ver(); s.rebuild(); } }

    @Override
    public void render(G g, double mx, double my) {
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD, y = s.y0 + 80;
        List<Lobby.Seat> rows = Lobby.seats();
        g.drawText("Lobby", lx + 24, y + 4, WHITE, true);
        P.pillsRight(g, rx, y, new Object[][]{{"Lobby", String.valueOf(rows.size())}, {"Auto-seat", GambleData.autoSeat ? "on" : "off", GambleData.autoSeat ? GREEN : DIM}});
        if (rows.isEmpty()) {
            String a = "Nobody in line", b = "A player whispers you: join blackjack 5M";
            g.drawText(a, lx + ((rx - lx - F.width(a)) / 2), y + 50, TEXT, false);
            g.drawText(b, lx + ((rx - lx - F.width(b)) / 2), y + 62, MUTE, false);
        }
        Widget.Clip clip = ChipsView.listClip(s, false);
        double ch = rows.size() * 30 - 3;
        double off = s.beginList(g, "lobby", clip.x(), clip.y(), clip.w(), clip.h(), ch);
        long t = now();
        for (int i = 0; i < rows.size(); i++) {
            Lobby.Seat d = rows.get(i);
            double ry = clip.y() + i * 30 - off;
            if (ry + 27 < clip.y() || ry > clip.y() + clip.h()) continue;
            card(g, lx, ry, rx - lx, 27, "row");
            boolean sh = Lobby.isShort(d);
            String err = Lobby.errSeat() == d ? Lobby.errText() : null;
            String rk = "#" + (i + 1); g.drawText(rk, lx + 20 - F.width(rk), ry + 10, i == 0 ? PINK : DIM, false);
            g.drawHead(skinOf(d.name()), lx + 22, ry + 5, 16);
            int bw = VipBadge.w(d.name()); VipBadge.draw(g, d.name(), lx + 43, ry + 4);
            g.drawText(F.trimToWidth(d.name(), 90 - bw), lx + 43 + bw, ry + 4, WHITE, false);
            // line 2: the game (icon + name), how long he has waited, or why he cannot sit
            Icons.drawGameIcon(g, d.game(), lx + 43, ry + 14, 11);
            long mins = (t - d.at()) / 60000;
            String sub = err != null ? err : sh ? "Short of chips, waits" : d.game() + ", " + (mins < 1 ? "just now" : mins + " min") + (d.amount() == 0 ? ", all his chips" : "");
            g.drawText(sub, lx + 57, ry + 15, err != null || sh ? RED : MUTE, false);
            // right: the amount he plays, before the buttons
            long amt = Lobby.amountOf(d);
            String v = Money.format(amt); double vx = rx - 6 - 18 - 4 - 40 - 8 - F.width(v);
            if (!sh) gradientText(g, v, vx, ry + 10, true); else g.drawText(v, vx, ry + 10, DIM, false);
        }
        s.endList(g, "lobby", ch);
        String foot = "Whisper join <game> [amount] or leave, a seat drops after 10 min";
        g.drawText(F.trimToWidth(foot, rx - lx), lx, s.y0 + GambleScreen.H - GambleScreen.PAD - 12, DIM, false);
    }

    @Override public void leave() {}
}
