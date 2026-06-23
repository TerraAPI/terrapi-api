# Precision (LOD) Design

## The LOD ladder (0–2)

Each `GeoUnitType` is stored in `geo_unit_precisions` at three levels of detail:

- **LOD 0** — *most detailed*. A small, **non-zero** tolerance (just above the data's native
  vertex spacing) so it is not a byte-for-byte duplicate of the original, yet visually
  ~full-detail. This is what makes it worth storing: it lets the whole-layer ("grid") endpoint
  serve a near-full-detail layer without ever shipping the multi-hundred-MB original.
- **LOD 1** — simplified.
- **LOD 2** — *coarsest*, the lightest payload (used as the grid's default).

Tolerances are a **single ladder** configured under `terrapi.precision.lod` in `application.yaml`
(one tolerance per LOD, driven by the parish base). They were chosen from the measured CAOP
geometry scale (native vertex spacing ≈ 18–37 m on the mainland, ≈ 3–15 m on the islands, plus the
smallest-feature sizes), so LOD 0 reduces vertex count meaningfully without distorting even the
smallest parishes/islands.

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

Partitioning by `lod` keeps the tolerance constant within a partition (required by
`ST_CoverageSimplify`). This is applied to the **finest** layer (parishes); coarser layers are
not simplified independently but **derived** from it (see *Hierarchical nesting* below).

### Authoritative coverage, no fallback

`ST_CoverageSimplify`/`ST_CoverageUnion` require a valid coverage. The CAOP dataset is an
authoritative, fixed-format single source of truth and is a valid coverage. There is **no**
fallback to independent per-type simplification: if the coverage is ever invalid (e.g. the format
changes), generation fails loudly (`FAILED`) and the transaction rolls back, preserving the
previous precisions — rather than silently producing a lower-quality, non-nested result.

### Configuration (`terrapi.precision`)

- `lod` — the single LOD ladder (one `{lod, tolerance}` per level).
- `simplify-boundary` — passed to `ST_CoverageSimplify` (default `false`, keeps the outer coverage boundary crisp).
- `validation` — `max-null-pct` / `max-invalid-pct` / `max-empty-pct` thresholds; a run is healthy when it produced rows and stays within them.

### Hierarchical nesting (unified precision)

Layers are **nested**: the whole hierarchy is derived from one simplified parish coverage per LOD,
so every level shares the same edge graph (a parish edge coincides exactly with its municipality,
district and NUTS edges). Per LOD level (tolerance `T` from the PARISH ladder):

1. **Parishes** — coverage-simplify the parish coverage at `T`.
2. **Municipalities** — `ST_CoverageUnion` of the simplified parishes, grouped by `parent_code`.
3. **Districts/Islands** — dissolve the simplified municipalities (target type from the parent unit).
4. **NUTS3 ← municipalities** (grouped by `nuts3_code`), **NUTS2 ← NUTS3**, **NUTS1 ← NUTS2**.

Because each level reuses the already-simplified child edges, boundaries are inherited, not
re-simplified — so parishes ⊂ municipalities ⊂ districts, and NUTS borders follow municipality
borders. The tradeoff (accepted): coarse layers inherit parish-edge density and are heavier than an
independent simplification would be; per-type tolerances are therefore retired.

Because the levels are coupled, generation rebuilds the **whole hierarchy**: `generate(type, lod)`
ignores `type` (always all layers); `lod` scopes to one level across all types (delete every row at
that LOD, rebuild the hierarchy for it).

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

**Two-tier selection.** The grid ships *simplified* geometry (cheap, just for clicking and
labelling). Once a unit is selected, its *precise* (full-detail original) boundary is fetched
on demand via the per-unit geometry endpoint (`GET /api/v1/geo/{code}/geometry`). This keeps
grid payloads small while still allowing exact rendering of the chosen unit.

Vector tiles (MVT) were removed: at Portugal's feature counts (≤ ~3k parishes nationally,
far fewer per parent) a single cached, simplified layer payload covers the selection,
choropleth and export use cases without the per-tile complexity. The
`geo_unit_precisions` / LOD / coverage pipeline is retained as the grid's data source.

## Layers vs borders

Two geometry-serving surfaces, fed from one nested topology:

- **`/api/v1/layers/{type}`** — polygon *fills* per `GeoUnitType`, derived by hierarchical
  dissolution (see *Hierarchical nesting*). Used for selection and choropleth.
- **`/api/v1/borders`** — the classified boundary-*line* network (CAOP `trocos`): each shared
  edge carries `level` (1 national … 5 parish), `lineType` (LAND/COAST/WATER) and `lengthKm`,
  and is drawn once. Used for boundary overlays / styling. Always served from the simplified tier
  (`border_segment_precisions`), `?lod={0..2}` (default `2`, coarsest) — like `/layers`, the
  full-detail network is never dumped wholesale.

Because layers are nested, a coarser layer's outline **is** the union of its children's edges, so
fills across levels align exactly and a fill outline coincides with the corresponding border line.

`/borders` is still a distinct surface — not for its geometry (which the layers now share) but for
its **edge-level semantics**: the border `level` (for thick-national / thin-parish styling) and
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
validation counts). Because each arc is simplified independently (not coverage-derived), border
lines *track* but do **not** byte-exactly coincide with the `ST_CoverageSimplify`'d layer fills at
`lod > 0`. The full-detail `border_segments` are never served wholesale — like the original
`geo_units.geometry`, they exist only for derivation (adjacency) and spatial-correctness queries.
