package com.github.kettoleon.llm.sandbox.search.gemini.model.request;

import lombok.Builder;
import lombok.Data;

import java.util.*;

import static java.util.Collections.singletonList;

@Data
@Builder
public class GenerateContentRequest {

    private List<Content> contents;
    private List<Map<String, Object>> tools;

    public static GenerateContentRequest requestWithGrounding(String text) {
        return GenerateContentRequest.builder()
                .contents(singletonList(Content.builder()
                        .parts(singletonList(Part.builder()
                                .text(text)
                                .build()))
                        .build()))
                .tools(singletonList(groundingTool()))
                .build();
    }

    private static Map<String, Object> groundingTool() {
        return Map.of("googleSearch", Map.of());
//        return Map.of("google_search_retrieval", Map.of("dynamic_retrieval_config", Map.of("mode","MODE_DYNAMIC", "dynamic_threshold", 1)));
    }

}
