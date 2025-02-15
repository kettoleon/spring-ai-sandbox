package com.github.kettoleon.llm.sandbox.common.prompt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.converter.BeanOutputConverter;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.function.Function;

import static org.apache.commons.lang3.StringUtils.*;

public class PromptTemplate {

    private final ChatClient chatClient;

    @Getter
    @Setter
    private boolean verbose = false;

    private PrintWriter out;

    public PromptTemplate(ChatClient chatClient){
        this.chatClient = chatClient;
        this.out = new PrintWriter(System.out);
    }

    public void setOut(PrintWriter out) {
        this.out = out;
    }

    public String prompt(String system, String user) {
        StringBuffer sb = new StringBuffer();
        if (verbose) {
            out.println(">>> " + user);
            out.flush();
        }
        ChatClient.ChatClientRequestSpec chatcc = chatClient.
                prompt()
                .advisors()
                .user(user);

        if (system != null) {
            chatcc = chatcc.system(system);
        }

        chatcc
                .stream().chatResponse()
                .doOnEach(cr -> {
                    String append = Optional.ofNullable(cr.get())
                            .map(ChatResponse::getResult)
                            .map(Generation::getOutput)
                            .map(AssistantMessage::getText)
                            .orElse("");
                    sb.append(append);
                    if (verbose) {
                        out.print(append);
                        out.flush();
                    }
                })
                .blockLast();
        if (verbose) {
            out.println();
            out.flush();
        }
        return sb.toString();
    }

    public <T> Optional<T> promptToBean(String system, String user, Class<T> out) {
        return promptToBean(system, user, out, null);
    }
    public <T> Optional<T> promptToBean(String system, String user, Class<T> outc, Function<String,T> lastResortParser) {
        BeanOutputConverter<T> boc = new BeanOutputConverter<>(outc);
        String completeSystem = system + "\n\n" + boc.getFormat();
        if (verbose) {
            out.println("$$> " + completeSystem);
            out.flush();
        }
        String result = prompt(completeSystem, user);
        String answer = substringAfterLast(result, "</think>");
        if (answer.contains("```json")) {
            answer = substringBeforeLast(substringAfter(answer, "```json"), "```").trim();
        }
        if (answer.trim().startsWith("{")) {
            try {
                T convert = boc.convert(answer);
                return Optional.of(convert);
            } catch (RuntimeException e) {
                if (verbose) {
                    out.println("Failed to parse LLM answer, asking again for correct format...");
                    out.flush();
                }
                result = prompt(buildRetrySystem(boc.getFormat()), answer);
                answer = substringAfterLast(result, "</think>");
                if (answer.contains("```json")) {
                    answer = substringBeforeLast(substringAfter(answer, "```json"), "```").trim();
                }
                try {
                    return Optional.of(boc.convert(answer));
                } catch (RuntimeException e2) {
                    e2.printStackTrace();
                }
                return Optional.empty();
            }
        } else if (lastResortParser != null) {
            return Optional.ofNullable(lastResortParser.apply(answer));
        }
        return Optional.empty();
    }

    private String buildRetrySystem(String format) {
        return """
                You just failed to return an answer with the proper format, please analise the following answer (without the thinking steps) and format it correctly.\n\n
                """ + format;
    }

}
