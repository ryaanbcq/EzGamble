package dev.ryaan.ezgamble.themes;

import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Vec;
import dev.ryaan.ezgamble.ui.Glass;

import java.util.List;

/**
 * The theme engine: six presets, {@link #apply(Theme)} writes the palette into P and Glass (every use site reads the
 * fields each frame, so the switch is instant) and bumps {@link #ver}, which the colour-keyed caches (Vec) put in
 * their keys. The chosen id is persisted by GambleData (config.json "theme") and applied in GambleData.load.
 */
public final class Themes {
    private Themes() {}

    /** The current colours, Ryan's Outmind look: the default and the values the constants had before themes. */
    public static final Theme LAVENDER = new Theme("lavender", "Lavender", 0xFFA18CD1, 0xFFFBC2EB, 0xFFCFC8DE, 0xFFB8AFCC, 0xFF9C93B5, 0xFF221836, 0xFF0C0A14);
    public static final List<Theme> ALL = List.of(
            LAVENDER,
            new Theme("emerald", "Emerald", 0xFF3DBE8A, 0xFFB9F2D0, 0xFFCDE4D8, 0xFFA9C8B8, 0xFF8BAA9A, 0xFF152A20, 0xFF08110C),
            new Theme("gold", "Gold Rush", 0xFFD9A441, 0xFFFFE2A8, 0xFFE6DBC8, 0xFFC9BBA0, 0xFFA89A80, 0xFF2A2210, 0xFF110D05),
            new Theme("crimson", "Crimson", 0xFFD24A64, 0xFFFFB3C6, 0xFFE4CCD3, 0xFFC7A8B3, 0xFFA88A95, 0xFF2A1218, 0xFF11070A),
            new Theme("ocean", "Ocean", 0xFF4A9BD9, 0xFFA8E6F5, 0xFFC9DCE8, 0xFFA8BFD0, 0xFF8BA3B5, 0xFF12202E, 0xFF070D14),
            new Theme("mono", "Mono", 0xFF9A9AA6, 0xFFFFFFFF, 0xFFDADADF, 0xFFB4B4BC, 0xFF8E8E98, 0xFF1E1E24, 0xFF0B0B0E));

    /** Bumped by every apply: a cache keyed on colours adds it to its key (Vec) so an old sprite is never shown. */
    public static int ver;
    private static Theme current = LAVENDER;

    public static Theme current() { return current; }

    /** The preset with this id, Lavender when unknown (an old or hand-edited config). */
    public static Theme byId(String id) {
        if (id != null) for (Theme t : ALL) if (t.id().equals(id)) return t;
        return LAVENDER;
    }

    public static void apply(String id) { apply(byId(id)); }

    /** Writes the theme into the palette fields of P and Glass; everything that draws reads them each frame. */
    public static void apply(Theme t) {
        current = t;
        P.LAV = Glass.LAV = t.lav(); P.PINK = Glass.PINK = t.pink();
        P.TEXT = Glass.TEXT = t.text(); P.MUTE = Glass.MUTE = t.mute(); P.DIM = Glass.DIM = t.dim();
        // the opaque panel constants keep their historical alphas over the theme's tints; the border is 30 % pink
        P.PANEL_TOP = Glass.PANEL_TOP = P.alpha(t.panelTop(), 0xF6 / 255.0); P.PANEL_BOT = Glass.PANEL_BOT = P.alpha(t.panelBot(), 0xF8 / 255.0);
        P.HUD_TOP = Glass.HUD_TOP = P.alpha(t.panelTop(), 0xDC / 255.0); P.HUD_BOT = Glass.HUD_BOT = P.alpha(t.panelBot(), 0xE6 / 255.0);
        P.BORDER = Glass.BORDER = P.alpha(t.pink(), 0.3);
        ver++;
        Vec.clear();   // the chips, coins and wheel sprites were painted in the old colours
    }
}
