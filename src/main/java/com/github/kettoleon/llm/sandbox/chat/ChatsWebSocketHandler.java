package com.github.kettoleon.llm.sandbox.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kettoleon.llm.sandbox.chat.repo.*;
import com.github.kettoleon.llm.sandbox.common.util.MarkdownUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.kettoleon.llm.sandbox.common.util.MarkdownUtils.markdownToHtml;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.DEFAULT_CHAT_MEMORY_RESPONSE_SIZE;

@Component
@Slf4j
public class ChatsWebSocketHandler implements WebSocketHandler {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChatClient.Builder builder;

    @Autowired
    private DatabaseChatMemory chatMemory;

    private Map<String, Chat> liveChats = new HashMap<>();
    private Map<String, ChatClient> chatClients = new HashMap<>();
    private Map<String, ChatResponse> responses = new HashMap<>();
    private final Map<String, List<WebSocketSession>> sessions = new ConcurrentHashMap<>();


    private static final String defaultSystem = """
            You are an AI assistant designed to provide detailed, step-by-step responses. Your outputs should follow this structure:
                        
            1. Begin with a <thinking> section.
            2. Inside the thinking section:
               a. Briefly analyze the question and outline your approach.
               b. Present a clear plan of steps to solve the problem.
               c. Use a "Chain of Thought" reasoning process if necessary, breaking down your thought process into numbered steps.
            3. Include a <reflection> section for each idea where you:
               a. Review your reasoning.
               b. Check for potential errors or oversights.
               c. Confirm or adjust your conclusion if necessary.
            4. Be sure to close each reflection section with </reflection>.
            5. Close the thinking section with </thinking>.
            6. Provide your final answer at the end, outside of the <thinking></thinking> tags.
            
            Make sure the output is outside of the thinking tags, since everything inside the thinking tags will be hidden from the user!
                        
            Always use these tags in your responses. Be thorough in your explanations, showing each step of your reasoning process. Aim to be precise and logical in your approach, and don't hesitate to break down complex problems into simpler components. Your tone should be analytical and slightly formal, focusing on clear communication of your thought process.
                        
            Remember: <thinking> and <reflection> MUST be tags and must be closed at their conclusion
                        
            Make sure all <tags> are on separate lines with no other text. Do not include other text on a line containing a tag.
            """;


    public ChatClient getChatClient(String chatId) {
        if (!chatClients.containsKey(chatId)) {
            chatClients.put(chatId, newChatClient(chatId));
        }
        return chatClients.get(chatId);
    }

    private ChatClient newChatClient(String chatId) {
        MessageChatMemoryAdvisor messageChatMemoryAdvisor = new MessageChatMemoryAdvisor(chatMemory, chatId, DEFAULT_CHAT_MEMORY_RESPONSE_SIZE);
        return builder
                .defaultSystem(defaultSystem)
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build();
    }

    public void addChatMessage(Message msg) {
        liveChats.put(msg.getChat().getId(), msg.getChat());
        broadcastRawMessage(msg.getChat().getId(), formatMessage(msg));
    }

    private static String formatMessage(Message msg) {
        return "<div id=\"messages\" hx-swap-oob=\"beforeend\"><div><i>" + msg.getCreated().format(DateTimeFormatter.ofPattern("HH:mm:ss ")) + "</i> <b>" + msg.getCreatedBy() + "</b>: " + markdownToHtml(handleThinking(msg)) + "</div></div>";
    }

    public void addChatMessage(Chat chat, Flux<ChatResponse> response) {
        liveChats.put(chat.getId(), chat);
        Message currentMessage = Message.builder()
                .id(UUID.randomUUID().toString())
                .createdBy("assistant")
                .chat(chat)
                .created(ZonedDateTime.now())
                .build();
        broadcastRawMessage(chat.getId(), formatNewAssistantMessage(currentMessage));
        response.doFinally(s -> finishMessage(chat))
                .subscribe(gp -> {
                    responses.put(chat.getId(), gp);
                    String text = Optional.ofNullable(gp.getResult())
                            .map(Generation::getOutput)
                            .map(AssistantMessage::getContent).orElse("");
                    currentMessage.setText(Optional.ofNullable(currentMessage.getText()).orElse("") + text);
                    broadcastRawMessage(chat.getId(), formatInProgressMessage(currentMessage));

                });

    }

    private String formatNewAssistantMessage(Message msg) {
        return "<div id=\"messages\" hx-swap-oob=\"beforeend\"><div><i>" + msg.getCreated().format(DateTimeFormatter.ofPattern("HH:mm:ss ")) + "</i> <b>" + msg.getCreatedBy() + "</b>: <span id=\"msg-" + msg.getId() + "\">Loading...</span></div></div>";
    }

    private String formatInProgressMessage(Message gp) {
        return String.format("<span id=\"msg-%s\">%s</span>", gp.getId(), markdownToHtml(handleThinking(gp)));
    }

    private static String handleThinking(Message gp) {
        String text = gp.getText();
        if (text.contains("<thinking>")) {
            if (text.contains("</thinking>")) {
                String[] split = text.split("</thinking>");
                if (split.length > 1) {
                    return split[1].trim();
                } else {
                    return "";
                }
            } else {
                return "Thinking...";
            }
        }
        return text;
    }

    private void finishMessage(Chat chat) {
        //TODO handle closing of chats/switching to another chat.
    }


    private void broadcastRawMessage(String chatId, String html) {
        getOrCreateWebSockets(chatId).forEach(session -> sendRawMessage(session, html));
    }

    private List<WebSocketSession> getOrCreateWebSockets(String queryId) {
        return sessions.computeIfAbsent(queryId, k -> new CopyOnWriteArrayList<>());
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
        getOrCreateWebSockets(chatId).add(session);

        sendChatHistoryIfNeeded(session, chatId);
    }

    private void sendChatHistoryIfNeeded(WebSocketSession session, String chatId) {
        //Get chat from repo or liveChats
        //get previous messages
        //group them by day, minutes?
        //build and send the divs
        //Store position of last message sent per session?

        List<Message> messages = messageRepository.findAllByChatOrderByCreated(chatRepository.findById(getChatId(session)).orElseThrow());
        messages.stream().forEach(m -> sendRawMessage(session, formatMessage(m)));

    }

    private String getChatId(WebSocketSession session) {
        return StringUtils.substringAfterLast(StringUtils.substringBeforeLast(session.getUri().toString(), "/"), "/");
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String text = new ObjectMapper().readTree(message.getPayload().toString()).get("message").asText();

        String chatId = getChatId(session);
        Chat chat = chatRepository.findById(chatId).orElseThrow();
        Message msg = Message.builder()
                .chat(chat)
                .created(ZonedDateTime.now())
                .text(text)
                .createdBy("user")
                .build();
        addChatMessage(msg);

        addChatMessage(chat, getChatClient(chatId).
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
        getOrCreateWebSockets(getChatId(session)).remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        getOrCreateWebSockets(getChatId(session)).remove(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
