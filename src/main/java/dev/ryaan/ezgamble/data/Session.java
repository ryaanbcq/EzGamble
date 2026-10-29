package dev.ryaan.ezgamble.data;

import java.util.ArrayList;
import java.util.List;

/** A stretch of gambling mode ON: one file in config/ezgamble/sessions/. */
public final class Session {
    public String server = "";
    public long start;
    public Long end;
    public List<Txn> txns = new ArrayList<>();
    transient String file;
}
