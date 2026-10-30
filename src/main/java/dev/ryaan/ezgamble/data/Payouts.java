package dev.ryaan.ezgamble.data;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.proto.P;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chips of a player = what he paid the gambler this session + what he won or lost at the tables (session.won) - what
 * was already sent back, never negative. A send counts at most his chips: sending more is a gift, not a debt. A send runs "/pay <name> <amount>" (one click = one command, never on its
 * own) and only counts once the server confirms "You paid <name> $X". No confirmation within 15 s = nothing deducted.
 */
public final class Payouts {
    private Payouts() {}

    /** resendAt > 0: the server asked to wait (command cooldown), the same /pay goes again at that time, once. */
    public record Pending(String name, long amount, long at, long resendAt, boolean resent) {
        Pending(String name, long amount, long at) { this(name, amount, at, 0, false); }
    }
    /** why: the server's refusal in a few words, null when it simply never answered. */
    public record Recent(boolean ok, long amount, long at, String why) {
        Recent(boolean ok, long amount, long at) { this(ok, amount, at, null); }
    }
    public record Row(String name, long recv, long sent, long won, long bal, Pending pend, Recent recent) {}

    public static final List<Pending> pending = new ArrayList<>();
    public static final Map<String, Recent> recent = new HashMap<>();
    public static int ver;

    /** "You paid Bob $ 5M" and its translations (EzChat / EzAuction), plus the Essentials form. */
    private static final String NAME = "\\.?[A-Za-z0-9_]{2,16}", AMT = "[\\d][\\d,]*(?:\\.\\d+)?\\s*[KMBkmb]?";
    private static final String YOU = "(?:you|vous|tu|du|usted|você|voce|jij|ty|vy|sen|anda)";
    private static final Pattern OUT_DONUT = Pattern.compile("^" + YOU + " [\\p{L}'’ ]{0,40}?(" + NAME + ") \\$\\s*(" + AMT + ")\\s*[.!]?$", Pattern.CASE_INSENSITIVE);
    /** A /pay gives an answer within a second or two: past this, it is "not confirmed". */
    private static final long TIMEOUT = 8000;
    /** The server's refusals of a /pay (DonutSMP), with the few words the button shows. */
    private static final Pattern WAIT = Pattern.compile("^You need to wait another ([\\d.]+) seconds? to execute a command", Pattern.CASE_INSENSITIVE);
    private static final String[][] REFUSED = {
        {"payments enabled", "Payments off"}, {"enough money", "Not enough money"}, {"insufficient", "Not enough money"},
        {"yourself", "Can't pay yourself"}, {"not found", "Player not found"}, {"never joined", "Player not found"},
        {"does not exist", "Player not found"}, {"doesn't exist", "Player not found"}, {"invalid amount", "Invalid amount"}};
    /** Sends that timed out lately: a confirmation that still turns up counts (never paid twice by a retry). */
    private static final List<Pending> late = new ArrayList<>();
    private static final Pattern OUT_VAULT = Pattern.compile("^(?:\\w+ )?You paid (" + AMT + ")\\s*\\$ to (" + NAME + ")\\.?$");

    /** The last session, even after mode OFF: chips stay owed. */
    public static Session session() { return GambleData.last(); }

    public static boolean same(String a, String b) { return norm(a).equals(norm(b)); }
    public static String norm(String n) { return n.toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""); }

    public static List<Row> rows() {
        Session s = session();
        if (s == null) return List.of();
        List<Row> out = new ArrayList<>();
        // credit first, then whoever only won (a rain winner who never sent chips is owed his prize too)
        var names = new java.util.LinkedHashSet<String>(s.credit.keySet());
        names.addAll(s.won.keySet());
        for (String n : names) {
            long recv = s.credit.getOrDefault(n, 0L), sent = s.sent.getOrDefault(n, 0L), won = s.won.getOrDefault(n, 0L);
            Pending pend = pending.stream().filter(p -> p.name.equals(n)).findFirst().orElse(null);
            out.add(new Row(n, recv, sent, won, Math.max(0, recv + won - sent), pend, recent.get(n)));
        }
        out.sort(Comparator.comparingLong((Row r) -> -r.recv).thenComparing(Row::name));
        return out;
    }

    public static long balance(String name) {
        if (name == null) return 0;
        for (Row r : rows()) if (r.name.equals(name)) return r.bal;
        return 0;
    }

    public static void send(String name, long amount) {
        if (amount <= 0 || pending.stream().anyMatch(p -> p.name.equals(name))) return;
        pending.add(new Pending(name, amount, P.now()));   // before the command: typed() sees our own /pay go out and must find it already waiting
        var nh = MinecraftClient.getInstance().getNetworkHandler();
        if (nh != null) nh.sendChatCommand("pay " + name + " " + amount);   // whole dollars: no K/M rounding on the server
        ver++;
    }

    /** A /pay typed by hand to a player of the session: counted like one sent from the menu (exact amount from the command). */
    public static void typed(String command) {
        String[] a = command.trim().split("\\s+");
        if (a.length != 3 || !a[0].equalsIgnoreCase("pay") || !GambleData.mode || session() == null) return;
        String name = a[1]; long amount = Money.parse(a[2]);
        if (amount <= 0 || pending.stream().anyMatch(p -> same(p.name, name))) return;
        if (rows().stream().noneMatch(r -> same(r.name, name))) return;   // not someone you play for: a private /pay
        pending.add(new Pending(name, amount, P.now()));
        ver++;
    }

    public static boolean onChat(String line) {
        String who = null; long amount = -1;
        Matcher m = OUT_DONUT.matcher(line);
        if (m.matches()) { who = m.group(1); amount = Money.parse(m.group(2)); }
        else if ((m = OUT_VAULT.matcher(line)).matches()) { who = m.group(2); amount = Money.parse(m.group(1)); }
        if (who == null) return refused(line);
        String w = who;
        Pending p = pending.stream().filter(q -> same(q.name, w)).findFirst().orElse(null);
        if (p == null) p = late.stream().filter(q -> same(q.name, w)).findFirst().orElse(null);
        if (p == null) return true;   // a /pay typed by hand: not ours to count
        pending.remove(p); late.remove(p);
        Session s = session();
        if (s != null) {
            // only what he had counts as chips sent back: anything above is a gift, never a debt that would eat his next payments
            long counted = Math.min(p.amount, balance(p.name));
            if (counted > 0) s.sent.merge(p.name, counted, Long::sum);
            s.sends.add(new Session.Pay(p.name, p.amount, P.now(), false));
            GambleData.touch();
        }
        recent.put(p.name, new Recent(true, p.amount, P.now()));
        ver++;
        P.play("cash", 1, 0.6);
        return true;
    }

    /** A refusal right after our /pay ends its wait at once (the oldest send of the last few seconds is the one refused). */
    private static boolean refused(String line) {
        long t = P.now();
        Pending p = pending.stream().filter(q -> q.resendAt == 0 && t - q.at < 5000).min(Comparator.comparingLong(Pending::at)).orElse(null);
        if (p == null) return false;
        Matcher m = WAIT.matcher(line);
        if (m.find()) {   // nothing was paid: the same click goes again once the cooldown is over
            if (p.resent) return false;
            pending.set(pending.indexOf(p), new Pending(p.name, p.amount, p.at, t + (long) (Double.parseDouble(m.group(1)) * 1000) + 150, true));
            return false;
        }
        String low = line.toLowerCase(Locale.ROOT);
        for (String[] r : REFUSED) if (low.contains(r[0])) {
            pending.remove(p);
            recent.put(p.name, new Recent(false, 0, t, r[1]));
            ver++;
            return false;
        }
        return false;
    }

    public static void tick() {
        long t = P.now();
        for (Pending p : new ArrayList<>(pending)) {
            if (p.resendAt > 0 && t >= p.resendAt) {
                var nh = MinecraftClient.getInstance().getNetworkHandler();
                if (nh != null) nh.sendChatCommand("pay " + p.name + " " + p.amount);
                pending.set(pending.indexOf(p), new Pending(p.name, p.amount, t, 0, true));
            } else if (p.resendAt == 0 && t - p.at > TIMEOUT) {
                pending.remove(p); late.add(p); recent.put(p.name, new Recent(false, 0, t)); ver++;
            }
        }
        late.removeIf(p -> t - p.at > 60000);
    }
}
