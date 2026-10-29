package dev.ryaan.ezgamble.duel;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.games.blackjack.BlackjackGame;
import dev.ryaan.ezgamble.games.roulette.RouletteTable;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.ui.GambleScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.ryaan.ezgamble.games.blackjack.BlackjackTable.BJ;
import static dev.ryaan.ezgamble.games.coinflip.CoinTable.CF;
import static dev.ryaan.ezgamble.games.coinflip.CoinTable.cfName;
import static dev.ryaan.ezgamble.games.crash.CrashTable.CX;
import static dev.ryaan.ezgamble.games.crash.CrashTable.crashText;
import static dev.ryaan.ezgamble.games.crash.CrashTable.parseAuto;
import static dev.ryaan.ezgamble.games.crates.CrateTable.CR;
import static dev.ryaan.ezgamble.games.mines.MinesTable.MN;
import static dev.ryaan.ezgamble.games.mines.MinesTable.parseTile;
import static dev.ryaan.ezgamble.games.mines.MinesTable.tileName;
import static dev.ryaan.ezgamble.games.roulette.RouletteTable.RL;
import static dev.ryaan.ezgamble.proto.P.*;

/**
 * While a game is open (Blackjack, Crates, Mines, Crash, Roulette, Coinflip), every whisper lands in a panel beside the
 * menu. When the player you are playing for whispers a command, it is played for him: Blackjack hit / stand / double /
 * split / deal / again, Crates open / again... Anyone else is shown, never obeyed. A command that comes while the cards
 * are still moving waits for them (4 s at most). Nothing is ever answered in chat.
 */
public final class TableChat {
    private TableChat() {}

    static final Map<String, String> CHAT_CMDS = Map.ofEntries(
            Map.entry("hit", "hit"), Map.entry("h", "hit"), Map.entry("hit me", "hit"), Map.entry("card", "hit"),
            Map.entry("stand", "stand"), Map.entry("s", "stand"), Map.entry("stay", "stand"), Map.entry("stop", "stand"),
            Map.entry("double", "double"), Map.entry("dd", "double"), Map.entry("double down", "double"), Map.entry("split", "split"),
            Map.entry("deal", "deal"), Map.entry("go", "deal"), Map.entry("again", "again"), Map.entry("rebet", "again"), Map.entry("run it back", "again"),
            Map.entry("open", "open"), Map.entry("spin", "open"), Map.entry("open it", "open"), Map.entry("start", "open"), Map.entry("launch", "open"),
            Map.entry("cash", "cash"), Map.entry("cashout", "cash"), Map.entry("cash out", "cash"), Map.entry("cash it", "cash"),
            Map.entry("random", "random"), Map.entry("r", "random"), Map.entry("any", "random"), Map.entry("rand", "random"));
    static final Map<String, String> CMD_LABEL = Map.of("hit", "Hit", "stand", "Stand", "double", "Double", "split", "Split", "deal", "Deal",
            "again", "Again", "open", "Open", "cash", "Cash out", "random", "Random");

    private static final Pattern CF_RE = Pattern.compile("^(heads|head|h|tails|tail|t|pile|face)(?:\\s+\\$?([\\d.,]+\\s*[kmb]?))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RL_RE = Pattern.compile("^(.+?)(?:\\s+\\$?([\\d.,]+\\s*[kmb]?))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTO_RE = Pattern.compile("^(auto|x?\\d)");

    /** What the mod did with a message. */
    static final class Act {
        final String label; final boolean ok, pending; final long at;
        Act(String label, boolean ok, boolean pending, long at) { this.label = label; this.ok = ok; this.pending = pending; this.at = at; }
        static Act of(String label, boolean ok) { return new Act(label, ok, false, 0); }
        static Act at(String label, boolean ok) { return new Act(label, ok, false, now()); }
    }

    static final class Msg {
        final String name, text; final long at; Act act;
        Msg(String name, String text) { this.name = name; this.text = text; this.at = now(); }
    }

    private record Waiting(Msg m, String cmd, long until) {}

    public static final List<Msg> msgs = new ArrayList<>();
    private static Waiting waiting;

    /** The game the gambler has on screen right now, or null. */
    static String game() {
        GambleScreen s = GambleScreen.current();
        return s != null && GambleScreen.tab.equals("games") && GambleScreen.sub != null && GambleScreen.GAME_SUBS.contains(GambleScreen.sub) ? GambleScreen.sub : null;
    }

    private static Host hostOf(String g) {
        return switch (g) { case "blackjack" -> BJ; case "mines" -> MN; case "crash" -> CX; case "roulette" -> RL; case "coinflip" -> CF; default -> CR; };
    }

    private static boolean same(String a, String b) { return a != null && b != null && Payouts.same(a, b); }

    public static boolean onChat(String line) {
        Matcher w = Duel.WHISPER_RE.matcher(line);
        if (!w.find()) return false;
        String game = game();
        if (game == null) return false;   // no game open: an ordinary whisper
        Msg m = new Msg(w.group(1), w.group(2).trim());
        msgs.add(m); if (msgs.size() > 30) msgs.remove(0);
        play("ui_tick_close", 1.4, 0.5);
        String key = m.text.toLowerCase(Locale.ROOT).replaceAll("[!?]+", "").replaceAll("[.,]+$", "").replaceAll("\\s+", " ").trim();   // keep the dot inside "x2.5" or "2.5m"
        String cmd = null;
        if (game.equals("coinflip")) {
            Matcher cfb = CF_RE.matcher(key);
            if (cfb.find()) cmd = "side:" + (cfb.group(1).matches("(?i)h|head|heads|face") ? "heads" : "tails") + ":" + (cfb.group(2) != null ? Money.parse(cfb.group(2).replaceAll("\\s", "")) : 0);
            else if (key.equals("flip")) cmd = "open";
        }
        if (cmd == null && game.equals("roulette")) {
            Matcher rlb = RL_RE.matcher(key);
            String rlKey = null; String amt = null;
            if (rlb.find()) { rlKey = RouletteTable.parseBet(rlb.group(1)); amt = rlb.group(2); }
            if (rlKey == null) { rlKey = RouletteTable.parseBet(key); amt = null; }   // "1st 12": the whole line is the bet (the split above cut it at "1st")
            if (rlKey != null) cmd = "bet:" + rlKey + ":" + (amt != null ? Money.parse(amt.replaceAll("\\s", "")) : 0);
            else if (key.equals("clear")) cmd = "clear";
        }
        if (cmd == null && game.equals("crash")) {
            Double a = parseAuto(key);
            if (a != null && AUTO_RE.matcher(key).find()) cmd = "auto:" + a;
        }
        if (cmd == null) cmd = CHAT_CMDS.get(key);
        if (cmd == null && game.equals("mines") && parseTile(key) >= 0) cmd = "tile:" + parseTile(key);   // Mines: "b3", "3b" or 1-25
        if (cmd != null) run(m, cmd, true);
        return true;
    }

    private static String num(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    /** Plays the command for its sender if he is the player at the table. Sets m.act. */
    static void run(Msg m, String cmd, boolean first) {
        String g = game();
        if (g == null) { m.act = Act.of("Table closed", false); return; }
        Host host = hostOf(g);
        if (g.equals("coinflip") && same(CF.backer, m.name)) {   // "heads", "tails 2m": picks the side (and the bet) and flips; "flip", "again"
            boolean ok = false; String label = "";
            if (cmd.startsWith("side:")) {
                String[] p = cmd.split(":"); String side = p[1]; long a = Long.parseLong(p[2]);
                if (CF.stage.equals("spin")) { m.act = Act.of("In the air", false); return; }
                if (CF.stage.equals("done")) { CF.settle(); CF.stage = "bet"; }
                CF.side = side; if (a > 0) { CF.bet = a; CF.betChips.clear(); }
                ok = CF.bet > 0 && CF.start(); label = cfName(side) + (ok ? " " + Money.format(CF.round.bet) : "");
                if (!ok) { m.act = Act.of(CF.warn != null && now() < CF.warn.until() ? CF.warn.text() : cfName(side) + ", no bet", false); return; }
            } else if (cmd.equals("open") || cmd.equals("deal")) { ok = CF.stage.equals("bet") && CF.start(); label = "Flip"; }
            else if (cmd.equals("again")) { ok = CF.stage.equals("done") && CF.again(); label = "Flip again"; }
            m.act = ok ? Act.at(label, true) : Act.of("Not now", false);
            return;
        }
        if (g.equals("roulette") && same(RL.backer, m.name)) {   // "red", "17 500k", "1st 12 2m": a bet (his amount, else the composed one); "spin", "clear"
            boolean ok = false; String label = CMD_LABEL.getOrDefault(cmd, "");
            if (cmd.startsWith("bet:")) {
                String[] p = cmd.split(":"); String key = p[1]; long a = Long.parseLong(p[2]); if (a == 0) a = RL.bet;
                if (RL.stage.equals("done")) { RL.settle(); RL.stage = "bet"; }
                ok = RL.stage.equals("bet") && a > 0 && RL.place(key, a); label = RouletteTable.BETS.get(key).label() + " " + Money.format(a);
                if (!ok) { m.act = Act.of(RL.warn != null && now() < RL.warn.until() ? RL.warn.text() : "Not now", false); return; }
            } else if (cmd.equals("open") || cmd.equals("deal")) { ok = RL.stage.equals("bet") && RL.spin(); label = "Spin"; }
            else if (cmd.equals("again")) { ok = RL.again(); label = "Spin again"; }
            else if (cmd.equals("clear")) { ok = RL.stage.equals("bet") && RL.placed() > 0; if (ok) RL.clearTable(); label = "Cleared"; }
            m.act = ok ? Act.at(label, true) : Act.of("Not now", false);
            return;
        }
        if (g.equals("crash") && same(CX.backer, m.name)) {   // "cash" is paid at the multiplier of the moment it ARRIVES
            boolean ok = false; String label = CMD_LABEL.get(cmd);
            if (cmd.startsWith("auto:")) { double a = Double.parseDouble(cmd.substring(5)); ok = !CX.stage.equals("play"); if (ok) { CX.auto = a; label = a != 0 ? "Auto x" + num(a) : "Auto off"; } }
            else if (cmd.equals("cash") || cmd.equals("stand")) {
                label = "Cash out"; ok = CX.cashout();
                if (ok) label = "Cashed " + crashText(CX.round.cashX);
                else if (CX.round != null && CX.round.crashed && CX.round.cashX == 0) { m.act = Act.of("Too late", false); return; }
            }
            else if (cmd.equals("open") || cmd.equals("again") || cmd.equals("deal")) { ok = CX.stage.equals("done") ? CX.again() : CX.stage.equals("bet") && CX.bet > 0 && CX.start(); label = "Launch"; }
            m.act = ok ? Act.at(label, true) : Act.of("Not now", false);
            return;
        }
        if (g.equals("mines") && same(host.backer, m.name)) {
            boolean ok = false; String label = CMD_LABEL.get(cmd);
            if (cmd.startsWith("tile:")) {
                int i = Integer.parseInt(cmd.substring(5)); label = tileName(i); ok = MN.stage.equals("play") && MN.pick(i);
                if (!ok && MN.stage.equals("play")) { m.act = Act.of("Already open", false); return; }
            }
            else if (cmd.equals("random")) { ok = MN.stage.equals("play") && MN.pickRandom(); if (ok) label = tileName(MN.round.picked.get(MN.round.picked.size() - 1)); }
            else if (cmd.equals("cash")) ok = MN.cashout();
            else if (cmd.equals("open") || cmd.equals("again") || cmd.equals("deal")) ok = MN.stage.equals("done") ? MN.again() : MN.stage.equals("bet") && MN.bet > 0 && MN.start();
            if (ok && MN.round != null && MN.round.boom >= 0 && MN.stage.equals("done") && !"Cash out".equals(label)) { m.act = Act.at(label + " Mine", false); return; }
            m.act = ok ? Act.at(label, true) : Act.of("Not now", false);
            return;
        }
        if (host.backer == null || !same(host.backer, m.name)) { m.act = Act.of("Not playing", false); return; }
        boolean ok = false, wait = false;
        if (g.equals("blackjack")) {
            BlackjackGame gm = BJ.game; boolean done = gm != null && gm.phase.equals("done");
            if (cmd.equals("hit") || cmd.equals("stand") || cmd.equals("double") || cmd.equals("split")) {
                if (BJ.stage.equals("play") && gm != null && !done && gm.can(cmd) && BJ.locked()) wait = true;   // the cards are still moving
                else ok = BJ.stage.equals("play") && BJ.act(cmd);
            } else if (cmd.equals("deal")) ok = BJ.stage.equals("bet") && BJ.bet > 0 && BJ.deal();
            else if (cmd.equals("again")) ok = done && BJ.resultAt != 0 && now() >= BJ.resultAt && BJ.rebet();
        } else if (cmd.equals("open") || cmd.equals("again") || cmd.equals("deal")) {
            ok = CR.stage.equals("done") ? CR.again() : CR.stage.equals("bet") && CR.bet > 0 && CR.open();
            if (CR.stage.equals("spin") && !ok) m.act = Act.of("Spinning", false);
        }
        if (wait && first) { waiting = new Waiting(m, cmd, now() + 4000); m.act = new Act(CMD_LABEL.get(cmd) + "...", true, true, 0); return; }
        if (m.act == null || m.act.label == null || m.act.pending) m.act = ok ? Act.of(CMD_LABEL.get(cmd), true) : Act.of("Not now", false);
        if (ok) m.act = new Act(m.act.label, true, false, now());
    }

    public static void tick() {
        Waiting w = waiting; if (w == null) return;
        if (now() > w.until()) { w.m().act = Act.of("Not now", false); waiting = null; return; }
        if (!BJ.locked()) { waiting = null; run(w.m(), w.cmd(), false); }
    }

    /** The panel: right of the menu (left if there is no room), the newest message at the bottom. */
    public static void render(G g, GambleScreen menu, double k) {
        String game = game();
        if (game == null || msgs.isEmpty()) return;
        int W = GambleScreen.W, H = GambleScreen.H; double gap = 8; long t = now();
        double right = menu.SW - 4 - (menu.x0 + W + gap), left = menu.x0 - gap - 4, room = Math.max(right, left);
        if (room < 96) return;   // a screen too narrow for it: the whispers stay in the chat
        double PW = Math.min(150, room), x = right >= left ? menu.x0 + W + gap : menu.x0 - gap - PW;
        double y = menu.y0;
        Host host = hostOf(game);
        g.push(); g.translate(0, (1 - k) * 8);
        glass(g, x, y, PW, H, 12, 0.84);
        g.drawText("Messages", x + 10, y + 10, WHITE, true);
        double top = y + 26, bottom = y + H - 8, rowH = 30;
        List<Msg> rows = new ArrayList<>();
        for (Msg m : msgs) if (t - m.at < 10 * 60000) rows.add(m);
        double ry = bottom - rowH;
        g.enableScissor(x, top, x + PW, bottom);
        for (int i = rows.size() - 1; i >= 0 && ry + rowH > top; i--, ry -= rowH) {
            Msg m = rows.get(i);
            double inK = easeOut(Math.min(1, (t - m.at) / 220.0));
            boolean mine = host.backer != null && same(host.backer, m.name);
            g.push(); g.translate((1 - inK) * 16, 0);
            if (mine) smooth(g, x + 4, ry, PW - 8, rowH - 3, 6, 0x14A18CD1, 0x33A18CD1);
            g.drawHead(skinOf(m.name), x + 9, ry + 4, 8);
            String nm = F.trimToWidth(m.name, PW - 30);
            if (mine) gradientText(g, nm, x + 21, ry + 4, false); else g.drawText(nm, x + 21, ry + 4, TEXT, false);
            Act a = m.act; double aw = a != null ? F.width(a.label) + 10 : 0;
            g.drawText(F.trimToWidth(m.text, PW - 22 - (a != null ? aw + 4 : 0)), x + 9, ry + 16, WHITE, false);
            if (a != null) {   // what the mod did with it
                double ax = x + PW - 8 - aw, pulse = a.ok && a.at != 0 && t - a.at < 600 ? 1 - (t - a.at) / 600.0 : 0;
                capsule(g, ax, ry + 13, aw, 13, a.ok && !a.pending ? "selected" : "ghost", false);
                if (pulse > 0) ring(g, ax, ry + 13, aw, 13, 6.5, alpha(GREEN, pulse), 1);
                g.drawText(a.label, ax + 5, ry + 16, a.ok ? (a.pending ? TEXT : GREEN) : DIM, false);
            }
            g.pop();
        }
        g.disableScissor();
        g.pop();
    }
}
