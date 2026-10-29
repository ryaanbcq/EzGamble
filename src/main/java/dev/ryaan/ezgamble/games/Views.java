package dev.ryaan.ezgamble.games;

import dev.ryaan.ezgamble.ui.GambleScreen;

/** The sub-views of the Games tab, by id. Each game's port provides its class. */
public final class Views {
    private Views() {}

    public static GameView create(String id, GambleScreen s) {
        return switch (id) {
            case "blackjack" -> new dev.ryaan.ezgamble.games.blackjack.BlackjackView(s);
            case "crates" -> new dev.ryaan.ezgamble.games.crates.CratesView(s);
            case "mines" -> new dev.ryaan.ezgamble.games.mines.MinesView(s);
            case "crash" -> new dev.ryaan.ezgamble.games.crash.CrashView(s);
            case "roulette" -> new dev.ryaan.ezgamble.games.roulette.RouletteView(s);
            case "coinflip" -> new dev.ryaan.ezgamble.games.coinflip.CoinflipView(s);
            case "auction" -> new dev.ryaan.ezgamble.auction.AuctionView(s);
            case "duels" -> new dev.ryaan.ezgamble.duel.DuelsView(s);
            case "chips" -> new dev.ryaan.ezgamble.ui.ChipsView(s);
            case "player" -> new dev.ryaan.ezgamble.ui.PlayerView(s);
            case "rollers" -> new dev.ryaan.ezgamble.ui.RollersView(s);
            default -> new Stub(s, id);
        };
    }

    /** Placeholder sub-view: a back button and the name (until its port lands). */
    public static class Stub implements GameView {
        protected final GambleScreen s;
        private final String id;

        public Stub(GambleScreen s, String id) { this.s = s; this.id = id; }

        @Override public void init() { s.add(new dev.ryaan.ezgamble.proto.GlassButton(s.x0 + GambleScreen.PAD, s.y0 + 56, 18, 16, "‹", "GHOST", s::back)); }
        @Override public void tick() {}
        @Override public void render(dev.ryaan.ezgamble.proto.G g, double mx, double my) {
            g.drawText(id + ": coming soon", s.x0 + GambleScreen.PAD + 26, s.y0 + 60, dev.ryaan.ezgamble.proto.P.MUTE, false);
        }
        @Override public void leave() {}
    }
}
