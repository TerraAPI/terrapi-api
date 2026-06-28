package pt.terrapi.core.config;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Primary datasource: the PostGIS geo database holding all spatial domain data
 * ({@code pt.terrapi.core.entities} + {@code pt.terrapi.pipeline.entities}).
 *
 * <p>Marked {@code @Primary} so Spring Boot's {@link EntityManagerFactoryBuilder},
 * Flyway, and any unqualified {@link DataSource} injection resolve to the geo database.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = {"pt.terrapi.core.repository", "pt.terrapi.pipeline.repository"},
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager")
public class GeoDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties geoDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource geoDataSource(@Qualifier("geoDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public Flyway geoFlyway(@Qualifier("geoDataSource") DataSource dataSource,
                            @Value("${terrapi.flyway.enabled:true}") boolean flywayEnabled) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/geo")
                .baselineOnMigrate(true)
                .load();
        if (flywayEnabled) {
            flyway.migrate();
        }
        return flyway;
    }

    @Primary
    @Bean
    @DependsOn("geoFlyway")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("geoDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("pt.terrapi.core.entities", "pt.terrapi.pipeline.entities")
                .persistenceUnit("geo")
                .build();
    }

    @Primary
    @Bean
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
