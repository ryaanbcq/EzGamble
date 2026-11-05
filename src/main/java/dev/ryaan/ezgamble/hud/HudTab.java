package dev.ryaan.ezgamble.hud;

import dev.ryaan.ezgamble.proto.*;
import dev.ryaan.ezgamble.themes.ThemeSwatches;
import dev.ryaan.ezgamble.ui.GambleScreen;
import net.minecraft.client.MinecraftClient;

import static dev.ryaan.ezgamble.hud.HudConfig.*;
import static dev.ryaan.ezgamble.proto.P.*;

/** The HUD tab of the menu: a switch per widget, the presets, Liquid glass, Auto update, Theme, then EDIT LAYOUT / Reset / Export. */
public final class HudTab {
    private HudTab() {}

    /** Row pitch: 7 rows of title + caption (2 and 13 px down) between y0 + 80 and the bottom buttons at y0 + 262. Was 28 with 6 rows. */
    private static final int ROW = 24;
    private static GlassSegmented presets;
    private static GlassButton resetBtn;
    private static ThemeSwatches swatches;
    private static long resetArmed;

    private static double rowY(GambleScreen s, int i) { return s.y0 + 80 + i * ROW; }

    public static void init(GambleScreen s) {
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD;
        for (int i = 0; i < ORDER.length; i++) {
            String id = ORDER[i];
            s.add(new GlassSwitch(rx - 28, rowY(s, i) + 4, 28, 14, () -> HUD.get(id).visible, v -> { HUD.get(id).visible = v; save(); }));
        }
        if (presets == null) presets = new GlassSegmented(0, 0, 18, new String[][]{{"minimal", "Minimal"}, {"standard", "Standard"}, {"streamer", "Streamer"}},
                HudConfig::presetOf, HudConfig::applyPreset);
        presets.place(rx - presets.w, rowY(s, ORDER.length) + 3);
        s.add(presets);
        s.add(new GlassSwitch(rx - 28, rowY(s, ORDER.length + 1) + 4, 28, 14, () -> dev.ryaan.ezgamble.data.GambleData.lens, dev.ryaan.ezgamble.data.GambleData::setLens));
        s.add(new GlassSwitch(rx - 28, rowY(s, ORDER.length + 2) + 4, 28, 14, () -> dev.ryaan.ezgamble.data.GambleData.autoUpdate, dev.ryaan.ezgamble.data.GambleData::setAutoUpdate));
        swatches = s.add(new ThemeSwatches(rx - ThemeSwatches.width(), rowY(s, ORDER.length + 3) + 6));   // theme: six two-tone swatches, applied at once
        double by = s.y0 + GambleScreen.H - GambleScreen.PAD - 24;
        s.add(new GlassButton(lx, by, GambleScreen.W - 2 * GambleScreen.PAD - 74 - 60 - 8, 24, "EDIT LAYOUT", "PRIMARY",
                () -> MinecraftClient.getInstance().setScreen(new HudEditorScreen())));
        resetBtn = s.add(new GlassButton(rx - 74 - 4 - 60, by + 3, 60, 18, resetArmed > now() ? "Sure?" : "Reset", "DANGER", HudTab::reset));
        s.add(new GlassButton(rx - 74, by, 74, 24, "Export", "GHOST", HudEditorScreen::export));
    }

    /** ProfitScreen.clear: first click arms for 3 s, second click resets. */
    private static void reset() {
        if (now() < resetArmed) { HudConfig.reset(); resetArmed = 0; resetBtn.label = "Reset"; }
        else { resetArmed = now() + 3000; resetBtn.label = "Sure?"; }
    }

    private static void row(G g, double lx, double y, String title, String desc) {
        g.drawText(title, lx, y + 2, TEXT, false);
        g.drawText(desc, lx, y + 13, DIM, false);
    }

    public static void render(GambleScreen s, G g) {
        if (resetArmed != 0 && now() > resetArmed) { resetArmed = 0; if (resetBtn != null) resetBtn.label = "Reset"; }
        double lx = s.x0 + GambleScreen.PAD;
        for (int i = 0; i < ORDER.length; i++) row(g, lx, rowY(s, i), DEF.get(ORDER[i]).name(), DEF.get(ORDER[i]).desc());   // SettingsScreen.row
        row(g, lx, rowY(s, ORDER.length), "Layout", presetOf().equals("custom") ? "Your own mix" : "One-click presets");
        row(g, lx, rowY(s, ORDER.length + 1), "Liquid glass", "a lens on the tabs, drag it");
        row(g, lx, rowY(s, ORDER.length + 2), "Auto update", "installed when the game closes");
        // the caption names the swatch under the pointer, else the theme in use
        row(g, lx, rowY(s, ORDER.length + 3), "Theme", (swatches == null ? dev.ryaan.ezgamble.themes.Themes.current() : swatches.shown()).name() + ", colours of the whole mod");
    }
}
