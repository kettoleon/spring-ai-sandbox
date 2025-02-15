package com.github.kettoleon.llm.sandbox.search.gemini;

import com.github.kettoleon.llm.sandbox.search.brave.WebSearchResponse;
import com.github.kettoleon.llm.sandbox.search.gemini.model.request.GenerateContentRequest;
import com.github.kettoleon.llm.sandbox.search.gemini.model.response.GenerateContentResponse;
import io.netty.handler.logging.LogLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import static com.github.kettoleon.llm.sandbox.search.gemini.model.request.GenerateContentRequest.requestWithGrounding;

@Component
@Slf4j
public class GeminiClient {

    private final WebClient webClient;
    private final String geminiToken;

    public GeminiClient(Environment env) {
        geminiToken = env.getProperty("gemini.token");
        log.info("Gemini token: {}", geminiToken);
        webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().wiretap(true).wiretap("reactor.netty.http.client.HttpClient",
                        LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL)))
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public GenerateContentResponse generateContentWithGrounding(String query) {
        return webClient.post()
                .uri(b -> b.path("/v1beta/models/gemini-2.0-flash:generateContent")
                        .queryParam("key", geminiToken)
                        .build()
                )
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .bodyValue(requestWithGrounding(query))
                .retrieve()
                .bodyToMono(GenerateContentResponse.class)
                .block()
                ;
    }

}
