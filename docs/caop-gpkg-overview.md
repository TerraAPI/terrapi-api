# CAOP 2025 GPKG Files

Column definitions for all tables are in [gpkg-table-columns.md](gpkg-table-columns.md).

---

## File Inventory

| File | Region | Prefix |
|------|--------|--------|
| `Continente_CAOP2025.gpkg` | Mainland Portugal | `cont_` |
| `ArqMadeira_CAOP2025.gpkg` | Madeira Archipelago | `ram_` |
| `ArqAcores_GOcidental_CAOP2025.gpkg` | Azores — Western Group | `raa_oci_` |
| `ArqAcores_GCentral_GOriental_CAOP2025.gpkg` | Azores — Central + Eastern | `raa_cen_ori_` |

All four files share the same schema.

---

## Row Counts

| Table | Continental | Madeira | Azores W | Azores C+E |
|-------|-------------|---------|----------|------------|
| distritos | 18 | 2 | 2 | 7 |
| municipios | 278 | 11 | 3 | 16 |
| freguesias | 3,049 | 54 | 12 | 144 |
| nuts1 | 1 | 1 | 1 | 1 |
| nuts2 | 7 | 1 | 1 | 1 |
| nuts3 | 24 | 1 | 1 | 1 |

NUTS entries are duplicated across files of the same autonomous region. The import service deduplicates by `codigo`.

---

## Code Conventions

| Level | Column | Width | Example | Pattern |
|-------|--------|-------|---------|---------|
| NUTS1 | `codigo` | 1 char | `"1"` | — |
| NUTS2 | `codigo` | 2 chars | `"11"` | NUTS1 code + 1 char |
| NUTS3 | `codigo` | 3 chars | `"111"` | NUTS2 code + 1 char |
| District | `dt` | 2 digits | `"01"` | sequential |
| Municipality | `dtmn` | 4 digits | `"0101"` | `dt` + 2-digit seq |
| Parish | `dtmnfr` | 6 digits | `"010103"` | `dtmn` + 2-digit seq |

---

## Autonomous Regions: Islands as Districts

In Continental Portugal, `{prefix}distritos` holds 18 administrative districts (Aveiro, Beja, etc.).

In Madeira and Azores, the same table holds **islands** — not administrative districts. This is a schema simplification: the `{prefix}distritos` table structure is reused rather than creating a separate entity.

| File | dt | distrito |
|------|----|----------|
| Madeira | 31 | Ilha da Madeira |
| Madeira | 32 | Ilha de Porto Santo |
| Azores W | 48 | Ilha das Flores |
| Azores W | 49 | Ilha do Corvo |
| Azores C+E | 41 | Ilha de Santa Maria |
| Azores C+E | 42 | Ilha de São Miguel |
| Azores C+E | 43 | Ilha Terceira |
| Azores C+E | 44 | Ilha da Graciosa |
| Azores C+E | 45 | Ilha de São Jorge |
| Azores C+E | 46 | Ilha do Pico |
| Azores C+E | 47 | Ilha do Faial |

The `distrito_ilha` column in `{prefix}municipios` is named "district/island" precisely because it references a district name in Continental Portugal and an island name in autonomous regions. Both are resolved via the same name-to-entity lookup at import time.
