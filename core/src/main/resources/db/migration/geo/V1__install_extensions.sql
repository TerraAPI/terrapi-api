-- Spatial types/functions used by the geo schema (geometry columns, ST_* functions,
-- and SRID 3763 / 4326 in spatial_ref_sys). Must run before the schema is created.
CREATE EXTENSION IF NOT EXISTS postgis;
