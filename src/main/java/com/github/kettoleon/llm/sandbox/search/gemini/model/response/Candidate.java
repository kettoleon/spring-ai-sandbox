package com.github.kettoleon.llm.sandbox.search.gemini.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.kettoleon.llm.sandbox.search.gemini.model.request.Content;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
public class Candidate {

    private Content content;

    private GroundingMetadata groundingMetadata;
}
