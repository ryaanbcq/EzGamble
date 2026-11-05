package dev.ryaan.ezgamble.achievements;

import dev.ryaan.ezgamble.games.crates.CrateTable;

/**
 * One badge: id, name, one-line description, rarity (the crates' colours) and its 9 x 9 pixel icon. `px` is nine rows
 * of nine characters: '.' transparent, A main colour, B its shade, C its highlight, D the second colour, E its shade.
 */
public record Achievement(String id, String name, String desc, String rarity, int main, int second, String[] px) {
    /** The rarity colour (common grey, rare blue, epic purple, legendary gold: CrateTable.RARITY). */
    public int color() { return CrateTable.RARITY.getOrDefault(rarity, 0xFFB8B0C8); }

    /** 0 common .. 3 legendary, for "the rarest earned". */
    public int rank() { return switch (rarity) { case "legendary" -> 3; case "epic" -> 2; case "rare" -> 1; default -> 0; }; }

    public String rarityName() { return Character.toUpperCase(rarity.charAt(0)) + rarity.substring(1); }
}
