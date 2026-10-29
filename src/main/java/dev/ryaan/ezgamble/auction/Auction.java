package dev.ryaan.ezgamble.auction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.ryaan.ezgamble.Chat;
import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.Payments;
import dev.ryaan.ezgamble.proto.Items2;
import dev.ryaan.ezgamble.proto.P;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * One auction at a time, EzAuction's rules: every "X paid you $Y" received between start and end is X's BID (it adds
 * to his total and never becomes chips); the highest total that reaches the minimum wins, ties to whoever got there
 * first; losers are not refunded; nothing is written in public chat (local lines only). Soft close: a bid that CHANGES
 * the leader in the last `overtime` seconds puts the timer back to it. Stream delay: the timer shows 0:00 but bids
 * still count for `streamDelay` seconds ("And the winner is..." with a drum roll), for livestream viewers.
 * Ryan, 2026-10-29: no QuickBuy, no auto /tpa, no API key. All the items, the screen, and the timer's tension.
 */
public final class Auction {
    private Auction() {}

    public enum State { IDLE, RUNNING, ENDED }

    public static final class Bid {
        public String name; public long total; public int payments; public long reachedAt, lastAt, last;
    }
    public static final class Win {
        public String name; public long total; public int payments;
        Win() {}
        Win(Bid b) { name = b.name; total = b.total; payments = b.payments; }
    }
    public static final class Hist {
        public long at; public String item; public int count; public String label; public long min;
        public Win winner; public long revenue; public int bidders;
    }
    public record LastBid(String name, long amount, long at) {}
    /** One entry of the item picker (prototype ITEM_LIST). */
    public record ItemEntry(String id, String name, String key) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("ezgamble");

    // the form (config/ezgamble/auction.json): kept between two auctions
    public static String item = "elytra";
    public static int count = 1;
    public static long min = 1_000_000;
    public static int secs = 120, overtime = 10, streamDelay = 0;
    public static State state = State.IDLE;
    public static long startAt, endAt, cutoffAt, endedAt, extendedAt, extendedBy;
    public static final Map<String, Bid> bids = new LinkedHashMap<>();
    public static Bid winner;
    public static LastBid lastBid;
    public static final List<Hist> history = new ArrayList<>();
    private static long lastTick = -1, nextDrum, cashAt, overlayAt;
    private static String overlayText;

    private static List<ItemEntry> itemList;
    private static final Map<String, String> NAMES = new TreeMap<>();

    static { load(); }

    // ---- items

    /** Readable name from the item id (Item.getName()). */
    public static String itemName(String id) {
        return NAMES.computeIfAbsent(id, i -> {
            var it = Registries.ITEM.get(Identifier.ofVanilla(i));
            return it == null ? i : it.getName().getString();
        });
    }

    /** Every item of the game (Registries.ITEM), sorted by id. */
    public static List<ItemEntry> itemList() {
        if (itemList == null) {
            List<ItemEntry> l = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds()) {
                if (!id.getNamespace().equals("minecraft") || id.getPath().equals("air")) continue;
                l.add(new ItemEntry(id.getPath(), itemName(id.getPath()), id.getPath().replace('_', ' ')));
            }
            l.sort(Comparator.comparing(ItemEntry::id));
            itemList = l;
        }
        return itemList;
    }

    // ---- persistence

    private static boolean validItem(String id) {
        return id != null && Registries.ITEM.containsId(Identifier.ofVanilla(id)) && !id.equals("air");
    }

    private static void load() {
        try {
            Path p = DIR.resolve("auction.json");
            if (Files.exists(p)) {
                var o = GSON.fromJson(Files.readString(p), com.google.gson.JsonObject.class);
                if (o.has("item")) item = o.get("item").getAsString();
                if (o.has("count")) count = o.get("count").getAsInt();
                if (o.has("min")) min = o.get("min").getAsLong();
                if (o.has("secs")) secs = o.get("secs").getAsInt();
                if (o.has("overtime")) overtime = o.get("overtime").getAsInt();
                if (o.has("streamDelay")) streamDelay = o.get("streamDelay").getAsInt();
            }
            Path h = DIR.resolve("auction-history.json");
            if (Files.exists(h)) {
                Type t = new TypeToken<List<Hist>>() {}.getType();
                List<Hist> l = GSON.fromJson(Files.readString(h), t);
                if (l != null) history.addAll(l);
            }
        } catch (Exception e) {
            EzGambleMod.LOG.warn("[ezgamble] auction load failed: {}", e.toString());
        }
        if (!validItem(item)) item = validItem("elytra") ? "elytra" : "diamond";
        count = Math.max(1, Math.min(64, count));
    }

    public static void save() {
        var o = new com.google.gson.JsonObject();
        o.addProperty("item", item); o.addProperty("count", count); o.addProperty("min", min);
        o.addProperty("secs", secs); o.addProperty("overtime", overtime); o.addProperty("streamDelay", streamDelay);
        write(DIR.resolve("auction.json"), GSON.toJson(o));
        write(DIR.resolve("auction-history.json"), GSON.toJson(history));
    }

    private static void write(Path p, String json) {
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            EzGambleMod.LOG.warn("[ezgamble] write {} failed: {}", p, e.toString());
        }
    }

    // ---- model

    public static String label() { String n = itemName(item); return count > 1 ? count + " x " + n : n; }

    /** Highest total first, then the one who got there first. */
    public static List<Bid> rows() {
        List<Bid> l = new ArrayList<>(bids.values());
        l.sort((a, b) -> a.total != b.total ? Long.compare(b.total, a.total) : Long.compare(a.reachedAt, b.reachedAt));
        return l;
    }

    /** Current leader among eligible bidders (total >= min), or null. */
    public static Bid leader() {
        for (Bid b : rows()) if (b.total >= min) return b;
        return null;
    }

    public static long pot() { long s = 0; for (Bid b : bids.values()) s += b.total; return s; }
    public static long remaining() { return state == State.RUNNING ? Math.max(0, endAt - P.now()) : 0; }
    public static long duration() { return endAt - startAt; }
    /** Stream delay grace: the timer is over on screen, late bids still count. */
    public static boolean inGrace() { return state == State.RUNNING && P.now() >= endAt; }

    public static void start() {
        long t = P.now();
        state = State.RUNNING; startAt = t; endAt = t + secs * 1000L; cutoffAt = t + (secs + Math.max(0, streamDelay)) * 1000L;
        bids.clear(); winner = null; endedAt = 0; extendedAt = 0; extendedBy = 0; lastBid = null; lastTick = -1; nextDrum = 0;
        cashAt = 0; overlayText = null;
        Payments.auctionBid = Auction::bid;
        floatingItem();   // GameRenderer.showFloatingItem: the reward pops like a totem
        P.play("ui_kcollide_open", 1, 0.6);
        // the stream delay is the host's business: not in chat
        say("ᴀᴜᴄᴛɪᴏɴ", "*" + label() + "* is up from *" + Money.format(min) + "*, *" + P.clock(secs * 1000L) + "* to bid", null);
    }

    public static void bid(String name, long amount) {
        if (state != State.RUNNING) return;
        long t = P.now();
        if (t > cutoffAt) { finish(); return; }   // a late line: the deadline is what counts
        Bid before = leader();
        String k = name.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
        Bid b = bids.get(k);
        if (b == null) { b = new Bid(); b.name = name; b.reachedAt = t; bids.put(k, b); }
        b.total += amount; b.payments++; b.reachedAt = t; b.lastAt = t; b.last = amount;
        lastBid = new LastBid(name, amount, t);
        // soft close: only a bid that CHANGES the leader counts (a $1 loop from the leader must not stall the auction),
        // in the last `overtime` seconds of the timer, drum roll included
        long ot = Math.max(0, overtime) * 1000L;
        Bid after = leader();
        if (ot == 0 || after == null || after == before || endAt - t >= ot) return;
        long newEnd = Math.max(endAt, t + ot);
        if (newEnd == endAt) return;
        extendedBy = newEnd - endAt; extendedAt = t;
        cutoffAt += newEnd - endAt; endAt = newEnd;
    }

    /** Every client tick. */
    public static void tick() {
        long t = P.now();
        if (cashAt != 0 && t >= cashAt) { cashAt = 0; P.play("cash", 1, 1); }   // after the gavel: sold!
        if (overlayText != null && t >= overlayAt) {   // revenue in the action bar, after the title lands
            var mc = MinecraftClient.getInstance();
            if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.literal(overlayText).withColor(P.GREEN & 0xFFFFFF), false);
            overlayText = null;
        }
        if (state != State.RUNNING) return;
        if (t >= cutoffAt) { finish(); return; }
        long left = endAt - t;
        if (left <= 0) { drumroll(t); return; }
        // stress countdown: one tick a second from 10 s, pitch climbing, two hits a second under 3 s with a low thud
        long sec = (long) Math.ceil(left / 1000.0), slot = sec <= 3 ? left / 500 : sec;
        if (sec <= 10 && slot != lastTick) {
            lastTick = slot;
            P.play("ui_tick_close", Math.min(2, 0.8 + (10 - sec) * 0.12), 0.9);
            if (sec <= 3) P.play("ui_kstack_close", 0.5, 1);
        }
    }

    /** Stream delay window: a snare roll that speeds up and gets louder until the cutoff, then the gavel. */
    private static void drumroll(long t) {
        double p = Math.min(1, (t - endAt) / (double) Math.max(1, cutoffAt - endAt));
        if (t < nextDrum) return;
        P.play("ui_kcards_close", 0.9 + 0.3 * p, 0.45 + 0.55 * p);
        nextDrum = t + Math.round(220 - 170 * p);
    }

    public static void finish() {
        if (state != State.RUNNING) return;
        long t = P.now();
        Bid w = leader();
        state = State.ENDED; endedAt = t; winner = w;
        Payments.auctionBid = null;
        Hist h = new Hist();
        h.at = t; h.item = item; h.count = count; h.label = label(); h.min = min;
        h.winner = w == null ? null : new Win(w); h.revenue = pot(); h.bidders = rows().size();
        history.add(h);
        save();
        P.play("fail", 1, 1);   // the gavel
        if (w == null) {
            say("ɴᴏ ᴡɪɴɴᴇʀ", "Nobody reached *" + Money.format(min) + "*", P.RED);
            title("ɴᴏ ᴡɪɴɴᴇʀ", "Nobody reached the " + Money.format(min) + " minimum.", P.RED);
            return;
        }
        cashAt = t + 450;   // then the jingle: sold!
        say("ꜱᴏʟᴅ", "*" + w.name + "* wins *" + label() + "* for *" + Money.format(w.total) + "*", P.GREEN);
        title(w.name, "ᴡɪɴꜱ " + label() + " ꜰᴏʀ " + Money.format(w.total), null);
        overlayText = "+" + Money.format(pot()); overlayAt = t + 1200;
    }

    public static void cancel() {
        if (state != State.RUNNING) return;
        state = State.IDLE; bids.clear(); winner = null;
        Payments.auctionBid = null;
        say("ᴄᴀɴᴄᴇʟʟᴇᴅ", "The auction is off", P.RED);
    }

    /** Back to the form, keeping nothing. */
    public static void reset() {
        if (state == State.RUNNING) return;
        state = State.IDLE; bids.clear(); winner = null;
    }

    /** Placeholder kept for the chat hook of EzGambleMod: bids arrive through Payments.auctionBid. */
    public static boolean onChat(String line) { return false; }

    // ---- vanilla output

    private static void floatingItem() {
        var mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null) return;
        mc.gameRenderer.showFloatingItem(P.shiny(Items2.stack(item)));
        mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ITEM_TOTEM_USE, 1f, 1f));
    }

    /** The mod's own chat line (Chat.java): ONE small caps tag in its colour (null = Outmind gradient), then a short grey
     * sentence, its *values* white. Local only, never sent to the server. */
    public static void say(String tag, String text, Integer color) {
        var mc = MinecraftClient.getInstance();
        if (mc.inGameHud == null) return;
        MutableText line = color == null ? Chat.gradient(tag) : Text.literal(tag).withColor(color & 0xFFFFFF);
        line.append(Text.literal(" "));
        String[] parts = text.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) if (!parts[i].isEmpty()) line.append(i % 2 == 1 ? Chat.white(parts[i]) : Chat.grey(parts[i]));
        mc.inGameHud.getChatHud().addMessage(line);
    }

    /** Vanilla title + subtitle, 10 + 100 + 20 ticks (color null = Outmind gradient over a soft grey subtitle). */
    public static void title(String big, String sub, Integer color) {
        var mc = MinecraftClient.getInstance();
        if (mc.inGameHud == null) return;
        mc.inGameHud.setTitleTicks(10, 100, 20);
        mc.inGameHud.setSubtitle(Text.literal(sub).withColor((color == null ? P.SOFT_GREY : color) & 0xFFFFFF));
        mc.inGameHud.setTitle(color == null ? Chat.gradient(big) : Text.literal(big).withColor(color & 0xFFFFFF));
    }

    /** client.getNetworkHandler().sendChatCommand(cmd). */
    public static void runCommand(String cmd) {
        var nh = MinecraftClient.getInstance().getNetworkHandler();
        if (nh != null) nh.sendChatCommand(cmd);
    }
}
