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

    public static void init(GambleScreen s) {
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD;
        for (int i = 0; i < ORDER.length; i++) {
            String id = ORDER[i];
            s.add(new GlassSwitch(rx - 28, s.y0 + 80 + i * 28 + 4, 28, 14, () -> HUD.get(id).visible, v -> { HUD.get(id).visible = v; save(); }));
        }
        if (presets == null) presets = new GlassSegmented(0, 0, 18, new String[][]{{"minimal", "Minimal"}, {"standard", "Standard"}, {"streamer", "Streamer"}},
                HudConfig::presetOf, HudConfig::applyPreset);
        presets.place(rx - presets.w, s.y0 + 80 + ORDER.length * 28 + 6);
        s.add(presets);
        s.add(new GlassSwitch(rx - 28, s.y0 + 80 + ORDER.length * 28 + 34 + 4, 28, 14, () -> dev.ryaan.ezgamble.data.GambleData.lens, dev.ryaan.ezgamble.data.GambleData::setLens));
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
            double y = s.y0 + 80 + i * 28;
            g.drawText(DEF.get(id).name(), lx, y + 2, TEXT, false);
            g.drawText(DEF.get(id).desc(), lx, y + 13, DIM, false);
        }
        double y = s.y0 + 80 + ORDER.length * 28 + 6;
        g.drawText("Layout", lx, y + 1, TEXT, false);
        g.drawText(presetOf().equals("custom") ? "Your own mix" : "One-click presets", lx, y + 12, DIM, false);
        y += 28;
        g.drawText("Liquid glass", lx, y + 2, TEXT, false);
        g.drawText("a lens on the tabs, drag it", lx, y + 13, DIM, false);
    }
}
