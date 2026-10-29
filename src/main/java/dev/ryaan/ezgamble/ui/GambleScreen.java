package dev.ryaan.ezgamble.ui;

import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.Updater;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Main menu (K): header, tabs, and the Games grid. Same geometry as the prototype's GambleScreen. */
public final class GambleScreen extends Screen {
    private static final int W = 380, H = 300, PAD = 14;
    private static final String[] TABS = {"Dashboard", "History", "Games", "HUD"};
    private static final String[][] GAMES = {
            {"blackjack", "Blackjack"}, {"auction", "Auction"}, {"mines", "Mines"}, {"crates", "Crates"},
            {"roulette", "Roulette"}, {"crash", "Crash"}, {"coinflip", "Coinflip"}};

    private static int tab = 2;   // kept between openings, like the prototype
    private int x0, y0;

    public GambleScreen() {
        super(Text.literal("EzGamble"));
        net.minecraft.client.MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.ui(EzGambleMod.OPEN, 1.0f, 0.8f));
    }

    // ---- pop in / out, as in EzAuction

    private static final long OPEN_MS = 280, CLOSE_MS = 220;
    private final long openedAt = System.currentTimeMillis();
    private long closeAt;
    private boolean closing;

    private float pop() {
        long now = System.currentTimeMillis();
        if (closing) { float f = Math.min(1f, (now - closeAt) / (float) CLOSE_MS); return 1f - f * f; }
        float f = Math.min(1f, (now - openedAt) / (float) OPEN_MS);
        return 1f - (1f - f) * (1f - f) * (1f - f);
    }

    private void beginPop(DrawContext g, float p) {
        g.getMatrices().pushMatrix();
        if (p >= 1f) return;
        float cx = x0 + W / 2f, cy = y0 + H / 2f, sc = 0.7f + 0.3f * p;
        g.getMatrices().translate(cx, cy);
        g.getMatrices().scale(sc, sc);
        g.getMatrices().translate(-cx, -cy);
    }

    @Override
    protected void init() {
        x0 = (this.width - W) / 2;
        y0 = (this.height - H) / 2;
        int x = x0 + PAD, y = y0 + 34;
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            int w = this.textRenderer.getWidth(TABS[i]) + 16;
            addDrawableChild(new GlassButton(x, y, w, 16, TABS[i], t == tab ? GlassButton.Kind.PRIMARY : GlassButton.Kind.GHOST,
                    () -> { tab = t; clearChildren(); init(); }));
            x += w + 4;
        }
        addUpdateButton();
    }

    /** Top right of the header: "Update 0.1.1" while one is waiting, then a quiet status. */
    private void addUpdateButton() {
        if (Updater.state() != Updater.State.AVAILABLE) return;
        String label = "Update " + Updater.latest();
        int w = this.textRenderer.getWidth(label) + 16;
        addDrawableChild(new GlassButton(x0 + W - PAD - w, y0 + PAD - 4, w, 16, label, GlassButton.Kind.PRIMARY,
                () -> { Updater.download(); clearChildren(); init(); }));
    }

    @Override
    public void tick() {
        Updater.State s = Updater.state();
        if (s != shownUpdate) { shownUpdate = s; clearChildren(); init(); }
    }

    private Updater.State shownUpdate = Updater.state();

    private void renderUpdateStatus(DrawContext g) {
        String t = switch (Updater.state()) {
            case DOWNLOADING -> "Downloading " + Updater.latest() + "...";
            case READY -> "Restart Minecraft to finish the update";
            default -> "";
        };
        if (t.isEmpty()) return;
        g.drawText(this.textRenderer, t, x0 + W - PAD - this.textRenderer.getWidth(t), y0 + PAD + 1,
                Updater.state() == Updater.State.READY ? Glass.GREEN : Glass.MUTE, false);
    }

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
        float p = pop();
        if (this.client != null && this.client.world != null) g.fill(0, 0, this.width, this.height, ((int) (0x8C * p) << 24) | 0x0A0710);
        else super.renderBackground(g, mouseX, mouseY, delta);
        beginPop(g, p);
        Glass.panel(g, x0, y0, W, H, false);
        Glass.gradientText(g, this.textRenderer, "EZ GAMBLE", x0 + PAD, y0 + PAD, true);
        g.drawText(this.textRenderer, "by ZKR5", x0 + PAD + this.textRenderer.getWidth("EZ GAMBLE") + 8, y0 + PAD + 1, Glass.MUTE, false);
        g.getMatrices().popMatrix();
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        if (closing && System.currentTimeMillis() - closeAt >= CLOSE_MS) { closeNow(); return; }
        beginPop(g, pop());
        super.render(g, mouseX, mouseY, delta);
        renderUpdateStatus(g);
        if (tab == 2) renderGames(g, mouseX, mouseY);
        else g.drawText(this.textRenderer, "Soon", x0 + PAD, y0 + 64, Glass.MUTE, false);
        g.getMatrices().popMatrix();
    }

    /** 4 x 2 grid of tiles: icon, name, a "›" on hover. */
    private void renderGames(DrawContext g, int mx, int my) {
        int cols = 4, gap = 8, tw = (W - 2 * PAD - gap * (cols - 1)) / cols, th = 64, top = y0 + 62;
        for (int i = 0; i < GAMES.length; i++) {
            int x = x0 + PAD + (i % cols) * (tw + gap), y = top + (i / cols) * (th + gap);
            boolean hover = mx >= x && mx < x + tw && my >= y && my < y + th;
            Glass.rounded(g, x, y, tw, th, hover ? 0x18FFFFFF : Glass.FILL_SOFT);
            Glass.outline(g, x, y, tw, th, hover ? 0x40FFFFFF : Glass.BORDER_SOFT);
            Identifier icon = Identifier.of("ezgamble", "textures/gui/games/" + GAMES[i][0] + ".png");
            g.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x + (tw - 32) / 2, y + 8, 0, 0, 32, 32, 32, 32);
            String name = GAMES[i][1];
            g.drawText(this.textRenderer, name, x + (tw - this.textRenderer.getWidth(name)) / 2, y + 46, Glass.WHITE, false);
            if (hover) g.drawText(this.textRenderer, "›", x + tw - 10, y + 46, Glass.PINK, false);
        }
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
