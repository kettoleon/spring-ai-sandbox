package com.github.kettoleon.llm.sandbox.search.brave;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResult {

    private String title;
    private String url;
    private String description;
}
