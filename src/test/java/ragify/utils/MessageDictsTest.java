package ragify.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ragify.ragify_client.ChatMessage;
import tools.jackson.databind.JsonNode;

class MessageDictsTest {

    @Test
    void humanMessageIsPlainRoleContent() {
        JsonNode message = MessageDicts.human("hello");

        assertThat(message.path("role").asText()).isEqualTo("user");
        assertThat(message.path("content").asText()).isEqualTo("hello");
        assertThat(message.has("type")).isFalse();
        assertThat(message.has("data")).isFalse();
    }

    @Test
    void aiMessageIsPlainRoleContent() {
        JsonNode message = MessageDicts.ai("hi");

        assertThat(message.path("role").asText()).isEqualTo("assistant");
        assertThat(message.path("content").asText()).isEqualTo("hi");
    }

    @Test
    void fromStoredReadsHistory() {
        var messages = JsonNodes.array();
        messages.add(MessageDicts.human("first"));
        messages.add(MessageDicts.ai("second"));

        List<ChatMessage> chat = MessageDicts.fromStored(messages);

        assertThat(chat).containsExactly(new ChatMessage("user", "first"), new ChatMessage("assistant", "second"));
    }

    @Test
    void sessionMessagesTransformsRolesAndIndexes() {
        var messages = JsonNodes.array();
        messages.add(MessageDicts.human("q"));
        messages.add(MessageDicts.ai("a"));

        List<java.util.Map<String, String>> rendered = MessageDicts.sessionMessages(messages);

        assertThat(rendered).hasSize(2);
        assertThat(rendered.get(0))
            .containsEntry("id", "msg-0")
            .containsEntry("role", "user")
            .containsEntry("content", "q");
        assertThat(rendered.get(1))
            .containsEntry("id", "msg-1")
            .containsEntry("role", "assistant")
            .containsEntry("content", "a");
    }
}
