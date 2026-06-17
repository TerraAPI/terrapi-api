package pt.terrapi.terrapi_api.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import pt.terrapi.terrapi_api.entities.Municipality;
import pt.terrapi.terrapi_api.entities.Parish;
import pt.terrapi.terrapi_api.utils.CaopGeometryUtil;

public final class ParishRowMapper {

    private ParishRowMapper() {}

    public static String[] columns() {
        return new String[]{"dtmnfr", "freguesia", "municipio", "designacao_simplificada", "nuts3_cod"};
    }

    public static Parish mapRow(ResultSet rs, Map<String, Municipality> municipalityByName) throws SQLException {
        Parish p = new Parish();
        p.setCode(rs.getString("dtmnfr"));
        p.setName(rs.getString("freguesia"));
        p.setMunicipality(municipalityByName.get(rs.getString("municipio")));
        p.setSimplifiedName(rs.getString("designacao_simplificada"));
        p.setNuts3Code(rs.getString("nuts3_cod"));
        CaopGeometryUtil.setGeoFields(p, rs);
        return p;
    }
}
