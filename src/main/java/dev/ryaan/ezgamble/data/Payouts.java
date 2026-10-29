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
 * was already sent back, never negative. A send runs "/pay <name> <amount>" (one click = one command, never on its
 * own) and only counts once the server confirms "You paid <name> $X". No confirmation within 15 s = nothing deducted.
 */
public final class Payouts {
    private Payouts() {}

    public record Pending(String name, long amount, long at) {}
    public record Recent(boolean ok, long amount, long at) {}
    public record Row(String name, long recv, long sent, long won, long bal, Pending pend, Recent recent) {}

    public static final List<Pending> pending = new ArrayList<>();
    public static final Map<String, Recent> recent = new HashMap<>();
    public static int ver;

    /** "You paid Bob $ 5M" and its translations (EzChat / EzAuction), plus the Essentials form. */
    private static final String NAME = "\\.?[A-Za-z0-9_]{2,16}", AMT = "[\\d][\\d,]*(?:\\.\\d+)?\\s*[KMBkmb]?";
    private static final String YOU = "(?:you|vous|tu|du|usted|você|voce|jij|ty|vy|sen|anda)";
    private static final Pattern OUT_DONUT = Pattern.compile("^" + YOU + " [\\p{L}'’ ]{0,40}?(" + NAME + ") \\$\\s*(" + AMT + ")\\s*[.!]?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OUT_VAULT = Pattern.compile("^(?:\\w+ )?You paid (" + AMT + ")\\s*\\$ to (" + NAME + ")\\.?$");

    /** The last session, even after mode OFF: chips stay owed. */
    public static Session session() { return GambleData.last(); }

    public static boolean same(String a, String b) { return norm(a).equals(norm(b)); }
    public static String norm(String n) { return n.toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""); }

    public static List<Row> rows() {
        Session s = session();
        if (s == null) return List.of();
        List<Row> out = new ArrayList<>();
        for (var e : s.credit.entrySet()) {
            String n = e.getKey();
            long recv = e.getValue(), sent = s.sent.getOrDefault(n, 0L), won = s.won.getOrDefault(n, 0L);
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
        var nh = MinecraftClient.getInstance().getNetworkHandler();
        if (nh != null) nh.sendChatCommand("pay " + name + " " + amount);   // whole dollars: no K/M rounding on the server
        pending.add(new Pending(name, amount, P.now()));
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
        if (who == null) return false;
        String w = who;
        Pending p = pending.stream().filter(q -> same(q.name, w)).findFirst().orElse(null);
        if (p == null) return true;   // a /pay typed by hand: not ours to count
        pending.remove(p);
        Session s = session();
        if (s != null) {
            s.sent.merge(p.name, p.amount, Long::sum);
            s.sends.add(new Session.Pay(p.name, p.amount, P.now(), false));
            GambleData.touch();
        }
        recent.put(p.name, new Recent(true, p.amount, P.now()));
        ver++;
        P.play("cash", 1, 0.6);
        return true;
    }

    public static void tick() {
        long t = P.now();
        for (Pending p : new ArrayList<>(pending)) if (t - p.at > 15000) { pending.remove(p); recent.put(p.name, new Recent(false, 0, t)); ver++; }
    }
}
