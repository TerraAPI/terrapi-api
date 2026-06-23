# OSM Import & Valhalla Isochrones

Two **independent** components built from the same `.osm.pbf`:

| Component | Owns | Purpose |
|-----------|------|---------|
| **OSM import** (osm2pgsql) | `routing_edges` table in PostGIS | Query the OSM road network from the DB (rendering, spatial joins). **Not** used for routing. |
| **Valhalla** (separate service) | Its own tiled routing graph | Serves isochrones (and could serve routing/matrix later). Reads the pbf directly. |

They share nothing at runtime — you can run, rebuild, or drop either without affecting the other.

```
osm/portugal-*.osm.pbf
   ├── osm2pgsql  ──► PostGIS  routing_edges      (GET via RoutingEdge / SQL)
   └── valhalla   ──► tiles    /custom_files      (GET /api/v1/isochrone)
```

---

## 1. OSM import (osm2pgsql → PostGIS)

Loads highway ways from a `.pbf` into the `routing_edges` table using an osm2pgsql **flex** style. Rare, admin-only, **synchronous**, and **fails loudly** (non-zero exit code or zero imported rows both raise).

### Prerequisites
`osm2pgsql` must be installed:
- **Linux:** `apt install osm2pgsql` (on `PATH`).
- **Windows (dev):** download the binary; either put its folder on `PATH` or set `OSM2PGSQL_PATH` to the full `.exe` path (e.g. in `.env`).

### Endpoints
| Method | Path | Notes |
|--------|------|-------|
| `POST` | `/api/v1/import/osm/{folder}` | Server-side folder containing **exactly one** `.pbf`. |
| `POST` | `/api/v1/import/osm/upload` | Multipart `.pbf` upload. |

Response: `ImportResult` — edge counts grouped by `highway` class.

### What it does
Shells out to:
```
osm2pgsql --output=flex --style=<routing-edges.lua> -d <db-uri> \
          --number-processes <N> --log-progress=true <file.pbf>
```
osm2pgsql **drops and recreates** `routing_edges` each run. The flex style (`src/main/resources/osm/routing-edges.lua`) keeps only routable highways (`area=yes` and non-routable lifecycle values excluded) and writes typed columns:

| Column | Type | |
|--------|------|--|
| `osm_id` | bigint | way id (PK on the `RoutingEdge` entity) |
| `highway` | text | road class |
| `oneway`, `junction` | text | direction hints |
| `maxspeed_kmh` | int2 | parsed speed (null if absent) |
| `access`, `foot`, `bicycle`, `motor_vehicle`, `motorcar` | text | legality |
| `service`, `surface` | text | refinements |
| `layer`, `bridge`, `tunnel` | int2 / bool | grade separation |
| `geom` | geometry(LineString, 4326) | edge geometry (+ GIST index) |

Read-only access from the app is via `RoutingEdge` (`@Immutable` JPA entity) + `RoutingEdgeRepository`.

### Configuration (`terrapi.osm.*`)
| Key | Default | Meaning |
|-----|---------|---------|
| `osm2pgsql-path` | `osm2pgsql` (env `OSM2PGSQL_PATH`) | executable location |
| `style-resource` | `osm/routing-edges.lua` | classpath Lua style |
| `slim` | `false` (env `OSM_SLIM`) | `false` = fast, in-RAM middle (needs several GB); `true` = `--slim --drop --flat-nodes`, memory-gentle for small hosts |
| `number-processes` | `0` | `0` = auto (CPU cores) |
| `log-progress-seconds` | `10` | throttle for osm2pgsql progress log lines |

> Portugal (~415 MB) imports in ~30–50s in non-slim mode.

---

## 2. Valhalla (isochrones)

Valhalla is a self-hosted, open-source routing engine (free; you pay only for compute). It builds its **own** graph tiles from the pbf and serves isochrones natively.

### Service
Defined in `docker-compose.yml`:
```yaml
valhalla:
  image: ghcr.io/nilsnolde/docker-valhalla/valhalla:latest
  ports: ["8002:8002"]
  environment:
    use_tiles_ignore_pbf: "False"   # build/refresh tiles from the pbf
    server_threads: "8"
  volumes:
    - ./osm:/custom_files           # picks up the .pbf, writes tiles here
```
On first `up` it builds tiles from the pbf in `./osm` (a few minutes). Restarts reuse cached tiles and rebuild only when the pbf changes (hash-tracked). Generated files (`valhalla.json`, tiles, `valhalla_tiles.tar`) live in `./osm` (gitignored).

### Endpoint
`GET /api/v1/isochrone?lat=&lon=&minutes=&mode=`

| Param | |
|-------|--|
| `lat`, `lon` | origin (EPSG:4326) |
| `minutes` | travel-time budget |
| `mode` | `car` / `foot` / `bike` → Valhalla `auto` / `pedestrian` / `bicycle` |

Returns a **GeoJSON FeatureCollection** (`application/geo+json`) of contour polygons, straight from Valhalla. Sub-second even for large budgets.

### Configuration
| Key | Default |
|-----|---------|
| `terrapi.valhalla.url` (env `VALHALLA_URL`) | `http://localhost:8002` |

---

## Runbook

```bash
# bring up DB (alpine PostGIS) + Valhalla
docker compose up -d            # first run: Valhalla builds Portugal tiles (~min)

# start the app, then load the road table (optional — only for DB queries)
curl -X POST "http://localhost:8080/api/v1/import/osm/osm"

# isochrone (Valhalla)
curl "http://localhost:8080/api/v1/isochrone?lat=38.7223&lon=-9.1393&minutes=40&mode=car"
```

### Quarterly data update
1. Replace the `.pbf` in `./osm`.
2. `docker restart terrapi-valhalla` → Valhalla rebuilds tiles automatically.
3. (Optional) re-run the OSM import endpoint to refresh `routing_edges`.
