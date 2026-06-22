# Precision (LOD) Design

## The LOD ladder (0–3)

Each `GeoUnitType` is stored in `geo_unit_precisions` at four levels of detail:

- **LOD 0** — *most detailed*. A small, **non-zero** tolerance (just above the data's native
  vertex spacing) so it is not a byte-for-byte duplicate of the original, yet visually
  ~full-detail. This is what makes it worth storing: it lets the whole-layer ("grid") endpoint
  serve a near-full-detail layer without ever shipping the multi-hundred-MB original.
- **LOD 1–2** — progressively simplified.
- **LOD 3** — *coarsest*, the lightest payload (used as the grid's default).

Tolerances are per type and configured under `terrapi.precision.lod` in `application.yaml`. They
were chosen from the measured CAOP geometry scale (native vertex spacing ≈ 18–37 m on the
mainland, ≈ 3–15 m on the islands, plus the smallest-feature sizes), so LOD 0 reduces vertex
count meaningfully without distorting even the smallest parishes/islands.

> LOD 0 must **never** be configured with tolerance `0`: `ST_SimplifyPreserveTopology(geom, 0)`
> returns the original unchanged, re-creating an exact duplicate and the large payload it exists
> to avoid.

## The original geometry

The untouched `geo_units.geometry` is **not** served as a whole layer. It is used only for:

- per-unit precise boundaries (`GET /api/v1/geo/{code}/geometry`), the "precise" half of the
  two-tier selection flow, and
- spatial-correctness queries (`reverse-geocode`, `contains`, `within`, `within-bbox`), where a
  simplified border would misclassify points near edges.

All whole-layer (`/api/v1/layers/{type}`) requests are served from `geo_unit_precisions`,
including LOD 0.

## Topology preservation per layer

Each `GeoUnitType` is a polygonal **coverage** — a gapless, non-overlapping tessellation (e.g. all parishes tile the country). Simplifying each unit independently with `ST_SimplifyPreserveTopology` only preserves the topology of a single polygon; adjacent units simplify their shared border differently, producing slivers, gaps and overlaps that break the layer's typology at higher LODs.

Generation therefore uses PostGIS `ST_CoverageSimplify`, a window function that simplifies the shared edges of a coverage consistently, so neighbouring units keep identical boundaries:

```sql
ST_CoverageSimplify(geom_3763, tolerance, <simplifyBoundary>) OVER (PARTITION BY lod)
```

Partitioning by `lod` makes each `(type, lod)` a single coverage and keeps the tolerance constant within a partition (required by `ST_CoverageSimplify`). Generation already runs per `GeoUnitType`, so each layer is simplified as its own coverage and layers never mix.

### Validity preflight and fallback

`ST_CoverageSimplify` requires a valid coverage as input. Before simplifying a type, the coverage is checked with `ST_CoverageInvalidEdges(geom, coverage-snap-tolerance) OVER ()`. If any invalid edges are found (common with raw CAOP micro-slivers), generation falls back to the per-feature `ST_SimplifyPreserveTopology` path for that type and the whole run is marked **DEGRADED** (non-blocking, but visible).

### Configuration (`terrapi.precision`)

- `topology-preserving` — master toggle (default `true`); `false` always uses per-feature simplification.
- `topology-preserving-by-type` — optional per-`GeoUnitType` override map.
- `simplify-boundary` — passed to `ST_CoverageSimplify` (default `false`, keeps the outer coverage boundary).
- `coverage-snap-tolerance` — tolerance for the `ST_CoverageInvalidEdges` validity check (default `0.0`, exact).

### Scope

This preserves topology **within** each layer only. Cross-layer hierarchical nesting (a parish edge coinciding with its parent municipality edge) is out of scope.

## Consumption: whole-layer selection grid

The simplified precisions feed a whole-layer **"grid"** endpoint used for on-map selection
(show all districts/municipalities/parishes, let the user click one):

```
GET /api/v1/layers/{type}?lod={n}&parent={code}
```

- Returns a GeoJSON `FeatureCollection` (`{code, name}` properties) for every unit of `type`.
- Geometry always comes from `geo_unit_precisions` (transformed 3857 → 4326), for every LOD
  including `lod 0` (most detailed). `lod 3` (coarsest) is the default. The original
  `geo_units` geometry is never served as a whole layer.
- `parent` (optional) restricts to a parent's direct children (a district's municipalities,
  a municipality's parishes) for drill-down selection.
- Responses are cached and ETag'd by the active `generation_id` (immutable per generation),
  the same invalidation key the precision pipeline produces.

**Two-tier selection.** The grid ships *simplified* geometry (cheap, just for clicking and
labelling). Once a unit is selected, its *precise* (full-detail original) boundary is fetched
on demand via the per-unit geometry endpoint (`GET /api/v1/geo/{code}/geometry`). This keeps
grid payloads small while still allowing exact rendering of the chosen unit.

Vector tiles (MVT) were removed: at Portugal's feature counts (≤ ~3k parishes nationally,
far fewer per parent) a single cached, simplified layer payload covers the selection,
choropleth and export use cases without the per-tile complexity. The
`geo_unit_precisions` / LOD / coverage pipeline is retained as the grid's data source.
