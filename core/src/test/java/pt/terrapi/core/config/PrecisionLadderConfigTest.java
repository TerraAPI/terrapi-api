package pt.terrapi.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import pt.terrapi.core.config.LodLevel;
import pt.terrapi.core.config.PrecisionProperties;
import pt.terrapi.core.service.precision.PrecisionPolicyService;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrecisionLadderConfigTest {

    private static PrecisionPolicyService loadPolicy() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        MutablePropertySources sources = new MutablePropertySources();
        for (PropertySource<?> ps : loader.load("application",
                new ClassPathResource("application.yaml"))) {
            sources.addLast(ps);
        }
        Binder binder = new Binder(ConfigurationPropertySources.from(sources));
        PrecisionProperties props = binder.bind("terrapi.precision", PrecisionProperties.class).get();
        return new PrecisionPolicyService(props);
    }

    @Test
    void singleSharedLadderBindsForAllTypes() throws IOException {
        PrecisionPolicyService policy = loadPolicy();
        List<LodLevel> ladder = policy.getLodLadder();

        assertThat(ladder).extracting(LodLevel::lod).containsExactly(0, 1, 2);
        assertThat(ladder).extracting(LodLevel::tolerance).containsExactly(25.0, 50.0, 200.0);
    }
}