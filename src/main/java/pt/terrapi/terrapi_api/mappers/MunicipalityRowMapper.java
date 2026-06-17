package pt.terrapi.terrapi_api.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import pt.terrapi.terrapi_api.entities.District;
import pt.terrapi.terrapi_api.entities.Municipality;
import pt.terrapi.terrapi_api.utils.CaopGeometryUtil;

public final class MunicipalityRowMapper {

    private MunicipalityRowMapper() {}

    public static String[] columns() {
        return new String[]{"dtmn", "municipio", "distrito_ilha", "nuts3_cod"};
    }

    public static Municipality mapRow(ResultSet rs, Map<String, District> districtByName) throws SQLException {
        Municipality m = new Municipality();
        m.setCode(rs.getString("dtmn"));
        m.setName(rs.getString("municipio"));
        m.setDistrict(districtByName.get(rs.getString("distrito_ilha")));
        m.setNuts3Code(rs.getString("nuts3_cod"));
        CaopGeometryUtil.setGeoFields(m, rs);
        return m;
    }
}
