package dev.ryaan.ezgamble.themes;

/**
 * A colour theme of the whole mod: the two accent colours of the Outmind gradient (lav = start, pink = end), the
 * three text greys and the glass sheet's tint (panelTop lighter at the top, panelBot at the bottom, RGB only: the
 * alpha comes from the surface being drawn). GREEN and RED are not part of a theme: profit stays green, loss red.
 */
public record Theme(String id, String name, int lav, int pink, int text, int mute, int dim, int panelTop, int panelBot) {}
