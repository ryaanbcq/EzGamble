package dev.ryaan.ezgamble.data;

import dev.ryaan.ezgamble.Money;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server games played by the gambler himself (chat lines -> transactions). The rules are the prototype's placeholders
 * until real server dumps exist; config/ezgamble/servers/<host>.json will replace them per server.
 */
public final class Detector {
    private Detector() {}

    private record Rule(String game, boolean win, Pattern re, int amountGroup, int betGroup) {}

    private static final String AMT = "\\$\\s?([\\d.,]+[KMBkmb]?)";
    private static final List<Rule> RULES = List.of(
            rule("Roulette", true, "^\\[Roulette\\] .*You won " + AMT + "(?: \\(bet " + AMT + "\\))?"),
            rule("Roulette", false, "^\\[Roulette\\] .*You lost " + AMT),
            rule("Blackjack", true, "^Blackjack » You won " + AMT + " on a " + AMT + " bet"),
            rule("Blackjack", false, "^Blackjack » Dealer wins\\. You lost " + AMT),
            rule("Coinflip", true, "^Coinflip » You won " + AMT + " against \\w+"),
            rule("Coinflip", false, "^Coinflip » You lost " + AMT + " against \\w+"),
            new Rule("Crash", true, Pattern.compile("^Crash » Cashed out at [\\d.]+x on " + AMT + ": \\+" + AMT), 2, 1),
            rule("Crash", false, "^Crash » Crashed at [\\d.]+x\\. You lost " + AMT),
            rule("Slots", true, "^Slots » .*\\+" + AMT + " \\(bet " + AMT + "\\)"),
            rule("Slots", false, "^Slots » No match\\. -" + AMT),
            rule("Buckshot", true, "^Buckshot » VICTORY \\+" + AMT + " \\(bet " + AMT + "\\)"),
            rule("Buckshot", false, "^Buckshot » You died\\. -" + AMT));

    private static Rule rule(String game, boolean win, String p) { return new Rule(game, win, Pattern.compile(p), 1, 2); }

    public static Txn detect(String line, long t) {
        for (Rule r : RULES) {
            Matcher m = r.re.matcher(line);
            if (!m.find()) continue;
            long amount = Money.parse(m.group(r.amountGroup));
            Long bet = m.groupCount() >= r.betGroup && m.group(r.betGroup) != null ? Money.parse(m.group(r.betGroup)) : null;
            if (amount < 0) continue;
            if (bet == null && !r.win) bet = amount;
            if (bet == null && r.game.equals("Coinflip")) bet = amount;
            return new Txn(t, r.game, bet, r.win ? amount : -amount, null, line);
        }
        return null;
    }
}
