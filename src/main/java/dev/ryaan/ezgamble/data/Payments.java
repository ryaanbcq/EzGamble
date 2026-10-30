package dev.ryaan.ezgamble.data;

import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.proto.P;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Incoming /pay: "X paid you $Y" in the client's language (EzAuction's detection, the one proven on DonutSMP), queued
 * for the HUD announcement; during a session the amount becomes X's chips (session.credit). While an auction runs it
 * is a bid instead.
 */
public final class Payments {
    private Payments() {}

    public record Paid(String name, long amount, long at) {}

    public static final long PAY_MS = 3400, PAY_IN = 260, PAY_OUT = 300, PAY_BIG = 10_000_000;
    public static final Deque<Paid> queue = new ArrayDeque<>();
    public static Paid cur;
    public static long total, count;
    /** Set by the auction while it runs: the payment is a bid. */
    public static BiConsumer<String, Long> auctionBid;

    // DonutSMP localises the line ("X paid you $ 50M", "X vous a payé $ 1."...): a name, then LOWERCASE words only,
    // then "$ amount", then optional lowercase words and a final dot. The words must name ME with an object pronoun.
    private static final Pattern PAID = Pattern.compile(
            "^(\\.?[A-Za-z0-9_]{3,16}) ((?:[\\p{Ll}\\p{Lo}'\\u2019]+ ){1,8})\\$\\s*(\\d+(?:[.,]\\d+)*[kmbKMB]?)((?: [\\p{Ll}\\p{Lo}]+){0,6})\\s*[.!]?\\s*$");
    private static final Pattern PAID_VAULT = Pattern.compile("^(?:\\w+ )?(\\.?[A-Za-z0-9_]{2,16}) paid you (\\d[\\d,]*(?:\\.\\d+)?\\s*[KMBkmb]?)\\s*\\$\\.?$");
    private static final Set<String> PRONOUNS = Set.of("you", "vous", "tu", "du", "usted", "voce", "você", "jij", "sie", "lei", "ty", "vy", "sen", "anda");
    private static final Set<String> ME = Set.of("you", "vous", "te", "toi", "t'", "dir", "dich", "ti", "je", "jou", "u", "ci", "tobie", "ciebie", "sana", "seni", "sizi",
            "vam", "vám", "tebe", "tebi", "dig", "deg", "sinulle", "sinut", "вам", "тебе", "вас");

    private static boolean namesMe(String words) {
        for (String w : words.trim().split("\\s+")) {
            String t = w.toLowerCase(Locale.ROOT);
            if (ME.contains(t) || t.startsWith("t'") || t.startsWith("t’")) return true;
        }
        return false;
    }

    private static boolean mentionsOnlinePlayer(String words) {
        var nh = MinecraftClient.getInstance().getNetworkHandler();
        if (nh == null) return false;
        Set<String> names = new HashSet<>();
        for (var e : nh.getPlayerList()) names.add(e.getProfile().name().toLowerCase(Locale.ROOT));
        for (String w : words.trim().split("\\s+")) if (!w.isEmpty() && names.contains(w.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    /** @return true if the line was an incoming payment. */
    public static boolean onChat(String line) {
        String s = line.strip();
        int dollar = s.indexOf('$');
        String head = dollar > 0 ? s.substring(0, dollar) : s;
        // whispers, DMs and public chat all carry a separator before the text: never a bank line
        if (head.contains(":") || head.contains("»") || head.contains(">") || head.contains("[") || head.contains("whisper") || head.contains("chuchote")) return false;
        String name; long amount;
        Matcher m = PAID.matcher(s);
        if (m.matches()) {
            if (mentionsOnlinePlayer(m.group(2) + m.group(4))) return false;
            if (!namesMe(m.group(2) + m.group(4))) return false;
            name = m.group(1); amount = Money.parse(m.group(3));
        } else if ((m = PAID_VAULT.matcher(s)).matches()) {
            name = m.group(1); amount = Money.parse(m.group(2));
        } else return false;
        if (amount <= 0) return false;
        var mc = MinecraftClient.getInstance();
        if (PRONOUNS.contains(name.toLowerCase(Locale.ROOT)) || (mc.player != null && name.equalsIgnoreCase(mc.player.getGameProfile().name()))) return false;
        received(name, amount);
        return true;
    }

    /** A payment of `amount` from `name` (also used by the smoke test). */
    public static void received(String name, long amount) {
        EzGambleMod.LOG.info("[ezgamble] paid by {} {}", name, amount);
        queue.add(new Paid(name, amount, 0));
        total += amount; count++;
        Session cs = GambleData.current();
        if (auctionBid != null) {   // an auction is on: this /pay is a bid, not chips
            auctionBid.accept(name, amount);
            if (cs != null) cs.payments.add(new Session.Pay(name, amount, P.now(), true));
            GambleData.touch();
            return;
        }
        if (GambleData.mode && cs != null) {
            // a debt left by an older version (sent back more than his chips): forgiven, this payment counts in full
            long before = cs.credit.getOrDefault(name, 0L) + cs.won.getOrDefault(name, 0L) - cs.sent.getOrDefault(name, 0L);
            long sentBefore = cs.sent.getOrDefault(name, 0L);
            if (before < 0 && sentBefore > 0) cs.sent.put(name, Math.max(0, sentBefore + before));
            cs.credit.merge(name, amount, Long::sum);
            cs.payments.add(new Session.Pay(name, amount, P.now(), false));
            GambleData.touch();
            Payouts.ver++;
        }
    }

    /** One announcement at a time; the next starts when the current card has slid out. */
    public static void tick() {
        long t = P.now();
        if (cur != null && t - cur.at() >= PAY_MS) cur = null;
        if (cur == null && !queue.isEmpty()) {
            Paid p = queue.poll();
            cur = new Paid(p.name(), p.amount(), t);
            P.play(cur.amount() >= PAY_BIG ? "cash" : "pay", 1, 0.6);
        }
    }
}
