-- Runs once on first init of the platform-db server (empty data dir), via
-- /docker-entrypoint-initdb.d. Creates the Keycloak database alongside
-- terrapi_platform on the same Postgres server. Separate databases, not a shared schema.
CREATE DATABASE keycloak OWNER terrapi;
