package dev.ryaan.ezgamble.ui;

import dev.ryaan.ezgamble.data.Fair;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.proto.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * Games > Provably fair. The seed hash to paste in chat BEFORE the rolls (Copy for chat), the client seed a viewer can
 * give you (only while the seed has no round yet: the rounds already played used the old one), Reveal = the seed to
 * paste in chat, then a new seed. Nothing is ever sent for you: you copy, you paste. Under it, the rounds of this seed.
 */
public final class FairView implements GameView {
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final double ROW_H = 13;

    private final GambleScreen s;
    private GlassField client;
    private String copied; private long copiedAt;
    private int builtVer = -1;

    public FairView(GambleScreen s) { this.s = s; }

    private double lx() { return s.x0 + GambleScreen.PAD; }
    private double rx() { return s.x0 + GambleScreen.W - GambleScreen.PAD; }
    private double y() { return s.y0 + 80; }
    private Widget.Clip listClip() { double top = y() + 146; return new Widget.Clip(lx(), top, rx() - lx(), s.y0 + GambleScreen.H - GambleScreen.PAD - top); }

    private void copy(String what, String text) { Fair.copy(text); copied = what; copiedAt = P.now(); P.play("click"); }

    @Override
    public void init() {
        double lx = lx(), rx = rx(), y = y();
        builtVer = Fair.ver();
        s.add(new GlassButton(lx, y, 18, 16, "‹", "GHOST", s::back));
        double cw = F.width("Copy for chat") + 16;
        s.add(new GlassButton(rx - 8 - cw, y + 31, cw, 16, "Copy for chat", "PRIMARY", () -> copy("hash", Fair.commitText())));
        boolean locked = Fair.rounds() > 0;
        if (!locked) {
            if (client == null) { client = new GlassField(0, 0, 130, 16, Fair.client(), "A word from a viewer", 32); client.allowed = "[A-Za-z0-9_-]"; client.focused = false; }
            client.value = Fair.client();
            client.x = lx + 8; client.y = y + 97;
            client.onChange = () -> Fair.setClient(client.value);
            s.add(client);
            s.add(new GlassButton(lx + 8 + 134, y + 97, 34, 16, "New", "GHOST", () -> { Fair.randomClient(); client.value = Fair.client(); P.play("click"); }));
        }
        double rw = F.width("Reveal seed") + 16;
        s.add(new GlassButton(rx - 8 - rw, y + 97, rw, 16, "Reveal seed", "DANGER", () -> { Fair.Spent sp = Fair.reveal(); copy("reveal", Fair.revealText(sp)); s.rebuild(); }));
        if (Fair.lastSpent() != null) {
            double lw = F.width("Copy last reveal") + 12;
            s.add(new GlassButton(rx - lw, y + 128, lw, 14, "Copy last reveal", "GHOST", () -> copy("last", Fair.revealText(Fair.lastSpent()))));
        }
    }

    @Override public void tick() { if (Fair.ver() != builtVer) s.rebuild(); }
    @Override public void leave() { if (client != null) client.focused = false; }

    @Override
    public boolean key(Key e) {
        if (client != null && client.focused && Fair.rounds() == 0) { if (e.is("Enter") || e.is("Escape")) { client.focused = false; return true; } return client.type(e); }
        return false;
    }

    @Override
    public void clicked(Widget hit) { if (client != null) client.focused = hit == client; }

    @Override
    public void render(G g, double mx, double my) {
        double lx = lx(), rx = rx(), y = y();
        g.drawText("Provably fair", lx + 24, y + 4, WHITE, true);
        pillsRight(g, rx, y, new Object[][]{{"Rounds", String.valueOf(Fair.rounds())}});
        boolean flash = copied != null && P.now() - copiedAt < 1600;
        // the commitment
        card(g, lx, y + 22, rx - lx, 52, "soft");
        g.drawText("Seed hash", lx + 8, y + 29, MUTE, false);
        String h = Fair.hash();
        g.drawText(h.substring(0, 16) + "…" + h.substring(h.length() - 16), lx + 8, y + 41, WHITE, true);
        g.drawText(flash && copied.equals("hash") ? "Copied, paste it in chat" : "Paste it in chat before the rolls: the seed can't change after.", lx + 8, y + 57, flash && copied.equals("hash") ? GREEN : DIM, false);
        // the client seed, then the reveal
        card(g, lx, y + 80, rx - lx, 42, "soft");
        g.drawText("Client seed", lx + 8, y + 86, MUTE, false);
        if (Fair.rounds() > 0) {
            g.drawText(Fair.client(), lx + 8, y + 101, TEXT, true);
            g.drawText("Reveal to change it", lx + 14 + F.width(Fair.client()), y + 101, DIM, false);
        }
        String cap = flash && (copied.equals("reveal") || copied.equals("last")) ? "Copied, paste it in chat. A new seed has started."
                : "Reveal between rounds: copies the seed, then starts a new one.";
        g.drawText(cap, lx + 2, y + 128 + 3, flash && !copied.equals("hash") ? GREEN : DIM, false);
        // the rounds of this seed, newest first
        List<Fair.Draw> ds = Fair.draws();
        Widget.Clip clip = listClip();
        if (ds.isEmpty()) {
            String a = "No round yet with this seed";
            g.drawText(a, lx + (rx - lx - F.width(a)) / 2, clip.y() + 14, DIM, false);
            return;
        }
        double hh = ds.size() * ROW_H, off = s.beginList(g, "fair", clip.x(), clip.y(), clip.w(), clip.h(), hh);
        for (int i = 0; i < ds.size(); i++) {
            Fair.Draw d = ds.get(ds.size() - 1 - i);
            double ry = clip.y() + i * ROW_H - off;
            if (ry + ROW_H < clip.y() || ry > clip.y() + clip.h()) continue;
            g.drawText("#" + d.nonce(), lx + 4, ry + 2, DIM, false);
            g.drawText(d.game(), lx + 34, ry + 2, TEXT, false);
            g.drawText(F.trimToWidth(d.result(), rx - lx - 190), lx + 120, ry + 2, WHITE, false);
            String tm = HM.format(Instant.ofEpochMilli(d.t()));
            g.drawText(tm, rx - 4 - F.width(tm), ry + 2, DIM, false);
        }
        s.endList(g, "fair", hh);
    }
}
