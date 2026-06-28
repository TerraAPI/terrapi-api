package pt.terrapi.core.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import pt.terrapi.core.entities.BorderSegment;
import pt.terrapi.core.entities.GeoUnit;
import pt.terrapi.core.enums.GeoUnitType;

public final class RowMappers {

    private static final WKBReader WKB_READER = new WKBReader();

    /**
     * Prefix applied to the statistical (NUTS) code namespace. CAOP's raw NUTS {@code codigo}
     * values (e.g. {@code 11}, {@code 15}) collide with administrative district {@code dt}
     * codes (Lisboa {@code 11}, Setúbal {@code 15}) in the single global {@code geo_units.code}
     * primary key. Prefixing NUTS codes with {@code PT} (the Eurostat NUTS root for Portugal)
     * makes them globally unique without overlapping any all-numeric administrative code.
     * The same prefix is applied to {@code nuts3_code} on municipalities/parishes so the
     * {@code nuts3_code = code} join to NUTS3 units is preserved.
     */
    public static final String STAT_CODE_PREFIX = "PT";

    /** Namespaces a raw NUTS code (null-safe). */
    public static String nutsCode(String rawCodigo) {
        return rawCodigo == null ? null : STAT_CODE_PREFIX + rawCodigo;
    }

    private RowMappers() {}

    public static BorderSegment mapBorderSegment(ResultSet rs) throws SQLException {
        BorderSegment b = new BorderSegment();
        b.setGeometry(readGeometry(rs.getBytes("geom")));
        b.setEaRight(rs.getString("ea_direita"));
        b.setEaLeft(rs.getString("ea_esquerda"));
        b.setLevel(parseLevel(rs.getString("nivel_limite_admin")));
        b.setLineType(parseLineType(rs.getString("significado_linha")));
        double len = rs.getDouble("comprimento_km");
        b.setLengthKm(rs.wasNull() ? null : len);
        return b;
    }

    private static Integer parseLevel(String nivel) {
        if (nivel == null) return null;
        for (int i = 0; i < nivel.length(); i++) {
            if (Character.isDigit(nivel.charAt(i))) {
                return Character.getNumericValue(nivel.charAt(i));
            }
        }
        return null;
    }

    private static String parseLineType(String significado) {
        if (significado == null) return "LAND";
        if (significado.contains("Costa")) return "COAST";
        if (significado.contains("gua")) return "WATER";
        return "LAND";
    }

    private static void setGeo(GeoUnit e, ResultSet rs) throws SQLException {
        e.setGeometry(readGeometry(rs.getBytes("geom")));
        e.setAreaHa(rs.getDouble("area_ha"));
        e.setPerimeterKm(rs.getDouble("perimetro_km"));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static Geometry readGeometry(byte[] gpkgBlob) {
        if (gpkgBlob == null) return null;
        byte[] wkb = stripGpkgHeader(gpkgBlob);
        if (wkb == null) return null;
        try {
            return WKB_READER.read(wkb);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse geometry", e);
        }
    }

    private static byte[] stripGpkgHeader(byte[] gpkgBlob) {
        int flags = gpkgBlob[3] & 0xFF;

        if ((flags & 0x10) != 0) {
            return null;
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
            return new String[]{"dt", "distrito", "nuts1_cod", "n_municipios", "n_freguesias"};
        }

        public static String[] municipalityColumns() {
            return new String[]{"dtmn", "municipio", "distrito_ilha", "nuts3_cod", "n_freguesias"};
        }

        public static String[] parishColumns() {
            return new String[]{"dtmnfr", "freguesia", "municipio", "designacao_simplificada", "nuts3_cod"};
        }

        public static String[] nuts1Columns() {
            return new String[]{"codigo", "nuts1", "n_municipios", "n_freguesias"};
        }

        public static String[] nuts2Columns() {
            return new String[]{"codigo", "nuts2", "nuts1", "n_municipios", "n_freguesias"};
        }

        public static String[] nuts3Columns() {
            return new String[]{"codigo", "nuts3", "nuts2", "n_municipios", "n_freguesias"};
        }

        public static GeoUnit mapRowDistrict(ResultSet rs, String prefix) throws SQLException {
            GeoUnit u = new GeoUnit();
            u.setType(prefix.startsWith("ram") || prefix.startsWith("raa")
                    ? GeoUnitType.ISLAND : GeoUnitType.DISTRICT);
            u.setCode(rs.getString("dt"));
            u.setName(rs.getString("distrito"));
            u.setNuts1Code(nutsCode(rs.getString("nuts1_cod")));
            u.setMunicipalityCount(nullableInt(rs, "n_municipios"));
            u.setParishCount(nullableInt(rs, "n_freguesias"));
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
            u.setNuts3Code(nutsCode(rs.getString("nuts3_cod")));
            u.setParishCount(nullableInt(rs, "n_freguesias"));
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
            u.setNuts3Code(nutsCode(rs.getString("nuts3_cod")));
            setGeo(u, rs);
            return u;
        }

        public static GeoUnit mapRowNuts1(ResultSet rs) throws SQLException {
            GeoUnit u = new GeoUnit();
            u.setType(GeoUnitType.NUTS1);
            u.setCode(nutsCode(rs.getString("codigo")));
            u.setName(rs.getString("nuts1"));
            u.setMunicipalityCount(nullableInt(rs, "n_municipios"));
            u.setParishCount(nullableInt(rs, "n_freguesias"));
            setGeo(u, rs);
            return u;
        }

        public static GeoUnit mapRowNuts2(ResultSet rs,
                Map<String, GeoUnit> nuts1ByName) throws SQLException {
            GeoUnit u = new GeoUnit();
            u.setType(GeoUnitType.NUTS2);
            u.setCode(nutsCode(rs.getString("codigo")));
            u.setName(rs.getString("nuts2"));
            u.setParent(nuts1ByName.get(rs.getString("nuts1")));
            u.setMunicipalityCount(nullableInt(rs, "n_municipios"));
            u.setParishCount(nullableInt(rs, "n_freguesias"));
            setGeo(u, rs);
            return u;
        }

        public static GeoUnit mapRowNuts3(ResultSet rs,
                Map<String, GeoUnit> nuts2ByName) throws SQLException {
            GeoUnit u = new GeoUnit();
            u.setType(GeoUnitType.NUTS3);
            u.setCode(nutsCode(rs.getString("codigo")));
            u.setName(rs.getString("nuts3"));
            u.setParent(nuts2ByName.get(rs.getString("nuts2")));
            u.setMunicipalityCount(nullableInt(rs, "n_municipios"));
            u.setParishCount(nullableInt(rs, "n_freguesias"));
            setGeo(u, rs);
            return u;
        }
    }
}
