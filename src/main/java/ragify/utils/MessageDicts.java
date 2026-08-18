package ragify.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ragify.ragify_client.ChatMessage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Plain {"role", "content"} message dicts stored in sessions.messages,
 * mirroring api-python's current (langchain-free) message handling.
 */
public final class MessageDicts {

    private MessageDicts() {}

    public static ObjectNode human(String content) {
        return message("user", content);
    }

    public static ObjectNode ai(String content) {
        return message("assistant", content);
    }

    public static ArrayNode toArray(List<ChatMessage> chat) {
        ArrayNode array = JsonNodes.array();
        for (ChatMessage message : chat) {
            array.add(message(message.role(), message.content()));
        }
        return array;
    }

    public static List<ChatMessage> fromStored(JsonNode messages) {
        List<ChatMessage> chat = new ArrayList<>();
        if (messages == null || !messages.isArray()) {
            return chat;
        }
        for (JsonNode node : messages) {
            chat.add(new ChatMessage(roleOf(node), contentOf(node)));
        }
        return chat;
    }

    public static List<Map<String, String>> sessionMessages(JsonNode messages) {
        List<Map<String, String>> result = new ArrayList<>();
        if (messages == null || !messages.isArray()) {
            return result;
        }
        int index = 0;
        for (JsonNode node : messages) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("id", "msg-" + index);
            entry.put("role", isAssistant(node) ? "assistant" : "user");
            entry.put("content", contentOf(node));
            entry.put("createdAt", createdAt(node));
            result.add(entry);
            index++;
        }
        return result;
    }

    private static ObjectNode message(String role, String content) {
        ObjectNode message = JsonNodes.object();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String roleOf(JsonNode node) {
        String role = node.path("role").asString("");
        if (!role.isEmpty()) {
            return role;
        }
        String type = node.path("type").asString("user");
        if ("ai".equals(type)) {
            return "assistant";
        }
        if ("human".equals(type)) {
            return "user";
        }
        return type;
    }

    private static boolean isAssistant(JsonNode node) {
        String role = node.path("role").asString("");
        if ("assistant".equals(role) || "ai".equals(role)) {
            return true;
        }
        return "ai".equals(node.path("type").asString(""));
    }

    private static String contentOf(JsonNode node) {
        JsonNode content = node.path("content");
        if (content.isString() && !content.asString().isEmpty()) {
            return content.asString();
        }
        return node.path("data").path("content").asString("");
    }

    private static String createdAt(JsonNode node) {
        JsonNode createdAt = node.path("createdAt");
        if (createdAt.isString() && !createdAt.asString().isEmpty()) {
            return createdAt.asString();
        }
        return node.path("data").path("additional_kwargs").path("created_at").asString("");
    }
}
