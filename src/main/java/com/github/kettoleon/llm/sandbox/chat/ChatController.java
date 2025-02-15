package com.github.kettoleon.llm.sandbox.chat;

import com.github.kettoleon.llm.sandbox.chat.repo.Chat;
import com.github.kettoleon.llm.sandbox.chat.repo.ChatRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.github.kettoleon.llm.sandbox.common.configuration.GlobalTemplateVariables.page;

@Controller
@Slf4j
public class ChatController {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ChatsWebSocketHandler chatsWebSocketHandler;

    @GetMapping(path = "/chats")
    public ModelAndView getChats() {
        ModelAndView chats = page("chats/chats", "Chats", "MyChatGPT");
        chats.addObject("chatGroups", makeChatGroups(chatRepository.findAll(Sort.by("created").descending())));
        return chats;
    }

    @GetMapping(path = "/chats/list")
    public ModelAndView getChatsList() {
        ModelAndView chats = new ModelAndView("chats/chat-list-show");
        chats.addObject("chatGroups", makeChatGroups(chatRepository.findAll(Sort.by("created").descending())));
        return chats;
    }

    @PutMapping(path = {"/api/v1/chats"})
    public ModelAndView openNewChat(HttpServletResponse response) {
        String chatId = UUID.randomUUID().toString();
        Chat chat = Chat.builder()
                .id(chatId)
                .title("New chat")
                .created(ZonedDateTime.now())
                .build();
        chatRepository.save(chat);
        response.addHeader("HX-Trigger", "chatsUpdated");
        return new ModelAndView("chats/chat")
                .addObject("chatId", chatId)
                ;
    }

    @GetMapping(path = {"/api/v1/chats/{chatId}"})
    public ModelAndView getChat(@PathVariable("chatId") String chatId) {
        if (chatRepository.existsById(chatId)) {
            return new ModelAndView("chats/chat").addObject("chatId", chatId);
        } else {
            return new ModelAndView("chats/chat").addObject("chatId", null);
        }
    }

    @GetMapping(path = {"/api/v1/chats/{chatId}/edit"})
    public ModelAndView getChatEditHtml(@PathVariable("chatId") String chatId) {
        return new ModelAndView("chats/chat-list-title-edit").addObject("chat", chatRepository.findById(chatId).orElseThrow());
    }

    @PostMapping(path = {"/api/v1/chats/{chatId}/edit"})
    public ModelAndView doChatEditHtml(@PathVariable("chatId") String chatId, @RequestParam("title") String title) {

        Optional<Chat> chat = chatRepository.findById(chatId);
        if (chat.isPresent()) {
            chat.get().setTitle(title);
            chatRepository.save(chat.get());
        }
        return new ModelAndView("chats/chat-list-title-show").addObject("chat", chat.orElseThrow());
    }

    @DeleteMapping(path = {"/api/v1/chats/{chatId}"})
    public ResponseEntity<?> deleteChat(@PathVariable("chatId") String chatId) {

        chatRepository.deleteById(chatId);
        chatsWebSocketHandler.remove(chatId);

        return ResponseEntity.ok().header("HX-Trigger", "chatsUpdated").build();
    }

    private Map<String, List<Chat>> makeChatGroups(List<Chat> chats) {
        List<Chat> today = new ArrayList<>();
        List<Chat> yesterday = new ArrayList<>();
        List<Chat> last7days = new ArrayList<>();
        List<Chat> last30days = new ArrayList<>();
        List<Chat> older = new ArrayList<>();
        for (Chat chat : chats) {
            if (chat.getCreated().isAfter(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS))) {
                today.add(chat);
            } else if (
                    chat.getCreated().isBefore(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS)) &&
                            chat.getCreated().isAfter(ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.DAYS))
            ) {
                yesterday.add(chat);
            } else if (
                    chat.getCreated().isBefore(ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.DAYS)) &&
                            chat.getCreated().isAfter(ZonedDateTime.now().minusDays(7).truncatedTo(ChronoUnit.DAYS))
            ) {
                last7days.add(chat);
            } else if (
                    chat.getCreated().isBefore(ZonedDateTime.now().minusDays(7).truncatedTo(ChronoUnit.DAYS)) &&
                            chat.getCreated().isAfter(ZonedDateTime.now().minusDays(30).truncatedTo(ChronoUnit.DAYS))
            ) {
                last30days.add(chat);
            } else if (
                    chat.getCreated().isBefore(ZonedDateTime.now().minusDays(30).truncatedTo(ChronoUnit.DAYS))) {
                older.add(chat); //TODO classify older chats by month and year
            }
        }

        Map<String, List<Chat>> groups = new LinkedHashMap<>();
        if (!today.isEmpty()) {
            groups.put("Today", today);
        }
        if (!yesterday.isEmpty()) {
            groups.put("Yesterday", yesterday);
        }
        if (!last7days.isEmpty()) {
            groups.put("Last 7 days", last7days);
        }
        if (!last30days.isEmpty()) {
            groups.put("Last 30 days", last30days);
        }
        if (!older.isEmpty()) {
            groups.put("Older", older);
        }

        return groups;

    }


}
