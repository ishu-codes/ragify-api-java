package ragify.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppPropertiesBindingTest {

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withPropertyValues(
                        "app.jwt-secret=super-secret-code",
                        "app.jwt-expires-minutes=60",
                        "app.cors-origins=http://localhost:3000",
                        "app.storage-path=storage/workspaces",
                        "app.grpc-endpoint=localhost:50051"
                )
                .withUserConfiguration(TestConfig.class);
    }

    @Test
    void bindsFromApplicationProperties() {
        runner().run(context -> {
            AppProperties properties = context.getBean(AppProperties.class);
            assertThat(properties.jwtSecret()).isEqualTo("super-secret-code");
            assertThat(properties.jwtExpiresMinutes()).isEqualTo(60);
            assertThat(properties.corsOrigins()).isEqualTo("http://localhost:3000");
            assertThat(properties.storagePath()).isEqualTo("storage/workspaces");
            assertThat(properties.grpcEndpoint()).isEqualTo("localhost:50051");
        });
    }

    @Test
    void binderBindsRecordDirectly() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "app.jwt-secret", "super-secret-code",
                "app.jwt-expires-minutes", "60",
                "app.cors-origins", "http://localhost:3000",
                "app.storage-path", "storage/workspaces",
                "app.grpc-endpoint", "localhost:50051"
        ));
        Binder binder = new Binder(source);

        AppProperties properties = binder.bind("app", Bindable.of(AppProperties.class)).get();

        assertThat(properties.jwtSecret()).isEqualTo("super-secret-code");
        assertThat(properties.corsOrigins()).isEqualTo("http://localhost:3000");
        assertThat(properties.grpcEndpoint()).isEqualTo("localhost:50051");
    }

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfig {
    }
}
