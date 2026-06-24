# Precision (LOD) Design

## The LOD ladder (0–4)

Each `GeoUnitType` has its own LOD ladder in `geo_unit_precisions`, with tolerances scaled
to the type's typical feature size:

- **LOD 0** — tolerance just above native mainland vertex spacing for that type.
- **LOD 1** — simplified (~2× LOD 0 tolerance).
- **LOD 2** — *coarsest default*, the lightest payload used as the grid's default.
- **LOD 3** — extra-coarse for zoomed-out overviews.
- **LOD 4** — ultra-coarse for highest zoom levels.

> LOD 0 must **never** be configured with tolerance `0`: `ST_SimplifyPreserveTopology(geom, 0)`
> returns the original unchanged, re-creating an exact duplicate and the large payload it exists
> to avoid.

| Type | LOD 0 | LOD 1 | LOD 2 | LOD 3 | LOD 4 |
|------|-------|-------|-------|-------|-------|
| PARISH | 25 m | 50 m | 200 m | 500 m | 5,000 m |
| MUNICIPALITY | 50 m | 100 m | 500 m | 1,000 m | 10,000 m |
| DISTRICT | 100 m | 200 m | 1,000 m | 2,500 m | 20,000 m |
| ISLAND | 50 m | 100 m | 500 m | 1,000 m | 10,000 m |
| NUTS3 | 100 m | 200 m | 1,000 m | 2,500 m | 20,000 m |
| NUTS2 | 200 m | 500 m | 2,000 m | 5,000 m | 25,000 m |
| NUTS1 | 500 m | 1,000 m | 5,000 m | 10,000 m | 50,000 m |

Tolerances were chosen from measured CAOP geometry scale: native vertex spacing ≈ 18–37 m on
the mainland, ≈ 3–15 m on the islands, and the smallest-feature sizes (smallest parish: 20 ha,
2 km perimeter; largest district: 1,026,332 ha). Each type's LOD 0 is just above native
spacing; each successive LOD roughly doubles the tolerance; LOD 4 produces ~50–100 vertices
for average features of that type.

## The original geometry

The untouched `geo_units.geometry` is **not** served as a whole layer. It is used only for:

- per-unit precise boundaries (`GET /api/v1/geo/{code}/geometry`), the "precise" half of the
  two-tier selection flow, and
- spatial-correctness queries (`reverse-geocode`, `contains`, `within`, `within-bbox`), where a
  simplified border would misclassify points near edges.

All whole-layer (`/api/v1/layers/{type}`) requests are served from `geo_unit_precisions`,
including LOD 0.

## Topology preservation per layer

Each `GeoUnitType` is coverage-simplified independently from its source geometry in
`geo_units`. All features of the same type form a valid, gapless coverage; edges are
**not** shared across types. A parish boundary does not coincide with its municipality
boundary at simplified LODs — each layer is optimized for its own feature scale.

Generation uses PostGIS `ST_CoverageSimplify`, a window function that simplifies the
shared edges of a coverage consistently, so neighbouring units of the same type keep
identical boundaries:

```sql
ST_CoverageSimplify(geom_3763, tolerance, <simplifyBoundary>) OVER (PARTITION BY lod)
```

Partitioning by `lod` keeps the tolerance constant within a partition (required by
`ST_CoverageSimplify`). This is applied to **each** type independently; there is no
hierarchical dissolution from finer layers into coarser ones.

### Authoritative coverage, no fallback

`ST_CoverageSimplify` requires a valid coverage. The CAOP dataset is an authoritative,
fixed-format single source of truth and is a valid coverage. There is **no** fallback to
independent per-feature simplification: if the coverage is ever invalid (e.g. the format
changes), generation fails loudly (`FAILED`) and the transaction rolls back, preserving
the previous precisions — rather than silently producing a lower-quality result.

### Configuration (`terrapi.precision`)

- `ladders` — per-type LOD ladders (keyed by lowercase `GeoUnitType` name: `parish`,
  `municipality`, `district`, `island`, `nuts3`, `nuts2`, `nuts1`). Each type defines its own
  `{lod, tolerance}` entries. All types share the same LOD indices (0–4) for API consistency.
- `simplify-boundary` — passed to `ST_CoverageSimplify` (default `false`, keeps the outer
  coverage boundary crisp).
- `validation` — `max-null-pct` / `max-invalid-pct` / `max-empty-pct` thresholds; a run
  is healthy when it produced rows and stays within them.

### Per-type independent simplification

Each `GeoUnitType` (DISTRICT, MUNICIPALITY, PARISH, ISLAND, NUTS1, NUTS2, NUTS3) is
coverage-simplified independently at each LOD. Coarser layers (districts, NUTS) are
simplified from their own source geometries — not dissolved from finer layers — so they
are lighter and better scaled to their feature size. The tradeoff: edges do not align
across layers, and regenerating all types is required for a full refresh.

Generation loops over all `GeoUnitType` values per LOD level: select features of that
type from `geo_units`, apply `ST_CoverageSimplify` at that type's tolerance, and insert into
`geo_unit_precisions`. Border arcs are independently line-simplified in the same
transaction, but only during full (all-types) generation runs.

Generation can be scoped to a single type via the `?type` query parameter, e.g.
`POST /api/v1/precision/generate?lod=2&type=PARISH`. When a type is specified, only that
type's precisions are deleted and regenerated; borders are left untouched.

## Consumption: whole-layer selection grid

The simplified precisions feed a whole-layer **"grid"** endpoint used for on-map selection
(show all districts/municipalities/parishes, let the user click one):

```
GET /api/v1/layers/{type}?lod={n}&parent={code}
```

- Returns a GeoJSON `FeatureCollection` (`{code, name}` properties) for every unit of `type`.
- Geometry always comes from `geo_unit_precisions` (transformed 3857 → 4326), for every LOD
  including `lod 0` (most detailed). `lod 2` (coarsest) is the default. The original
  `geo_units` geometry is never served as a whole layer.
- `parent` (optional) restricts to a parent's direct children (a district's municipalities,
  a municipality's parishes) for drill-down selection.
- Responses are cached and ETag'd by the active `generation_id` (immutable per generation),
  the same invalidation key the precision pipeline produces.

Precisions are generated via:
```
POST /api/v1/precision/generate?lod={n}&type={GEO_UNIT_TYPE}
```
- `lod` (optional) scopes to a single LOD level across all types.
- `type` (optional) scopes to a single `GeoUnitType` (e.g. `PARISH`, `NUTS3`) — only that
  type's precisions are deleted and regenerated; borders are untouched.
- Omit both for a full regeneration of all types and borders.

**Two-tier selection.** The grid ships *simplified* geometry (cheap, just for clicking and
labelling). Once a unit is selected, its *precise* (full-detail original) boundary is fetched
on demand via the per-unit geometry endpoint (`GET /api/v1/geo/{code}/geometry`). This keeps
grid payloads small while still allowing exact rendering of the chosen unit.

Vector tiles (MVT) were removed: at Portugal's feature counts (≤ ~3k parishes nationally,
far fewer per parent) a single cached, simplified layer payload covers the selection,
choropleth and export use cases without the per-tile complexity. The
`geo_unit_precisions` / LOD / coverage pipeline is retained as the grid's data source.

## Layers vs borders

Two geometry-serving surfaces, fed from one generation transaction:

- **`/api/v1/layers/{type}`** — polygon *fills* per `GeoUnitType`, produced by
  `ST_CoverageSimplify` independently per type. Used for selection and choropleth.
- **`/api/v1/borders`** — the classified boundary-*line* network (CAOP `trocos`): each shared
  edge carries `level` (1 national … 5 parish), `lineType` (LAND/COAST/WATER) and `lengthKm`,
  and is drawn once. Used for boundary overlays / styling. Always served from the simplified tier
  (`border_segment_precisions`), `?lod={0..2}` (default `2`, coarsest) — like `/layers`, the
  full-detail network is never dumped wholesale.

`/borders` is still a distinct surface — not for its geometry but for its
**edge-level semantics**: the border `level` (for thick-national / thin-parish styling) and
especially the coastline/water `lineType`, which is **not** derivable from admin polygons. It is
also the better rendering primitive — each shared edge is stroked once instead of double-drawn by
polygon outlines.

## Border precision (LOD)

Borders are originally ingested at **full detail** from CAOP `trocos` into `border_segments`
(EPSG:4326, no simplification). To match the layer LOD ladder, each arc is **independently**
line-simplified into `border_segment_precisions` per LOD using
`ST_SimplifyPreserveTopology(geom_3763, tolerance)` (the **same** `terrapi.precision.lod` ladder as
the layers, stored in 3857). Arc endpoints — the shared network nodes between `trocos` — are
preserved by Douglas–Peucker, so the line network stays connected and per-arc non-self-intersecting.

Generation runs **inside** `PrecisionWriter.write`, in the same transaction and under the same
`generation_id` as the layer hierarchy: borders and layers rebuild atomically, share one
invalidation key, and roll back together on an unhealthy result (border rows are folded into the
validation counts).
