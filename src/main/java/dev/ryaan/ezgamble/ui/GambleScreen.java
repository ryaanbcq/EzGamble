package dev.ryaan.ezgamble.ui;

import dev.ryaan.ezgamble.DebugRun;
import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.Updater;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Stats;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.games.Chips;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.games.Views;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Payments;
import dev.ryaan.ezgamble.proto.*;
import net.minecraft.client.MinecraftClient;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Main menu (K), port of the prototype's GambleScreen: tabs Dashboard / History / Games / HUD, the game sub-views. */
public final class GambleScreen extends ProtoScreen {
    public static final int W = 380, H = 300, PAD = 14;
    /** Sub-views drawn on the felt (their header replaces the Recording row). */
    public static final List<String> GAME_SUBS = List.of("blackjack", "crates", "mines", "crash", "roulette", "coinflip");
    private static final String[][] GREETINGS = {
            {"Welcome back, ", ""}, {"Feeling lucky, ", "?"}, {"The table is yours, ", ""}, {"Place your bets, ", ""},
            {"Back for more, ", "?"}, {"Let's roll, ", ""}, {"Luck looks good on you, ", ""}, {"Good to see you, ", ""}};
    private static int lastGreeting = -1;

    // kept between openings, like the prototype (its GambleScreen lived on)
    public static String tab = "dashboard", range = "session", filter = "all";
    public static String sub;

    public double x0, y0;
    public boolean welcome = GambleData.name == null;
    private String[] greeting = pickGreeting();
    public long titleAt = P.now(), chartAt = P.now(), openedAt = P.now(), closeAt;
    public boolean closing;
    private final Map<String, GlassSegmented> segs = new HashMap<>();
    private GlassField nameField;
    private boolean builtMode = GambleData.mode;
    private Updater.State shownUpdate = Updater.state();
    private String lastSub;
    /** The list under the pointer for the wheel (beginList). */
    public double[] listHit; public String listHitId;
    private final Map<String, GameView> views = new HashMap<>();
    /** The Games tab's credit card (hover lift). */
    private double cardK; private long cardT;
    private Widget cardBtn;

    public GambleScreen() {
        super("EzGamble");
        viewX = GambleData.winX; viewY = GambleData.winY; viewZ = GambleData.winZoom;
        Updater.recheck();
        // the podium's skins load while you look at the dashboard: all there once you open High rollers
        for (String r : new String[]{"session", "all"}) dev.ryaan.ezgamble.proto.Faces.prefetch(dev.ryaan.ezgamble.data.PlayerStats.highRollers(r).stream().limit(12).map(dev.ryaan.ezgamble.data.PlayerStats.Roller::name).toList());
        P.play("open", 0.8);
    }

    public static GambleScreen current() { return MinecraftClient.getInstance().currentScreen instanceof GambleScreen s ? s : null; }

    public GameView view(String id) { return views.computeIfAbsent(id, k -> Views.create(k, this)); }

    private static String[] pickGreeting() {
        int h = LocalTime.now().getHour();
        String[] timed = {h < 5 ? "Late night, " : h < 12 ? "Good morning, " : h < 18 ? "Good afternoon, " : "Good evening, ", ""};
        int i;
        do i = ThreadLocalRandom.current().nextInt(GREETINGS.length + 1); while (i == lastGreeting);
        lastGreeting = i;
        return i == GREETINGS.length ? timed : GREETINGS[i];
    }

    /**
     * The felt of a game sub-view (prototype BlackjackView.prototype.geom): lx/rx = content edges, hy = the game's header
     * row (the Recording row's place), fy/fh = the 156 px layout box, ft/fth = the felt itself, by = the bottom button row.
     */
    public record Geom(double lx, double rx, double hy, double fy, double fh, double ft, double fth, double by, double cx) {}

    public Geom geom() {
        double lx = x0 + PAD, rx = x0 + W - PAD;
        return new Geom(lx, rx, y0 + 56, y0 + 88, 156, y0 + 76, 180, y0 + H - PAD - 24, lx + (int) ((rx - lx) / 2));
    }

    // ---- pop

    public double pop() {
        long t = P.now();
        if (closing) { double f = Math.min(1, (t - closeAt) / 220.0); return 1 - f * f; }
        return P.easeOut((t - openedAt) / 280.0);
    }

    private void beginPop(G g, double p) {
        g.push();
        if (p >= 1) return;
        double cx = x0 + W / 2.0, cy = y0 + H / 2.0, sc = 0.7 + 0.3 * p;
        g.translate(cx, cy); g.scale(sc, sc); g.translate(-cx, -cy);
    }

    // ---- layout

    @Override
    protected void init2() {
        if (viewZ > maxZoom() + 1e-6 || viewZ < minZoom() - 1e-6) { viewZ = snapZoom(viewZ); SW = width / viewZ; SH = height / viewZ; }   // a smaller screen than last time
        x0 = (int) ((SW - W) / 2); y0 = (int) ((SH - H) / 2);
        clampWindow();
        double lx = x0 + PAD, rx = x0 + W - PAD;
        builtMode = GambleData.mode;
        if (welcome) {
            double fw = 150, fx = x0 + (W - fw) / 2;
            if (nameField == null) nameField = new GlassField(0, 0, fw, 22, MinecraftClient.getInstance().getSession().getUsername(), "Your name");
            nameField.x = fx; nameField.y = y0 + 150;
            add(nameField);
            add(new GlassButton(fx, y0 + 182, fw, 22, "Continue", "PRIMARY", this::submitName));
            return;
        }
        add(new GlassSwitch(rx - 28, y0 + PAD - 3, 28, 14, () -> GambleData.mode, GambleData::setMode));
        if (Updater.state() == Updater.State.AVAILABLE) {
            String label = "Update " + Updater.latest();
            double w = F.width(label) + 16, lw = F.width("Gambling mode");
            add(new GlassButton(rx - 28 - 6 - lw - 8 - w, y0 + PAD - 4, w, 16, label, "PRIMARY", () -> { Updater.download(); rebuild(); }));
        }
        GlassSegmented tabs = seg("tabs", new String[][]{{"dashboard", "Dashboard"}, {"transactions", "History"}, {"games", "Games"}, {"hud", "HUD"}}, () -> tab, id -> {
            leaveSub();
            sub = null;
            if (!id.equals(tab)) { if (id.equals("dashboard")) greeting = pickGreeting(); if (id.equals("dashboard") || tab.equals("dashboard")) titleAt = P.now(); }   // the title animates only when its text changes (greeting <-> EZ GAMBLE)
            tab = id; chartAt = P.now(); rebuild();
        });
        tabs.place(lx, y0 + 33);
        add(tabs);
        if (tab.equals("dashboard")) rightGroup(new String[][]{{"session", "Session"}, {"1h", "1h"}, {"24h", "24h"}, {"all", "All"}}, () -> range, v -> { range = v; chartAt = P.now(); });
        if (tab.equals("transactions")) rightGroup(new String[][]{{"all", "All"}, {"wins", "Wins"}, {"losses", "Losses"}}, () -> filter, v -> { filter = v; chartAt = P.now(); });
        if (!GambleData.mode && !(tab.equals("games") && sub != null && GAME_SUBS.contains(sub)))
            add(new GlassButton(rx - 54, y0 + 58, 52, 14, "Turn on", "PRIMARY", () -> GambleData.setMode(true)));
        if (tab.equals("games") && sub == null) {
            cardBtn = add(new Widget(lx, y0 + 80, rx - lx, 64) { @Override public void click(double mx, double my) { P.play("click"); sub = "chips"; rebuild(); } });
            double[] gc = gamesClip();
            Widget.Clip clip = new Widget.Clip(gc[0], gc[1], gc[2], gc[3]);
            for (double[] tl : gameTilesRects()) {
                String s = GAME_IDS[(int) tl[4]];
                Widget w = add(new Widget(tl[0], tl[1], tl[2], tl[3]) { @Override public void click(double mx, double my) { P.play("click"); sub = s; rebuild(); } });
                w.list = new Widget.ListPos("games", tl[1]); w.clip = clip;
            }
        }
        if (tab.equals("games") && (sub == null || sub.equals("duels"))) {
            int n = dev.ryaan.ezgamble.duel.Duel.pendingCount();
            String lbl = n > 0 ? "Requests " + n : "Requests";
            double bw = F.width(lbl) + 16;
            add(new GlassButton(rx - bw, y0 + 34, bw, 16, lbl, n > 0 && !"duels".equals(sub) ? "PRIMARY" : "GHOST", () -> { sub = "duels".equals(sub) ? null : "duels"; rebuild(); }));
        }
        if (tab.equals("games") && sub != null) view(sub).init();
        if (tab.equals("hud")) dev.ryaan.ezgamble.hud.HudTab.init(this);
    }

    private GlassSegmented seg(String key, String[][] items, java.util.function.Supplier<String> get, java.util.function.Consumer<String> set) {
        GlassSegmented s = segs.computeIfAbsent(key, k -> new GlassSegmented(0, 0, 18, items, get, set));
        s.lens = key.equals("tabs") || key.equals("right-dashboard");   // the Liquid Glass lens: main tabs and the dashboard period
        return s;
    }

    private void rightGroup(String[][] items, java.util.function.Supplier<String> get, java.util.function.Consumer<String> set) {
        GlassSegmented s = seg("right-" + tab, items, get, set);
        s.place(x0 + W - PAD - s.w, y0 + 33);
        add(s);
    }

    /** Leaving the current game sub-view: settle it (‹, tab change, close). */
    public void leaveSub() { if (sub != null && tab.equals("games")) view(sub).leave(); }

    /** ‹ from a sub-view. */
    public void back() { leaveSub(); sub = null; rebuild(); }

    public void submitName() {
        String n = nameField.value.trim();
        GambleData.setName(n.isEmpty() ? MinecraftClient.getInstance().getSession().getUsername() : n);
        welcome = false; greeting = pickGreeting(); titleAt = P.now();
        P.play("pay", 1.1);
        rebuild();
    }

    @Override
    public void tick() {
        if (lastSub != null && !lastSub.equals(sub) && views.containsKey(lastSub)) views.get(lastSub).leave();
        lastSub = sub;
        if (tab.equals("games") && sub != null) { view(sub).tick(); }
        if (builtMode != GambleData.mode) rebuild();
        if (Updater.state() != shownUpdate) { shownUpdate = Updater.state(); rebuild(); }
        if (duelVer != dev.ryaan.ezgamble.duel.Duel.ver()) { duelVer = dev.ryaan.ezgamble.duel.Duel.ver(); if (tab.equals("games") && (sub == null || sub.equals("duels"))) rebuild(); }
    }
    private int duelVer = -1;

    // ---- drawing

    @Override
    protected void renderBg(G g, double mx, double my) {
        long t0 = System.nanoTime();
        double p = pop();
        if (client != null && client.world != null) {
            g.d.applyBlur();
            g.fill(vx(0), vy(0), vx(width), vy(height), ((int) (0x55 * p) << 24) | 0x0A0710);   // the whole screen, wherever the window is
        }
        beginPop(g, p);
        P.glass(g, x0, y0, W, H, 12, 0.84, 1);
        renderGrip(g, mx, my);
        renderTitle(g);
        String lbl = "Gambling mode";
        if (!welcome) g.drawText(lbl, x0 + W - PAD - 28 - 6 - F.width(lbl), y0 + PAD, GambleData.mode ? P.TEXT : P.MUTE, false);
        g.pop();
        if (DebugRun.ON) DebugRun.renderNanos += System.nanoTime() - t0;
    }

    private void renderTitle(G g) {
        double x = x0 + PAD, f = P.easeOut((P.now() - titleAt) / 260.0), y = y0 + PAD + Math.round((1 - f) * 4), a = Math.max(0.02, f);
        if (GambleData.name != null && !welcome && tab.equals("dashboard")) {
            String pre = greeting[0], suf = greeting[1], name = GambleData.name;
            g.drawText(pre, x, y, P.alpha(P.WHITE, a), true);
            P.gradientText(g, name, x + F.width(pre), y, true, a);
            if (!suf.isEmpty()) g.drawText(suf, x + F.width(pre) + F.width(name), y, P.alpha(P.WHITE, a), true);
            return;
        }
        P.gradientText(g, "EZ GAMBLE", x, y, true, a);
        g.drawText("by ZKR5", x + F.width("EZ GAMBLE") + 8, y + 1, P.alpha(P.MUTE, a), false);
    }

    private void renderWelcome(G g) {
        double cx = x0 + W / 2.0;
        String t1 = "Welcome to EzGamble";
        g.push(); g.translate(cx - F.width(t1), y0 + 72); g.scale(2, 2); P.gradientText(g, t1, 0, 0, true); g.pop();
        String q = "What should we call you?", h = "Shown at the top of your dashboard.";
        g.drawText(q, cx - (F.width(q) >> 1), y0 + 120, P.WHITE, true);
        g.drawText(h, cx - (F.width(h) >> 1), y0 + 133, P.MUTE, false);
    }

    @Override
    protected void renderFg(G g, double mx, double my) {
        long t0 = System.nanoTime();
        if (closing && P.now() - closeAt >= 220) { closeNow(); return; }
        beginPop(g, pop());
        if (welcome) { renderChildren(g, mx, my); renderWelcome(g); g.pop(); return; }
        boolean game = tab.equals("games") && sub != null && GAME_SUBS.contains(sub);
        if (!game) renderStatus(g);
        listHit = null;
        layoutListWidgets();
        if (game) {
            view(sub).render(g, mx, my);
            renderChildren(g, mx, my);
            g.pop();
            dev.ryaan.ezgamble.duel.TableChat.render(g, this, pop());
            renderUpdateStatus(g);
            if (DebugRun.ON) { DebugRun.renderNanos += System.nanoTime() - t0; DebugRun.renderFrames++; }
            return;
        }
        if (tab.equals("games") && sub != null) {   // chips, player, auction, duels: drawn under their widgets like the prototype
            renderChildren(g, mx, my);
            view(sub).render(g, mx, my);
        } else {
            renderChildren(g, mx, my);
            switch (tab) {
                case "dashboard" -> renderDashboard(g, mx, my);
                case "transactions" -> renderTransactions(g);
                case "hud" -> dev.ryaan.ezgamble.hud.HudTab.render(this, g);
                case "games" -> renderGames(g, mx, my);
                default -> {}
            }
        }
        g.pop();
        renderUpdateStatus(g);
        if (DebugRun.ON) { DebugRun.renderNanos += System.nanoTime() - t0; DebugRun.renderFrames++; }
    }

    private void renderUpdateStatus(G g) {
        String t = switch (Updater.state()) {
            case DOWNLOADING -> "Downloading " + Updater.latest() + "...";
            case READY -> "Restart Minecraft to finish the update";
            default -> "";
        };
        if (!t.isEmpty()) g.drawText(t, x0 + (W - F.width(t)) / 2.0, y0 + H + 6, Updater.state() == Updater.State.READY ? P.GREEN : P.MUTE, true);
    }

    /** y0+56..74: recording line, or the red OFF banner. */
    private void renderStatus(G g) {
        double lx = x0 + PAD, rx = x0 + W - PAD, y = y0 + 56;
        if (!GambleData.mode) {
            P.card(g, lx, y, rx - lx, 18, "red");
            g.drawText("✖", lx + 6, y + 5, P.RED, false);
            g.drawText("Gambling mode is off", lx + 18, y + 5, P.RED, false);
            return;
        }
        Session s = GambleData.current();
        P.dot(g, lx, y + 6, P.alpha(P.GREEN, 0.55 + 0.45 * P.pulse(P.now())));
        g.drawText("Recording", lx + 10, y + 6, P.WHITE, false);
        if (s != null) P.pillsRight(g, rx, y + 2, new Object[][]{{"Session", P.clock(P.now() - s.start)}, {"Bets", String.valueOf(s.txns.size())}});
    }

    private void renderDashboard(G g, double mx, double my) {
        double lx = x0 + PAD, rx = x0 + W - PAD;
        List<Txn> list = GambleData.inRange(range);
        Stats st = Stats.of(list);
        double half = (int) ((rx - lx) / 2), third = (int) ((rx - lx) / 3);
        double y = y0 + 80;
        P.card(g, lx, y, rx - lx, 46, "accent");
        g.drawText("Profit", lx + 10, y + 8, P.MUTE, false);
        g.push(); g.translate(lx + 10, y + 20); g.scale(2, 2);
        g.drawText(Money.signed(st.profit()), 0, 0, st.profit() >= 0 ? P.GREEN : P.RED, true);
        g.pop();
        double q = (int) ((rx - lx - half) / 2);
        P.tile(g, lx + half + 8, y + 8, "Volume", Money.format(st.volume()), P.WHITE);
        P.tile(g, lx + half + 8 + q, y + 8, "Transactions", String.valueOf(st.n()), P.WHITE);
        y += 50;
        P.card(g, lx, y, rx - lx, 34, "soft");
        P.tile(g, lx + 10, y + 6, "Win rate", st.n() > 0 ? Math.round(st.rate() * 100) + "%" : "-", st.n() > 0 ? P.WHITE : P.DIM);
        P.tile(g, lx + 10 + third, y + 6, "Best win", st.best() == null ? "-" : Money.signed(st.best()), st.best() == null ? P.DIM : P.GREEN);
        P.tile(g, lx + 10 + 2 * third, y + 6, "Worst loss", st.worst() == null ? "-" : Money.signed(st.worst()), st.worst() == null ? P.DIM : P.RED);
        double cy = y + 40, ch = y0 + H - PAD - cy;
        P.card(g, lx, cy, rx - lx, ch, "chart");
        if (list.isEmpty()) { g.drawText("No transaction yet.", lx + 8, cy + (int) (ch / 2) - 4, P.DIM, false); return; }
        float reveal = (float) P.easeOut((P.now() - chartAt) / 600.0);
        long[] deltas = new long[list.size()];
        for (int i = 0; i < deltas.length; i++) deltas[i] = list.get(i).delta();
        ProfitChart.Result r = ProfitChart.draw(g.d, deltas, (int) lx, (int) cy, (int) (rx - lx), (int) ch, (int) mx, (int) my, false, 0, true, reveal);
        if (r.hover() > 0) {
            Txn t = list.get(r.hover() - 1);
            ProfitChart.tooltip(g.d, t.game(), Money.signed(t.delta()), t.delta() >= 0 ? P.GREEN : P.RED, r.px()[r.hover()], (int) cy, (int) lx, (int) rx,
                    P.DATE_S(t.t()), t.bet() != null ? "Bet " + Money.format(t.bet()) : "");
        }
    }

    /** Running all-time total, then the filter, newest first. */
    private List<Txn> filtered() {
        List<Txn> all = GambleData.all(), out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            Txn t = all.get(i);
            if (filter.equals("all") || (filter.equals("wins") ? t.delta() > 0 : t.delta() < 0)) out.add(t);
        }
        return out;
    }

    private void renderTransactions(G g) {
        double lx = x0 + PAD, rx = x0 + W - PAD;
        List<Txn> rows = filtered();
        double top = y0 + 80, lh = y0 + H - PAD - 18 - top;
        String id = "hist-" + filter;
        if (rows.isEmpty()) g.drawText("No transaction yet.", lx, top + 4, P.DIM, false);
        double off = beginList(g, id, lx, top, rx - lx, lh, rows.size() * 30 - 3);
        int first = (int) Math.max(0, Math.floor(off / 30));
        double y = top + first * 30 - off;
        for (int i = first; i < Math.min(rows.size(), first + (int) Math.ceil(lh / 30) + 1); i++) {
            Txn t = rows.get(i);
            P.card(g, lx, y, rx - lx, 27, "row");
            Icons.drawGameIcon(g, t.game(), lx + 4, y + 5, 16);
            g.drawText(t.game(), lx + 26, y + 4, P.WHITE, false);
            if (t.player() != null) {
                double px = lx + 26 + F.width(t.game()) + 8;
                g.drawHead(t.player(), px, y + 4, 8); g.drawText(F.trimToWidth(t.player(), 70), px + 11, y + 4, P.MUTE, false);
            }
            String d = P.DATE_S(t.t());
            g.drawText(d, rx - 6 - F.width(d), y + 4, P.DIM, false);
            if (t.bet() != null) g.drawText("Bet " + Money.format(t.bet()), lx + 26, y + 15, P.MUTE, false);
            String right = Money.signed(t.delta());
            g.drawText(right, rx - 6 - F.width(right), y + 15, t.delta() >= 0 ? P.GREEN : P.RED, false);
            y += 30;
        }
        endList(g, id, rows.size() * 30 - 3);
        Stats st = Stats.of(rows);
        double fy = y0 + H - PAD - 10;
        P.pill(g, lx, fy - 4, "Wins", String.valueOf(st.wins()), P.GREEN);
        P.pill(g, lx + P.pillW("Wins", String.valueOf(st.wins())) + 4, fy - 4, "Losses", String.valueOf(st.losses()), P.RED);
    }

    // ---- Games tab

    private static final String[][] GAMES = {
            {"Blackjack", "Hit or stand", "game/blackjack"}, {"Auction", "Highest bid", "game/auction"}, {"Mines", "Avoid bombs", "game/mines"},
            {"Crates", "Open crates", "game/crates"}, {"Roulette", "Red, black", "game/roulette"}, {"Crash", "Cash out", "game/crash"}, {"Coinflip", "Heads, tails", "game/coinflip"},
            {"High rollers", "Top wagers", "game/highrollers"},
            {"Provably fair", "Seeds", "game/fair"}};
    private static final String[] GAME_IDS = {"blackjack", "auction", "mines", "crates", "roulette", "crash", "coinflip", "rollers", "fair"};

    public double[] gamesClip() { double top = y0 + 152; return new double[]{x0 + PAD, top, W - 2 * PAD, y0 + H - PAD - top}; }

    /** x, y, w, h, index. */
    private List<double[]> gameTilesRects() {
        double lx = x0 + PAD, rx = x0 + W - PAD, tw = (int) ((rx - lx - 12) / 3), th = 52, y = gamesClip()[1];
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < GAMES.length; i++) out.add(new double[]{lx + (i % 3) * (tw + 6), y + Math.floor(i / 3.0) * (th + 6), tw, th, i});
        return out;
    }

    /** The session credit as chips (who backed the gambler), then the game tiles. */
    private void renderGames(G g, double mx, double my) {
        double lx = x0 + PAD, rx = x0 + W - PAD, y = y0 + 80;
        boolean hov = mx >= lx && mx < rx && my >= y && my < y + 64;
        long t = P.now();
        cardK = cardK + ((hov ? 1 : 0) - cardK) * Math.min(1, (t - (cardT == 0 ? t : cardT)) / 90.0);
        cardT = t;
        double ck = P.easeOut(Math.max(0, Math.min(1, cardK))), cxm = (lx + rx) / 2, cym = y + 32;
        g.push();
        double csc = cardBtn != null && cardBtn.down && hov ? 0.985 : 1 + 0.012 * ck;
        g.translate(cxm, cym); g.scale(csc, csc); g.translate(-cxm, -cym);
        P.card(g, lx, y, rx - lx, 64, "accent");
        P.hoverSheen(g, lx, y, rx - lx, 64, 7, ck);
        List<Payouts.Row> held = Payouts.rows().stream().filter(r -> r.bal() > 0).toList();
        long total = held.stream().mapToLong(Payouts.Row::bal).sum();
        var cb = Chips.chipsFor(total);
        int nStacks = Math.min(4, cb.stacks().size());
        for (int i = 0; i < nStacks; i++) {
            var stk = cb.stacks().get(i);
            double sx = lx + 20 + i * 28;
            double bob = Payments.cur != null && P.now() - Payments.cur.at() < 400 ? Math.round(Math.sin((P.now() - Payments.cur.at()) / 40.0)) : 0;
            Chips.chipStack(g, sx, y + 50 + bob, 12, stk.chip(), stk.n());
            String lbl = "×" + stk.n();
            g.drawText(lbl, sx - (F.width(lbl) >> 1), y + 53, P.DIM, false);
        }
        if (nStacks == 0) Chips.chipStack(g, lx + 20, y + 50, 12, Chips.GHOST, 1);
        double tx = lx + 20 + Math.max(1, nStacks) * 28 - 4;
        g.drawText("Credit", tx, y + 8, P.MUTE, false);
        g.push(); g.translate(tx, y + 20); g.scale(2, 2); P.gradientText(g, Money.format(total), 0, 0, true); g.pop();
        int players = held.size();
        String me = MinecraftClient.getInstance().getSession().getUsername();
        g.drawText(!GambleData.mode && total == 0 ? "Counted while gambling mode is on" : total > 0 ? "From " + players + " player" + (players == 1 ? "" : "s") : "Send /pay " + me + " to back the session",
                tx, y + 44, P.DIM, false);
        // backers: the biggest three
        double bx = rx - 112;
        for (int i = 0; i < Math.min(3, held.size()); i++) {
            Payouts.Row r = held.get(i);
            double by = y + 8 + i * 13;
            g.drawHead(r.name(), bx, by, 8);
            g.drawText(F.trimToWidth(r.name(), 52), bx + 11, by, P.TEXT, false);
            String vs = Money.format(r.bal());
            g.drawText(vs, rx - 8 - F.width(vs), by, P.GREEN, false);
        }
        g.pop();
        // tiles, 3 per row, the grid scrolls
        double[] gc = gamesClip();
        List<double[]> tiles = gameTilesRects();
        double gh = tiles.isEmpty() ? 0 : tiles.get(tiles.size() - 1)[1] + tiles.get(0)[3] - gc[1];
        double goff = beginList(g, "games", gc[0], gc[1], gc[2], gc[3], gh);
        boolean inGrid = my >= gc[1] && my < gc[1] + gc[3];
        for (double[] tl : tiles) {
            String[] gm = GAMES[(int) tl[4]];
            double tx2 = tl[0], tw = tl[2], th = tl[3], ty = tl[1] - goff;
            if (ty + th < gc[1] || ty > gc[1] + gc[3]) continue;
            boolean h2 = inGrid && mx >= tx2 && mx < tx2 + tw && my >= ty && my < ty + th;
            P.smooth(g, tx2, ty, tw, th, 8, h2 ? 0x17FFFFFF : 0x0DFFFFFF, h2 ? 0x80A18CD1 : 0x14FFFFFF);
            g.drawSprite(gm[2], tx2 + 8, ty + 10, 32, 32);
            g.drawText(gm[0], tx2 + 46, ty + 16, P.WHITE, true);
            if (F.width(gm[1]) <= tw - 50) g.drawText(gm[1], tx2 + 46, ty + 28, P.MUTE, false);
            if (h2) g.drawText("›", tx2 + tw - 10, ty + th - 13, P.PINK, false);   // bottom right: a long title never runs into it
        }
        endList(g, "games", gh);
    }

    // ---- scrolling lists

    public double beginList(G g, String id, double x, double y, double w, double h, double contentH) {
        Scroller s = sc(id);
        s.update(contentH - h);
        listHit = new double[]{x, y, w, h}; listHitId = id;
        g.enableScissor(x, y, x + w, y + h);
        return Math.round(s.pos);
    }

    public void endList(G g, String id, double contentH) {
        g.disableScissor();
        if (listHit != null) sc(id).indicator(g, listHit[0] + listHit[2] - 4, listHit[1] + 2, listHit[3] - 4, contentH);
    }

    /** Widgets living in a list follow its scroll. */
    public void layoutListWidgets() {
        for (Widget c : children) if (c.list != null && c.clip != null) {
            c.y = c.list.baseY() - Math.round(sc(c.list.id()).pos);
            c.visible = c.y + c.h > c.clip.y() && c.y < c.clip.y() + c.clip.h();
        }
    }

    @Override
    public boolean mouseScrolled2(double mx, double my, double dy, double dx) {
        if (tab.equals("games") && sub != null && view(sub).scrolled(mx, my, dy, dx)) return true;
        double[] L = listHit;
        if (L == null || mx < L[0] || mx >= L[0] + L[2] || my < L[1] || my >= L[1] + L[3]) return false;
        sc(listHitId).wheel(dy);
        return true;
    }

    // ---- input

    @Override
    public void key(Key e) {
        if (welcome) {
            if (e.is("Enter")) submitName();
            else if (e.is("Escape")) close();
            else nameField.type(e);
            return;
        }
        if (tab.equals("games") && sub != null && view(sub).key(e)) return;
        if (e.is("Escape") || e.is("k")) close();
    }

    // ---- the window: drag the top bar to move it, the bottom-right corner to resize it, double-click the bar to reset

    private static final double GRIP = 12;
    private boolean moving, resizing;
    private double grabX, grabY, anchorL, anchorT;

    private boolean overGrip(double mx, double my) { return mx >= x0 + W - GRIP && mx < x0 + W && my >= y0 + H - GRIP && my < y0 + H; }
    private boolean overBar(double mx, double my) { return mx >= x0 && mx < x0 + W && my >= y0 && my < y0 + 28; }

    /** Zoom bounds: real pixels never under 1 per GUI px, and the window never bigger than the screen. */
    private double minZoom() { return Math.max(0.5, 1 / client.getWindow().getScaleFactor()); }
    private double maxZoom() { return Math.max(1, Math.min(width / (double) W, height / (double) H)); }

    /** At rest the zoom snaps to whole real pixels per GUI px (crisp text): steps of 1 / GUI scale. */
    private double snapZoom(double z) {
        double sf = client.getWindow().getScaleFactor(), n = Math.round(z * sf) / sf;
        return Math.max(minZoom(), Math.min(maxZoom(), n > maxZoom() ? Math.floor(maxZoom() * sf) / sf : n));
    }

    /** Keeps the top bar on the screen: at least 60 px of it visible sideways, never above the top or under the bottom. */
    private void clampWindow() {
        double z = viewZ, left = (width - W * z) / 2 + viewX, top = (height - H * z) / 2 + viewY;
        double nl = Math.max(60 - W * z, Math.min(width - 60, left)), nt = Math.max(0, Math.min(height - 28 * z, top));
        viewX += nl - left; viewY += nt - top;
    }

    private void renderGrip(G g, double mx, double my) {
        if (welcome) return;
        boolean h = overGrip(mx, my) || resizing;
        int c = h ? 0xB0FBC2EB : 0x30FFFFFF;
        double x = x0 + W - 4, y = y0 + H - 4;
        for (int i = 0; i < 3; i++) for (int j = 0; j <= i; j++) g.fill(x - (i - j) * 3 - 1, y - j * 3 - 1, x - (i - j) * 3, y - j * 3, c);   // three dots on a diagonal, like a resize corner
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mx = vx(click.x()), my = vy(click.y());
        if (!welcome && !closing && click.button() == 0 && overGrip(mx, my)) {
            resizing = true;
            anchorL = (width - W * viewZ) / 2 + viewX; anchorT = (height - H * viewZ) / 2 + viewY;   // the top-left corner stays put
            grabX = anchorL + W * viewZ - click.x();
            return true;
        }
        if (super.mouseClicked(click, doubled)) return true;
        if (!closing && click.button() == 0 && overBar(mx, my)) {
            if (doubled) { viewX = viewY = 0; viewZ = 1; relayout(); GambleData.setWindow(0, 0, 1); P.play("click"); return true; }
            moving = true; grabX = click.x() - viewX; grabY = click.y() - viewY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double dx, double dy) {
        if (moving) { viewX = click.x() - grabX; viewY = click.y() - grabY; clampWindow(); return true; }
        if (resizing) {
            double z = Math.max(minZoom(), Math.min(maxZoom(), (click.x() + grabX - anchorL) / W));
            setZoom(z);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (moving || resizing) {
            if (resizing) setZoom(snapZoom(viewZ));
            moving = resizing = false;
            GambleData.setWindow(viewX, viewY, viewZ);
            return true;
        }
        return super.mouseReleased(click);
    }

    private void setZoom(double z) {
        viewZ = z;
        viewX = anchorL - (width - W * z) / 2; viewY = anchorT - (height - H * z) / 2;
        relayout();
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        boolean hit = super.mouseDown(mx, my, button);
        if (tab.equals("games") && sub != null) view(sub).clicked(hit ? focusedDrag : null);
        return hit;
    }

    @Override
    public void close() {
        if (closing) return;
        leaveSub();
        closing = true; closeAt = P.now();
        P.play("close", 0.8);
    }

    private void closeNow() { if (client != null) client.setScreen(null); }

    /** Smoke test hooks (DebugRun). */
    public static void debug(String cmd, String arg) {
        if (cmd.equals("tab")) { tab = arg; sub = null; }
        if (cmd.equals("sub")) { tab = "games"; sub = arg.equals("none") ? null : arg; }
        if (cmd.equals("range")) range = arg;
        GambleScreen s = current();
        if (s != null) { s.chartAt = 0; s.rebuild(); }
    }
}
