package pt.terrapi.terrapi_api.utils;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import pt.terrapi.terrapi_api.entities.BaseGeoEntity;

public final class CaopGeometryUtil {

    private static final WKBReader WKB_READER = new WKBReader();

    private CaopGeometryUtil() {}

    static String[] geoColumns() {
        return new String[]{"geom", "area_ha", "perimetro_km"};
    }

    static void setGeoFields(BaseGeoEntity entity, ResultSet rs) throws SQLException {
        entity.setPolygon(readGeometry(rs.getBytes("geom")));
        entity.setAreaHa(rs.getDouble("area_ha"));
        entity.setPerimeterKm(rs.getDouble("perimetro_km"));
    }

    private static Geometry readGeometry(byte[] gpkgBlob) {
        if (gpkgBlob == null) return null;
        byte[] wkb = stripGpkgHeader(gpkgBlob);
        try {
            return WKB_READER.read(wkb);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse geometry", e);
        }
    }

    private static byte[] stripGpkgHeader(byte[] gpkgBlob) {
        int flags = gpkgBlob[3] & 0xFF;
        if ((flags & 0x10) != 0) {
            return new byte[]{0x00, 0x00, 0x00, 0x00, 0x00};
        }
        byte[] wkb = new byte[gpkgBlob.length - 8];
        System.arraycopy(gpkgBlob, 8, wkb, 0, wkb.length);
        return wkb;
    }
}
