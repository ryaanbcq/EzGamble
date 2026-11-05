package dev.ryaan.ezgamble.lobby;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.duel.Duel;
import dev.ryaan.ezgamble.games.Chips;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.ui.GambleScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.ryaan.ezgamble.games.blackjack.BlackjackTable.BJ;
import static dev.ryaan.ezgamble.games.coinflip.CoinTable.CF;
import static dev.ryaan.ezgamble.games.crash.CrashTable.CX;
import static dev.ryaan.ezgamble.games.crates.CrateTable.CR;
import static dev.ryaan.ezgamble.games.mines.MinesTable.MN;
import static dev.ryaan.ezgamble.games.roulette.RouletteTable.RL;

/**
 * The waiting line for the tables. A player whispers "join blackjack 5M" (queue / sit / play work too, "leave" steps
 * out) and takes a number; the host sees the line (Games > Lobby, the Next in line pill on every felt) and seats the
 * next player in one click, or the Auto-seat option does it 2.5 s after the round on the open felt ends. A whisper is
 * accepted only while gambling mode is on and his chips cover the amount (no amount = everything he has when he sits
 * down). A seat older than 10 min is dropped; a player whose chips fell under his amount stays, flagged short. The line
 * survives a restart (config/ezgamble/lobby.json). Nothing is ever sent in chat for him.
 */
public final class Lobby {
    private Lobby() {}

    /** game = the table's display name (Blackjack...), amount 0 = all his chips at seating time, at = his last whisper. */
    public record Seat(String name, String game, long amount, long at) {}

    static final Pattern JOIN_RE = Pattern.compile("^/?(?:join|queue|sit|play)\\s+([a-z]+)(?:\\s+\\$?\\s*([\\d.,]+\\s*[kmbKMB]?))?\\s*$", Pattern.CASE_INSENSITIVE);
    static final Pattern LEAVE_RE = Pattern.compile("^/?(?:leave|unqueue|unjoin)\\s*$", Pattern.CASE_INSENSITIVE);
    /** Duel.DUEL_GAMES (copied, it is package-private there) plus roulette. */
    static final Map<String, String> GAMES = Map.ofEntries(
            Map.entry("blackjack", "Blackjack"), Map.entry("bj", "Blackjack"), Map.entry("crates", "Crates"), Map.entry("crate", "Crates"),
            Map.entry("mines", "Mines"), Map.entry("mine", "Mines"), Map.entry("crash", "Crash"),
            Map.entry("coinflip", "Coinflip"), Map.entry("cf", "Coinflip"), Map.entry("flip", "Coinflip"),
            Map.entry("roulette", "Roulette"), Map.entry("rl", "Roulette"), Map.entry("wheel", "Roulette"));
    /** Each game's felt (GambleScreen.sub). */
    static final Map<String, String> SUB_OF = Map.of("Blackjack", "blackjack", "Crates", "crates", "Mines", "mines", "Crash", "crash", "Coinflip", "coinflip", "Roulette", "roulette");

    public static final long WAIT_MS = 600_000, AUTO_MS = 2500, JOIN_SLIDE_MS = 2500, ERR_MS = 2500;

    public static final List<Seat> queue = new ArrayList<>();
    private static int ver;
    /** The last player who joined and when (data only: the HUD shows the join as a Banners toast). */
    public static Seat lastJoin; public static long joinAt;
    /** Auto-seat: when the next player sits down and on which felt (0 = no countdown). */
    private static long autoAt; private static String autoSub;
    /** The felt's backer and bet when the countdown started: a change means the host is composing, nobody is seated over him. */
    private static String autoBacker; private static long autoBet;
    private static String lastSub, lastPhase;
    /** The last refused seating (the row and the pill say why for 2.5 s). */
    private static Seat errSeat; private static String errText; private static long errAt;

    // ---- persistence: the line itself, so a restart in the middle of an evening loses nobody

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ezgamble").resolve("lobby.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final class State { List<Seat> queue = new ArrayList<>(); }

    static {
        try {
            if (Files.exists(FILE)) {
                State st = GSON.fromJson(Files.readString(FILE), State.class);
                if (st != null && st.queue != null) for (Seat s : st.queue) if (s != null && s.name != null && s.game != null) queue.add(s);
            }
        } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] lobby load failed: {}", e.toString()); }
    }

    private static void save() {
        State st = new State(); st.queue.addAll(queue);
        try {   // like GambleData.write: a tmp file, then an atomic move
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(st), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] lobby write failed: {}", e.toString()); }
    }

    private static void changed() { ver++; save(); }

    // ---- the line

    public static int ver() { return ver; }
    public static int size() { return queue.size(); }
    public static List<Seat> seats() { return queue; }
    /** The display name of the game played on a felt, or null (rain, auction, the lists). */
    public static String gameOf(String sub) {
        if (sub == null) return null;
        for (var e : SUB_OF.entrySet()) if (e.getValue().equals(sub)) return e.getKey();
        return null;
    }

    /** How many wait for that game (the short ones too). */
    public static int pendingFor(String game) { int n = 0; for (Seat s : queue) if (s.game.equals(game)) n++; return n; }
    /** The next player who can sit at that game now (his chips cover his amount). */
    public static Seat next(String game) { for (Seat s : queue) if (s.game.equals(game) && !isShort(s)) return s; return null; }

    public static long chips(String name) {
        String nn = Payouts.norm(name);   // normalised once, not per row
        for (Payouts.Row r : Payouts.rows()) if (Payouts.norm(r.name()).equals(nn)) return r.bal();
        return 0;
    }

    private static Payouts.Row rowOf(String name) {
        String nn = Payouts.norm(name);
        for (Payouts.Row r : Payouts.rows()) if (Payouts.norm(r.name()).equals(nn)) return r;
        return null;
    }

    /** What he plays when seated: his amount, or all his chips when he named none. */
    public static long amountOf(Seat s) { return s.amount > 0 ? s.amount : chips(s.name); }
    /** His chips fell under his amount since he joined (or he has none left): he keeps his place, flagged. */
    public static boolean isShort(Seat s) { long c = chips(s.name); return s.amount > 0 ? c < s.amount : c <= 0; }

    public static void remove(Seat s) { if (queue.remove(s)) changed(); }

    /** The last refused seating, for 2.5 s: {seat, why}, or null. */
    public static Seat errSeat() { return errText != null && P.now() - errAt < ERR_MS ? errSeat : null; }
    public static String errText() { return errText != null && P.now() - errAt < ERR_MS ? errText : null; }

    /** A whisper line: a valid join or leave is taken and true is returned (the line still shows in chat as usual). */
    public static boolean onChat(String line) {
        Matcher w = Duel.WHISPER_RE.matcher(line);
        if (!w.find()) return false;
        String name = w.group(1), text = w.group(2).trim();
        if (LEAVE_RE.matcher(text).find()) {   // "leave": out of the line, only ours to read when he was in it
            if (!queue.removeIf(s -> Payouts.same(s.name, name))) return false;
            changed(); P.play("ui_tick_close", 0.9, 0.5);
            return true;
        }
        Matcher m = JOIN_RE.matcher(text);
        if (!m.find()) return false;
        String game = GAMES.get(m.group(1).toLowerCase());
        if (game == null) return false;   // "play with me": not a table, somebody else's line
        long amount = m.group(2) != null ? Money.parse(m.group(2).replaceAll("\\s", "")) : 0, c = chips(name);
        if (!GambleData.mode || amount < 0 || (amount > 0 ? c < amount : c <= 0)) return true;   // not a seat we can give: read, ignored
        int i = -1;
        for (int k = 0; k < queue.size(); k++) if (Payouts.same(queue.get(k).name, name)) i = k;
        Seat s = new Seat(i >= 0 ? queue.get(i).name : name, game, amount, P.now());   // his new word replaces the old one, same place; the 10 min restart from it
        if (i >= 0) queue.set(i, s); else queue.add(s);
        lastJoin = s; joinAt = P.now();
        final String nm = s.name, gm = s.game;   // the HUD toast: his gem, his name, the game's icon and name, in the one top-centre slot
        dev.ryaan.ezgamble.hud.Banners.show(new dev.ryaan.ezgamble.hud.Banners.Toast(dev.ryaan.ezgamble.vip.VipBadge.w(nm) > 0 ? (g, x, y) -> dev.ryaan.ezgamble.vip.VipBadge.draw(g, nm, x, y - 1) : null, java.util.List.of(dev.ryaan.ezgamble.hud.Banners.seg(nm, P.WHITE), dev.ryaan.ezgamble.hud.Banners.seg(" joined the line for ", P.MUTE), dev.ryaan.ezgamble.hud.Banners.icon((g, x, y) -> dev.ryaan.ezgamble.ui.Icons.drawGameIcon(g, gm, x, y - 2, 11), 14), dev.ryaan.ezgamble.hud.Banners.seg(gm, P.PINK)), P.LAV, 2500, null));
        changed();
        P.play("ui_tick_open", 1.2, 0.6);
        return true;
    }

    // ---- auto-seat: the round on the open felt ends, the next in line for that game sits down 2.5 s later

    /** "live" while a round runs on that felt, "done" once it has ended, "bet" otherwise. */
    static String phase(String sub) {
        return switch (sub) {
            case "blackjack" -> BJ.game == null ? "bet" : BJ.game.phase.equals("done") ? "done" : BJ.game.phase.equals("player") ? "live" : "bet";
            case "coinflip" -> CF.stage.equals("spin") ? "live" : CF.stage;
            case "crash" -> CX.stage.equals("play") ? "live" : CX.stage;
            case "mines" -> MN.stage.equals("play") ? "live" : MN.stage;
            case "crates" -> CR.stage.equals("spin") ? "live" : CR.stage;
            case "roulette" -> RL.stage.equals("spin") ? "live" : RL.stage;
            default -> "bet";
        };
    }

    /** The table played on a felt (gameOf(sub) != null). */
    static Host hostOf(String sub) {
        return switch (sub) {
            case "coinflip" -> CF; case "crash" -> CX; case "mines" -> MN; case "crates" -> CR; case "roulette" -> RL;
            default -> BJ;
        };
    }

    /** The host touched the felt since the countdown started (picked a backer, clicked chips). */
    private static boolean touched(String sub) {
        Host h = hostOf(sub);
        return !java.util.Objects.equals(h.backer, autoBacker) || h.bet != autoBet;
    }

    /** When the countdown on that felt ends (0 = none running). */
    public static long autoEnd(String sub) { return autoAt > 0 && sub != null && sub.equals(autoSub) ? autoAt : 0; }

    public static void tick() {
        long t = P.now();
        if (queue.removeIf(s -> t - s.at >= WAIT_MS)) changed();   // nobody waits more than 10 min
        GambleScreen sc = GambleScreen.current();
        String sub = sc != null && GambleScreen.tab.equals("games") && gameOf(GambleScreen.sub) != null ? GambleScreen.sub : null;
        String ph = sub == null ? null : phase(sub);
        if (sub != null && sub.equals(lastSub) && "live".equals(lastPhase) && "done".equals(ph) && GambleData.autoSeat && next(gameOf(sub)) != null) {
            autoAt = t + AUTO_MS; autoSub = sub; Host h = hostOf(sub); autoBacker = h.backer; autoBet = h.bet;
        }
        lastSub = sub; lastPhase = ph;
        if (autoAt > 0) {
            // the host left the felt, turned the option off, dealt again himself or is composing the next bet (another backer,
            // chips clicked): nobody is seated behind his back
            if (sub == null || !sub.equals(autoSub) || !GambleData.autoSeat || "live".equals(ph) || touched(sub)) autoAt = 0;
            else if (t >= autoAt) { autoAt = 0; Seat s = next(gameOf(sub)); if (s != null) seat(s); }
        }
    }

    // ---- seating (the few lines of Duel.accept: backer, bet built as chips, the felt, the round)

    private static String fail(Seat s, String why) { errSeat = s; errText = why; errAt = P.now(); P.play("fail", 1.3, 0.4); return why; }
    /** The table refused the round (house guard, max bet...): it already shows its red warning and played the sound; the row and the pill say it too, he keeps his place. */
    private static String refused(Seat s, Host h) { errSeat = s; errText = h.warn != null && dev.ryaan.ezgamble.proto.F.width(h.warn.text()) <= 142 ? h.warn.text() : "Seat refused"; errAt = P.now(); return errText; }   // the pill holds 142 px; the table's own button shows the full reason

    private static void seed(Host h, Payouts.Row row, long amount) {
        h.backer = row.name(); h.clearBet();
        h.bet = amount;
        for (Chips.Stack st : Chips.chipsFor(amount).stacks()) for (int i = 0; i < st.n(); i++) h.betChips.add(st.chip().v());
    }

    private static void open(String sub) {
        GambleScreen.tab = "games"; GambleScreen.sub = sub;
        GambleScreen cur = GambleScreen.current();
        if (cur != null) cur.rebuild(); else MinecraftClient.getInstance().setScreen(new GambleScreen());   // the menu is open (the list, a felt): the view changes in place
    }

    private static void take(Seat s) { queue.remove(s); changed(); }

    /**
     * Seats him: his chips are the bet, his felt opens, the round starts. null = done, otherwise why not. He leaves the line
     * only once the table took the round: a refusal (house guard, max bet) keeps his place and shows why.
     */
    public static String seat(Seat d) {
        if (d == null) return "Nobody in line";
        autoAt = 0;   // a seating, by hand or by the countdown, ends the countdown
        if (!GambleData.mode) return fail(d, "Gambling mode is off");
        long amount = amountOf(d);
        if (amount <= 0 || chips(d.name) < amount) return fail(d, "Not enough chips");
        Payouts.Row row = rowOf(d.name);
        String sub = SUB_OF.get(d.game);
        switch (d.game) {
            case "Coinflip" -> {   // his last side (heads the first time), flipped at once
                if (CF.stage.equals("spin")) return fail(d, "Coin in the air, wait");
                CF.settle(); CF.stage = "bet"; seed(CF, row, amount);
                open(sub);
                if (!CF.start()) return refused(d, CF);
                take(d);
            }
            case "Crash" -> {   // his last auto cash out (x2 the first time), launched at once
                if (CX.stage.equals("play")) return fail(d, "Flight in play, wait");
                CX.settle(); CX.stage = "bet"; seed(CX, row, amount);
                open(sub);
                if (!CX.start()) return refused(d, CX);
                take(d);
            }
            case "Mines" -> {   // the mines count he last played (3 the first time), board drawn at once
                if (MN.stage.equals("play")) return fail(d, "Board in play, wait");
                MN.settle(); MN.stage = "bet"; seed(MN, row, amount);
                open(sub);
                if (!MN.start()) return refused(d, MN);
                take(d);
            }
            case "Crates" -> {   // the crate he last opened (Basic the first time), opened at once
                if (CR.stage.equals("spin")) return fail(d, "Crate opening, wait");
                CR.settle(); CR.stage = "bet"; seed(CR, row, amount);
                open(sub);
                if (!CR.open()) return refused(d, CR);
                take(d);
            }
            case "Roulette" -> {   // a wheel has no single bet: he sits with his amount composed, the spin comes with his cell (a whisper "red", or the host's click)
                if (RL.stage.equals("spin")) return fail(d, "Wheel spinning, wait");
                RL.settle(); RL.stage = "bet"; RL.bets.clear(); RL.order.clear(); seed(RL, row, amount); Host.lastBacker = row.name();
                open(sub);
                take(d);
            }
            default -> {
                if (BJ.game != null && !BJ.game.phase.equals("done")) return fail(d, "Hand in play, wait");
                BJ.apply(); BJ.stage = "bet"; seed(BJ, row, amount);
                open(sub);
                if (!BJ.deal()) return refused(d, BJ);   // straight into the hand
                take(d);
            }
        }
        P.play("mc_chime", 1.2, 0.8);
        return null;
    }

    /** One line per seat, for the log. */
    public static String describe() {
        StringBuilder b = new StringBuilder("lobby " + queue.size() + (GambleData.autoSeat ? " auto-seat on" : ""));
        for (int i = 0; i < queue.size(); i++) {
            Seat s = queue.get(i);
            b.append(" | #").append(i + 1).append(' ').append(s.name).append(' ').append(s.game).append(' ').append(s.amount > 0 ? Money.format(s.amount) : "all")
             .append(" waited ").append((P.now() - s.at) / 1000).append("s").append(isShort(s) ? " SHORT" : "");
        }
        return b.toString();
    }
}
