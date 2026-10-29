package dev.ryaan.ezgamble.ui;

import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.Updater;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Stats;
import dev.ryaan.ezgamble.data.Txn;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Main menu (K). Same geometry as the prototype's GambleScreen: 380 x 300, header, tabs at y0+33, status row at y0+56, page from y0+80. */
public final class GambleScreen extends Screen {
    private static final int W = 380, H = 300, PAD = 14;
    private static final String[][] GAMES = {
            {"blackjack", "Blackjack"}, {"auction", "Auction"}, {"mines", "Mines"}, {"crates", "Crates"},
            {"roulette", "Roulette"}, {"crash", "Crash"}, {"coinflip", "Coinflip"}};
    /** Dashboard greetings: before the name, after it. Never the same twice in a row. */
    private static final String[][] GREETINGS = {
            {"Welcome back, ", ""}, {"Feeling lucky, ", "?"}, {"The table is yours, ", ""}, {"Place your bets, ", ""},
            {"Back for more, ", "?"}, {"Let's roll, ", ""}, {"Luck looks good on you, ", ""}, {"Good to see you, ", ""}};
    private static int lastGreeting = -1;

    // kept between openings, like the prototype
    private static String tab = "dashboard", range = "session";

    private int x0, y0;
    private boolean welcome = GambleData.name == null;
    private String[] greeting = pickGreeting();
    private long titleAt = System.currentTimeMillis(), chartAt = System.currentTimeMillis();
    private final Map<String, GlassSegmented> segs = new HashMap<>();
    private GlassField nameField;
    private boolean builtMode = GambleData.mode;
    private Updater.State shownUpdate = Updater.state();

    public GambleScreen() {
        super(Text.literal("EzGamble"));
        Updater.recheck();
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(EzGambleMod.OPEN, 1.0f, 0.8f));
    }

    private static String[] pickGreeting() {
        int h = LocalTime.now().getHour();
        String[] timed = {h < 5 ? "Late night, " : h < 12 ? "Good morning, " : h < 18 ? "Good afternoon, " : "Good evening, ", ""};
        int i;
        do i = ThreadLocalRandom.current().nextInt(GREETINGS.length + 1); while (i == lastGreeting);
        lastGreeting = i;
        return i == GREETINGS.length ? timed : GREETINGS[i];
    }

    // ---- pop in / out, as in EzAuction

    private static final long OPEN_MS = 280, CLOSE_MS = 220;
    private final long openedAt = System.currentTimeMillis();
    private long closeAt;
    private boolean closing;

    private float pop() {
        long now = System.currentTimeMillis();
        if (closing) { float f = Math.min(1f, (now - closeAt) / (float) CLOSE_MS); return 1f - f * f; }
        return Ui.easeOut((now - openedAt) / (float) OPEN_MS);
    }

    private void beginPop(DrawContext g, float p) {
        g.getMatrices().pushMatrix();
        if (p >= 1f) return;
        float cx = x0 + W / 2f, cy = y0 + H / 2f, sc = 0.7f + 0.3f * p;
        g.getMatrices().translate(cx, cy);
        g.getMatrices().scale(sc, sc);
        g.getMatrices().translate(-cx, -cy);
    }

    // ---- layout

    @Override
    protected void init() {
        x0 = (this.width - W) / 2;
        y0 = (this.height - H) / 2;
        int lx = x0 + PAD, rx = x0 + W - PAD;
        builtMode = GambleData.mode;
        if (welcome) {   // name field + Continue, centred; nothing else until it is answered
            int fw = 150, fx = x0 + (W - fw) / 2;
            String prev = nameField != null ? nameField.text() : MinecraftClient.getInstance().getSession().getUsername();
            nameField = new GlassField(this.textRenderer, fx, y0 + 150, fw, 22, prev, 24);
            addDrawableChild(nameField.field);
            setInitialFocus(nameField.field);
            addDrawableChild(new GlassButton(fx, y0 + 182, fw, 22, "Continue", GlassButton.Kind.PRIMARY, this::submitName));
            return;
        }
        addDrawableChild(new GlassSwitch(rx - 28, y0 + PAD - 3, 28, 14, () -> GambleData.mode, GambleData::setMode));
        GlassSegmented tabs = seg("tabs", new String[][]{{"dashboard", "Dashboard"}, {"transactions", "History"}, {"games", "Games"}, {"hud", "HUD"}},
                () -> tab, id -> {
                    if (id.equals("dashboard")) greeting = pickGreeting();
                    titleAt = chartAt = System.currentTimeMillis();
                    tab = id;
                    rebuild();
                });
        tabs.place(lx, y0 + 33);
        addDrawableChild(tabs);
        if (tab.equals("dashboard")) {
            GlassSegmented r = seg("range", new String[][]{{"session", "Session"}, {"1h", "1h"}, {"24h", "24h"}, {"all", "All"}},
                    () -> range, v -> { range = v; chartAt = System.currentTimeMillis(); });
            r.place(rx - r.getWidth(), y0 + 33);
            addDrawableChild(r);
        }
        if (!GambleData.mode && !tab.equals("games"))
            addDrawableChild(new GlassButton(rx - 54, y0 + 58, 52, 14, "Turn on", GlassButton.Kind.PRIMARY, () -> GambleData.setMode(true)));
        if (Updater.state() == Updater.State.AVAILABLE) {
            String label = "Update " + Updater.latest();
            int w = this.textRenderer.getWidth(label) + 16, lw = this.textRenderer.getWidth("Gambling mode");
            addDrawableChild(new GlassButton(rx - 28 - 6 - lw - 8 - w, y0 + PAD - 4, w, 16, label, GlassButton.Kind.PRIMARY,
                    () -> { Updater.download(); rebuild(); }));
        }
    }

    /** Segmented controls survive rebuilds so their pill keeps sliding instead of jumping. */
    private GlassSegmented seg(String key, String[][] items, java.util.function.Supplier<String> get, java.util.function.Consumer<String> set) {
        return segs.computeIfAbsent(key, k -> new GlassSegmented(18, items, get, set));
    }

    private void rebuild() { clearChildren(); init(); }

    private void submitName() {
        String n = nameField.text().trim();
        GambleData.setName(n.isEmpty() ? MinecraftClient.getInstance().getSession().getUsername() : n);
        welcome = false;
        greeting = pickGreeting();
        titleAt = System.currentTimeMillis();
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(EzGambleMod.PAY, 1.1f, 0.8f));
        rebuild();
    }

    @Override
    public void tick() {
        if (builtMode != GambleData.mode) rebuild();
        if (Updater.state() != shownUpdate) { shownUpdate = Updater.state(); rebuild(); }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (welcome && (input.key() == 257 || input.key() == 335)) { submitName(); return true; }   // Enter
        return super.keyPressed(input);
    }

    // ---- drawing

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
        float p = pop();
        if (this.client != null && this.client.world != null) {
            g.applyBlur();   // the frosted backdrop (vanilla menu blur), then a light veil: the glass does the separating
            g.fill(0, 0, this.width, this.height, ((int) (0x55 * p) << 24) | 0x0A0710);
        } else super.renderBackground(g, mouseX, mouseY, delta);
        beginPop(g, p);
        Liquid.glass(g, x0, y0, W, H, 12, 0.84f, 1f);
        renderTitle(g);
        if (!welcome) {
            String lbl = "Gambling mode";
            g.drawText(this.textRenderer, lbl, x0 + W - PAD - 28 - 6 - this.textRenderer.getWidth(lbl), y0 + PAD, GambleData.mode ? Glass.TEXT : Glass.MUTE, false);
        } else nameField.drawBox(g);
        g.getMatrices().popMatrix();
    }

    /** "Welcome back, Ryan" on the Dashboard, the EZ GAMBLE wordmark elsewhere; fades and rises in on each change. */
    private void renderTitle(DrawContext g) {
        int x = x0 + PAD;
        float f = Ui.easeOut((System.currentTimeMillis() - titleAt) / 260f);
        int y = y0 + PAD + Math.round((1 - f) * 4);
        float a = Math.max(0.02f, f);
        if (GambleData.name != null && !welcome && tab.equals("dashboard")) {
            String pre = greeting[0], suf = greeting[1], name = GambleData.name;
            g.drawText(this.textRenderer, pre, x, y, Glass.alpha(Glass.WHITE, a), true);
            Ui.gradientText(g, name, x + this.textRenderer.getWidth(pre), y, true, a);
            if (!suf.isEmpty()) g.drawText(this.textRenderer, suf, x + this.textRenderer.getWidth(pre + name), y, Glass.alpha(Glass.WHITE, a), true);
            return;
        }
        Ui.gradientText(g, "EZ GAMBLE", x, y, true, a);
        g.drawText(this.textRenderer, "by ZKR5", x + this.textRenderer.getWidth("EZ GAMBLE") + 8, y + 1, Glass.alpha(Glass.MUTE, a), false);
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        if (closing && System.currentTimeMillis() - closeAt >= CLOSE_MS) { closeNow(); return; }
        beginPop(g, pop());
        try {
            if (welcome) { renderWelcome(g); super.render(g, mouseX, mouseY, delta); return; }
            if (!tab.equals("games")) renderStatus(g);
            super.render(g, mouseX, mouseY, delta);
            switch (tab) {
                case "dashboard" -> renderDashboard(g, mouseX, mouseY);
                case "games" -> renderGames(g, mouseX, mouseY);
                default -> g.drawText(this.textRenderer, "Coming in the next update.", x0 + PAD, y0 + 84, Glass.MUTE, false);
            }
            renderUpdateStatus(g);
        } finally {
            g.getMatrices().popMatrix();
        }
    }

    private void renderWelcome(DrawContext g) {
        int cx = x0 + W / 2;
        String t1 = "Welcome to EzGamble";
        g.getMatrices().pushMatrix();
        g.getMatrices().translate(cx - this.textRenderer.getWidth(t1), y0 + 72);
        g.getMatrices().scale(2, 2);
        Ui.gradientText(g, t1, 0, 0, true, 1);
        g.getMatrices().popMatrix();
        String q = "What should we call you?", h = "Shown at the top of your dashboard.";
        g.drawText(this.textRenderer, q, cx - this.textRenderer.getWidth(q) / 2, y0 + 120, Glass.WHITE, true);
        g.drawText(this.textRenderer, h, cx - this.textRenderer.getWidth(h) / 2, y0 + 133, Glass.MUTE, false);
    }

    /** y0+56..74: the recording line, or the red OFF banner. */
    private void renderStatus(DrawContext g) {
        int lx = x0 + PAD, rx = x0 + W - PAD, y = y0 + 56;
        if (!GambleData.mode) {
            Ui.card(g, lx, y, rx - lx, 18, "red");
            g.drawText(this.textRenderer, "✖", lx + 6, y + 5, Glass.RED, false);
            g.drawText(this.textRenderer, "Gambling mode is off", lx + 18, y + 5, Glass.RED, false);
            return;
        }
        Session s = GambleData.current();
        long now = System.currentTimeMillis();
        Glass.dot(g, lx, y + 6, Glass.alpha(Glass.GREEN, 0.55f + 0.45f * Ui.pulse(now)));
        g.drawText(this.textRenderer, "Recording", lx + 10, y + 6, Glass.WHITE, false);
        if (s != null) Ui.pillsRight(g, rx, y + 2, new Object[][]{{"Session", Ui.clock(now - s.start)}, {"Bets", String.valueOf(s.txns.size())}});
    }

    private void renderDashboard(DrawContext g, int mx, int my) {
        int lx = x0 + PAD, rx = x0 + W - PAD;
        List<Txn> list = GambleData.inRange(range);
        Stats st = Stats.of(list);
        int half = (rx - lx) / 2, third = (rx - lx) / 3;
        // hero: profit in big digits, volume and count beside it
        int y = y0 + 80;
        Ui.card(g, lx, y, rx - lx, 46, "accent");
        g.drawText(this.textRenderer, "Profit", lx + 10, y + 8, Glass.MUTE, false);
        g.getMatrices().pushMatrix();
        g.getMatrices().translate(lx + 10, y + 20);
        g.getMatrices().scale(2, 2);
        g.drawText(this.textRenderer, Money.signed(st.profit()), 0, 0, st.profit() >= 0 ? Glass.GREEN : Glass.RED, true);
        g.getMatrices().popMatrix();
        int q = (rx - lx - half) / 2;
        Ui.tile(g, lx + half + 8, y + 8, "Volume", Money.format(st.volume()), Glass.WHITE);
        Ui.tile(g, lx + half + 8 + q, y + 8, "Transactions", String.valueOf(st.n()), Glass.WHITE);
        y += 50;
        Ui.card(g, lx, y, rx - lx, 34, "soft");
        Ui.tile(g, lx + 10, y + 6, "Win rate", st.n() > 0 ? Math.round(st.rate() * 100) + "%" : "-", st.n() > 0 ? Glass.WHITE : Glass.DIM);
        Ui.tile(g, lx + 10 + third, y + 6, "Best win", st.best() == null ? "-" : Money.signed(st.best()), st.best() == null ? Glass.DIM : Glass.GREEN);
        Ui.tile(g, lx + 10 + 2 * third, y + 6, "Worst loss", st.worst() == null ? "-" : Money.signed(st.worst()), st.worst() == null ? Glass.DIM : Glass.RED);
        int cy = y + 40, ch = y0 + H - PAD - cy;
        Ui.card(g, lx, cy, rx - lx, ch, "chart");
        if (list.isEmpty()) { g.drawText(this.textRenderer, "No transaction yet.", lx + 8, cy + ch / 2 - 4, Glass.DIM, false); return; }
        float reveal = Ui.easeOut((System.currentTimeMillis() - chartAt) / 600f);
        long[] deltas = new long[list.size()];
        for (int i = 0; i < deltas.length; i++) deltas[i] = list.get(i).delta();
        ProfitChart.Result r = ProfitChart.draw(g, deltas, lx, cy, rx - lx, ch, mx, my, false, 0, true, reveal);
        if (r.hover() > 0) {
            Txn t = list.get(r.hover() - 1);
            ProfitChart.tooltip(g, t.game(), Money.signed(t.delta()), t.delta() >= 0 ? Glass.GREEN : Glass.RED, r.px()[r.hover()], cy, lx, rx,
                    Ui.dateS(t.t()), t.bet() != null ? "Bet " + Money.format(t.bet()) : "");
        }
    }

    /** 4 x 2 grid of tiles: icon, name, a "›" on hover. */
    private void renderGames(DrawContext g, int mx, int my) {
        int cols = 4, gap = 8, tw = (W - 2 * PAD - gap * (cols - 1)) / cols, th = 64, top = y0 + 62;
        for (int i = 0; i < GAMES.length; i++) {
            int x = x0 + PAD + (i % cols) * (tw + gap), y = top + (i / cols) * (th + gap);
            boolean hover = mx >= x && mx < x + tw && my >= y && my < y + th;
            Liquid.smooth(g, x, y, tw, th, 7, hover ? 0x18FFFFFF : 0x0DFFFFFF, hover ? Glass.alpha(Glass.PINK, 0.6f) : 0x14FFFFFF);
            Identifier icon = Identifier.of("ezgamble", "textures/gui/games/" + GAMES[i][0] + ".png");
            g.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x + (tw - 32) / 2, y + 8, 0, 0, 32, 32, 32, 32);
            String name = GAMES[i][1];
            g.drawText(this.textRenderer, name, x + (tw - this.textRenderer.getWidth(name)) / 2, y + 46, Glass.WHITE, false);
            if (hover) g.drawText(this.textRenderer, "›", x + tw - 10, y + 46, Glass.PINK, false);
        }
    }

    private void renderUpdateStatus(DrawContext g) {
        String t = switch (Updater.state()) {
            case DOWNLOADING -> "Downloading " + Updater.latest() + "...";
            case READY -> "Restart Minecraft to finish the update";
            default -> "";
        };
        if (t.isEmpty()) return;
        g.drawText(this.textRenderer, t, x0 + (W - this.textRenderer.getWidth(t)) / 2, y0 + H + 6,
                Updater.state() == Updater.State.READY ? Glass.GREEN : Glass.MUTE, true);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeAt = System.currentTimeMillis();
        if (this.client != null) this.client.getSoundManager().play(PositionedSoundInstance.ui(EzGambleMod.CLOSE, 1.0f, 0.8f));
    }

    private void closeNow() {
        if (this.client != null) this.client.setScreen(null);
    }
}
