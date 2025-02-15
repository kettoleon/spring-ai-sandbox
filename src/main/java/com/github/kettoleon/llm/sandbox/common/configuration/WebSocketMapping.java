package com.github.kettoleon.llm.sandbox.common.configuration;

import com.github.kettoleon.llm.sandbox.chat.ChatsWebSocketHandler;
import com.github.kettoleon.llm.sandbox.pathfinder.CortexWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketMapping implements WebSocketConfigurer {

    @Autowired
    private ChatsWebSocketHandler chatsWebSocketHandler;

    @Autowired
    private CortexWebSocketHandler cortexWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatsWebSocketHandler, "/api/v1/chats/*/messages");
        registry.addHandler(cortexWebSocketHandler, "/api/v1/cortex");
    }
}
