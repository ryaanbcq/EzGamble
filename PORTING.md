# Porting the prototype to the mod

The spec is the HTML prototype `~/ezgamble/prototype/src.html` (canvas JS that emulates Minecraft's DrawContext). The mod
must look and behave exactly like it: same constants, texts, layout numbers, timings, odds, sounds, keyboard shortcuts.
Port line by line, keep the prototype's comments where they explain a choice. English UI texts, no em dash anywhere.

Minecraft 1.21.11, Fabric, yarn mappings, Java 21, client only. Package `dev.ryaan.ezgamble`.

## Build

```
cd <your worktree>
JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home ./gradlew build -q --no-daemon
```
Run it outside the sandbox; the network can be flaky, retry once. Never launch Minecraft yourself: there is one test
instance and the lead runs the smoke tests after merging. Never touch `~/ezauction`, `~/ezchat` or any Prism instance.

## The runtime that mirrors the prototype (already written, do not rewrite)

| Prototype | Java |
|---|---|
| `g.fill / fillGradient / drawText / drawItem(name, x, y, glint) / drawSprite / drawHead(skin, ...) / push / pop / translate / scale / rotate / enableScissor / disableScissor` | `proto.G` (same names, double coordinates). `drawHead(name, ...)` takes the player NAME (`P.skinOf(name)` returns the name). `drawItem` takes a vanilla id ("emerald", "tnt_side" works) or an ItemStack. Sprites: `card/face`, `card/back`, `game/<id>`, `crate/<id>` |
| `F.width`, `F.trimToWidth` | `proto.F` |
| top-level helpers and colours: `LAV PINK TEXT WHITE MUTE DIM GREEN RED INK`, `lerp alpha shade now easeOut easeIn pulse play rounded outline accentBar accentRounded dot gradientText smooth ring knob capsule glass hoverSheen squish card tile pill pillW pillsRight seatTag seatTagW clock DATE DATE_S` | `proto.P` (static, same names). Gradients for smooth: `P.h(a, b)` = `['h', a, b]`, `P.v(a, b)` = `['v', a, b]`. `capsule(g, x, y, w, h, kind, hover[, opacity])` (no mx/my) |
| `Widget` (x, y, w, h, visible, active, down, clip, list; over, render, click, drag, release) | `proto.Widget` (+ `key(Key)` for focused widgets). `const w = s.add(new Widget(...)); w.click = () => ...` becomes `Widgets.area(x, y, w, h, () -> ...)` or an anonymous subclass |
| `GlassButton(x, y, w, h, label, 'PRIMARY'/'GHOST'/'DANGER', action)` | `proto.GlassButton` (public label, kind, action, active) |
| `GlassField(x, y, w, h, value, placeholder, max)` (value, focused, search, selectAll, onChange, type(e)) | `proto.GlassField` (same fields; `type(Key)`) |
| `GlassSegmented`, `GlassSwitch`, `GlassSlider`, `ColorSwatches`, `Scroller` | `proto.*` same names |
| keyboard `e.key` ('Escape', 'Enter', 'ArrowUp', 'Backspace', a typed character) | `proto.Key`: `e.key()`, `e.is("Enter")`, `e.isChar()` (typed text, letters come here) |
| `play(name, pitch, vol)` | `P.play` / `proto.Snd` (all the prototype's sound names exist: pay, cash, fail, open, close, click, ui_*, mc_*) |
| canvas drawing (`ctx.ellipse`, gradients, arcs, strokes: chips, coins, wheel, curves) | `proto.Vec.draw(g, key, x, y, w, h, pad, g2 -> ...)`: Java2D painter in GUI units, anti-aliased, cached by key at real-pixel resolution. Put everything that changes the picture in the key and QUANTISE animations (e.g. an angle in 64 steps): a new key = a texture upload |
| `chipsFor, chipStack, drawBet, CHIPS` | `games.Chips` |
| `CrateTable` fields shared by every host table: backer, bet, betChips, field, picker, warn, backers(), balance(), fail(), addChip(), undoChip(), clearBet() | `games.Host` (abstract, `settle()`), + `fitBet()` = the "bet shrunk to the balance" of again() |
| `BackerPicker(x, y, w, host)`, `ChipButton(x, y, chip, host)`, `RefundPill(host, settle)`, `payStateOf`, `payOut`, `drawCard`, `rand01`, `multText` | `games.Widgets.*` (a round that can be paid extends `Widgets.Round`) |
| `DB.mode`, `record({...})`, `currentSession()`, `lastSession()` | `data.GambleData.mode / record(new Txn(t, game, bet, delta, player, raw)) / current() / last()` |
| `Payouts.rows() / session() / send / pending / recent`, `s.won[name] += v` | `data.Payouts.*`, `session.addWon(name, v)` |
| `Payments` | `data.Payments` |
| `GambleScreen` (x0, y0, W, H, PAD, add, children, rebuild, sub, sc(id), beginList, endList) | `ui.GambleScreen`: same + `geom()` (the felt: lx, rx, hy, fy, fh, ft, fth, by, cx) and `back()` (the ‹ button: settles the view, back to the tiles) |
| `XxxView` (constructor(screen), init, tick, render, key) inside GambleScreen | implement `games.GameView` (init, tick, render, key, scrolled, leave = settle when leaving, clicked(hit) = what mouseDown did after the click: fold fields and pickers that were not clicked) |
| the table singleton (`const MN = new MinesTable()`) | a `public static final` instance in your table class (it survives the menu) |
| a game clock that runs with the menu closed (`CX.tick()` in the frame loop) | one line in `games.Ticks.tick()` |

Things the prototype emulates that are vanilla in Java: `FloatingItem.show(item)` = `client.gameRenderer.showFloatingItem(stack)`;
`Title` = `inGameHud.setTitleTicks(10, 100, 20)` + `setTitle` / `setSubtitle`; `sendCommand(cmd)` = `client.getNetworkHandler().sendChatCommand(cmd)`.

Skip what is prototype-only: the fake world, the simulation, the dev bar, `Liquid.reduce` branches (always take the glass branch).

## Rules

- Your files only: your package (listed in your task). Do not edit shared files except the one-line hook your task names.
  If you need a helper that does not exist, put it in your package.
- Keep the stub class names: `Views` already creates `games.blackjack.BlackjackView`, `games.crates.CratesView`,
  `games.mines.MinesView`, `games.crash.CrashView`, `games.roulette.RouletteView`, `games.coinflip.CoinflipView`,
  `auction.AuctionView`, `duel.DuelsView`, `ui.ChipsView`, `ui.PlayerView`, each with a constructor `(GambleScreen s)`.
- Performance: every `g.fill` costs ~5 us in 1.21.11. Never fill pixel by pixel each frame: use `Vec` sprites (cached) or
  few large fills. The whole menu must stay under ~3 ms of CPU per frame.
- Work in your own git worktree and branch (your task says which), commit there, never push, never touch `main`.
- Final report: the branch, the files, anything not ported or approximated and why.
