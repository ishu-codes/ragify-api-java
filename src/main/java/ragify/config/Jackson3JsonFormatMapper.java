package ragify.config;

import java.lang.reflect.Type;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.databind.ObjectMapper;

/**
 * Hibernate's default JSON format mapper uses Jackson 2, but this project runs
 * on Spring Boot 4 / Jackson 3 (tools.jackson). This mapper keeps JSONB
 * (de)serialization consistent with the Jackson 3 JsonNode used by the models.
 */
public class Jackson3JsonFormatMapper extends AbstractJsonFormatMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected <T> T fromString(CharSequence charSequence, Type type) {
        try {
            return MAPPER.readValue(charSequence.toString(), MAPPER.getTypeFactory().constructType(type));
        } catch (Exception ex) {
            throw new RuntimeException("Could not deserialize JSON: " + ex.getMessage(), ex);
        }
    }

    @Override
    protected <T> String toString(T value, Type type) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException("Could not serialize JSON: " + ex.getMessage(), ex);
        }
    }
}
