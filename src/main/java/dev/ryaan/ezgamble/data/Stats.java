package dev.ryaan.ezgamble.data;

import java.util.List;

/** Dashboard cards: profit, volume, count, win rate, best win and worst loss. */
public record Stats(long profit, long volume, int n, int wins, int losses, float rate, Long best, Long worst) {
    public static Stats of(List<Txn> list) {
        long profit = 0, volume = 0;
        int wins = 0;
        Long best = null, worst = null;
        for (Txn t : list) {
            profit += t.delta();
            volume += t.bet() != null ? t.bet() : Math.abs(t.delta());
            if (t.delta() > 0) wins++;
            if (t.delta() > 0 && (best == null || t.delta() > best)) best = t.delta();
            if (t.delta() < 0 && (worst == null || t.delta() < worst)) worst = t.delta();
        }
        int n = list.size();
        return new Stats(profit, volume, n, wins, n - wins, n > 0 ? (float) wins / n : 0, best, worst);
    }
}
