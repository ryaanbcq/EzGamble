package dev.ryaan.ezgamble.hud;

import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.ui.GambleScreen;

/** Placeholder until the HUD port lands: the HUD tab of the menu. */
public final class HudTab {
    private HudTab() {}
    public static void init(GambleScreen s) {}
    public static void render(GambleScreen s, G g) { g.drawText("HUD settings: coming soon", s.x0 + GambleScreen.PAD, s.y0 + 84, dev.ryaan.ezgamble.proto.P.MUTE, false); }
}
