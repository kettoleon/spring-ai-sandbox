package com.github.kettoleon.llm.sandbox.search.brave;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class BraveClient {

    private final WebClient webClient;
    private final String braveToken;

    public BraveClient(Environment env) {
        braveToken = env.getProperty("brave.token");
        log.info("Brave token: {}", braveToken);
        webClient = WebClient.builder()
//                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().wiretap(true).wiretap("reactor.netty.http.client.HttpClient",
//                        LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL)))
                .baseUrl("https://api.search.brave.com")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("X-Subscription-Token", braveToken)
                .build();
    }

    public WebSearchResponse search(String query) {
        return webClient.get()
                .uri(b -> b.path("res/v1/web/search").queryParam("q", query).queryParam("spellcheck", 0).build())
                .header("Accept", "application/json")
                .header("X-Subscription-Token", braveToken)
                .retrieve()
                .bodyToMono(WebSearchResponse.class)
                .block()
                ;
    }

}
