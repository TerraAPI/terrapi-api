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
