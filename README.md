# Mouse Gestures for IntelliJ IDEA

Execute IDE actions by drawing mouse gestures. Hold the **right mouse button**, drag to draw a shape, release — the assigned action fires.

## Features

- **Live trail** drawn directly in the IDE with real-time direction arrows
- **Match preview** — see which action will fire before you release
- **40+ curated actions** — navigate, edit, build, debug, and more
- Correct **Undo / Redo** (full editor context, not just at mouse position)
- Correct **Navigate Backward / Forward** (no spurious history entries)
- **Recording mode** — draw gestures directly over the settings window
- **Revert** after recording to undo an accidental change
- Duplicate-pattern detection with inline warning
- Per-gesture **enable / disable** toggle
- **Short right-click** (no drag) still shows the context menu — no conflicts
- Trail color, match color, and thickness **fully customizable**

## Default Gestures

| Gesture | Action |
|---------|--------|
| ← Left | Navigate Backward |
| → Right | Navigate Forward |
| ↓ Down | Comment / Uncomment Line |
| ↓↘ Down + Right | Close Tab |

## Installation

### From JetBrains Marketplace
1. Open **Settings → Plugins → Marketplace**
2. Search for **Mouse Gestures**
3. Click **Install** and restart the IDE

### From disk (`.zip` / `.jar`)
1. Download the latest release from [Releases](../../releases)
2. Open **Settings → Plugins → ⚙ → Install Plugin from Disk…**
3. Select the downloaded file and restart the IDE

## Usage

1. **Draw a gesture** — hold right mouse button anywhere in the editor and drag
2. **Release** to execute the matched action
3. **Short right-click** (no drag) still opens the context menu

## Configuration

Open **Settings → Tools → Mouse Gestures** or use **Tools → Mouse Gestures → Settings**.

### Adding a gesture
1. Click **+ Add**
2. Enter a name
3. Click **⏺ Record Gesture** and draw with the right mouse button
4. Select an action from the list
5. Click **Apply** or **OK**

### Visualization
Adjust trail color, match highlight color, thickness, and direction-arrow visibility in the **Gesture Visualization** section at the bottom of the settings panel.

## Compatibility

| IDE | Minimum version |
|-----|----------------|
| IntelliJ IDEA (Community & Ultimate) | 2024.2 |
| Other JetBrains IDEs | 2024.2+ |

## Building from source

```bash
git clone https://github.com/Aadammm/Mouse_Gestures
cd Mouse_Gestures
./gradlew buildPlugin          # produces build/distributions/*.zip
./gradlew runIde               # launches a sandboxed IDE for testing
```

Requires JDK 21.

## Acknowledgements

Inspired by [MouseGestures](https://github.com/Quasabe/MouseGestures) by Quasabe.

## License

[MIT](LICENSE)
