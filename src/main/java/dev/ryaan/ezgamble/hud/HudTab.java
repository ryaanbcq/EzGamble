package dev.ryaan.ezgamble.hud;

import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.proto.*;
import dev.ryaan.ezgamble.themes.ThemeSwatches;
import dev.ryaan.ezgamble.themes.Themes;
import dev.ryaan.ezgamble.ui.GambleScreen;
import net.minecraft.client.MinecraftClient;

import static dev.ryaan.ezgamble.hud.HudConfig.*;
import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The HUD tab of the menu: a switch per widget, the presets, then the options (Liquid glass, Auto update, House guard,
 * Auto-seat, Theme) and the bottom buttons Edit layout / Reset / Export (all 24 high, the bottom-row rule). Nine rows of title + caption share one 20 px
 * pitch between y0 + 80 and the buttons at y0 + 262 (9 x 20 = 180 px): every row has the same rhythm whatever it holds.
 */
public final class HudTab {
    private HudTab() {}

    /** Row pitch: title at +1, caption at +11, a 14 px switch at +3, the 10 px swatches at +5. */
    private static final int ROW = 20;
    private static GlassSegmented presets;
    private static GlassButton resetBtn;
    private static ThemeSwatches swatches;
    private static long resetArmed;

    private static double rowY(GambleScreen s, int i) { return s.y0 + 80 + i * ROW; }

    public static void init(GambleScreen s) {
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD;
        for (int i = 0; i < ORDER.length; i++) {
            String id = ORDER[i];
            s.add(new GlassSwitch(rx - 28, rowY(s, i) + 3, 28, 14, () -> HUD.get(id).visible, v -> { HUD.get(id).visible = v; save(); }));
        }
        int r = ORDER.length;
        if (presets == null) presets = new GlassSegmented(0, 0, 18, new String[][]{{"minimal", "Minimal"}, {"standard", "Standard"}, {"streamer", "Streamer"}},
                HudConfig::presetOf, HudConfig::applyPreset);
        presets.place(rx - presets.w, rowY(s, r) + 1);
        s.add(presets);
        s.add(new GlassSwitch(rx - 28, rowY(s, r + 1) + 3, 28, 14, () -> GambleData.lens, GambleData::setLens));
        s.add(new GlassSwitch(rx - 28, rowY(s, r + 2) + 3, 28, 14, () -> GambleData.autoUpdate, GambleData::setAutoUpdate));
        s.add(new GlassSwitch(rx - 28, rowY(s, r + 3) + 3, 28, 14, () -> GambleData.houseGuard, GambleData::setHouseGuard));
        s.add(new GlassSwitch(rx - 28, rowY(s, r + 4) + 3, 28, 14, () -> GambleData.autoSeat, GambleData::setAutoSeat));
        swatches = s.add(new ThemeSwatches(rx - ThemeSwatches.width(), rowY(s, r + 5) + 5));   // theme: six two-tone swatches, applied at once
        double by = s.y0 + GambleScreen.H - GambleScreen.PAD - 24;
        s.add(new GlassButton(lx, by, GambleScreen.W - 2 * GambleScreen.PAD - 74 - 60 - 8, 24, "Edit layout", "PRIMARY",
                () -> MinecraftClient.getInstance().setScreen(new HudEditorScreen())));
        resetBtn = s.add(new GlassButton(rx - 74 - 4 - 60, by, 60, 24, resetArmed > now() ? "Sure?" : "Reset", "DANGER", HudTab::reset));
        s.add(new GlassButton(rx - 74, by, 74, 24, "Export", "GHOST", HudEditorScreen::export));
    }

    /** ProfitScreen.clear: first click arms for 3 s, second click resets. */
    private static void reset() {
        if (now() < resetArmed) { HudConfig.reset(); resetArmed = 0; resetBtn.label = "Reset"; }
        else { resetArmed = now() + 3000; resetBtn.label = "Sure?"; }
    }

    /** One row: the title, the caption under it; the caption is trimmed so it never runs under the control at the right. */
    private static void row(G g, double lx, double y, double room, String title, String desc) {
        g.drawText(title, lx, y + 1, TEXT, false);
        g.drawText(F.trimToWidth(desc, room), lx, y + 11, DIM, false);
    }

    public static void render(GambleScreen s, G g) {
        if (resetArmed != 0 && now() > resetArmed) { resetArmed = 0; if (resetBtn != null) resetBtn.label = "Reset"; }
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD;
        double sw = rx - 28 - 6 - lx;   // the room left of a switch
        for (int i = 0; i < ORDER.length; i++) row(g, lx, rowY(s, i), sw, DEF.get(ORDER[i]).name(), DEF.get(ORDER[i]).desc());   // SettingsScreen.row
        int r = ORDER.length;
        row(g, lx, rowY(s, r), presets == null ? sw : presets.x - 6 - lx, "Layout", presetOf().equals("custom") ? "Your own mix" : "One-click presets");
        row(g, lx, rowY(s, r + 1), sw, "Liquid glass", "a lens on the tabs, drag it");
        row(g, lx, rowY(s, r + 2), sw, "Auto update", "installed when the game closes");
        row(g, lx, rowY(s, r + 3), sw, "House guard", "refuse bets the house can't cover");
        row(g, lx, rowY(s, r + 4), sw, "Auto-seat", "the next in line sits down after a round");
        // the caption names the swatch under the pointer, else the theme in use
        row(g, lx, rowY(s, r + 5), rx - ThemeSwatches.width() - 6 - lx, "Theme", (swatches == null ? Themes.current() : swatches.shown()).name() + ", colours of the whole mod");
    }
}
