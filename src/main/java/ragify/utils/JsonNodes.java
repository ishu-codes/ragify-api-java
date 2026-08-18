package ragify.utils;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class JsonNodes {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNodes() {}

    public static ArrayNode array() {
        return MAPPER.createArrayNode();
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }
}
