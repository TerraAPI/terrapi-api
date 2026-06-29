-- All indexes for the geo schema: GiST spatial indexes plus the btree lookup indexes
-- previously declared via JPA @Index / created by SchemaInitializer.

-- geo_units
CREATE INDEX idx_geo_units_geometry    ON geo_units USING GIST (geometry);
CREATE INDEX idx_geo_units_type        ON geo_units (type);
CREATE INDEX idx_geo_units_parent_code ON geo_units (parent_code);
CREATE INDEX idx_geo_units_name        ON geo_units (name);

-- geo_unit_precisions
CREATE INDEX idx_gp_geometry      ON geo_unit_precisions USING GIST (geometry);
CREATE INDEX idx_gp_code_lod      ON geo_unit_precisions (geo_unit_code, lod);
CREATE INDEX idx_gp_generation_id ON geo_unit_precisions (generation_id);
CREATE INDEX idx_gp_type_lod      ON geo_unit_precisions (type, lod) INCLUDE (geo_unit_code);

-- border_segments
CREATE INDEX idx_border_geometry ON border_segments USING GIST (geometry);
CREATE INDEX idx_border_level    ON border_segments (level);

-- border_segment_precisions
CREATE INDEX idx_bsp_geometry      ON border_segment_precisions USING GIST (geometry);
CREATE INDEX idx_bsp_generation_id ON border_segment_precisions (generation_id);
CREATE INDEX idx_bsp_lod_level     ON border_segment_precisions (lod, level) INCLUDE (border_segment_id);

-- geo_unit_adjacency
CREATE INDEX idx_adjacency_code ON geo_unit_adjacency (code);
