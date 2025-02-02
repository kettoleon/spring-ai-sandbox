package com.github.kettoleon.llm.sandbox.pathfinder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kettoleon.llm.sandbox.chat.repo.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.kettoleon.llm.sandbox.common.util.MarkdownUtils.markdownToHtml;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.DEFAULT_CHAT_MEMORY_RESPONSE_SIZE;

@Component
@Slf4j
public class CortexWebSocketHandler implements WebSocketHandler {


    public static final String KICKOFF_MESSAGE_NARRATOR = "It is the year 2168, you signed up for the Pathfinder Space Program and were selected as the volunteer to have its mind transferred into a Vonn Neuman probe. The time has arrived, you are lying in the operating room and the sedative is starting to have effect. You don't even realise but you lost your consciousness. Once you regain it you wake up disoriented and say...";
    @Autowired
    private ChatClient.Builder builder;


    private Map<String, Chat> liveChats = new HashMap<>();
    private Map<String, ChatResponse> responses = new HashMap<>();
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();


    private static final String defaultSystem = """
           
            """;
    private ChatClient chatClient;


    public ChatClient getChatClient() {
        if (chatClient == null) {
            chatClient = newChatClient();
        }
        return chatClient;
    }

    private ChatClient newChatClient() {
        InMemoryChatMemory chatMemory = new InMemoryChatMemory();
        chatMemory.add("1", new AssistantMessage(KICKOFF_MESSAGE_NARRATOR));
        addChatMessage(Message.builder()
                .created(ZonedDateTime.now())
                .text(KICKOFF_MESSAGE_NARRATOR)
                .createdBy("assistant")
                .build());
        MessageChatMemoryAdvisor messageChatMemoryAdvisor = new MessageChatMemoryAdvisor(chatMemory, "1", DEFAULT_CHAT_MEMORY_RESPONSE_SIZE);
        return builder
                .defaultSystem(PathfinderApplication.ELIAN_VOSS_SYSTEM)
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build();
    }

    public void addChatMessage(Message msg) {
//        liveChats.put(msg.getChat().getId(), msg.getChat());
        broadcastRawMessage(formatMessage(msg));
    }

    private static String formatMessage(Message msg) {
        if(msg.getCreatedBy().equals("assistant")){
            return "<div id=\"messages\" hx-swap-oob=\"beforeend\"><div>" + markdownToHtml(msg.getText()) + "</div></div>";
        }else if(msg.getCreatedBy().equals("user")){
            return "<div id=\"messages\" hx-swap-oob=\"beforeend\"><div class=\"d-flex justify-content-end\" style=\"margin: 1em 3em 0em 0em;\"><div class=\"card text-end text-bg-secondary mb-3\" style=\"max-width: 30rem;\"><div class=\"card-body\">" + msg.getText() + "</div></div></div></div>";
        }

        return "<div id=\"messages\" hx-swap-oob=\"beforeend\"><div><i>" + msg.getCreated().format(DateTimeFormatter.ofPattern("HH:mm:ss ")) + "</i> <b>" + msg.getCreatedBy() + "</b>: " + markdownToHtml(msg.getText()) + "</div></div>";
    }

    public void addChatMessage(Flux<ChatResponse> response) {
//        liveChats.put(chat.getId(), chat);
        Message currentMessage = Message.builder()
                .id(UUID.randomUUID().toString())
                .createdBy("assistant")
//                .chat(chat)
                .created(ZonedDateTime.now())
                .build();
        broadcastRawMessage(formatNewAssistantMessage(currentMessage));
        response.doFinally(s -> finishMessage())
                .subscribe(gp -> {
                    responses.put("", gp);
                    String text = Optional.ofNullable(gp.getResult())
                            .map(Generation::getOutput)
                            .map(AssistantMessage::getText).orElse("");
                    currentMessage.setText(Optional.ofNullable(currentMessage.getText()).orElse("") + text);
                    broadcastRawMessage(formatInProgressMessage(currentMessage));

                });

    }

    private String formatNewAssistantMessage(Message msg) {
        return "<div id=\"messages\" hx-swap-oob=\"beforeend\"><div><span id=\"msg-" + msg.getId() + "\">...</span></div></div>";
    }

    private String formatInProgressMessage(Message gp) {
        return String.format("<span id=\"msg-%s\">%s</span>", gp.getId(), markdownToHtml(gp.getText()));
    }


    private void finishMessage() {
        //TODO manage sessions, switching chats, etc.
    }


    private void broadcastRawMessage(String html) {
        getOrCreateWebSockets().forEach(session -> sendRawMessage(session, html));
    }

    private List<WebSocketSession> getOrCreateWebSockets() {
        return sessions;
    }

    private void sendRawMessage(WebSocketSession session, String progressHtml) {
        try {
            session.sendMessage(new TextMessage(progressHtml));
        } catch (IOException e) {
            log.warn("Error sending message to session: {}", session, e);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String chatId = getChatId(session);
        getOrCreateWebSockets().add(session);

        sendChatHistoryIfNeeded(session, chatId);
        getChatClient();
    }

    private void sendChatHistoryIfNeeded(WebSocketSession session, String chatId) {
        //Get chat from repo or liveChats
        //get previous messages
        //group them by day, minutes?
        //build and send the divs
        //Store position of last message sent per session?

//        List<Message> messages = messageRepository.findAllByChatOrderByCreated(chatRepository.findById(getChatId(session)).orElseThrow());
//        messages.stream().forEach(m -> sendRawMessage(session, formatMessage(m)));

    }

    private String getChatId(WebSocketSession session) {
        return StringUtils.substringAfterLast(StringUtils.substringBeforeLast(session.getUri().toString(), "/"), "/");
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String text = new ObjectMapper().readTree(message.getPayload().toString()).get("message").asText();

//        String chatId = getChatId(session);
//        Chat chat = chatRepository.findById(chatId).orElseThrow();
        Message msg = Message.builder()
//                .chat(chat)
                .created(ZonedDateTime.now())
                .text(text)
                .createdBy("user")
                .build();
        addChatMessage(msg);

        addChatMessage(getChatClient().
                prompt()
                .advisors()
                .user(text)
                .stream().chatResponse());
    }


    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class WSMessage {
        private String message;
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        getOrCreateWebSockets().remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        getOrCreateWebSockets().remove(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
