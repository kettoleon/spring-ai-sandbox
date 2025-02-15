package com.github.kettoleon.llm.sandbox.search.gemini.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
public class GroundingMetadata {

    private List<GroundingChunk> groundingChunks;
    private List<GroundingSupports> groundingSupports;

}
