package ragify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RagifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagifyApplication.class, args);
    }
}
