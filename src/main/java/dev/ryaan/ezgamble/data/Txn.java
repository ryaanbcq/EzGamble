package dev.ryaan.ezgamble.data;

/** One bet seen by the mod. delta = house side profit; bet may be null; player = backer whose chips were played (null for server games). */
public record Txn(long t, String game, Long bet, long delta, String player, String raw) {}
