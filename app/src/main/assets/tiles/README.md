# Offline Tile Archives Directory

Place your pre-packaged route corridor map tiles here:
- `route_85.sqlite` or `route_85.mbtiles`

### Recommended Tile Generation Tools:
1. **MOBAC (Mobile Atlas Creator)**:
   - Select OSM Standard / OpenStreetMap
   - Choose Zoom Levels: 13, 14, 15, 16, 17
   - Atlas Format: "osmdroid SQLite"
   - Output filename: `route_85.sqlite`
2. Save to this directory (`app/src/main/assets/tiles/`).

`OfflineTileManager` automatically copies `.sqlite` / `.mbtiles` files to the app's private osmdroid storage directory on first run and configures `OfflineTileProvider` with `setUseDataConnection(false)`.
