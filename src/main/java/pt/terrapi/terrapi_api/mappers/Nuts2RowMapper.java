package pt.terrapi.terrapi_api.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import pt.terrapi.terrapi_api.entities.Nuts1;
import pt.terrapi.terrapi_api.entities.Nuts2;
import pt.terrapi.terrapi_api.utils.CaopGeometryUtil;

public final class Nuts2RowMapper {

    private Nuts2RowMapper() {}

    public static String[] columns() {
        return new String[]{"codigo", "nuts2", "nuts1"};
    }

    public static Nuts2 mapRow(ResultSet rs, Map<String, Nuts1> nuts1ByName) throws SQLException {
        Nuts2 n = new Nuts2();
        n.setCode(rs.getString("codigo"));
        n.setName(rs.getString("nuts2"));
        n.setParent(nuts1ByName.get(rs.getString("nuts1")));
        CaopGeometryUtil.setGeoFields(n, rs);
        return n;
    }
}
