package dev.ryaan.ezgamble.data;

import java.util.ArrayList;
import java.util.List;

/** A stretch of gambling mode ON: one file in config/ezgamble/sessions/. */
public final class Session {
    public String server = "";
    public long start;
    public Long end;
    public List<Txn> txns = new ArrayList<>();
    /** What each player /paid the gambler this session (name -> total). */
    public java.util.Map<String, Long> credit = new java.util.LinkedHashMap<>();
    /** Sent back to each player with /pay (confirmed by the server). */
    public java.util.Map<String, Long> sent = new java.util.LinkedHashMap<>();
    /** Won (+) or lost (-) by each player at the mod's tables, his point of view. */
    public java.util.Map<String, Long> won = new java.util.LinkedHashMap<>();
    /** Every /pay received (bid = during an auction) and every confirmed send. */
    public List<Pay> payments = new ArrayList<>(), sends = new ArrayList<>();

    public record Pay(String name, long amount, long t, boolean bid) {}

    public void addWon(String name, long v) { won.merge(name, v, Long::sum); GambleData.touch(); }
    transient String file;
}
