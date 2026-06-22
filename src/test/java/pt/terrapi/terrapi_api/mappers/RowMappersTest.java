package pt.terrapi.terrapi_api.mappers;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.io.WKBWriter;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

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
}
