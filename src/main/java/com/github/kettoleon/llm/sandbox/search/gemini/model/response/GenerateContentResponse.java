package com.github.kettoleon.llm.sandbox.search.gemini.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.kettoleon.llm.sandbox.search.gemini.model.request.Content;
import com.github.kettoleon.llm.sandbox.search.gemini.model.request.Part;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
public class GenerateContentResponse {

    private List<Candidate> candidates;

}
