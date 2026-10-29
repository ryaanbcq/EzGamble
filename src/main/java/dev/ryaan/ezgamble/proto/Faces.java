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
                if (r == null || r.profile().properties().get("textures").isEmpty()) { fail(k, System.currentTimeMillis() + 60_000); return; }
                GameProfile p = r.profile();
                client.execute(() -> { FETCHING.put(k, client.getSkinProvider().fetchSkinTextures(p)); PENDING.remove(k); });
            } catch (Exception e) {
                fail(k, System.currentTimeMillis() + 60_000);
            }
        });
    }

    private static void fail(String k, long until) {
        MinecraftClient.getInstance().execute(() -> { FAILED.put(k, until); PENDING.remove(k); });
    }
}
