package dev.ryaan.ezgamble.proto;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.response.NameAndId;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Real skins by name (prototype skinOf): tab list first, then Mojang on a background thread, the default skin of the
 * name meanwhile and for Bedrock names (leading dot) or unknown names. Copied from EzChat's Faces, simplified.
 */
public final class Faces {
    private Faces() {}

    private static final Map<String, SkinTextures> READY = new HashMap<>();
    private static final Map<String, CompletableFuture<Optional<SkinTextures>>> FETCHING = new HashMap<>();
    private static final Set<String> PENDING = new HashSet<>();
    private static final Map<String, Long> FAILED = new HashMap<>();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "ezgamble-skins"); t.setDaemon(true); return t; });

    /** Smoke test: skip authlib, as if its lookup had failed. */
    public static boolean forceDirect;

    private static String key(String name) { return name.toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""); }

    public static SkinTextures get(String name) {
        if (name == null || name.isEmpty()) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        String k = key(name);
        // the tab list first, read every time and never cached: right after joining (or while the server's skin is
        // downloading) an entry hands out the DEFAULT skin, and caching that drew the player as a Steve for the whole
        // session (seen live on ZKR5). A default from the tab list sends us to Mojang instead.
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                if (!key(e.getProfile().name()).equals(k)) continue;
                SkinTextures t = e.getSkinTextures();
                if (!t.equals(DefaultSkinHelper.getSkinTextures(e.getProfile().id()))) return t;
                break;
            }
        }
        SkinTextures ready = READY.get(k);
        if (ready != null) return ready;
        CompletableFuture<Optional<SkinTextures>> f = FETCHING.get(k);
        if (f != null && f.isDone()) {
            SkinTextures t;
            try { t = f.getNow(Optional.empty()).orElse(def(name)); } catch (Exception e) { t = def(name); }
            READY.put(k, t);
            FETCHING.remove(k);
            return t;
        }
        if (f == null && !PENDING.contains(k) && !name.startsWith(".") && name.matches("[A-Za-z0-9_]{3,16}")
                && System.currentTimeMillis() >= FAILED.getOrDefault(k, 0L)) resolve(name, k);
        return def(name);
    }

    private static SkinTextures def(String name) {
        return DefaultSkinHelper.getSkinTextures(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
    }

    private static void resolve(String name, String k) {
        PENDING.add(k);
        MinecraftClient client = MinecraftClient.getInstance();
        POOL.submit(() -> {
            try {
                UUID id = client.getApiServices().profileRepository().findProfileByName(name).map(NameAndId::id).orElse(null);
                if (id == null) { fail(k, Long.MAX_VALUE); return; }
                ProfileResult r = client.getApiServices().sessionService().fetchProfile(id, false);
                // authlib caches a FAILED lookup for 6 hours (insecureProfiles): one hiccup of the session server drew the
                // player as a Steve all evening (seen live on NotRyzn). Then we ask the session server ourselves.
                GameProfile p = !forceDirect && r != null && !r.profile().properties().get("textures").isEmpty() ? r.profile() : direct(id, name);
                if (p == null) { fail(k, System.currentTimeMillis() + 30_000); return; }
                client.execute(() -> { FETCHING.put(k, client.getSkinProvider().fetchSkinTextures(p)); PENDING.remove(k); });
            } catch (Exception e) {
                dev.ryaan.ezgamble.EzGambleMod.LOG.info("[ezgamble] skin lookup of {} failed: {}", name, e.toString());
                fail(k, System.currentTimeMillis() + 60_000);
            }
        });
    }

    private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(8)).build();

    /** The profile with its signed textures, straight from the session server (no authlib cache), or null. */
    private static GameProfile direct(UUID id, String name) {
        try {
            var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://sessionserver.mojang.com/session/minecraft/profile/"
                    + id.toString().replace("-", "") + "?unsigned=false")).timeout(java.time.Duration.ofSeconds(10)).GET().build();
            var res = HTTP.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) { dev.ryaan.ezgamble.EzGambleMod.LOG.info("[ezgamble] skin of {}: session server {}", name, res.statusCode()); return null; }
            var o = com.google.gson.JsonParser.parseString(res.body()).getAsJsonObject();
            com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> m = com.google.common.collect.LinkedHashMultimap.create();
            for (var e : o.getAsJsonArray("properties")) {
                var po = e.getAsJsonObject();
                if (!po.get("name").getAsString().equals("textures")) continue;
                m.put("textures", new com.mojang.authlib.properties.Property("textures", po.get("value").getAsString(), po.has("signature") ? po.get("signature").getAsString() : null));
            }
            return m.isEmpty() ? null : new GameProfile(id, o.has("name") ? o.get("name").getAsString() : name, new com.mojang.authlib.properties.PropertyMap(m));
        } catch (Exception e) {
            dev.ryaan.ezgamble.EzGambleMod.LOG.info("[ezgamble] skin of {}: {}", name, e.toString());
            return null;
        }
    }

    private static void fail(String k, long until) {
        MinecraftClient.getInstance().execute(() -> { FAILED.put(k, until); PENDING.remove(k); });
    }
}
