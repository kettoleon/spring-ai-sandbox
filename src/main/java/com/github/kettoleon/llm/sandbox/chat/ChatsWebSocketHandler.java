package com.github.kettoleon.llm.sandbox.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kettoleon.llm.sandbox.chat.repo.*;
import com.github.kettoleon.llm.sandbox.common.configuration.AiEnvironment;
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
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.StringWriter;
import java.text.Format;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.kettoleon.llm.sandbox.common.util.MarkdownUtils.markdownToHtml;
import static com.github.kettoleon.llm.sandbox.common.util.MarkdownUtils.markdownToHtmlNullable;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.DEFAULT_CHAT_MEMORY_RESPONSE_SIZE;

@Component
@Slf4j
public class ChatsWebSocketHandler implements WebSocketHandler {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private AiEnvironment aiEnvironment;

    @Autowired
    private SpringTemplateEngine springTemplateEngine;

    private Map<String, Chat> liveChats = new HashMap<>();
    private Map<String, ChatClient> chatClients = new HashMap<>();
    private Map<String, MessageChatMemoryAdvisor> memoryAdvisors = new HashMap<>();
    private Map<String, ChatResponse> responses = new HashMap<>();
    private final Map<String, List<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public ChatClient getChatClient(String chatId) {
        if (!chatClients.containsKey(chatId)) {
            chatClients.put(chatId, newChatClient(chatId));
        }
        return chatClients.get(chatId);
    }

    public void remove(String chatId) {
        liveChats.remove(chatId);
        chatClients.remove(chatId);
        memoryAdvisors.remove(chatId);
        responses.remove(chatId);
        List<WebSocketSession> ss = sessions.get(chatId);
        for (WebSocketSession s : ss) {
            try {
                s.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        sessions.remove(chatId);
    }

    private ChatClient newChatClient(String chatId) {
        memoryAdvisors.put(chatId, new MessageChatMemoryAdvisor(new DatabaseChatMemory(chatRepository, messageRepository), chatId, DEFAULT_CHAT_MEMORY_RESPONSE_SIZE));
        return aiEnvironment.getDefaultChatClientBuilder().build();
    }

    public void addChatMessage(Message msg) {
        liveChats.put(msg.getChat().getId(), msg.getChat());
        broadcastRawMessage(msg.getChat().getId(), formatMessage(msg));
    }


    private String formatMessage(Message msg) {
        if (msg.getCreatedBy().equals("assistant")) {
            return "<div id=\"messages\" hx-swap-oob=\"beforeend\"><div>" + handleThinking(msg) + "</div></div>";
        } else if (msg.getCreatedBy().equals("user")) {
            return "<div id=\"messages\" hx-swap-oob=\"beforeend\"><div class=\"d-flex justify-content-end\" style=\"margin: 1em;\"><div class=\"card bg-body-secondary mb-3\"><div class=\"card-body\" style=\"white-space: pre-wrap; word-break: break-word;\">" + msg.getText() + "</div></div></div></div>";
        }

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
                            .map(AssistantMessage::getText).orElse("");
                    currentMessage.setText(Optional.ofNullable(currentMessage.getText()).orElse("") + text);
                    broadcastRawMessage(chat.getId(), formatInProgressMessage(currentMessage));

                });

    }

    private String formatNewAssistantMessage(Message msg) {

        StringBuffer sb = new StringBuffer();
        sb.append("<div id=\"messages\" hx-swap-oob=\"beforeend\"><div>");

        Context context = new Context();
        context.setVariable("id", msg.getId());
        context.setVariable("thinking", null);
        context.setVariable("answer", null);
        StringWriter writer = new StringWriter();
        springTemplateEngine.process("chats/message", context, writer);

        sb.append(writer.toString());

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

    private String handleThinking(Message msg) {

        if (msg.getCreatedBy().equals("assistant")) {

            Context context = new Context();
            context.setVariable("id", msg.getId());
            context.setVariable("thinking", markdownToHtmlNullable(getThinkingPart(msg.getText())));
            context.setVariable("answer", markdownToHtmlNullable(getAnswerPart(msg.getText())));
            StringWriter writer = new StringWriter();
            springTemplateEngine.process("chats/message", context, writer);

            return writer.toString();

//            StringBuffer sb = new StringBuffer();
//            sb.append("  <div class=\"accordion\">");
//            sb.append("    <div class=\"accordion-item\">");
//            sb.append("      <h2 class=\"accordion-header\" id=\"think-header-" + msg.getId() + "\">");
//            sb.append("        <button class=\"accordion-button collapsed\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#think-body-" + msg.getId() + "\" aria-expanded=\"true\" aria-controls=\"think-body-" + msg.getId() + "\">Thoughts</button>");
//            sb.append("      </h2>");
//            sb.append("      <div id=\"think-body-" + msg.getId() + "\" class=\"accordion-collapse collapse\" aria-labelledby=\"think-header-" + msg.getId() + "\"><div class=\"accordion-body\">");
//            sb.append("        " + markdownToHtml(getThinkingPart(msg.getText())));
//            sb.append("      </div></div>");
//            sb.append("    </div>");
//            sb.append("    <div class=\"accordion-item\">");
//            sb.append("      <h2 class=\"accordion-header\" id=\"answer-header-" + msg.getId() + "\">");
//            sb.append("        <button class=\"accordion-button\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#answer-body-" + msg.getId() + "\" aria-expanded=\"true\" aria-controls=\"answer-body-" + msg.getId() + "\">Answer</button>");
//            sb.append("      </h2>");
//            sb.append("      <div id=\"answer-body-" + msg.getId() + "\" class=\"accordion-collapse collapse show\" aria-labelledby=\"answer-header-" + msg.getId() + "\"><div class=\"accordion-body\">");
//            sb.append("        " + markdownToHtml(getAnswerPart(msg.getText())));
//            sb.append("      </div></div>");
//            sb.append("    </div>");
//            sb.append("  </div>");

//            return sb.toString();
        } else {
            return markdownToHtml(msg.getText());
        }
    }

    private static String getAnswerPart(String text) {
        if (text.contains("<think>")) {
            String answerPart = null;
            if (text.contains("</think>")) {
                answerPart = StringUtils.substringAfterLast(text, "</think>").trim();
            }
            if(isBlank(answerPart)){
                return null;
            }
            return answerPart;
        }
        if(isBlank(text)){
            return null;
        }
        return text;
    }

    private static String getThinkingPart(String text) {
        if (text.contains("<think>")) {
            String thinkPart = StringUtils.substringAfter(text, "<think>");
            if (text.contains("</think>")) {
                thinkPart = StringUtils.substringBeforeLast(StringUtils.substringAfter(text, "<think>"), "</think>");
            }
            if(isBlank(thinkPart)){
                return null;
            }
            return thinkPart;
        }
        return null;
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
        log.info("Received message for chat {}: {}", chatId, text);
        if(isNotBlank(text)) {
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
                    .advisors(memoryAdvisors.get(chatId))
                    .user(text)
                    .stream().chatResponse());
        }
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
