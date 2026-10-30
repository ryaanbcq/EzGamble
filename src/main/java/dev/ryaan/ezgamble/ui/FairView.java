package dev.ryaan.ezgamble.ui;

import dev.ryaan.ezgamble.data.Fair;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.proto.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * Games > Provably fair. Three steps along the top (share the hash, play, reveal) instead of sentences; the hash in
 * full in the accent card with Copy for chat; the client seed (editable while the seed has no round) next to Reveal;
 * the rounds of this seed listed like History, each with its game's icon and its outcome. Nothing is ever sent for
 * you: every button copies, you paste.
 */
public final class FairView implements GameView {
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final double ROW_H = 14;
    private static final Set<Integer> RED_N = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    /** The hash last copied for chat: step 1 is done for this seed. */
    private static String shared;

    private final GambleScreen s;
    private GlassField client;
    private GlassButton copyBtn;
    private String copied; private long copiedAt;
    private int builtVer = -1;

    public FairView(GambleScreen s) { this.s = s; }

    private double lx() { return s.x0 + GambleScreen.PAD; }
    private double rx() { return s.x0 + GambleScreen.W - GambleScreen.PAD; }
    private double y() { return s.y0 + 80; }
    private double splitX() { return lx() + 216; }
    private Widget.Clip listClip() { double top = y() + 150; return new Widget.Clip(lx(), top, rx() - lx(), s.y0 + GambleScreen.H - GambleScreen.PAD - top); }
    private boolean flash(String what) { return what.equals(copied) && P.now() - copiedAt < 1800; }

    private void copy(String what, String text) { Fair.copy(text); copied = what; copiedAt = P.now(); P.play("click"); }

    @Override
    public void init() {
        double lx = lx(), rx = rx(), y = y(), sx = splitX();
        builtVer = Fair.ver();
        s.add(new GlassButton(lx, y, 18, 16, "‹", "GHOST", s::back));
        if (Fair.lastSpent() != null) {   // left of the Rounds pill
            String r = String.valueOf(Fair.rounds());
            double lw = F.width("Last reveal") + 14, px = rx - pillW("Rounds", r) - 6 - lw;
            s.add(new GlassButton(px, y, lw, 15, "Last reveal", "GHOST", () -> copy("last", Fair.revealText(Fair.lastSpent()))));
        }
        double cw = F.width("Copy for chat") + 18;
        copyBtn = s.add(new GlassButton(rx - 10 - cw, y + 48, cw, 16, "Copy for chat", "PRIMARY", () -> { shared = Fair.hash(); copy("hash", Fair.commitText()); }));
        if (Fair.rounds() == 0) {
            if (client == null) { client = new GlassField(0, 0, 150, 16, Fair.client(), "A word from a viewer", 32); client.allowed = "[A-Za-z0-9_-]"; client.focused = false; }
            client.value = Fair.client();
            client.x = lx + 8; client.y = y + 121; client.w = sx - lx - 8 - 6 - 40 - 8;
            client.onChange = () -> Fair.setClient(client.value);
            s.add(client);
            s.add(new GlassButton(sx - 8 - 40, y + 121, 40, 16, "New", "GHOST", () -> { Fair.randomClient(); client.value = Fair.client(); P.play("click"); }));
        }
        s.add(new GlassButton(sx + 6 + 8, y + 121, rx - sx - 6 - 16, 16, "Reveal seed", "DANGER", () -> { Fair.Spent sp = Fair.reveal(); copy("reveal", Fair.revealText(sp)); s.rebuild(); }));
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
        double lx = lx(), rx = rx(), y = y(), sx = splitX();
        String hash = Fair.hash();
        boolean played = Fair.rounds() > 0, revealed = flash("reveal");
        g.drawText("Provably fair", lx + 24, y + 4, WHITE, true);
        pillsRight(g, rx, y, new Object[][]{{"Rounds", String.valueOf(Fair.rounds())}});

        // the three steps: what to do now, what is done
        int now = revealed ? 3 : played || hash.equals(shared) ? 2 : 1;
        String[] steps = {"Share the hash", "Play", "Reveal"};
        double px = lx, sy = y + 22;
        for (int i = 0; i < 3; i++) {
            boolean done = i + 1 < now || revealed, on = i + 1 == now && !revealed;
            String t = (done ? "✔ " : (i + 1) + " ") + steps[i];
            double w = F.width(t) + 16;
            if (on) { smooth(g, px, sy, w, 15, 7.5, h(0x3DA18CD1, 0x30FBC2EB), 0x80FBC2EB); }
            else smooth(g, px, sy, w, 15, 7.5, done ? 0x1A4BE07A : 0x0CFFFFFF, done ? 0x404BE07A : 0x14FFFFFF);
            g.drawText(t, px + 8, sy + 4, on ? WHITE : done ? GREEN : DIM, false);
            px += w;
            if (i < 2) { g.fill(px + 3, sy + 7, px + 13, sy + 8, done ? 0x804BE07A : 0x24FFFFFF); px += 16; }
        }

        // the commitment: the hash in full, two lines of 32, groups of 8 in two shades so it reads like a code
        card(g, lx, y + 44, rx - lx, 54, "accent");
        g.drawSprite("game/fair", lx + 10, y + 55, 30, 30);
        g.drawText("Seed hash", lx + 48, y + 50, MUTE, false);
        String state = played ? "locked in" : hash.equals(shared) ? "shared" : "not shared yet";
        g.drawText(state, lx + 48 + F.width("Seed hash") + 6, y + 50, played || hash.equals(shared) ? GREEN : DIM, false);
        for (int line = 0; line < 2; line++) {
            double tx = lx + 48;
            for (int k = 0; k < 4; k++) {
                String part = hash.substring(line * 32 + k * 8, line * 32 + k * 8 + 8);
                g.drawText(part, tx, y + 67 + line * 11, k % 2 == 0 ? WHITE : PINK, true);
                tx += F.width(part) + 3;
            }
        }
        if (copyBtn != null) copyBtn.label = flash("hash") ? "✔ Copied" : "Copy for chat";

        // the client seed, then the reveal
        card(g, lx, y + 104, sx - lx, 40, "soft");
        g.drawText("Client seed", lx + 8, y + 110, MUTE, false);
        if (played) {
            g.drawText(Fair.client(), lx + 8, y + 125, TEXT, true);
            String lk = "until the reveal";
            g.drawText(lk, sx - 8 - F.width(lk), y + 125, DIM, false);
        }
        card(g, sx + 6, y + 104, rx - sx - 6, 40, "soft");
        String rl = revealed ? "✔ Copied, new seed" : flash("last") ? "✔ Copied" : "Then a new seed";
        g.drawText(rl, sx + 14, y + 110, revealed || flash("last") ? GREEN : MUTE, false);

        // the rounds of this seed, newest first
        List<Fair.Draw> ds = Fair.draws();
        Widget.Clip clip = listClip();
        if (ds.isEmpty()) {
            String a = revealed ? "Paste the reveal in chat: they can check every round." : "Play a round: it lands here with its number.";
            g.drawText(a, lx + (rx - lx - F.width(a)) / 2, clip.y() + 20, DIM, false);
            return;
        }
        double hh = ds.size() * ROW_H, off = s.beginList(g, "fair", clip.x(), clip.y(), clip.w(), clip.h(), hh);
        for (int i = 0; i < ds.size(); i++) {
            Fair.Draw d = ds.get(ds.size() - 1 - i);
            double ry = clip.y() + i * ROW_H - off;
            if (ry + ROW_H < clip.y() || ry > clip.y() + clip.h()) continue;
            if (i % 2 == 0) smooth(g, lx, ry, rx - lx, ROW_H - 1, 4, 0x08FFFFFF);
            String game = d.game().split(" ")[0];
            Icons.drawGameIcon(g, game, lx + 4, ry + 1, 11);
            g.drawText("#" + d.nonce(), lx + 20, ry + 3, DIM, false);
            g.drawText(game, lx + 44, ry + 3, TEXT, false);
            String tm = HM.format(Instant.ofEpochMilli(d.t()));
            g.drawText(tm, rx - 4 - F.width(tm), ry + 3, DIM, false);
            outcome(g, d, rx - 4 - F.width(tm) - 8, ry);
        }
        s.endList(g, "fair", hh);
    }

    /** The outcome, right-aligned at x: a roulette number on its colour, a coin, a multiplier, the bombs. */
    private static void outcome(G g, Fair.Draw d, double right, double ry) {
        String r = d.result();
        switch (d.game().split(" ")[0]) {
            case "Roulette" -> {
                int n; try { n = Integer.parseInt(r); } catch (NumberFormatException e) { n = -1; }
                int c = n == 0 ? 0xFF2B9A57 : RED_N.contains(n) ? 0xFFC8344A : 0xFF2A2136;
                double w = Math.max(16, F.width(r) + 8);
                smooth(g, right - w, ry + 1, w, 11, 3, c, n > 0 && !RED_N.contains(n) ? 0x30FFFFFF : 0);
                g.drawText(r, right - w + (w - F.width(r)) / 2, ry + 3, WHITE, false);
            }
            case "Coinflip" -> {
                String t = r.equals("heads") ? "Heads" : "Tails";
                double tx = right - F.width(t);
                g.drawText(t, tx, ry + 3, WHITE, false);
                smooth(g, tx - 11, ry + 2, 8, 8, 4, r.equals("heads") ? 0xFFF2C14E : 0xFFC7CCD6);
            }
            case "Crash" -> {
                double x = 0; try { x = Double.parseDouble(r.substring(1)); } catch (Exception ignored) {}
                if (x >= 10) gradientText(g, r, right - F.width(r), ry + 3, false);
                else g.drawText(r, right - F.width(r), ry + 3, x < 1.5 ? RED : WHITE, false);
            }
            default -> { String t = F.trimToWidth(r, 140); g.drawText(t, right - F.width(t), ry + 3, WHITE, false); }
        }
    }
}
