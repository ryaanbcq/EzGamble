# EzGamble

Client-side Fabric mod for Minecraft 1.21.11 by ZKR5: host gambling games on an SMP with in-game money. Players `/pay` you, you track every bet, chips and payout from one glass menu (key **K**).

## Install

Drop the latest jar from [`update/`](update/) into your `mods` folder, with Fabric API.

## Updates

The mod checks `update/latest.json` at startup. When a newer version exists, an **Update** button appears in the menu: it downloads the jar, checks its SHA-256, and swaps it the next time Minecraft starts.

## Build

```
./gradlew build
```

Release a version: bump `mod_version` in `gradle.properties`, then `./publish.sh`.
