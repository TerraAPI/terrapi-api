-- osm2pgsql flex style: extract the routable highway network into `routing_edges`.
-- Highways only; raw tags are NOT stored, only the typed columns needed for
-- car/foot/bike isochrone routing. Geometry is reprojected to EPSG:4326.

local tables = {}

tables.routing_edges = osm2pgsql.define_table({
    name = 'routing_edges',
    ids = { type = 'way', id_column = 'osm_id' },
    columns = {
        { column = 'highway',       type = 'text', not_null = true },
        { column = 'oneway',        type = 'text' },
        { column = 'junction',      type = 'text' },
        { column = 'maxspeed_kmh',  type = 'int2' },
        { column = 'access',        type = 'text' },
        { column = 'foot',          type = 'text' },
        { column = 'bicycle',       type = 'text' },
        { column = 'motor_vehicle', type = 'text' },
        { column = 'motorcar',      type = 'text' },
        { column = 'service',       type = 'text' },
        { column = 'surface',       type = 'text' },
        { column = 'layer',         type = 'int2' },
        { column = 'bridge',        type = 'boolean' },
        { column = 'tunnel',        type = 'boolean' },
        { column = 'geom',          type = 'linestring', projection = 4326, not_null = true },
    }
})

local routable = {
    motorway = true, motorway_link = true,
    trunk = true, trunk_link = true,
    primary = true, primary_link = true,
    secondary = true, secondary_link = true,
    tertiary = true, tertiary_link = true,
    unclassified = true, residential = true, living_street = true,
    service = true, road = true, track = true, busway = true,
    pedestrian = true, footway = true, path = true, steps = true,
    cycleway = true, bridleway = true,
}

local function parse_speed_kmh(v)
    if not v then return nil end
    local n = tonumber(v)
    if n then return math.floor(n + 0.5) end
    local num, unit = v:match('^(%d+%.?%d*)%s*(%a+)')
    if not num then return nil end
    num = tonumber(num)
    if unit == 'mph' then return math.floor(num * 1.609344 + 0.5) end
    if unit == 'knots' then return math.floor(num * 1.852 + 0.5) end
    return math.floor(num + 0.5)
end

local function parse_int(v)
    if not v then return nil end
    local n = tonumber(v)
    if not n then return nil end
    return math.floor(n)
end

local function parse_bool(v)
    if not v then return nil end
    if v == 'no' or v == 'false' or v == '0' then return false end
    return true
end

function osm2pgsql.process_way(object)
    local tags = object.tags
    local hw = tags.highway
    if not hw or not routable[hw] then return end
    if tags.area == 'yes' then return end

    local geom = object:as_linestring()
    if geom:is_null() then return end

    tables.routing_edges:insert({
        highway       = hw,
        oneway        = tags.oneway,
        junction      = tags.junction,
        maxspeed_kmh  = parse_speed_kmh(tags.maxspeed),
        access        = tags.access,
        foot          = tags.foot,
        bicycle       = tags.bicycle,
        motor_vehicle = tags.motor_vehicle,
        motorcar      = tags.motorcar,
        service       = tags.service,
        surface       = tags.surface,
        layer         = parse_int(tags.layer),
        bridge        = parse_bool(tags.bridge),
        tunnel        = parse_bool(tags.tunnel),
        geom          = geom,
    })
end
