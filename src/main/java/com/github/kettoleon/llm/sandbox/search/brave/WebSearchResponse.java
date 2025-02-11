package com.github.kettoleon.llm.sandbox.search.brave;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * @see <a href="https://api-dashboard.search.brave.com/app/documentation/web-search/responses#WebSearchApiResponse">Brave web search api responses</a>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSearchResponse {
    private Search web;
}
