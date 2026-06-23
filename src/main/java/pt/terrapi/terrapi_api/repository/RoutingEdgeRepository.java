package pt.terrapi.terrapi_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.terrapi.terrapi_api.entities.RoutingEdge;

/**
 * Read-only access to the osm2pgsql-owned {@code routing_edges} table. Spatial query methods
 * (nearest edge, bbox, isochrone graph extraction) are added in the topology/isochrone phase.
 */
public interface RoutingEdgeRepository extends JpaRepository<RoutingEdge, Long> {
}
