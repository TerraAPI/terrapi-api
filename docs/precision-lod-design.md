# Precision (LOD) Design

## Why no LOD 0

LOD 0 with tolerance 0 produces `ST_SimplifyPreserveTopology(geom, 0)` which returns the original geometry unchanged — an exact duplicate of `geo_units.geometry`. Storing it in `geo_unit_precisions` wastes storage, compute, and I/O with no benefit.

LOD levels start at **1** and represent progressively simplified geometries at increasing tolerances.

## Retrieving LOD 0

When a client requests LOD 0 (original geometry), the retrieval service must use a **combined approach**:

```
if lod == 0:
    return geo_units.geometry    (direct from the source table)
else:
    return geo_unit_precisions  (filtered by code, type, lod, status = 'ACTIVE')
```

This means:
- LOD 0 queries hit `geo_units` only
- LOD 1+ queries hit `geo_unit_precisions` only
- No need for UNION or fallback logic — LOD 0 never exists in the precisions table

### Service method sketch

```java
public Geometry getGeometry(String code, int lod) {
    if (lod == 0) {
        return geoUnitRepository.findByCode(code)
                .map(GeoUnit::getPolygon)
                .orElse(null);
    }
    return precisionRepository.findActiveByCodeAndLod(code, lod)
            .map(GeoUnitPrecision::getGeometry)
            .orElse(null);
}
```

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
- `lod >= 1` → selection-grade simplified geometry from `geo_unit_precisions` (transformed
  3857 → 4326); `lod = 0` → full detail from `geo_units`.
- `parent` (optional) restricts to a parent's direct children (a district's municipalities,
  a municipality's parishes) for drill-down selection.
- Responses are cached and ETag'd by the active `generation_id` (immutable per generation),
  the same invalidation key the precision pipeline produces.

**Two-tier selection.** The grid ships *simplified* geometry (cheap, just for clicking and
labelling). Once a unit is selected, its *precise* boundary is fetched on demand via the
per-unit geometry endpoint (`GET /api/v1/geo/{code}/geometry`, LOD 0). This keeps grid
payloads small while still allowing exact rendering of the chosen unit.

Vector tiles (MVT) were removed: at Portugal's feature counts (≤ ~3k parishes nationally,
far fewer per parent) a single cached, simplified layer payload covers the selection,
choropleth and export use cases without the per-tile complexity. The
`geo_unit_precisions` / LOD / coverage pipeline is retained as the grid's data source.
