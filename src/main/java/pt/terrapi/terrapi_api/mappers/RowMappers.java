package pt.terrapi.terrapi_api.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import pt.terrapi.terrapi_api.entities.BaseGeoEntity;

public final class RowMappers {

    private static final WKBReader WKB_READER = new WKBReader();

    private RowMappers() {}

    private static void setGeo(BaseGeoEntity e, ResultSet rs) throws SQLException {
        e.setPolygon(readGeometry(rs.getBytes("geom")));
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
            case 0 -> 0;
            case 1 -> 32;
            case 2, 3 -> 48;
            case 4 -> 64;
            default -> 0;
        };

        byte[] wkb = new byte[gpkgBlob.length - offset];
        System.arraycopy(gpkgBlob, offset, wkb, 0, wkb.length);
        return wkb;
    }

    public static final class Nuts1 {
        private Nuts1() {}

        public static String[] columns() {
            return new String[]{"codigo", "nuts1"};
        }

        public static pt.terrapi.terrapi_api.entities.Nuts1 mapRow(ResultSet rs) throws SQLException {
            pt.terrapi.terrapi_api.entities.Nuts1 n = new pt.terrapi.terrapi_api.entities.Nuts1();
            n.setCode(rs.getString("codigo"));
            n.setName(rs.getString("nuts1"));
            setGeo(n, rs);
            return n;
        }
    }

    public static final class Nuts2 {
        private Nuts2() {}

        public static String[] columns() {
            return new String[]{"codigo", "nuts2", "nuts1"};
        }

        public static pt.terrapi.terrapi_api.entities.Nuts2 mapRow(ResultSet rs,
                                                                   Map<String, pt.terrapi.terrapi_api.entities.Nuts1> nuts1ByName) throws SQLException {
            pt.terrapi.terrapi_api.entities.Nuts2 n = new pt.terrapi.terrapi_api.entities.Nuts2();
            n.setCode(rs.getString("codigo"));
            n.setName(rs.getString("nuts2"));
            n.setParent(nuts1ByName.get(rs.getString("nuts1")));
            setGeo(n, rs);
            return n;
        }
    }

    public static final class Nuts3 {
        private Nuts3() {}

        public static String[] columns() {
            return new String[]{"codigo", "nuts3", "nuts2"};
        }

        public static pt.terrapi.terrapi_api.entities.Nuts3 mapRow(ResultSet rs,
                                                                   Map<String, pt.terrapi.terrapi_api.entities.Nuts2> nuts2ByName) throws SQLException {
            pt.terrapi.terrapi_api.entities.Nuts3 n = new pt.terrapi.terrapi_api.entities.Nuts3();
            n.setCode(rs.getString("codigo"));
            n.setName(rs.getString("nuts3"));
            n.setParent(nuts2ByName.get(rs.getString("nuts2")));
            setGeo(n, rs);
            return n;
        }
    }

    public static final class District {
        private District() {}

        public static String[] columns() {
            return new String[]{"dt", "distrito", "nuts1_cod"};
        }

        public static pt.terrapi.terrapi_api.entities.District mapRow(ResultSet rs) throws SQLException {
            pt.terrapi.terrapi_api.entities.District d = new pt.terrapi.terrapi_api.entities.District();
            d.setCode(rs.getString("dt"));
            d.setName(rs.getString("distrito"));
            d.setNuts1Code(rs.getString("nuts1_cod"));
            setGeo(d, rs);
            return d;
        }
    }

    public static final class Municipality {
        private Municipality() {}

        public static String[] columns() {
            return new String[]{"dtmn", "municipio", "distrito_ilha", "nuts3_cod"};
        }

        public static pt.terrapi.terrapi_api.entities.Municipality mapRow(ResultSet rs,
                                                                          Map<String, pt.terrapi.terrapi_api.entities.District> districtByName) throws SQLException {
            pt.terrapi.terrapi_api.entities.Municipality m = new pt.terrapi.terrapi_api.entities.Municipality();
            m.setCode(rs.getString("dtmn"));
            m.setName(rs.getString("municipio"));
            m.setDistrict(districtByName.get(rs.getString("distrito_ilha")));
            m.setNuts3Code(rs.getString("nuts3_cod"));
            setGeo(m, rs);
            return m;
        }
    }

    public static final class Parish {
        private Parish() {}

        public static String[] columns() {
            return new String[]{"dtmnfr", "freguesia", "municipio", "designacao_simplificada", "nuts3_cod"};
        }

        public static pt.terrapi.terrapi_api.entities.Parish mapRow(ResultSet rs,
                                                                    Map<String, pt.terrapi.terrapi_api.entities.Municipality> municipalityByName) throws SQLException {
            pt.terrapi.terrapi_api.entities.Parish p = new pt.terrapi.terrapi_api.entities.Parish();
            p.setCode(rs.getString("dtmnfr"));
            p.setName(rs.getString("freguesia"));
            p.setMunicipality(municipalityByName.get(rs.getString("municipio")));
            p.setSimplifiedName(rs.getString("designacao_simplificada"));
            p.setNuts3Code(rs.getString("nuts3_cod"));
            setGeo(p, rs);
            return p;
        }
    }
}
