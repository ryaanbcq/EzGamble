# EzGamble

Client-side Fabric mod for Minecraft 1.21.11 by ZKR5: host gambling games on an SMP with in-game money. Players `/pay` you, you track every bet, chips and payout from one glass menu (key **K**).

## What is in the menu

- **Dashboard**: profit, volume, win rate, the session's curve, and the **House** line: your bankroll (read from the server's sidebar) against the chips you owe, with a cover ratio. Healthy, Tight or Under-covered, with an alarm when you owe more than you have.
- **History**: every bet, payment and payout, filtered by wins or losses.
- **Games**: Blackjack, Roulette, Crash, Mines, Crates, Coinflip, Rain, Auction, the High rollers podium, plus the Events, Books and Jackpot tiles (the grid scrolls). Players `/pay` you, their chips sit on the table, you play for them and send winnings back in one click. The header holds Requests (duel challenges), Fair (provably fair log) and the Lobby.
- **Lobby**: players whisper `join blackjack 5M` (or `queue`, `sit`, `play`; `leave` steps out) and take a number. Games > Lobby lists the line, every felt shows a clickable "Next in line" pill, and the Auto-seat option (HUD tab) seats the next player a couple of seconds after a round.
- **VIP**: players earn XP by wagering at your tables. Bronze, Silver (25M), Gold (100M), Platinum (500M) and Diamond (2B) tiers, a gem next to their name everywhere, a cashback rate per tier (0 to 2 %) that you owe and pay in one click from their profile. The ladder is under Games, chips card, VIP.
- **Badges**: 24 achievements a player earns at your tables (First Blood, Whale, Hot Streak, High Roller, Diamond Hands...), derived from his history with a rarity colour each. A badges row in his profile opens the catalogue, All players shows his count, a new one slides down the HUD.
- **Events**: timed events, 5 to 60 minutes, one at a time. A **Wager race** ranks the players who wager the most on a live leaderboard and ends on a podium; the prize pool goes 50 / 30 / 20 to the top three, one Pay click per winner. **Double XP** counts VIP XP twice, **Happy hour** adds 1 % cashback to every tier. A banner on the HUD counts down while one runs.
- **Jackpot**: a progressive pot fed by a slice of every bet placed with a player's chips (0.5, 1 or 2 %), drawn at on every such bet with a provably fair round, the odds growing with the bet. A hit puts the pot in the player's chips, plays a full-screen finale, and restarts the pot at your seed. The HUD shows the pot.
- **Books**: your accounting. Every session tallied per table (bets, volume, house profit, realised return against the table's theoretical RTP), by hour of the day, by day over five weeks, and per session with the best and worst. Export CSV, copy the totals, open any session's Recap.
- **House guard**: a bet whose top win you could not pay on top of what you already owe is refused at the table (HUD tab to turn it off).
- **Recap**: when a session closes (or from the Dashboard, or `/ezgamble recap`), a full-screen story of the session: profit and curve, hottest table, the high roller on a podium, best and worst moments, who sat at the table, and a share card saved as a PNG or copied as text.
- **Themes**: six colour themes for the whole mod (Lavender, Emerald, Gold Rush, Crimson, Ocean, Mono), picked from the HUD tab.
- **HUD**: a session card (curve, win rate, all-time, house cover, the lobby count), a toast per bet, the payments card, placed and styled in the HUD editor. News (a tier-up, a badge, a player joining the line, an event, the recap hint) shows in one banner queue at the top centre, one at a time; the running event and the jackpot sit in a row under it.

## Smoke tests

`tools/smoke.sh <script>` runs a script of `DebugRun` commands in the EzTest instance and saves screenshots: `tools/smoke-vip.txt`, `tools/smoke-house.txt`, `tools/smoke-recap.txt`, `tools/smoke-lobby.txt`, `tools/smoke-events.txt`, `tools/smoke-badges.txt`, `tools/smoke-books.txt`, `tools/smoke-themes.txt`, `tools/smoke-jackpot.txt`.

## Install

Drop the latest jar from [`update/`](update/) into your `mods` folder, with Fabric API.

## Updates

The mod checks `update/latest.json` at startup. When a newer version exists, an **Update** button appears in the menu: it downloads the jar, checks its SHA-256, and swaps it the next time Minecraft starts.

## Build

```
./gradlew build
```

Release a version: bump `mod_version` in `gradle.properties`, then `./publish.sh`.
