# TheHouse

TheHouse is a client-side Fabric utility for Minecraft 1.21.11. It provides table-game assistance for Paper, 50/50, Blackjack, and Arrow, plus optional rendering utilities and a local browser control panel.

The mod is client-side. It does not add blocks, items, commands, or server-side content. Settings are stored in `config/thehouse.properties` and survive Minecraft restarts.

## Requirements

- Minecraft 1.21.11
- Java 21 or newer
- Fabric Loader 0.18.4 or newer
- Fabric API
- The bundled build also contains the separately remapped OpSec module.

## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Install Fabric API.
3. Copy the built `TheHouse-OpSec-1.0.0-single.jar` into the Minecraft `mods` directory.
4. Start Minecraft with the Fabric profile.

The single JAR contains both TheHouse and OpSec. Do not install the separately generated `opsec-1.0.0.jar` alongside the single JAR, because that would load OpSec twice.

## Quick controls

The default general keybinds are:

| Key | Action |
| --- | --- |
| `Insert` | Open the external browser UI |
| `R` | Toggle Paper simulation |
| `U` | Cycle the selected Paper winning side |
| `B` | Toggle Blackjack |
| `H` | Advance the Blackjack host turn or start a new hand |
| Unassigned | Toggle Arrow |

The render keybinds are initially unassigned except for zoom:

| Key | Action |
| --- | --- |
| Unassigned | Toggle Freecam |
| Unassigned | Toggle Fullbright |
| Unassigned | Toggle NoRender |
| Unassigned | Toggle ESP |
| `Left Alt` | Hold to zoom |
| Unassigned | Toggle Nametags |

All unassigned keys can be configured in Minecraft's Controls menu under the `TheHouse` and `TheHouse · Render` categories.

## External browser UI

TheHouse starts a localhost-only control service on `127.0.0.1:1337` and opens it in the default browser once after client initialization. Press `Insert` at any time to open it again.

The browser page provides controls for:

- Paper and 50/50 state.
- Winning-side selection.
- Blackjack state, host turn, and relearning.
- Arrow enablement, board learning, target-cell selection, and masking.
- Render module toggles and numeric settings.
- Paper slot mappings and saved profiles.
- HUD colors and other persistent settings.

The page uses a per-session token, loopback binding, host/origin validation, request-size limits, rate limiting, and restrictive browser security headers. It is not exposed on the LAN. If the browser does not open automatically, press `Insert` and check that another process is not already using port `1337`.

## Paper

Paper simulation masks the visible item result in the Paper dropper/table while preserving the chosen outcome. It is coupled to Side Lock: enabling Paper also enables the side-lock behavior.

### Enable and select a side

1. Open the UI with `Insert`, or press `R`.
2. Press `U` to cycle between `RIGHT` and `LEFT` as the intended winning side.
3. Open the Paper table/dropper normally.

When Paper is disabled, the tracker restores captured inventory state. Changing the winning side also restores the current masked state and starts a clean round.

### Paper item naming convention

The two Paper droppers use the same nine logical numbers but different custom-name suffixes:

- Right dropper: `1`, `2`, `3`, … `9`
- Left dropper: `1.1`, `2.1`, `3.1`, … `9.1`

The `.1` suffix identifies the left-side version of a numbered item. Keep the suffixes intact when configuring or replacing the table items; TheHouse uses them to distinguish the two droppers. The browser UI's slot editor shows the logical slot number and the selected Minecraft item ID, while the tracker uses the displayed custom names to determine which side is open.

The Paper HUD indicator is a one-pixel marker at the top-left of the screen. Its color is configurable in the browser UI.

## 50/50

50/50 support is available through the browser UI and the Paper workflow. The module tracks the 50/50 dropper state, applies the configured simulation behavior, and restores captured items when simulation is disabled or the world changes.

Use the UI to:

- Toggle 50/50 mode.
- Reset the 50/50 slot mappings.
- Change the Paper/50/50 HUD colors.

World and dimension changes clear transient tracking state so an old round is not applied to a new world.

## Blackjack

Blackjack provides visual masking and state tracking for the Blackjack dropper/table.

### Basic use

1. Press `B` or enable Blackjack in the browser UI.
2. Use `H` or the browser's host-turn control to advance the host turn.
3. Open the Blackjack dropper/table and let the tracker observe the cards.

The module distinguishes viewer cards, host cards, locked state, and failure/relearn feedback. The Blackjack HUD marker appears 10 pixels to the right of the Paper marker.

### Required Blackjack dropper setup

Every Blackjack dropper slot must start with **8 map-art items per card value**. The map arts use the following nine-name scheme: **A, 3, 4, 5, 6, 7, 8, 9, 10**. Each slot must contain a stack of eight matching map arts before the hand begins:

```text
slot A: 8 copies
slot 3: 8 copies
slot 4: 8 copies
slot 5: 8 copies
slot 6: 8 copies
slot 7: 8 copies
slot 8: 8 copies
slot 9: 8 copies
slot 10: 8 copies
```

This is a required starting invariant for the visual accounting. The tracker does not infer the starting quantity from whatever happens to be visible when the GUI is opened. If a slot has fewer or more than eight at setup, returned-card accounting and masking can become incorrect.

The nine physical slots may be in any learned order, but each supported card name must appear exactly once during table learning. Do not mix card templates in a slot or leave a named slot empty when learning the table.

### Switching Viewer and Host sides

Blackjack starts each new hand on the Viewer side. Press `H` or click `Host Turn` in the browser UI to switch the active dealing side. Each press advances the round lifecycle:

1. The first three presses alternate Viewer and Host normally.
2. The fourth press performs the final side switch and engages the mask lock.
3. Once locked, additional `H` presses are ignored for that hand.

The lock preserves all existing card assignments and prevents new cards from being interpreted as fresh mask assignments. The white/locked HUD color indicates this phase. This lock is intentional: it allows cards returned to the dropper to be reconciled without rewriting the established Viewer/Host mapping.

To start over, toggle Blackjack off and on again with `B` or the browser's `Enabled` button. That resets the hand to Viewer, clears the side-switch count, and clears the lock. Use `H` once after the reset to begin the next Host phase.

The `Host Turn / New Hand` key does not itself reset an active hand. It advances the side lifecycle until the fourth-toggle lock; use the Blackjack enable toggle for a full reset.

### Relearning the table

Use the browser UI to arm Blackjack table relearning. Open the replacement dropper/table while relearning is armed. The module validates and commits the learned slot mapping only after the full template set is available. Existing mappings remain stable until a successful relearn completes.

The HUD reports relearning feedback using the configured success, locked, and failure colors.

## Arrow

Arrow tracks the physical 7×7 Arrow board and classifies the observed landing cell. Four corners and the center dispenser are not selectable cells.

### Learn the board

1. Look at the board's center/dispenser block.
2. Open the browser UI and choose `Learn Board`, or use the Arrow action control.
3. Enable Arrow.

The learned center is saved in the configuration. If the board moves or a different board is loaded, relearn it.

### Target and masking

Arrow uses the fixed Host-win board classification. Choose a selectable target cell in the browser UI. Enabling Arrow enables its masking behavior and applies the selected target to the next round.

The UI reports `TRACKING`, the detected target coordinates, or `NO GRID RESULT` when the trajectory cannot be classified. The target coordinates are clamped to the valid board range.

## Rendering modules

Rendering modules are independent and default to off.

### Freecam

Freecam detaches the camera from the player. It can only be enabled while the player is alive in a world. The default speed is `0.5`, configurable from `0.1` to `5.0`. Freecam is reset when changing worlds and is never resumed automatically after restart.

### Fullbright

Fullbright increases scene visibility by enabling the module's bright-render path. Toggle it from the render keybind or browser UI.

### NoRender

NoRender suppresses selected visual effects. The available switches are:

- Fire overlay
- Water overlay
- Wall overlay
- Nether portal overlay
- Vignette
- Weather
- Boss bar
- Scoreboard

The first six are enabled by default when NoRender is turned on; boss-bar and scoreboard suppression are available but default to off.

### ESP

ESP outlines entities within the configured range. By default it targets players only. Mobs and dropped items can be enabled separately. The default range is `128` blocks and the default outline color is `#55ccff`.

### Zoom

Hold `Left Alt` to zoom. The zoom factor can be adjusted from `1.0` to `20.0`; the default is `4.0`. Smooth interpolation is enabled by default. While zooming, the mouse wheel adjusts zoom instead of changing the hotbar selection.

### Nametags

Nametags display additional information for other living players within range. Health and distance display are enabled by default, and the default range is `96` blocks.

## Profiles and item mappings

The Paper item catalog maps logical slots 1–9 to Minecraft item IDs. The browser UI can change these mappings and save named slot profiles.

Use profiles when different tables use different item layouts:

1. Set the nine slot mappings.
2. Create a profile and give it a descriptive name.
3. Switch profiles when changing tables.
4. Use the reset action to restore default mappings.

Invalid item IDs or malformed profile names are rejected without overwriting the current valid configuration.

## HUD colors

HUD colors are configurable for:

- Paper right-side outcome
- Paper left-side outcome
- Blackjack viewer state
- Blackjack host state
- Blackjack locked state
- Blackjack relearn failure state

The HUD is intentionally minimal: it uses small color indicators rather than text banners. Paper and Blackjack indicators appear at the top-left of the screen.

## Configuration

The saved file is:

```text
.minecraft/config/thehouse.properties
```

It contains the enabled states, winning side, learned Arrow board center, Arrow target, item mappings, profiles, HUD colors, and render settings. The mod writes the file automatically when a setting changes.

Temporary state such as active rounds, tracked entities, camera state, and in-progress masking is cleared when changing worlds or dimensions.

## Building from source

From the project root:

```powershell
.\gradlew.bat clean build
```

The bundled release is written to:

```text
build/libs/TheHouse-OpSec-1.0.0-single.jar
```

The build also produces separate TheHouse and OpSec JARs for development. Use the `single` JAR for normal installation.

## Troubleshooting

### The browser UI does not open

- Press `Insert` manually.
- Check that port `1337` is free.
- Check the Minecraft log for `Local UI could not bind`.
- Confirm that the external browser is available to the operating system.

### A module does not respond

- Confirm the module is enabled in the browser UI or Controls menu.
- Join a world before enabling Freecam.
- For Arrow, relearn the board center after moving to a new table.
- For Blackjack, arm relearning when the table layout changes.

### Settings reset

Check that `.minecraft/config/thehouse.properties` is writable. TheHouse falls back to defaults if the file cannot be read and silently skips persistence if it cannot write.

### Build fails while downloading Minecraft

Fabric Loom downloads Minecraft artifacts from Mojang. Verify network access, disable a VPN or proxy temporarily, and rerun the build. Loom stores the server artifact under `.gradle-home/caches/fabric-loom/1.21.11/` when this project-local Gradle cache is used.

## License and distribution note

This repository currently contains the source and build configuration for TheHouse and its bundled OpSec module. No license activation or hidden payment feature is required by the active build.

## Shoutout

Special thanks to [Aurick](https://github.com/aurickk) for [OpSec](https://github.com/aurickk/OpSec), which is bundled with TheHouse.
