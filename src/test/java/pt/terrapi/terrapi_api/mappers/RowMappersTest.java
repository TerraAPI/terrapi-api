package pt.terrapi.terrapi_api.mappers;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.io.WKBWriter;
import pt.terrapi.terrapi_api.entities.BorderSegment;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RowMappersTest {

    @Test
    void readGeometry_emptyGpkgGeometry_returnsNull() {
        byte[] emptyBlob = {0x47, 0x50, 0x00, 0x11};

        Geometry result = RowMappers.readGeometry(emptyBlob);

        assertThat(result).isNull();
    }

    @Test
    void readGeometry_nullBlob_returnsNull() {
        assertThat(RowMappers.readGeometry(null)).isNull();
    }

    @Test
    void readGeometry_nonEmptyGeometry_isParsed() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        Geometry point = gf.createPoint(new Coordinate(1, 2));
        byte[] wkb = new WKBWriter().write(point);

        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        blob.write(new byte[]{0x47, 0x50, 0x00, 0x01});
        blob.write(new byte[]{(byte) 0xE6, 0x10, 0x00, 0x00});
        blob.write(wkb);

        Geometry result = RowMappers.readGeometry(blob.toByteArray());

        assertThat(result).isNotNull();
        assertThat(result.getCoordinate().x).isEqualTo(1);
        assertThat(result.getCoordinate().y).isEqualTo(2);
    }

    @Test
    void mapRowDistrict_parsesCountsNullSafe() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("dt")).thenReturn("11");
        when(rs.getString("distrito")).thenReturn("Porto");
        when(rs.getInt("n_municipios")).thenReturn(17);
        when(rs.getInt("n_freguesias")).thenReturn(0);
        when(rs.wasNull()).thenReturn(false, true);
        when(rs.getBytes("geom")).thenReturn(null);
        when(rs.getDouble("area_ha")).thenReturn(100.0);
        when(rs.getDouble("perimetro_km")).thenReturn(50.0);

        GeoUnit u = RowMappers.GeoUnitMapper.mapRowDistrict(rs, "cont_");

        assertThat(u.getType()).isEqualTo(GeoUnitType.DISTRICT);
        assertThat(u.getMunicipalityCount()).isEqualTo(17);
        assertThat(u.getParishCount()).isNull();
    }

    @Test
    void mapBorderSegment_parsesLevelAndLineType() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getBytes("geom")).thenReturn(null);
        when(rs.getString("ea_direita")).thenReturn("021111");
        when(rs.getString("ea_esquerda")).thenReturn("99");
        when(rs.getString("nivel_limite_admin")).thenReturn("3ª Ordem");
        when(rs.getString("significado_linha")).thenReturn("Limite e Linha de Costa");
        when(rs.getDouble("comprimento_km")).thenReturn(5.0);
        when(rs.wasNull()).thenReturn(false);

        BorderSegment b = RowMappers.mapBorderSegment(rs);

        assertThat(b.getLevel()).isEqualTo(3);
        assertThat(b.getLineType()).isEqualTo("COAST");
        assertThat(b.getEaRight()).isEqualTo("021111");
        assertThat(b.getEaLeft()).isEqualTo("99");
        assertThat(b.getLengthKm()).isEqualTo(5.0);
    }
}
