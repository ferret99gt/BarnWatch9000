# Changelog

This changelog focuses on user-visible BarnWatch9000 changes. The Git history contains the
implementation-level record.

## 0.2.0

Version 0.2.0 is the first formally tracked BarnWatch9000 release.

### Camera wall

- Camera devices and the selected grid layout are persisted in SQLite.
- Substream grids can switch to a camera's main stream in focused mode.
- Equal-tile, emphasized-tile, paging, drag-and-drop ordering, theater, zoom, and pan layouts
  support a range of camera-wall arrangements.
- Compatible Foscam cameras expose focused pan, tilt, preset, reset, and optical zoom controls.
- VLC is discovered from common 64-bit Windows installation locations.

### Packaging and maintenance

- The build baseline is Microsoft OpenJDK 25.0.4 LTS, JavaFX 25.0.4, Maven 3.9+, JUnit 6,
  SQLite JDBC 3.53.2.1, and VLCJ 4.12.1.
- Maven lifecycle plugins and the Windows app-image packaging plugin are explicitly pinned.
- The Windows release bundle contains a self-contained Java runtime, release documentation,
  and a SHA-256 checksum. A separate 64-bit VLC installation is still required for playback.
- Clean-build, unit-test, packaged-runtime, launcher smoke-test, and archive checks run in
  GitHub Actions.
- Maven and GitHub Actions dependencies are monitored weekly by Dependabot.

## 0.1.0

Historical baseline covering BarnWatch9000 work before formal release tracking began.
