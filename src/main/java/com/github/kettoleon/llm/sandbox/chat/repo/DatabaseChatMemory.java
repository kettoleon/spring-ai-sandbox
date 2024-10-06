package com.github.kettoleon.llm.sandbox.chat.repo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DatabaseChatMemory implements ChatMemory {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Override
    public void add(String conversationId, List<Message> messages) {
        Chat chat = chatRepository.findById(conversationId).orElseThrow();

        for (Message message : messages) {
            messageRepository.save(com.github.kettoleon.llm.sandbox.chat.repo.Message.builder()
                    .createdBy(getCreatedBy(message))
                    .text(message.getContent())
                    .chat(chat)
                    .created(ZonedDateTime.now())
                    .build());
        }

    }

    private String getCreatedBy(Message message) {
        if (message instanceof UserMessage) {
            return "user";
        } else if (message instanceof SystemMessage) {
            return "system";
        } else if (message instanceof AssistantMessage) {
            return "assistant";
        }
        throw new RuntimeException("Unknown message source: " + message.getClass().getSimpleName());
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        return mapMessages(messageRepository.findAllByChatOrderByCreated(chatRepository.findById(conversationId).orElseThrow()));
    }

    private List<Message> mapMessages(List<com.github.kettoleon.llm.sandbox.chat.repo.Message> repoMessages) {
        return repoMessages.stream().map(this::mapMessage).collect(Collectors.toList());
    }

    private Message mapMessage(com.github.kettoleon.llm.sandbox.chat.repo.Message repoMessage) {
        if (repoMessage.getCreatedBy().equals("user")) {
            return new UserMessage(repoMessage.getText());
        } else if (repoMessage.getCreatedBy().equals("system")) {
            return new SystemMessage(repoMessage.getText());
        } else if (repoMessage.getCreatedBy().equals("assistant")) {
            return new AssistantMessage(repoMessage.getText());
        }
        throw new RuntimeException("Unknown message source: " + repoMessage.getCreatedBy());
    }

    @Override
    public void clear(String conversationId) {
        log.info("Ignored call to clear conversation");
    }
}
