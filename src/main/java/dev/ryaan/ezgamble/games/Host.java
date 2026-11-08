package dev.ryaan.ezgamble.games;

import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.proto.GlassField;
import dev.ryaan.ezgamble.proto.P;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What every host table shares (the prototype's CrateTable fields): the player whose chips are played, the bet built
 * from chip clicks or the amount field, the search picker, a warning shown on the main button.
 */
public abstract class Host {
    public String backer;
    public long bet;
    public final List<Long> betChips = new ArrayList<>();
    public GlassField field;
    public Widgets.BackerPicker picker;
    public Widgets.RefundPill refundPill;
    public Warn warn;
    /** The player of the last game, any table (prototype: `MN.backer = CR.backer || BJ.backer`): set it when a game starts or a backer is picked. */
    public static String lastBacker;

    public record Warn(String text, long until) {}

    /** Players with chips on the table, biggest first. */
    public List<Payouts.Row> backers() {
        List<Payouts.Row> l = new ArrayList<>(Payouts.rows().stream().filter(r -> r.bal() > 0 || dev.ryaan.ezgamble.data.GambleData.free).toList());
        if (dev.ryaan.ezgamble.data.GambleData.free) freePlayers(l);
        l.sort(Comparator.comparingLong((Payouts.Row r) -> -r.bal()).thenComparing(Payouts.Row::name));
        return l;
    }

    public long balance() { return dev.ryaan.ezgamble.data.GambleData.free && backer != null ? dev.ryaan.ezgamble.data.GambleData.FREE_BAL : Payouts.balance(backer); }

    /** What the table shows as his chips: "Free" in free mode. */
    public String balanceText() { return dev.ryaan.ezgamble.data.GambleData.free ? "Free" : dev.ryaan.ezgamble.Money.format(balance()); }

    /** Free mode: everyone in the tab list can be played for (nobody has to /pay first), yourself excluded. */
    private static void freePlayers(List<Payouts.Row> l) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;
        String me = mc.getSession().getUsername();
        for (var e : mc.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().name();
            if (n == null || Payouts.same(n, me) || l.stream().anyMatch(r -> Payouts.same(r.name(), n))) continue;
            l.add(new Payouts.Row(n, 0, 0, 0, 0, null, null));
        }
    }

    public boolean fail(String text) { P.play("fail", 1, 0.5); warn = new Warn(text, P.now() + 2200); return false; }

    /** The house guard: a round whose top win the gambler could not pay on top of what he owes is refused (house.House). */
    protected boolean covered(long maxWin) {
        if (!dev.ryaan.ezgamble.data.GambleData.houseGuard || dev.ryaan.ezgamble.data.GambleData.free || dev.ryaan.ezgamble.house.House.canCover(maxWin)) return true;
        return fail(dev.ryaan.ezgamble.house.House.reason(maxWin));
    }

    public boolean addChip(Chips.Chip c) {
        if (backer == null) return fail("Pick a player first");
        if (bet + c.v() > balance()) return fail("Not enough chips");
        bet += c.v(); betChips.add(c.v()); if (field != null) field.value = "";
        P.play("ui_kcollide_open", 1, 0.5);
        return true;
    }

    public void undoChip() {
        if (!betChips.isEmpty()) { bet -= betChips.remove(betChips.size() - 1); P.play("ui_kcollide_close", 1, 0.5); }
        else if (bet > 0) { bet = 0; if (field != null) field.value = ""; P.play("ui_kcollide_close", 1, 0.5); }
    }

    public void clearBet() { bet = 0; betChips.clear(); if (field != null) field.value = ""; }

    /** The bet shrunk to what the backer still has, rebuilt as chips (Play again after a loss). */
    public void fitBet() {
        if (bet > balance()) {
            bet = balance(); betChips.clear();
            for (Chips.Stack st : Chips.chipsFor(bet).stacks()) for (int i = 0; i < st.n(); i++) betChips.add(st.chip().v());
        }
    }

    /** The finished round is in the backer's chips (idempotent). */
    public abstract void settle();
}
