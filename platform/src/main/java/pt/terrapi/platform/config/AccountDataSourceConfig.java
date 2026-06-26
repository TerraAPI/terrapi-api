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
 * Secondary datasource: the account/SaaS database (plain Postgres) holding identity,
 * organization, API-key and (later) usage/billing data ({@code pt.terrapi.account.entities}).
 *
 * <p>Kept separate from the PostGIS geo database so SaaS data and spatial data scale and
 * back up independently. Account repositories use {@code accountEntityManagerFactory} and
 * {@code accountTransactionManager}; account services must annotate
 * {@code @Transactional("accountTransactionManager")}.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "pt.terrapi.platform.repository",
        entityManagerFactoryRef = "accountEntityManagerFactory",
        transactionManagerRef = "accountTransactionManager")
public class AccountDataSourceConfig {

    @Bean
    @ConfigurationProperties("terrapi.datasource.account")
    public DataSourceProperties accountDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("terrapi.datasource.account.hikari")
    public DataSource accountDataSource(
            @Qualifier("accountDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean accountEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("accountDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("pt.terrapi.account.entities")
                .persistenceUnit("account")
                .build();
    }

    @Bean
    public PlatformTransactionManager accountTransactionManager(
            @Qualifier("accountEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
