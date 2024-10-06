package com.github.kettoleon.llm.sandbox.chat;

import com.github.kettoleon.llm.sandbox.chat.repo.Chat;
import com.github.kettoleon.llm.sandbox.chat.repo.ChatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.servlet.ModelAndView;

import java.time.ZonedDateTime;
import java.util.*;

import static com.github.kettoleon.llm.sandbox.common.configuration.GlobalTemplateVariables.page;

@Controller
@Slf4j
public class ChatController {

    @Autowired
    private ChatRepository chatRepository;

    @GetMapping(path = {"", "/", "/chats"})
    public ModelAndView chats() {
        ModelAndView chats = page("chats/chats", "Chats");
        chats.addObject("chats", chatRepository.findAll(Sort.by("created").descending()));
        return chats;
    }

    @PutMapping(path = {"/api/v1/chats"})
    public ModelAndView newChatForm() {
        String chatId = UUID.randomUUID().toString();
        return new ModelAndView("chats/new")
                .addObject("chatId", chatId)
                ;
    }

    @GetMapping(path = {"/api/v1/chats/{chatId}"})
    public ModelAndView getChat(@PathVariable("chatId") String chatId) {
        Chat chat = Chat.builder()
                .id(chatId)
                .title("New chat")
                .created(ZonedDateTime.now())
                .build();
        if(!chatRepository.findById(chatId).isPresent()) {
            chatRepository.save(chat);
        }
        return new ModelAndView("chats/chat").addObject("chatId", chatId);
    }


}
