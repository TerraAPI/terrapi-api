package pt.terrapi.platform.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Secondary datasource: the platform/SaaS control-plane database (plain Postgres) holding
 * identity, organization, API-key and (later) usage/pricing/billing data
 * ({@code pt.terrapi.platform}).
 *
 * <p>Kept separate from the PostGIS geo database so control-plane data and spatial data scale
 * and back up independently. Platform repositories use {@code platformEntityManagerFactory} and
 * {@code platformTransactionManager}; platform services must annotate
 * {@code @Transactional("platformTransactionManager")}. The base-package scan covers every
 * bounded context under {@code pt.terrapi.platform} (identity now; usage/billing later).
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "pt.terrapi.platform",
        entityManagerFactoryRef = "platformEntityManagerFactory",
        transactionManagerRef = "platformTransactionManager")
public class PlatformDataSourceConfig {

    @Bean
    @ConfigurationProperties("terrapi.datasource.platform")
    public DataSourceProperties platformDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("terrapi.datasource.platform.hikari")
    public DataSource platformDataSource(
            @Qualifier("platformDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean platformEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("platformDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("pt.terrapi.platform")
                .persistenceUnit("platform")
                .build();
    }

    @Bean
    public PlatformTransactionManager platformTransactionManager(
            @Qualifier("platformEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
