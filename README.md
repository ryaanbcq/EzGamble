# EzGamble

Client-side Fabric mod for Minecraft 1.21.11 by ZKR5: host gambling games on an SMP with in-game money. Players `/pay` you, you track every bet, chips and payout from one glass menu (key **K**).

## What is in the menu

- **Dashboard**: profit, volume, win rate, the session's curve, and the **House** line: your bankroll (read from the server's sidebar) against the chips you owe, with a cover ratio. Healthy, Tight or Under-covered, with an alarm when you owe more than you have.
- **History**: every bet, payment and payout, filtered by wins or losses.
- **Games**: Blackjack, Roulette, Crash, Mines, Crates, Coinflip, Rain, Auction, the High rollers podium and Provably fair. Players `/pay` you, their chips sit on the table, you play for them and send winnings back in one click.
- **VIP**: players earn XP by wagering at your tables. Bronze, Silver (25M), Gold (100M), Platinum (500M) and Diamond (2B) tiers, a gem next to their name everywhere, a cashback rate per tier (0 to 2 %) that you owe and pay in one click from their profile. The ladder is under Games, chips card, VIP.
- **House guard**: a bet whose top win you could not pay on top of what you already owe is refused at the table (HUD tab to turn it off).
- **Recap**: when a session closes (or from the Dashboard, or `/ezgamble recap`), a full-screen story of the session: profit and curve, hottest table, the high roller on a podium, best and worst moments, who sat at the table, and a share card saved as a PNG or copied as text.
- **HUD**: a session card (curve, win rate, all-time, house cover), a toast per bet, the payments card, placed and styled in the HUD editor.

## Smoke tests

`tools/smoke.sh <script>` runs a script of `DebugRun` commands in the EzTest instance and saves screenshots: `tools/smoke-vip.txt`, `tools/smoke-house.txt`, `tools/smoke-recap.txt`.

## Install

Drop the latest jar from [`update/`](update/) into your `mods` folder, with Fabric API.

## Updates

The mod checks `update/latest.json` at startup. When a newer version exists, an **Update** button appears in the menu: it downloads the jar, checks its SHA-256, and swaps it the next time Minecraft starts.

## Build

```
./gradlew build
```

Release a version: bump `mod_version` in `gradle.properties`, then `./publish.sh`.
