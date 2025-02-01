package com.github.kettoleon.llm.sandbox.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kettoleon.llm.sandbox.chat.repo.*;
import com.github.kettoleon.llm.sandbox.common.util.MarkdownUtils;
import io.netty.util.internal.StringUtil;
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
import java.text.Format;
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

    public ChatClient getChatClient(String chatId) {
        if (!chatClients.containsKey(chatId)) {
            chatClients.put(chatId, newChatClient(chatId));
        }
        return chatClients.get(chatId);
    }

    private ChatClient newChatClient(String chatId) {
        MessageChatMemoryAdvisor messageChatMemoryAdvisor = new MessageChatMemoryAdvisor(chatMemory, chatId, DEFAULT_CHAT_MEMORY_RESPONSE_SIZE);
        return builder
                .defaultAdvisors(messageChatMemoryAdvisor)
                .build();
    }

    public void addChatMessage(Message msg) {
        liveChats.put(msg.getChat().getId(), msg.getChat());
        broadcastRawMessage(msg.getChat().getId(), formatMessage(msg));
    }

    private static String formatMessage(Message msg) {
        log.info("CALL TO FORMAT_MESSAGE");
        return "<div id=\"messages\" hx-swap-oob=\"beforeend\"><div><i>" + msg.getCreated().format(DateTimeFormatter.ofPattern("HH:mm:ss ")) + "</i> <b>" + msg.getCreatedBy() + "</b>: " + handleThinking(msg) + "</div></div>";
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
        log.info("CALL TO FORMAT_NEW_ASSISTANT_MESSAGE");
        StringBuffer sb = new StringBuffer();
        sb.append("<div id=\"messages\" hx-swap-oob=\"beforeend\"><div><i>" + msg.getCreated().format(DateTimeFormatter.ofPattern("HH:mm:ss ")) + "</i> <b>" + msg.getCreatedBy() + "</b>:");
        sb.append("  <div class=\"accordion\">");
        sb.append("    <div class=\"accordion-item\">");
        sb.append("      <h2 class=\"accordion-header\" id=\"think-header-" + msg.getId() + "\">");
        sb.append("        <button class=\"accordion-button collapsed\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#think-body-" + msg.getId() + "\" aria-expanded=\"true\" aria-controls=\"think-body-" + msg.getId() + "\">Thoughts</button>");
        sb.append("      </h2>");
        sb.append("      <div id=\"think-body-" + msg.getId() + "\" class=\"accordion-collapse collapse\" aria-labelledby=\"think-header-" + msg.getId() + "\"><div class=\"accordion-body\">");
        sb.append("        <span id=\"msg-think-" + msg.getId() + "\"></span>");
        sb.append("      </div></div>");
        sb.append("    </div>");
        sb.append("    <div class=\"accordion-item\">");
        sb.append("      <h2 class=\"accordion-header\" id=\"answer-header-" + msg.getId() + "\">");
        sb.append("        <button class=\"accordion-button\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#answer-body-" + msg.getId() + "\" aria-expanded=\"true\" aria-controls=\"answer-body-" + msg.getId() + "\">Answer</button>");
        sb.append("      </h2>");
        sb.append("      <div id=\"answer-body-" + msg.getId() + "\" class=\"accordion-collapse collapse show\" aria-labelledby=\"answer-header-" + msg.getId() + "\"><div class=\"accordion-body\">");
        sb.append("        <span id=\"msg-answer-" + msg.getId() + "\"></span>");
        sb.append("      </div></div>");
        sb.append("    </div>");
        sb.append("  </div>");
        sb.append("</div></div>");

        return sb.toString();

    }

    private String formatInProgressMessage(Message gp) {
        String text = gp.getText();
        if (text.contains("<think>")) {
            if (text.contains("</think>")) {
                String answerPart = StringUtils.substringAfterLast(text, "</think>").trim();
                return String.format("<span id=\"msg-answer-%s\">%s</span>", gp.getId(), markdownToHtml(answerPart));
            } else {
                String thinkPart = StringUtils.substringAfter(text, "<think>");
                return String.format("<span id=\"msg-think-%s\">%s</span>", gp.getId(), markdownToHtml(thinkPart));
            }
        }
        return String.format("<span id=\"msg-answer-%s\">%s</span>", gp.getId(), markdownToHtml(text));
    }

    private static String handleThinking(Message msg) {

        if (msg.getCreatedBy().equals("assistant")) {

            StringBuffer sb = new StringBuffer();
            sb.append("  <div class=\"accordion\">");
            sb.append("    <div class=\"accordion-item\">");
            sb.append("      <h2 class=\"accordion-header\" id=\"think-header-" + msg.getId() + "\">");
            sb.append("        <button class=\"accordion-button collapsed\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#think-body-" + msg.getId() + "\" aria-expanded=\"true\" aria-controls=\"think-body-" + msg.getId() + "\">Thoughts</button>");
            sb.append("      </h2>");
            sb.append("      <div id=\"think-body-" + msg.getId() + "\" class=\"accordion-collapse collapse\" aria-labelledby=\"think-header-" + msg.getId() + "\"><div class=\"accordion-body\">");
            sb.append("        " + markdownToHtml(getThinkingPart(msg.getText())));
            sb.append("      </div></div>");
            sb.append("    </div>");
            sb.append("    <div class=\"accordion-item\">");
            sb.append("      <h2 class=\"accordion-header\" id=\"answer-header-" + msg.getId() + "\">");
            sb.append("        <button class=\"accordion-button\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#answer-body-" + msg.getId() + "\" aria-expanded=\"true\" aria-controls=\"answer-body-" + msg.getId() + "\">Answer</button>");
            sb.append("      </h2>");
            sb.append("      <div id=\"answer-body-" + msg.getId() + "\" class=\"accordion-collapse collapse show\" aria-labelledby=\"answer-header-" + msg.getId() + "\"><div class=\"accordion-body\">");
            sb.append("        " + markdownToHtml(getAnswerPart(msg.getText())));
            sb.append("      </div></div>");
            sb.append("    </div>");
            sb.append("  </div>");

            return sb.toString();
        } else {
            return markdownToHtml(msg.getText());
        }
    }

    private static String getAnswerPart(String text) {
        if (text.contains("<think>")) {
            String answerPart = "";
            if (text.contains("</think>")) {
                answerPart = StringUtils.substringAfterLast(text, "</think>").trim();
            }
            return answerPart;
        }
        return text;
    }

    private static String getThinkingPart(String text) {
        if (text.contains("<think>")) {
            String thinkPart = StringUtils.substringAfter(text, "<think>");
            if (text.contains("</think>")) {
                thinkPart = StringUtils.substringBeforeLast(StringUtils.substringAfter(text, "<think>"), "</think>");
            }
            return thinkPart;
        }
        return "";
    }

//    private String getResponse(String chatId) {
//        return responses.get(chatId).getFullResponse();
//    }

    private void finishMessage(Chat chat) {
//        log.info("Closing connection for chat {}", chat.getId());
//        ChatResponse gp = responses.get(chat.getId());
//        responses.remove(chat.getId());
//        liveChats.remove(chat.getId());
//        sessions.get(chat.getId()).forEach(session -> {
//            try {
//                session.close();
//            } catch (IOException e) {
//                log.warn("Error closing session: {}", session, e);
//            }
//        });
//        sessions.remove(chat.getId());
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

//    private String buildProgressHtml(Message query, ChatResponse gp) {
////        if (gp.isError()) {
////            return String.format("<div id=\"qr-%s\" class=\"alert alert-danger d-flex align-items-center\" role=\"alert\"><i role=\"img\" class=\"bi bi-exclamation-circle-fill flex-shrink-0 me-2\"></i><div>%s</div></div>", query.getId(), gp.getErrorMessage());
////        }
//        if (StringUtils.isBlank(gp.getFullResponse())) {
//            return String.format("<div id=\"message-%s\" class=\"spinner-border spinner-border-sm\" role=\"status\"><span class=\"visually-hidden\">Loading...</span></div>", query.getId());
//        }
//        if (gp.isDone()) {
//            return String.format("<span id=\"qr-%s\">%s</span><li id=\"query-status-%s\" class=\"list-group-item\">Generated %d tokens in %.2f seconds. (%s tokens/second)</li>",
//                    query.getId(),
//                    MarkdownUtils.markdownToHtml(gp.getFullResponse()),
//                    query.getId(),
//                    gp.getResponseTokens(),
//                    gp.getResponseSeconds(),
//                    gp.getHumanReadableTokensPerSecond()
//
//            );
//        }
//        return String.format("<span id=\"qr-%s\">%s</span>", query.getId(), MarkdownUtils.markdownToHtml(gp.getFullResponse()));
//    }

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
