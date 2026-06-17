package pt.terrapi.terrapi_api.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import pt.terrapi.terrapi_api.entities.Nuts2;
import pt.terrapi.terrapi_api.entities.Nuts3;
import pt.terrapi.terrapi_api.utils.CaopGeometryUtil;

public final class Nuts3RowMapper {

    private Nuts3RowMapper() {}

    public static String[] columns() {
        return new String[]{"codigo", "nuts3", "nuts2"};
    }

    public static Nuts3 mapRow(ResultSet rs, Map<String, Nuts2> nuts2ByName) throws SQLException {
        Nuts3 n = new Nuts3();
        n.setCode(rs.getString("codigo"));
        n.setName(rs.getString("nuts3"));
        n.setParent(nuts2ByName.get(rs.getString("nuts2")));
        CaopGeometryUtil.setGeoFields(n, rs);
        return n;
    }
}
