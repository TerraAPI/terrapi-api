package pt.terrapi.terrapi_api.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

public final class RowMappers {

    private static final WKBReader WKB_READER = new WKBReader();

    private RowMappers() {}

    private static void setGeo(GeoUnit e, ResultSet rs) throws SQLException {
        e.setGeometry(readGeometry(rs.getBytes("geom")));
        e.setAreaHa(rs.getDouble("area_ha"));
        e.setPerimeterKm(rs.getDouble("perimetro_km"));
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

        int offset = 8;

        if ((flags & 0x20) != 0) {
            offset += 4;
        }

        int env = (flags >> 1) & 0x07;
        offset += switch (env) {
            case 1 -> 32;
            case 2, 3 -> 48;
            case 4 -> 64;
            default -> 0;
        };

        byte[] wkb = new byte[gpkgBlob.length - offset];
        System.arraycopy(gpkgBlob, offset, wkb, 0, wkb.length);
        return wkb;
    }

    public static final class GeoUnitMapper {
        private GeoUnitMapper() {}

        public static String[] districtColumns() {
            return new String[]{"dt", "distrito"};
        }

        public static String[] municipalityColumns() {
            return new String[]{"dtmn", "municipio", "distrito_ilha", "nuts3_cod"};
        }

        public static String[] parishColumns() {
            return new String[]{"dtmnfr", "freguesia", "municipio", "designacao_simplificada", "nuts3_cod"};
        }

        public static String[] nuts1Columns() {
            return new String[]{"codigo", "nuts1"};
        }

        public static String[] nuts2Columns() {
            return new String[]{"codigo", "nuts2", "nuts1"};
        }

        public static String[] nuts3Columns() {
            return new String[]{"codigo", "nuts3", "nuts2"};
        }

        public static GeoUnit mapRowDistrict(ResultSet rs, String prefix) throws SQLException {
            GeoUnit u = new GeoUnit();
            u.setType(prefix.startsWith("ram") || prefix.startsWith("raa")
                    ? GeoUnitType.ISLAND : GeoUnitType.DISTRICT);
            u.setCode(rs.getString("dt"));
            u.setName(rs.getString("distrito"));
            setGeo(u, rs);
            return u;
        }

        public static GeoUnit mapRowMunicipality(ResultSet rs,
                Map<String, GeoUnit> districtByName) throws SQLException {
            GeoUnit u = new GeoUnit();
            u.setType(GeoUnitType.MUNICIPALITY);
            u.setCode(rs.getString("dtmn"));
            u.setName(rs.getString("municipio"));
            u.setParent(districtByName.get(rs.getString("distrito_ilha")));
            u.setNuts3Code(rs.getString("nuts3_cod"));
            setGeo(u, rs);
            return u;
        }

        public static GeoUnit mapRowParish(ResultSet rs,
                Map<String, GeoUnit> municipalityByName) throws SQLException {
            GeoUnit u = new GeoUnit();
            u.setType(GeoUnitType.PARISH);
            u.setCode(rs.getString("dtmnfr"));
            u.setName(rs.getString("freguesia"));
            u.setParent(municipalityByName.get(rs.getString("municipio")));
            u.setSimplifiedName(rs.getString("designacao_simplificada"));
            u.setNuts3Code(rs.getString("nuts3_cod"));
            setGeo(u, rs);
            return u;
        }

        public static GeoUnit mapRowNuts1(ResultSet rs) throws SQLException {
            GeoUnit u = new GeoUnit();
            u.setType(GeoUnitType.NUTS1);
            u.setCode(rs.getString("codigo"));
            u.setName(rs.getString("nuts1"));
            setGeo(u, rs);
            return u;
        }

        public static GeoUnit mapRowNuts2(ResultSet rs,
                Map<String, GeoUnit> nuts1ByName) throws SQLException {
            GeoUnit u = new GeoUnit();
            u.setType(GeoUnitType.NUTS2);
            u.setCode(rs.getString("codigo"));
            u.setName(rs.getString("nuts2"));
            u.setParent(nuts1ByName.get(rs.getString("nuts1")));
            setGeo(u, rs);
            return u;
        }

        public static GeoUnit mapRowNuts3(ResultSet rs,
                Map<String, GeoUnit> nuts2ByName) throws SQLException {
            GeoUnit u = new GeoUnit();
            u.setType(GeoUnitType.NUTS3);
            u.setCode(rs.getString("codigo"));
            u.setName(rs.getString("nuts3"));
            u.setParent(nuts2ByName.get(rs.getString("nuts2")));
            setGeo(u, rs);
            return u;
        }
    }
}
