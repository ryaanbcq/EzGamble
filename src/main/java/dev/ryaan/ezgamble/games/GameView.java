package dev.ryaan.ezgamble.games;

import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.Key;

/**
 * A game's sub-view inside GambleScreen (prototype: BlackjackView, MinesView...). The screen calls init() on every
 * rebuild (widgets go through screen.add), tick() each client tick, render() each frame with the felt geometry,
 * key() for keys (true = used), scrolled() for the wheel, leave() when the player goes back or changes tab.
 */
public interface GameView {
    void init();
    void tick();
    void render(G g, double mx, double my);
    default boolean key(Key e) { return false; }
    default boolean scrolled(double mx, double my, double dy, double dx) { return false; }
    /** Leaving the game (‹, tab change, menu closed): settle what is finished. */
    void leave();
    /** After every click on the screen: hit = the widget that took it (or null). Fold open fields / pickers here. */
    default void clicked(dev.ryaan.ezgamble.proto.Widget hit) {}
}
