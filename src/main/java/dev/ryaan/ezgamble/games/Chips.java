package dev.ryaan.ezgamble.games;

import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Vec;

import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Outmind casino chips (prototype Chips.java): denominations, greedy breakdown, 3/4 view stacks drawn with Java2D. */
public final class Chips {
    private Chips() {}

    public record Chip(long v, int face, int edge, int ink, String label) {
        public Chip faded(double a) { return new Chip(v, P.alpha(face, a * ((face >>> 24) / 255.0)), P.alpha(edge, a * ((edge >>> 24) / 255.0)), P.alpha(ink, a * ((ink >>> 24) / 255.0)), label); }
    }
    public record Stack(Chip chip, int n) {}
    public record Breakdown(List<Stack> stacks, long loose, int count) {}

    public static final Chip[] CHIPS = {
            new Chip(1_000_000_000L, 0xFFF2EDFA, 0xFFA18CD1, P.INK, "1B"),
            new Chip(100_000_000L, 0xFF221832, 0xFFFBC2EB, 0xFFFBC2EB, "100M"),
            new Chip(10_000_000L, 0xFFFFD36B, 0xFFFFFFFF, P.INK, "10M"),
            new Chip(1_000_000L, 0xFFFBC2EB, 0xFFFFFFFF, P.INK, "1M"),
            new Chip(100_000L, 0xFFA18CD1, 0xFFFFFFFF, P.INK, "100K")};
    /** The faint chip shown for an empty bet or an amount under 100K. */
    public static final Chip GHOST = new Chip(100_000L, 0x33A18CD1, 0x33FFFFFF, 0x33FFFFFF, "100K");

    public static Breakdown chipsFor(long amount) {
        List<Stack> out = new ArrayList<>();
        long left = Math.max(0, amount);
        int count = 0;
        for (Chip c : CHIPS) { int n = (int) (left / c.v()); if (n > 0) { out.add(new Stack(c, n)); left -= n * c.v(); count += n; } }
        return new Breakdown(out, left, count);
    }

    /** A stack of n chips (at most 10 drawn), bottom centre (x, y) GUI px, chip radius r. */
    public static void chipStack(G g, double x, double y, double r, Chip chip, int n) {
        int shown = Math.max(1, Math.min(10, n));
        double rx = r, ry = r * 0.36, th = r * 0.3, w = 2 * rx + 2, hgt = ry * 3 + th * shown + 2;
        String key = "chip/" + chip + "/" + r + "/" + shown;
        double pad = 3;
        Vec.draw(g, key, x - w / 2, y - hgt + ry + 1, w, hgt, pad, g2 -> {
            double cx = w / 2, baseY = hgt - ry - 1;
            g2.setColor(new java.awt.Color(0, 0, 0, 64));
            g2.fill(new Ellipse2D.Double(cx - rx * 1.05, baseY - ry * 1.05, rx * 2.1, ry * 2.1));
            for (int i = 0; i < shown; i++) side(g2, cx, baseY - ry - th - i * th, rx, ry, th, chip, i == shown - 1);
        });
    }

    /** One chip from the side: body band with edge stripes, then the face ellipse (top chip: dashes, ring, gloss). */
    private static void side(Graphics2D g2, double cx, double y, double rx, double ry, double th, Chip c, boolean top) {
        Area body = new Area(new Rectangle2D.Double(cx - rx, y, rx * 2, th));
        body.add(new Area(new Ellipse2D.Double(cx - rx, y + th - ry, rx * 2, ry * 2)));
        g2.setColor(Vec.c(P.shade(c.face(), 0.62)));
        g2.fill(body);
        var clip = g2.getClip();
        g2.clip(body);
        g2.setColor(Vec.c(P.alpha(c.edge(), 0.85 * ((c.edge() >>> 24) / 255.0))));
        for (int i = -3; i <= 3; i++) { double sx = cx + (i / 3.6) * rx; g2.fill(new Rectangle2D.Double(sx - rx * 0.07, y - ry, rx * 0.14, th + ry * 2)); }
        g2.setClip(clip);
        g2.setColor(Vec.c(c.face()));
        g2.fill(new Ellipse2D.Double(cx - rx, y - ry, rx * 2, ry * 2));
        if (!top) return;
        float lw = (float) Math.max(0.35, rx * 0.16);
        g2.setColor(Vec.c(P.alpha(c.edge(), 0.9 * ((c.edge() >>> 24) / 255.0))));
        g2.setStroke(new BasicStroke(lw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{(float) (rx * 0.32), (float) (rx * 0.36)}, 0));
        g2.draw(new Ellipse2D.Double(cx - rx * 0.86, y - ry * 0.86, rx * 1.72, ry * 1.72));
        g2.setStroke(new BasicStroke((float) Math.max(0.3, rx * 0.06)));
        g2.setColor(Vec.c(P.alpha(c.ink(), 0.35 * ((c.ink() >>> 24) / 255.0))));
        g2.draw(new Ellipse2D.Double(cx - rx * 0.55, y - ry * 0.55, rx * 1.1, ry * 1.1));
        g2.setPaint(new GradientPaint(0, (float) (y - ry), new java.awt.Color(255, 255, 255, 115), 0, (float) y, new java.awt.Color(255, 255, 255, 0)));
        g2.fill(new Ellipse2D.Double(cx - rx, y - ry, rx * 2, ry * 2));
    }

    /** A bet as small chips (biggest three stacks, right to left). Under 100K: one faint chip. */
    public static void drawBet(G g, double x, double y, long amount) { drawBet(g, x, y, amount, 6); }
    public static void drawBet(G g, double x, double y, long amount, double r) {
        List<Stack> stacks = chipsFor(amount).stacks();
        if (stacks.isEmpty()) { if (amount > 0) chipStack(g, x, y, r, new Chip(100_000L, 0x66A18CD1, 0x66FFFFFF, 0x66FFFFFF, "100K"), 1); return; }
        for (int i = 0; i < Math.min(3, stacks.size()); i++) chipStack(g, x - i * (r + 4), y - i, r, stacks.get(i).chip(), stacks.get(i).n());
    }
}
