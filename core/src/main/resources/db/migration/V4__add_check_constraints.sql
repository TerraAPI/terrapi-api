-- Domain CHECK constraints derived from the enums / documented value ranges.

ALTER TABLE geo_units
    ADD CONSTRAINT chk_geo_units_type CHECK (type IN (1, 2, 3, 7, 8, 11, 12, 13));

ALTER TABLE geo_unit_precisions
    ADD CONSTRAINT chk_gp_type CHECK (type IN (1, 2, 3, 7, 8, 11, 12, 13)),
    ADD CONSTRAINT chk_gp_lod  CHECK (lod >= 0);

ALTER TABLE border_segments
    ADD CONSTRAINT chk_border_level CHECK (level BETWEEN 1 AND 5);

ALTER TABLE border_segment_precisions
    ADD CONSTRAINT chk_bsp_level CHECK (level BETWEEN 1 AND 5),
    ADD CONSTRAINT chk_bsp_lod   CHECK (lod >= 0);

ALTER TABLE precision_generations
    ADD CONSTRAINT chk_precision_generations_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'));
