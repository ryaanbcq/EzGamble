package dev.ryaan.ezgamble.games.crates;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.games.Chips;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.Items2;
import dev.ryaan.ezgamble.proto.P;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Three crates, each a prize table of Minecraft items worth a multiple of the bet (EV ~0.90 like the other Outmind
 * games, see DESIGN.md 32). The gambler picks the player, the crate and the bet, opens: a reel of items scrolls under
 * a marker and slows down onto the prize (drawn BEFORE the reel starts, crypto RNG). The prize is bet x multiplier,
 * paid into the player's chips; the house's side (bet - prize) is one transaction.
 */
public final class CrateTable extends Host {
    public static final Map<String, Integer> RARITY = Map.of("common", 0xFFB8B0C8, "uncommon", 0xFF4BE07A, "rare", 0xFF5AA9FF, "epic", 0xFFC77DFF, "legendary", 0xFFFFD36B);

    /** [multiplier, chance %, item, rarity]. */
    public record Prize(double mult, double chance, String item, String rarity) {
        public boolean shiny() { return rarity.equals("legendary") || rarity.equals("epic"); }
        public int color() { return RARITY.get(rarity); }
    }

    public static final class CrateType {
        public final String id, name, icon, desc;
        public final List<Prize> prizes;
        public final double rtp, top;
        CrateType(String id, String name, String desc, Prize... prizes) {
            this.id = id; this.name = name; this.icon = "crate/" + id; this.desc = desc; this.prizes = List.of(prizes);
            double r = 0, t = 0;
            for (Prize p : prizes) { r += p.mult * p.chance; t = Math.max(t, p.mult); }
            rtp = r; top = t;
        }
    }

    /**
     * Tuned by simulation (DESIGN.md 32, scratch tool cratesim): the riskier the crate, the more the house keeps (its
     * risk grows with the jackpot), and every number is shown to the player: the chance of each prize, the % returned.
     * Basic: a win 38 % of the time, never below x0.4. Gold: a win 27 %, x15 about 1 in 200. Void: a win 20 %, x50 1 in 500.
     */
    public static final List<CrateType> CRATE_TYPES = List.of(
            new CrateType("basic", "Basic", "Safe",   // returns 94.9 %
                    new Prize(0.4, 23, "coal", "common"), new Prize(0.7, 25, "copper_ingot", "common"), new Prize(0.9, 14, "iron_ingot", "common"),
                    new Prize(1.2, 22, "gold_ingot", "uncommon"), new Prize(1.5, 10.5, "emerald", "rare"), new Prize(2, 4.3, "diamond", "epic"), new Prize(4, 1.2, "netherite_ingot", "legendary")),
            new CrateType("gold", "Gold", "Risky",   // returns 92.2 %
                    new Prize(0.2, 39.8, "rotten_flesh", "common"), new Prize(0.5, 22, "copper_ingot", "common"), new Prize(0.8, 11.5, "iron_ingot", "common"),
                    new Prize(1.5, 17, "gold_ingot", "uncommon"), new Prize(2.5, 6, "diamond", "rare"), new Prize(5, 3.2, "netherite_ingot", "epic"), new Prize(15, 0.5, "nether_star", "legendary")),
            new CrateType("void", "Void", "All in",   // returns 89.5 %
                    new Prize(0, 52.5, "bone", "common"), new Prize(0.5, 27, "amethyst_shard", "uncommon"), new Prize(2, 13, "echo_shard", "rare"),
                    new Prize(4, 5.5, "totem_of_undying", "epic"), new Prize(10, 1.8, "heart_of_the_sea", "epic"), new Prize(50, 0.2, "elytra", "legendary")));

    /** The most one open may pay (host setting, Java: config); the max bet of a crate = this / its top multiplier. */
    public static final double CRATE_MAX_WIN = 500e6;
    public static long crateMaxBet(CrateType c) { return (long) Math.floor(CRATE_MAX_WIN / c.top); }
    public static final int CRATE_CELL = 40, CRATE_WIN_AT = 72;
    public static final long CRATE_SPIN_MS = 8000;   // 8 s, a long slow end (Ryan: "tourne plus longtemps")

    private static final Map<String, String> ITEM_NAMES = Map.of("cooked_beef", "Steak", "tnt", "TNT", "cod", "Raw Cod", "salmon", "Raw Salmon");
    /** Readable name from the item id (Java: Item.getName()). */
    public static String itemName(String id) {
        String n = ITEM_NAMES.get(id);
        if (n != null) return n;
        var st = Items2.stack(id);
        if (!st.isEmpty()) return st.getName().getString();
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("_")) { if (sb.length() > 0) sb.append(' '); if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)); }
        return sb.toString();
    }

    public static Prize drawPrize(List<Prize> prizes) {
        double sum = 0;
        for (Prize p : prizes) sum += p.chance;
        double r = Widgets.rand01() * sum;
        for (Prize p : prizes) { r -= p.chance; if (r < 0) return p; }
        return prizes.get(prizes.size() - 1);
    }

    public static final class Round extends Widgets.Round {
        public CrateType type; public Prize prize; public List<Prize> strip; public long bet; public String backer;
        public long at, landedAt; public double land; public int lastCell = -1; public long tickAt; public boolean applied;
    }

    public static final CrateTable CR = new CrateTable();

    public String stage = "bet";
    public int crate;
    public Round round;

    public CrateType type() { return CRATE_TYPES.get(crate); }

    /** Draw the prize, build the reel around it, start the spin. */
    public boolean open() {
        if (!GambleData.mode) return fail("Gambling mode is off");
        if (backer == null) return fail("Pick a player first");
        if (bet <= 0) return fail("Place a bet");
        if (bet > balance()) return fail("Not enough chips");
        if (bet > crateMaxBet(type())) return fail("Max bet " + Money.format(crateMaxBet(type())));
        settle();
        CrateType type = type();
        Prize prize = drawPrize(type.prizes);
        List<Prize> strip = new ArrayList<>();
        for (int i = 0; i < CRATE_WIN_AT + 6; i++) strip.add(i == CRATE_WIN_AT ? prize : drawPrize(type.prizes));   // the reel shows the TRUE odds: no fake near miss
        // stop somewhere inside the prize's cell, never dead centre (the near miss is part of the show)
        double land = CRATE_WIN_AT * CRATE_CELL + CRATE_CELL / 2.0 + (Widgets.rand01() - 0.5) * (CRATE_CELL - 10);
        Round r = new Round();
        r.type = type; r.prize = prize; r.strip = strip; r.bet = bet; r.backer = backer; r.at = P.now(); r.land = land;
        round = r;
        Host.lastBacker = backer;
        stage = "spin";
        P.play("ui_kcards_open", 1, 0.7);
        return true;
    }

    public boolean again() {
        if (bet > balance()) fitBet();
        if (bet <= 0) return fail("No chips left");
        return open();
    }

    /** Reel position (px from the strip's start to the marker) at time t: cubic ease out, like a crate wheel losing speed. */
    public double pos(long t) {
        Round r = round;
        double f = Math.min(1, (double) (t - r.at) / CRATE_SPIN_MS);
        return 2 * CRATE_CELL + (r.land - 2 * CRATE_CELL) * (1 - Math.pow(1 - f, 4));
    }

    public long payoutOf(Round r) { return Math.round(r.bet * r.prize.mult); }

    public void tick() {
        Round r = round;
        if (r == null || !stage.equals("spin")) return;
        long t = P.now();
        int cell = (int) Math.floor(pos(t) / CRATE_CELL);
        if (cell != r.lastCell) {
            if (r.lastCell >= 0 && t - r.tickAt >= 45) { P.play("ui_tick_close", 1 + 0.15 * Widgets.rand01(), 0.35); r.tickAt = t; }   // one tick per item under the marker, 45 ms apart at most (a buzz at full speed anyway)
            r.lastCell = cell;
        }
        if (t - r.at >= CRATE_SPIN_MS) {
            stage = "done"; r.landedAt = t; settle();
            boolean won = payoutOf(r) > r.bet;
            if (r.prize.shiny()) { P.play("cash", 1, 0.8); P.play("mc_totem", 1.2, 0.35); }
            else if (won) P.play("pay", 1, 0.6);
            else P.play("fail", 1.2, 0.35);
        }
    }

    /** Settle a landed crate into the session (his chips: prize - bet; the house: bet - prize). Idempotent. */
    @Override
    public void settle() {
        Round r = round;
        if (r == null || !stage.equals("done") || r.applied) return;
        r.applied = true;
        var s = Payouts.session();
        long net = payoutOf(r) - r.bet;
        if (s != null) s.addWon(r.backer, net);
        GambleData.record(new Txn(P.now(), "Crates", r.bet, -net, r.backer, "Crates " + r.type.name + " " + Widgets.multText(r.prize.mult) + " for " + r.backer));
    }

    /** Pay button: only a crate that paid more than the bet; it sends the whole prize. */
    public Widgets.PayState payState() {
        Round r = round;
        if (r == null || !stage.equals("done")) return null;
        long p = payoutOf(r);
        return Widgets.payStateOf(r, r.backer, p > r.bet ? Math.min(p, balance()) : 0, r.landedAt);
    }

    public void pay() { Widgets.payOut(round, round.backer, payState(), this::settle); }
}
