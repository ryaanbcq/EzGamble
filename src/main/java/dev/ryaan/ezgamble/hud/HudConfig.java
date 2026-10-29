package dev.ryaan.ezgamble.hud;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.proto.ColorSwatches;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Prototype HudConfig (exported JSON, positions relative 0..1), persisted to config/ezgamble/hud.json in the same
 * format as the prototype's export (version 2).
 * <p>
 * Three pieces instead of six cards:
 *  - session: ONE modular card, the anchor. Its style is the HUD's style.
 *  - last: a toast that pops out of the card for 3.5 s after each bet, attached below (above when the card sits in
 *    the lower half). Not placed by hand, uses the card's style.
 *  - pay: the payments card (your name, who just paid you), placed on its own.
 */
public final class HudConfig {
    private HudConfig() {}

    /** Unscaled widths, GUI pixels; full = extra width when numbers are "1 500 000". */
    public record Def(int w, int full, String name, String desc) {}
    public static final Map<String, Def> DEF = new LinkedHashMap<>();
    static {
        DEF.put("session", new Def(132, 36, "Session card", "profit, timer, curve, win rate"));
        DEF.put("last", new Def(132, 36, "Last bet", "pops out after each bet"));
        DEF.put("pay", new Def(150, 30, "Payments", "your name, who just paid you"));
    }
    public static final String[] ORDER = {"session", "last", "pay"};
    public static final int CURVE_H = 28, WINRATE_H = 13, TOTAL_H = 13;
    public static final Object[] COLORS = ColorSwatches.COLORS;
    public static final double[] SCALES = {0.5, 0.75, 1, 1.25, 1.5, 2};

    public static final class Rows {
        public boolean curve, winrate, total;
        Rows(boolean c, boolean w, boolean t) { curve = c; winrate = w; total = t; }
        Rows copy() { return new Rows(curve, winrate, total); }
    }

    public static final class Style {
        public boolean visible = true, autoSignColor = true;
        public double x, y, scale = 1, background = 1;
        /** "gradient" or an Integer 0xAARRGGBB. */
        public Object color = "gradient";
        public String numbers = "short";
        public Rows rows;
        Style(double x, double y) { this.x = x; this.y = y; }
        public boolean gradient() { return "gradient".equals(color); }
        public int rgb() { return color instanceof Integer i ? i : 0xFFFFFFFF; }
    }

    public static final class Data {
        public Style session, last, pay;
        public Style get(String id) { return id.equals("session") ? session : id.equals("last") ? last : pay; }
    }

    public record Preset(boolean curve, boolean winrate, boolean total, double scale) {}
    /** Rows + size; positions are kept. */
    public static final Map<String, Preset> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put("minimal", new Preset(false, false, false, 1));
        PRESETS.put("standard", new Preset(true, true, false, 1));
        PRESETS.put("streamer", new Preset(true, true, true, 1.25));
    }

    public static Data HUD = load();

    public static Style style(String id) { return id.equals("last") ? HUD.session : HUD.get(id); }
    public static int baseW(String id) { return DEF.get(id).w() + (style(id).numbers.equals("full") ? DEF.get(id).full() : 0); }
    public static int baseH(String id) {
        if (id.equals("pay")) return 34;
        if (id.equals("last")) return 24;
        Rows r = HUD.session.rows;
        boolean any = r.curve || r.winrate || r.total;
        return 37 + (any ? 4 + (r.curve ? CURVE_H : 0) + (r.winrate ? WINRATE_H : 0) + (r.total ? TOTAL_H : 0) + 2 : 0);
    }

    public static double sw() { return MinecraftClient.getInstance().getWindow().getScaledWidth(); }
    public static double sh() { return MinecraftClient.getInstance().getWindow().getScaledHeight(); }

    public static void applyPreset(String name) {
        Preset p = PRESETS.get(name);
        Style sc = HUD.session;
        sc.rows = new Rows(p.curve(), p.winrate(), p.total()); sc.scale = p.scale(); sc.visible = true;
        HUD.last.visible = true; HUD.pay.visible = true; HUD.pay.scale = p.scale();
        save();
    }

    public static String presetOf() {
        Style sc = HUD.session;
        for (var e : PRESETS.entrySet()) {
            Preset p = e.getValue();
            if (sc.scale == p.scale() && sc.rows.curve == p.curve() && sc.rows.winrate == p.winrate() && sc.rows.total == p.total()) return e.getKey();
        }
        return "custom";
    }

    public static Data defaults() {
        Data d = new Data();
        Preset std = PRESETS.get("standard");
        d.session = new Style(0.006, 0.30); d.session.rows = new Rows(std.curve(), std.winrate(), std.total());
        d.last = new Style(0, 0);
        d.pay = new Style(0.994, 0.03);
        return d;
    }

    public static void reset() { HUD = defaults(); save(); }

    /**
     * Scale actually drawn: the chosen size, shrunk if the widget would take more than 30 % of the screen width
     * or 40 % of its height.
     */
    public static double effScale(String id) {
        String key = id.equals("last") ? "session" : id;
        Style c = HUD.get(key);
        return Math.min(c.scale, Math.min(sw() * 0.30 / baseW(key), sh() * 0.40 / baseH(key)));
    }

    /** below = null when it does not apply (a placed widget, or a toast whose card is hidden). */
    public record Rect(int x, int y, int w, int h, Boolean below) {}

    /**
     * Screen rect of a widget: x/y are fractions of the FREE space (0 = flush left/top, 1 = flush right/bottom).
     * The toast is not placed: it hangs 4 px under the card, or above it when the card is in the lower half.
     */
    public static Rect wrect(String id) {
        double SW = sw(), SH = sh();
        if (id.equals("last")) {
            Rect s2 = wrect("session");
            double sc = effScale("session");
            int h = (int) Math.round(24 * sc), gap = (int) Math.round(4 * sc);
            if (!HUD.session.visible) return new Rect(s2.x(), s2.y(), s2.w(), h, null);
            boolean below = s2.y() + s2.h() / 2.0 < SH / 2;
            return new Rect(s2.x(), below ? s2.y() + s2.h() + gap : s2.y() - h - gap, s2.w(), h, below);
        }
        Style c = HUD.get(id);
        double k = effScale(id);
        int w = (int) Math.round(baseW(id) * k), h = (int) Math.round(baseH(id) * k);
        return new Rect((int) Math.round(c.x * Math.max(0, SW - w)), (int) Math.round(c.y * Math.max(0, SH - h)), w, h, null);
    }

    // ---- persistence

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("ezgamble").resolve("hud.json"); }

    public static void save() {
        try {
            Path p = file();
            Files.createDirectories(p.getParent());
            Files.writeString(p, exportJson());
        } catch (Exception e) {
            EzGambleMod.LOG.warn("[ezgamble] hud save failed: {}", e.toString());
        }
    }

    private static String hexColor(Object c) {
        return c instanceof Integer i ? String.format(Locale.ROOT, "#%06X", i & 0xFFFFFF) : "gradient";
    }

    private static Number num(double v) { return v == Math.rint(v) ? (Number) (long) v : (Number) v; }
    private static double r(double v, int d) { double m = Math.pow(10, d); return Math.round(v * m) / m; }

    private static JsonObject styleJson(Style c) {
        JsonObject o = new JsonObject();
        o.addProperty("visible", c.visible);
        o.addProperty("x", num(r(c.x, 4))); o.addProperty("y", num(r(c.y, 4)));
        o.addProperty("scale", num(c.scale));
        o.addProperty("color", hexColor(c.color));
        o.addProperty("background", num(r(c.background, 2)));
        o.addProperty("numbers", c.numbers);
        o.addProperty("autoSignColor", c.autoSignColor);
        return o;
    }

    /** The prototype's export: version 2, widgets session / last (visible only) / pay. */
    public static String exportJson() {
        JsonObject out = new JsonObject(), ws = new JsonObject();
        out.addProperty("version", 2);
        JsonObject s = styleJson(HUD.session), rows = new JsonObject();
        rows.addProperty("curve", HUD.session.rows.curve); rows.addProperty("winrate", HUD.session.rows.winrate); rows.addProperty("total", HUD.session.rows.total);
        s.add("rows", rows);
        ws.add("session", s);
        JsonObject last = new JsonObject(); last.addProperty("visible", HUD.last.visible);
        ws.add("last", last);
        ws.add("pay", styleJson(HUD.pay));
        out.add("widgets", ws);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(out);
    }

    private static Data load() {
        Data d = defaults();
        try {
            Path p = file();
            if (!Files.exists(p)) return d;
            JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            if (!o.has("version") || o.get("version").getAsInt() != 2) return d;
            JsonObject ws = o.getAsJsonObject("widgets");
            readStyle(d.session, ws.getAsJsonObject("session"));
            readStyle(d.pay, ws.getAsJsonObject("pay"));
            JsonObject last = ws.getAsJsonObject("last");
            if (last != null && last.has("visible")) d.last.visible = last.get("visible").getAsBoolean();
            JsonObject rows = ws.getAsJsonObject("session").getAsJsonObject("rows");
            if (rows != null) {
                if (rows.has("curve")) d.session.rows.curve = rows.get("curve").getAsBoolean();
                if (rows.has("winrate")) d.session.rows.winrate = rows.get("winrate").getAsBoolean();
                if (rows.has("total")) d.session.rows.total = rows.get("total").getAsBoolean();
            }
        } catch (Exception e) {
            EzGambleMod.LOG.warn("[ezgamble] hud load failed, defaults: {}", e.toString());
            return defaults();
        }
        return d;
    }

    private static void readStyle(Style c, JsonObject o) {
        if (o == null) return;
        if (o.has("visible")) c.visible = o.get("visible").getAsBoolean();
        if (o.has("x")) c.x = clamp01(o.get("x").getAsDouble());
        if (o.has("y")) c.y = clamp01(o.get("y").getAsDouble());
        if (o.has("scale")) c.scale = Math.max(0.25, Math.min(4, o.get("scale").getAsDouble()));
        if (o.has("background")) c.background = clamp01(o.get("background").getAsDouble());
        if (o.has("numbers")) c.numbers = o.get("numbers").getAsString().equals("full") ? "full" : "short";
        if (o.has("autoSignColor")) c.autoSignColor = o.get("autoSignColor").getAsBoolean();
        if (o.has("color")) {
            JsonElement e = o.get("color");
            String s = e.getAsString();
            if (s.startsWith("#") && s.length() == 7) c.color = 0xFF000000 | Integer.parseInt(s.substring(1), 16);
            else c.color = "gradient";
        }
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
}
