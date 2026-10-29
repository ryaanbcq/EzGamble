package dev.ryaan.ezgamble.duel;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.games.Chips;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.proto.Items2;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.ui.GambleScreen;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.ryaan.ezgamble.games.blackjack.BlackjackTable.BJ;
import static dev.ryaan.ezgamble.games.coinflip.CoinTable.CF;
import static dev.ryaan.ezgamble.games.crash.CrashTable.CX;
import static dev.ryaan.ezgamble.games.crates.CrateTable.CR;
import static dev.ryaan.ezgamble.games.mines.MinesTable.MN;

/**
 * A backer whispers "duel blackjack 10M": if gambling mode is on and his chips cover the amount, an iron sword pops
 * like a totem of undying (the game's own floating item animation and sound, like the PvP Club challenges), then the
 * world blurs and a menu asks: Accept (the table opens, his hand is dealt at once) or Decline. 20 s to answer.
 * In combat (hit or got hit in the last 10 s) nothing pops: the challenge waits, and is dropped after 60 s. It also
 * waits while another screen is open (chat, inventory...). Nothing is ever sent in chat for him.
 */
public final class Duel {
    private Duel() {}

    /** DonutSMP EN / FR (EzChat's Parser.IN). */
    // DonutSMP translates whispers for each client: "X whispers to you: hi", "X vous chuchote : hi", "X te chuchote : hi", "X flüstert dir: hi"...
    // a name, then words that carry a stem of "whisper", then a colon; "You whisper to X" / "Tu chuchotes à X" (sent) never match
    public static final Pattern WHISPER_RE = Pattern.compile("^(?!(?:you|vous|tu|du|sie|usted|voce|você|jij|ty|vy|sen)\\b)(\\.?[A-Za-z0-9_]{3,16}) (?:[\\p{L}'\u2019]+ ){0,4}?[\\p{L}'\u2019]*(?:whisper|chuchot|flüster|fluster|fluester|susurr|sussurr|fluister|szep|шеп|fısıld|fisild|hvisk|visk|kuisk|suttog|šep)[\\p{L}'\u2019]*(?: [\\p{L}'\u2019]+){0,4}\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    static final Pattern DUEL_RE = Pattern.compile("^/?duel\\s+([a-z]+)(?:\\s+\\$?\\s*([\\d.,]+\\s*[kmbKMB]?))?\\s*$", Pattern.CASE_INSENSITIVE);
    /** Games that can be played on a challenge (the others are "soon"). */
    static final Map<String, String> DUEL_GAMES = Map.ofEntries(
            Map.entry("blackjack", "Blackjack"), Map.entry("bj", "Blackjack"), Map.entry("crates", "Crates"), Map.entry("crate", "Crates"),
            Map.entry("mines", "Mines"), Map.entry("mine", "Mines"), Map.entry("crash", "Crash"),
            Map.entry("coinflip", "Coinflip"), Map.entry("cf", "Coinflip"), Map.entry("flip", "Coinflip"));

    /** What announces a challenge (item + its vanilla sounds {name, pitch, volume}): a duel is coming. */
    record Herald(String item, double[][] sounds, String[] names) {}
    static final Herald[] HERALDS = {
            new Herald("goat_horn", new double[][]{{1, 0.8}}, new String[]{"mc_horn"}),   // a war horn
            new Herald("netherite_sword", new double[][]{{1, 0.8}, {0.9, 0.6}}, new String[]{"mc_netherite", "mc_sweep"}),
            new Herald("mace", new double[][]{{1, 0.7}}, new String[]{"mc_mace"}),
            new Herald("crossbow", new double[][]{{1, 0.8}}, new String[]{"mc_crossbow"}),
            new Herald("trident", new double[][]{{1, 0.7}}, new String[]{"mc_trident"}),
            new Herald("netherite_spear", new double[][]{{1, 0.8}}, new String[]{"mc_spear"}),
            new Herald("bell", new double[][]{{0.9, 0.7}}, new String[]{"mc_bell"}),
            new Herald("iron_sword", new double[][]{{1, 0.7}}, new String[]{"mc_totem"}),
    };
    public static final long DUEL_MS = 20000, DUEL_WAIT_MS = 60000, DUEL_POP_MS = 1250;   // the menu opens as the totem pop settles

    /** A challenge. */
    public static final class Req {
        public String name, game; public long amount, at, shownAt; public boolean opened;
        Req(String name, String game, long amount, long at) { this.name = name; this.game = game; this.amount = amount; this.at = at; }
        Req copy() { return new Req(name, game, amount, at); }
    }

    public static Req cur;
    public static final List<Req> queue = new ArrayList<>();
    public static List<Req> pending = new ArrayList<>();
    private static int ver;
    private static long combatUntil;
    private static int lastHerald = -1;

    public static int pendingCount() { return pending.size(); }
    public static int ver() { return ver; }

    /** Wires the "you hit something" half of the combat flag (the other half, taking damage, is watched in tick). */
    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()) combat();
            return ActionResult.PASS;
        });
    }

    public static boolean inCombat() { return P.now() < combatUntil; }
    /** AttackEntityCallback (you hit) + the player taking damage set this. */
    public static void combat() { combatUntil = P.now() + 10000; }

    public static long chips(String name) {
        for (Payouts.Row r : Payouts.rows()) if (Payouts.same(r.name(), name)) return r.bal();
        return 0;
    }

    private static Payouts.Row rowOf(String name) {
        for (Payouts.Row r : Payouts.rows()) if (Payouts.same(r.name(), name)) return r;
        return null;
    }

    /** A whisper line: a valid challenge is queued and true is returned (the line still shows in chat as usual). */
    public static boolean onChat(String line) {
        Matcher w = WHISPER_RE.matcher(line);
        if (!w.find()) return false;
        Matcher m = DUEL_RE.matcher(w.group(2).trim());
        if (!m.find()) return false;
        String name = w.group(1), game = DUEL_GAMES.get(m.group(1).toLowerCase());
        long amount = m.group(2) != null ? Money.parse(m.group(2).replaceAll("\\s", "")) : chips(name);   // "duel blackjack" with no amount: all his chips
        if (!GambleData.mode || game == null || !(amount > 0) || chips(name) < amount) return true;   // not a challenge we can take: read, ignored
        queue.removeIf(d -> Payouts.same(d.name, name));                                                // his last word replaces the one waiting
        if (pending.removeIf(d -> Payouts.same(d.name, name))) ver++;
        if (cur != null && Payouts.same(cur.name, name)) { cur.game = game; cur.amount = amount; return true; }
        queue.add(new Req(name, game, amount, P.now()));
        return true;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.hurtTime > 0) combat();   // you took a hit
        long t = P.now();
        Req d = cur;
        if (d != null && inCombat()) {   // a fight starts: put it away (menu closed), it comes back after
            if (mc.currentScreen instanceof DuelScreen) mc.setScreen(null);
            Req back = d.copy(); queue.add(0, back); cur = null;
        } else if (d != null && !d.opened && t - d.shownAt >= DUEL_POP_MS && mc.currentScreen == null) {
            d.opened = true; mc.setScreen(new DuelScreen(d));
        } else if (d != null && d.opened && !(mc.currentScreen instanceof DuelScreen)) {
            later(); cur = null;   // the menu was replaced by something else (disconnect...): kept for later, never lost
        }
        queue.removeIf(q -> t - q.at >= DUEL_WAIT_MS);
        if (cur == null && !queue.isEmpty() && !inCombat() && GambleData.mode && mc.currentScreen == null && mc.player != null) {
            cur = queue.remove(0); cur.shownAt = t;
            // the herald: an enchanted weapon (or a horn, a bell...) pops like a totem, each with its own vanilla sound; never the same twice in a row
            int i; do i = ThreadLocalRandom.current().nextInt(HERALDS.length); while (HERALDS.length > 1 && i == lastHerald);
            lastHerald = i; Herald h = HERALDS[i];
            ItemStack st = Items2.stack(h.item());
            if (st.isEmpty()) st = Items2.stack("iron_sword");
            mc.gameRenderer.showFloatingItem(P.shiny(st));   // GameRenderer.showFloatingItem(stack with ENCHANTMENT_GLINT_OVERRIDE)
            for (int k = 0; k < h.names().length; k++) P.play(h.names()[k], h.sounds()[k][0], h.sounds()[k][1]);
        }
    }

    /** The menu is gone: the next challenge may come. */
    public static void done() { cur = null; }

    /** Later (button, N, Esc, or no answer in 20 s): the challenge goes to the pending requests (Games > Requests). */
    public static void later() {
        Req d = cur; if (d == null) return;
        pending.removeIf(q -> Payouts.same(q.name, d.name));
        pending.add(0, d.copy()); ver++;
    }

    public static void remove(Req req) { pending.remove(req); ver++; }

    private static void seed(Host h, Payouts.Row row, Req d) {
        h.backer = row.name(); h.clearBet();
        h.bet = d.amount;
        for (Chips.Stack st : Chips.chipsFor(d.amount).stacks()) for (int i = 0; i < st.n(); i++) h.betChips.add(st.chip().v());
    }

    private static void openGame(String sub) {
        GambleScreen.tab = "games"; GambleScreen.sub = sub;
        MinecraftClient.getInstance().setScreen(new GambleScreen());
    }

    /** Accept the challenge on screen, or a pending one (req). null = done, otherwise why not. */
    public static String accept(Req req) {
        Req d = req != null ? req : cur;
        if (d == null) return "No challenge";
        if (!GambleData.mode || chips(d.name) < d.amount) return "Not enough chips anymore";
        Payouts.Row row = rowOf(d.name);
        switch (d.game) {
            case "Coinflip" -> {   // his last side (heads the first time), flipped at once
                if (CF.stage.equals("spin")) return "A coin is in the air, wait for it";
                if (req != null) remove(req); else done();
                CF.settle(); CF.stage = "bet"; seed(CF, row, d);
                openGame("coinflip");
                CF.start();
            }
            case "Crash" -> {   // his last auto cash out (x2 the first time), launched at once
                if (CX.stage.equals("play")) return "Finish the flight in play first";
                if (req != null) remove(req); else done();
                CX.settle(); CX.stage = "bet"; seed(CX, row, d);
                openGame("crash");
                CX.start();
            }
            case "Mines" -> {   // the mines count he last played (3 the first time), board drawn at once
                if (MN.stage.equals("play")) return "Finish the board in play first";
                if (req != null) remove(req); else done();
                MN.settle(); MN.stage = "bet"; seed(MN, row, d);
                openGame("mines");
                MN.start();
            }
            case "Crates" -> {   // the crate he last opened (Basic the first time), opened at once
                if (CR.stage.equals("spin")) return "A crate is opening, wait for it";
                if (req != null) remove(req); else done();
                CR.settle(); CR.stage = "bet"; seed(CR, row, d);
                openGame("crates");
                CR.open();
            }
            default -> {
                if (BJ.game != null && !BJ.game.phase.equals("done")) return "Finish the hand in play first";
                if (req != null) remove(req);
                BJ.apply(); BJ.stage = "bet"; seed(BJ, row, d);
                if (req == null) done();
                openGame("blackjack");
                BJ.deal();   // straight into the hand
            }
        }
        return null;
    }
}
