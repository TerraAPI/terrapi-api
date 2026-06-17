package pt.terrapi.terrapi_api.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import pt.terrapi.terrapi_api.entities.Nuts1;
import pt.terrapi.terrapi_api.utils.CaopGeometryUtil;

public final class Nuts1RowMapper {

    private Nuts1RowMapper() {}

    public static String[] columns() {
        return new String[]{"codigo", "nuts1"};
    }

    public static Nuts1 mapRow(ResultSet rs) throws SQLException {
        Nuts1 n = new Nuts1();
        n.setCode(rs.getString("codigo"));
        n.setName(rs.getString("nuts1"));
        CaopGeometryUtil.setGeoFields(n, rs);
        return n;
    }
}
