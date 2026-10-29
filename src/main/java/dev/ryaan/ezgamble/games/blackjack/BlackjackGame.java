package dev.ryaan.ezgamble.games.blackjack;

import dev.ryaan.ezgamble.games.Widgets;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules, no rendering, no money (prototype BlackjackGame).
 * Six-deck shoe, reshuffled between hands once under 25 %. The dealer draws to 17 and STANDS on soft 17. A natural
 * pays 3:2, a push returns the bet. Hit / Stand / Double (one card, on the first two cards) / Split (once, two cards
 * of the same value; a 21 after a split is not a natural). No insurance. The dealer only draws when a player hand
 * is still live (not bust, not a natural). Random source: SecureRandom.
 * Money never enters here: the game returns deltas, the table applies them to the backer's chips.
 */
public final class BlackjackGame extends Widgets.Round {
    public static final String[] SUITS = {"♠", "♥", "♦", "♣"};
    public static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

    public static int cardValue(String r) { return r.equals("A") ? 11 : "JQK".contains(r) ? 10 : Integer.parseInt(r); }

    private static int cardSeq = 0;

    public static final class Card {
        public final String r, s;
        public final int id;
        public boolean hidden;
        public Card(String r, String s, int id) { this.r = r; this.s = s; this.id = id; }
        public boolean isRed() { return s.equals("♥") || s.equals("♦"); }
    }

    public static final class Shoe {
        public final int decks;
        public final List<Card> cards = new ArrayList<>();
        private static final SecureRandom RNG = new SecureRandom();
        /** Test hook: a rigged shoe never reports itself low. */
        boolean never;

        public Shoe(int decks) { this.decks = decks; shuffle(); }

        public void shuffle() {
            List<Card> c = new ArrayList<>();
            for (int d = 0; d < decks; d++) for (String s : SUITS) for (String r : RANKS) c.add(new Card(r, s, ++cardSeq));
            for (int i = c.size() - 1; i > 0; i--) { int j = RNG.nextInt(i + 1); Card t = c.get(i); c.set(i, c.get(j)); c.set(j, t); }   // Fisher-Yates
            cards.clear(); cards.addAll(c);
        }

        public int remaining() { return cards.size(); }
        public boolean low() { return !never && cards.size() < decks * 52 * 0.25; }
        public Card draw() { if (cards.isEmpty()) shuffle(); return cards.remove(cards.size() - 1); }
    }

    public record Value(int total, boolean soft) {}

    /** Best total: aces count 11, then 1 one by one while over 21. soft = an ace still counts 11. */
    public static Value handValue(List<Card> cards) {
        int total = 0, aces = 0;
        for (Card c : cards) { total += cardValue(c.r); if (c.r.equals("A")) aces++; }
        while (total > 21 && aces > 0) { total -= 10; aces--; }
        return new Value(total, aces > 0);
    }

    public static boolean isNatural(List<Card> cards) { return cards.size() == 2 && handValue(cards).total() == 21; }

    public static final class Hand {
        public final List<Card> cards = new ArrayList<>();
        public long bet;
        public boolean doubled, split, done;
        Hand(long bet, boolean split) { this.bet = bet; this.split = split; }
    }

    public record Result(String outcome, long delta, long bet) {}

    /** Every card dealt, hole card revealed and split, in order (the screen animates it). */
    public static final class Entry {
        public final String type;   // card | reveal | split
        public final Card card;
        public final boolean toPlayer;
        Entry(String type, Card card, boolean toPlayer) { this.type = type; this.card = card; this.toPlayer = toPlayer; }
    }

    public final Shoe shoe;
    public List<Hand> hands = new ArrayList<>();
    public List<Card> dealer = new ArrayList<>();
    public int active;
    public String phase = "bet";   // bet -> player -> done
    public List<Result> results;
    public final List<Entry> log = new ArrayList<>();
    public boolean reshuffled;

    public BlackjackGame(Shoe shoe) { this.shoe = shoe; }

    public void deal(long bet) {
        reshuffled = shoe.low();
        if (reshuffled) shoe.shuffle();
        hands = new ArrayList<>(); hands.add(new Hand(bet, false));
        dealer = new ArrayList<>(); active = 0; phase = "player"; results = null; log.clear();
        give(0); giveDealer(false); give(0); giveDealer(true);
        if (isNatural(dealer) || isNatural(hands.get(0).cards)) { hands.get(0).done = true; finish(); }
    }

    Card give(int i) { Card c = shoe.draw(); hands.get(i).cards.add(c); log.add(new Entry("card", c, true)); return c; }
    Card giveDealer(boolean hidden) { Card c = shoe.draw(); c.hidden = hidden; dealer.add(c); log.add(new Entry("card", c, false)); return c; }

    public Hand hand() { return hands.get(active); }

    public boolean can(String a) {
        if (!phase.equals("player")) return false;
        Hand h = hand();
        boolean two = h.cards.size() == 2;
        return switch (a) {
            case "hit", "stand" -> true;
            case "double" -> two && !h.doubled;
            case "split" -> two && hands.size() == 1 && cardValue(h.cards.get(0).r) == cardValue(h.cards.get(1).r);
            default -> false;
        };
    }

    public boolean act(String a) {
        return switch (a) {
            case "hit" -> hit();
            case "stand" -> stand();
            case "double" -> doubleDown();
            case "split" -> split();
            default -> false;
        };
    }

    public boolean hit() { if (!can("hit")) return false; give(active); if (handValue(hand().cards).total() >= 21) endHand(); return true; }
    public boolean stand() { if (!can("stand")) return false; endHand(); return true; }
    public boolean doubleDown() { if (!can("double")) return false; Hand h = hand(); h.bet *= 2; h.doubled = true; give(active); endHand(); return true; }

    public boolean split() {
        if (!can("split")) return false;
        Hand h = hand();
        Card c2 = h.cards.remove(h.cards.size() - 1);
        h.split = true;
        Hand n = new Hand(h.bet, true); n.cards.add(c2); hands.add(n);
        log.add(new Entry("split", null, true));
        give(0);
        if (handValue(h.cards).total() == 21) endHand();
        return true;
    }

    /** The active hand is over: the next hand gets its second card and plays, or the dealer plays. */
    void endHand() {
        hand().done = true;
        int next = -1;
        for (int i = 0; i < hands.size(); i++) if (!hands.get(i).done) { next = i; break; }
        if (next < 0) { finish(); return; }
        active = next;
        if (hands.get(next).cards.size() == 1) { give(next); if (handValue(hands.get(next).cards).total() == 21) endHand(); }
    }

    void finish() {
        for (Card c : dealer) if (c.hidden) { c.hidden = false; log.add(new Entry("reveal", c, false)); break; }
        boolean live = hands.stream().anyMatch(h -> handValue(h.cards).total() <= 21 && !(isNatural(h.cards) && !h.split));
        if (live && !isNatural(dealer)) while (handValue(dealer).total() < 17) giveDealer(false);   // stands on soft 17
        List<Result> r = new ArrayList<>();
        for (Hand h : hands) r.add(settle(h));
        results = r;
        phase = "done";
    }

    Result settle(Hand h) {
        int p = handValue(h.cards).total(), d = handValue(dealer).total();
        boolean pn = isNatural(h.cards) && !h.split, dn = isNatural(dealer);
        String outcome; long delta;
        if (p > 21) { outcome = "bust"; delta = -h.bet; }
        else if (pn && dn) { outcome = "push"; delta = 0; }
        else if (pn) { outcome = "blackjack"; delta = Math.round(h.bet * 1.5); }
        else if (dn) { outcome = "lose"; delta = -h.bet; }
        else if (d > 21) { outcome = "dealerbust"; delta = h.bet; }
        else if (p > d) { outcome = "win"; delta = h.bet; }
        else if (p < d) { outcome = "lose"; delta = -h.bet; }
        else { outcome = "push"; delta = 0; }
        return new Result(outcome, delta, h.bet);
    }

    public long total() { return results == null ? 0 : results.stream().mapToLong(Result::delta).sum(); }
}
