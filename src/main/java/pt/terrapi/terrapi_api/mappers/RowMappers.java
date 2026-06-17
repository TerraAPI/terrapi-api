package pt.terrapi.terrapi_api.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import pt.terrapi.terrapi_api.entities.AdminUnit;
import pt.terrapi.terrapi_api.entities.BaseGeoEntity;
import pt.terrapi.terrapi_api.entities.StatUnit;
import pt.terrapi.terrapi_api.enums.AdminUnitType;
import pt.terrapi.terrapi_api.enums.StatUnitLevel;

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
            case 1 -> 32;
            case 2, 3 -> 48;
            case 4 -> 64;
            default -> 0;
        };

        byte[] wkb = new byte[gpkgBlob.length - offset];
        System.arraycopy(gpkgBlob, offset, wkb, 0, wkb.length);
        return wkb;
    }

    public static final class StatUnitMapper {
        private StatUnitMapper() {}

        public static String[] nuts1Columns() {
            return new String[]{"codigo", "nuts1"};
        }

        public static String[] nuts2Columns() {
            return new String[]{"codigo", "nuts2", "nuts1"};
        }

        public static String[] nuts3Columns() {
            return new String[]{"codigo", "nuts3", "nuts2"};
        }

        public static StatUnit mapRowNuts1(ResultSet rs) throws SQLException {
            StatUnit s = new StatUnit();
            s.setLevel(StatUnitLevel.NUTS1);
            s.setCode(rs.getString("codigo"));
            s.setName(rs.getString("nuts1"));
            setGeo(s, rs);
            return s;
        }

        public static StatUnit mapRowNuts2(ResultSet rs,
                Map<String, StatUnit> nuts1ByName) throws SQLException {
            StatUnit s = new StatUnit();
            s.setLevel(StatUnitLevel.NUTS2);
            s.setCode(rs.getString("codigo"));
            s.setName(rs.getString("nuts2"));
            s.setParent(nuts1ByName.get(rs.getString("nuts1")));
            setGeo(s, rs);
            return s;
        }

        public static StatUnit mapRowNuts3(ResultSet rs,
                Map<String, StatUnit> nuts2ByName) throws SQLException {
            StatUnit s = new StatUnit();
            s.setLevel(StatUnitLevel.NUTS3);
            s.setCode(rs.getString("codigo"));
            s.setName(rs.getString("nuts3"));
            s.setParent(nuts2ByName.get(rs.getString("nuts2")));
            setGeo(s, rs);
            return s;
        }
    }

    public static final class AdminUnitMapper {
        private AdminUnitMapper() {}

        public static String[] districtColumns() {
            return new String[]{"dt", "distrito"};
        }

        public static String[] municipalityColumns() {
            return new String[]{"dtmn", "municipio", "distrito_ilha"};
        }

        public static String[] parishColumns() {
            return new String[]{"dtmnfr", "freguesia", "municipio", "designacao_simplificada"};
        }

        public static AdminUnit mapRowDistrict(ResultSet rs) throws SQLException {
            AdminUnit a = new AdminUnit();
            a.setType(AdminUnitType.DISTRICT);
            a.setCode(rs.getString("dt"));
            a.setName(rs.getString("distrito"));
            setGeo(a, rs);
            return a;
        }

        public static AdminUnit mapRowMunicipality(ResultSet rs,
                Map<String, AdminUnit> districtByName) throws SQLException {
            AdminUnit a = new AdminUnit();
            a.setType(AdminUnitType.MUNICIPALITY);
            a.setCode(rs.getString("dtmn"));
            a.setName(rs.getString("municipio"));
            a.setParent(districtByName.get(rs.getString("distrito_ilha")));
            setGeo(a, rs);
            return a;
        }

        public static AdminUnit mapRowParish(ResultSet rs,
                Map<String, AdminUnit> municipalityByName) throws SQLException {
            AdminUnit a = new AdminUnit();
            a.setType(AdminUnitType.PARISH);
            a.setCode(rs.getString("dtmnfr"));
            a.setName(rs.getString("freguesia"));
            a.setParent(municipalityByName.get(rs.getString("municipio")));
            a.setSimplifiedName(rs.getString("designacao_simplificada"));
            setGeo(a, rs);
            return a;
        }
    }
}
