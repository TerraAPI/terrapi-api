package pt.terrapi.terrapi_api.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import pt.terrapi.terrapi_api.entities.District;
import pt.terrapi.terrapi_api.utils.CaopGeometryUtil;

public final class DistrictRowMapper {

    private DistrictRowMapper() {}

    public static String[] columns() {
        return new String[]{"dt", "distrito", "nuts1_cod"};
    }

    public static District mapRow(ResultSet rs) throws SQLException {
        District d = new District();
        d.setCode(rs.getString("dt"));
        d.setName(rs.getString("distrito"));
        d.setNuts1Code(rs.getString("nuts1_cod"));
        CaopGeometryUtil.setGeoFields(d, rs);
        return d;
    }
}
