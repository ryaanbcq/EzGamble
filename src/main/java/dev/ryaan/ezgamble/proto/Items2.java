package dev.ryaan.ezgamble.proto;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Prototype names -> items and sprites. */
public final class Items2 {
    private Items2() {}

    private static final Map<String, ItemStack> ITEMS = new HashMap<>();
    /** Texture names the prototype draws as items. */
    private static final Map<String, String> ALIAS = Map.of("tnt_side", "tnt", "clock_00", "clock", "crossbow_standby", "crossbow",
            "compass_00", "compass", "recovery_compass_00", "recovery_compass");
    /** Sprites shipped by the mod (assets/ezgamble/textures/gui/...). */
    private static final Set<String> OURS = Set.of("card", "game", "crate", "chip", "coin");

    public static ItemStack stack(String name) {
        return ITEMS.computeIfAbsent(name, n -> {
            String id = ALIAS.getOrDefault(n, n);
            if (id.contains(":")) id = id.substring(id.indexOf(':') + 1);
            for (String tryId = id; ; ) {
                Identifier ident = Identifier.ofVanilla(tryId);
                if (Registries.ITEM.containsId(ident)) return new ItemStack(Registries.ITEM.get(ident));
                int u = tryId.lastIndexOf('_');
                if (u < 0) return ItemStack.EMPTY;
                tryId = tryId.substring(0, u);
            }
        }).copy();
    }

    public static Identifier sprite(String name) {
        int s = name.indexOf('/');
        if (s > 0 && OURS.contains(name.substring(0, s))) {
            String dir = switch (name.substring(0, s)) { case "card" -> "cards"; case "game" -> "games"; case "crate" -> "crates"; default -> name.substring(0, s); };
            return Identifier.of("ezgamble", "textures/gui/" + dir + "/" + name.substring(s + 1) + ".png");
        }
        return Identifier.ofVanilla("textures/" + name + ".png");
    }
}
