package dev.ryaan.ezgamble.games.blackjack;

import dev.ryaan.ezgamble.games.blackjack.BlackjackGame.Card;
import dev.ryaan.ezgamble.games.blackjack.BlackjackGame.Shoe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Rule checks on rigged shoes (prototype bjSelfTest). Plain Java: run main, it prints the failures. */
public final class BlackjackSelfTest {
    private BlackjackSelfTest() {}

    /** Deal order: player, dealer, player, dealer, then draws. */
    private static Shoe rig(String... ranks) {
        Shoe sh = new Shoe(1);
        List<Card> l = new ArrayList<>();
        for (int i = 0; i < ranks.length; i++) l.add(new Card(ranks[i], "♠", -1 - i));
        Collections.reverse(l);
        sh.cards.clear(); sh.cards.addAll(l);
        sh.never = true;
        return sh;
    }

    private static BlackjackGame play(Shoe shoe, long bet, String... acts) {
        BlackjackGame gm = new BlackjackGame(shoe);
        gm.deal(bet);
        for (String a : acts) gm.act(a);
        return gm;
    }

    private static List<Card> cs(String... r) { List<Card> l = new ArrayList<>(); for (String x : r) l.add(new Card(x, "♠", 0)); return l; }

    public static List<String> run() {
        List<String> out = new ArrayList<>();
        BlackjackGame.Value v = BlackjackGame.handValue(cs("A", "K")); if (!(v.total() == 21 && v.soft())) out.add("A K = soft 21");
        v = BlackjackGame.handValue(cs("A", "A", "9")); if (!(v.total() == 21 && v.soft())) out.add("A A 9 = soft 21");
        v = BlackjackGame.handValue(cs("10", "6", "A")); if (!(v.total() == 17 && !v.soft())) out.add("10 6 A = hard 17");
        BlackjackGame gm = play(rig("A", "5", "K", "9"), 1000);
        if (!(gm.phase.equals("done") && gm.results.get(0).outcome().equals("blackjack") && gm.results.get(0).delta() == 1500 && gm.dealer.size() == 2)) out.add("natural pays 3:2, dealer does not draw");
        gm = play(rig("10", "A", "8", "6"), 1000, "stand");
        if (!(gm.dealer.size() == 2 && gm.results.get(0).delta() == 1000)) out.add("dealer stands on soft 17");
        gm = play(rig("10", "10", "9", "6", "5"), 1000, "stand");
        if (!(gm.dealer.size() == 3 && gm.results.get(0).outcome().equals("lose") && gm.results.get(0).delta() == -1000)) out.add("dealer hits 16");
        gm = play(rig("10", "10", "8", "8"), 1000, "stand");
        if (!(gm.results.get(0).outcome().equals("push") && gm.results.get(0).delta() == 0)) out.add("push returns the bet");
        gm = play(rig("10", "5", "6", "9", "9"), 1000, "hit");
        if (!(gm.phase.equals("done") && gm.results.get(0).outcome().equals("bust") && gm.results.get(0).delta() == -1000 && gm.dealer.size() == 2)) out.add("bust ends the hand, dealer does not draw");
        gm = play(rig("5", "10", "6", "7", "10"), 1000, "double");
        if (!(gm.hands.get(0).bet == 2000 && gm.hands.get(0).cards.size() == 3 && gm.results.get(0).delta() == 2000)) out.add("double: one card, bet doubled");
        gm = play(rig("5", "10", "6", "7", "2", "9", "9"), 1000, "hit");
        if (gm.can("double") || gm.can("split")) out.add("no double or split after a hit");
        gm = play(rig("8", "10", "8", "7", "3", "10"), 1000);
        if (!gm.can("split")) out.add("split on a pair");
        gm.split();
        if (!(gm.hands.size() == 2 && gm.hands.get(0).cards.size() == 2 && gm.hands.get(1).cards.size() == 1 && !gm.can("split"))) out.add("split once: hand 1 waits for its card");
        gm.stand();
        if (!(gm.active == 1 && gm.hands.get(1).cards.size() == 2)) out.add("next hand gets its second card");
        gm.stand();
        if (!(gm.phase.equals("done") && gm.results.get(0).delta() == -1000 && gm.results.get(1).delta() == 1000 && gm.total() == 0)) out.add("split hands settled one by one");
        gm = play(rig("10", "5", "K", "9"), 1000); if (!gm.can("split")) out.add("10 and K split by value");
        gm = play(rig("A", "5", "A", "9", "K", "K", "2", "2"), 1000); gm.split();   // A A -> two 21s, dealer 14 draws 2 then 2
        if (!(gm.phase.equals("done") && gm.results.get(0).outcome().equals("win") && gm.results.get(0).delta() == 1000)) out.add("21 after a split is not a natural");
        gm = play(rig("10", "A", "10", "K"), 1000); if (!(gm.phase.equals("done") && gm.results.get(0).outcome().equals("lose"))) out.add("dealer natural beats 20");
        gm = play(rig("A", "A", "K", "K"), 1000); if (!gm.results.get(0).outcome().equals("push")) out.add("two naturals push");
        Shoe sh = new Shoe(6);
        HashSet<Integer> ids = new HashSet<>();
        long aces = 0;
        for (Card c : sh.cards) { ids.add(c.id); if (c.r.equals("A")) aces++; }
        if (!(sh.remaining() == 312 && ids.size() == 312 && aces == 24)) out.add("six decks, every card once");
        sh.cards.subList(70, sh.cards.size()).clear();
        gm = new BlackjackGame(sh); gm.deal(100);
        if (!(gm.reshuffled && sh.remaining() == 312 - 4)) out.add("reshuffled under 25 %");
        return out;
    }

    public static void main(String[] args) {
        List<String> f = run();
        if (f.isEmpty()) System.out.println("Blackjack rules: all checks pass"); else { System.err.println("Blackjack rules failed: " + f); System.exit(1); }
    }
}
