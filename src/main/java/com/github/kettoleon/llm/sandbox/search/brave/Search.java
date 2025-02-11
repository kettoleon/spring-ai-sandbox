package com.github.kettoleon.llm.sandbox.search.brave;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Search {

    private List<SearchResult> results;

}
