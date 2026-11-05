package dev.ryaan.ezgamble.hud;

import dev.ryaan.ezgamble.proto.*;
import dev.ryaan.ezgamble.ui.GambleScreen;
import net.minecraft.client.MinecraftClient;

import static dev.ryaan.ezgamble.hud.HudConfig.*;
import static dev.ryaan.ezgamble.proto.P.*;

/** The HUD tab of the menu: a switch per widget, the presets, EDIT LAYOUT / Reset / Export. */
public final class HudTab {
    private HudTab() {}

    private static GlassSegmented presets;
    private static GlassButton resetBtn;
    private static long resetArmed;
    /** A widget row (switch + two lines of text) and an option row under the presets (title and hint, tighter). */
    private static final int ROW = 24, OPT = 19;

    public static void init(GambleScreen s) {
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD;
        for (int i = 0; i < ORDER.length; i++) {
            String id = ORDER[i];
            s.add(new GlassSwitch(rx - 28, s.y0 + 80 + i * ROW + 4, 28, 14, () -> HUD.get(id).visible, v -> { HUD.get(id).visible = v; save(); }));
        }
        if (presets == null) presets = new GlassSegmented(0, 0, 18, new String[][]{{"minimal", "Minimal"}, {"standard", "Standard"}, {"streamer", "Streamer"}},
                HudConfig::presetOf, HudConfig::applyPreset);
        presets.place(rx - presets.w, s.y0 + 80 + ORDER.length * ROW + 6);
        s.add(presets);
        // four option rows under the presets, OPT px each (compact: widget rows shrank to ROW so Auto-seat fits under House guard, above the bottom buttons)
        double oy = s.y0 + 80 + ORDER.length * ROW + 6 + 24;
        s.add(new GlassSwitch(rx - 28, oy + 3, 28, 14, () -> dev.ryaan.ezgamble.data.GambleData.lens, dev.ryaan.ezgamble.data.GambleData::setLens));
        s.add(new GlassSwitch(rx - 28, oy + OPT + 3, 28, 14, () -> dev.ryaan.ezgamble.data.GambleData.autoUpdate, dev.ryaan.ezgamble.data.GambleData::setAutoUpdate));
        s.add(new GlassSwitch(rx - 28, oy + 2 * OPT + 3, 28, 14, () -> dev.ryaan.ezgamble.data.GambleData.houseGuard, dev.ryaan.ezgamble.data.GambleData::setHouseGuard));
        s.add(new GlassSwitch(rx - 28, oy + 3 * OPT + 3, 28, 14, () -> dev.ryaan.ezgamble.data.GambleData.autoSeat, dev.ryaan.ezgamble.data.GambleData::setAutoSeat));
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

    public static void render(GambleScreen s, G g) {
        if (resetArmed != 0 && now() > resetArmed) { resetArmed = 0; if (resetBtn != null) resetBtn.label = "Reset"; }
        double lx = s.x0 + GambleScreen.PAD;
        for (int i = 0; i < ORDER.length; i++) {   // SettingsScreen.row
            String id = ORDER[i];
            double y = s.y0 + 80 + i * ROW;
            g.drawText(DEF.get(id).name(), lx, y + 2, TEXT, false);
            g.drawText(DEF.get(id).desc(), lx, y + 12, DIM, false);
        }
        double y = s.y0 + 80 + ORDER.length * ROW + 6;
        g.drawText("Layout", lx, y + 1, TEXT, false);
        g.drawText(presetOf().equals("custom") ? "Your own mix" : "One-click presets", lx, y + 12, DIM, false);
        y += 24;
        g.drawText("Liquid glass", lx, y + 1, TEXT, false);
        g.drawText("a lens on the tabs, drag it", lx, y + 11, DIM, false);
        y += OPT;
        g.drawText("Auto update", lx, y + 1, TEXT, false);
        g.drawText("installed when the game closes", lx, y + 11, DIM, false);
        y += OPT;
        g.drawText("House guard", lx, y + 1, TEXT, false);
        g.drawText("refuse bets the house can't cover", lx, y + 11, DIM, false);
        y += OPT;
        g.drawText("Auto-seat", lx, y + 1, TEXT, false);
        g.drawText("the next in line sits down after a round", lx, y + 11, DIM, false);
    }
}
