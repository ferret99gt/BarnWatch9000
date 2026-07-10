# Barn Watch 9000

Lightweight JavaFX RTSP wall viewer for barn cameras. Developed with assistance from Codex using GPT-5.4 and later models.

## Current Scope

- SQLite-backed camera device list
- SQLite-backed app settings for persisted grid selection
- Arbitrary wall viewing with substream grid mode and mainstream focus mode
- Multiple grid views, including equal-tile and emphasis layouts
- Paging for layouts with fewer visible slots than configured cameras
- Double-click a tile to focus a single camera
- Theater mode that fills the selected display without relying on JavaFX fullscreen
- Mouse wheel zoom on a tile
- Click-and-drag pan while zoomed
- Drag-and-drop camera reordering with persistent saved order
- Right-click tile menu with `Reconnect`
- Focused PTZ controls for compatible cameras:
  - click-and-drag pan/tilt
  - preset dropdown with `Go`
  - `Reset`
  - optical `Zoom - / +`
- Bottom overlay labels with camera name and active stream
- Automatic VLC detection for 64-bit Windows installs
- PTZ control is currently implemented against the Foscam CGI/API path, not a generic camera-control standard

## Requirements

- JDK 25
- Maven 3.9+
- VLC installed on Windows, 64-bit

## Run

```bash
mvn javafx:run
```

## Build

```bash
mvn -DskipTests package
```

## Release App Image

```bash
mvn -DskipTests -Prelease package
```

The packaged app image is written under `target/dist`.

## License

Barn Watch 9000 is released under the MIT License. See [LICENSE](LICENSE).
