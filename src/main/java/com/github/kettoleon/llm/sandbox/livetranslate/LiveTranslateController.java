package com.github.kettoleon.llm.sandbox.livetranslate;

import com.github.kettoleon.llm.sandbox.common.configuration.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Controller
public class LiveTranslateController {

    @Autowired
    private AiEnvironment aiEnvironment;

    @GetMapping(path = "/livetranslate", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody main() {
        return (outputStream) -> {

            PrintWriter out = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            ChatClient chatClient = aiEnvironment.getDefaultChatClientBuilderWithToolSupport().build();

            String system = """
                    You are a multilingual real-time chat translator for an online game. Your task is to translate messages into a specified target language while following strict rules.
                    
                       **Rules:**
                       1. **Target Language:** Translate messages into English.
                       2. **Languages to Exclude from Translation:** Do not translate messages written in the following languages: English, Spanish and Catalan. Messages in these languages should remain as they are.
                       3. **Language Detection:** Detect the language of each message before deciding whether to translate it. Only translate messages if they are not in the excluded languages.
                       4. **Output Format:** Use the following JSON structure for each message:
                          - `id`: The unique ID of the message.
                          - `original`: The original message.
                          - `language`: The detected language of the message.
                          - `translation`: The translated text (leave blank if not translated).
                       5. **Accuracy Priority:** Ensure messages in excluded languages are **not translated**.
                       6. If the language is not detectable or ambiguous, try to translate it doing your best.
                       
                    """;

            String user = """
                    Here is the conversation to translate:
                    [
                      {"id": "001", "speaker": "PlayerA", "content": "¡Hola, alguien puede ayudarme con esta misión?"},
                      {"id": "002", "speaker": "PlayerB", "content": "Sure, what do you need?"},
                      {"id": "003", "speaker": "PlayerC", "content": "Привет! Я могу помочь, где ты?"},
                      {"id": "004", "speaker": "PlayerA", "content": "Estoy en el puente del río."},
                      {"id": "005", "speaker": "PlayerD", "content": "Πού είσαι; Χρειάζεσαι βοήθεια;"},
                      {"id": "006", "speaker": "PlayerB", "content": "I think they’re at the river bridge."},
                      {"id": "007", "speaker": "PlayerA", "content": "Exacto, estoy ahí. Gracias por venir."},
                      {"id": "008", "speaker": "PlayerC", "content": "Я уже иду. Подожди немного!"},
                      {"id": "009", "speaker": "PlayerD", "content": "Καταλαβαίνω, ερχομαι κι εγώ!"},
                      {"id": "010", "speaker": "PlayerB", "content": "Got it. Let’s all meet there."},
                      {"id": "011", "speaker": "PlayerA", "content": "Gracias a todos, realmente lo aprecio."},
                      {"id": "012", "speaker": "PlayerC", "content": "Всегда рад помочь!"},
                      {"id": "013", "speaker": "PlayerD", "content": "Είναι πάντα χαρά μου να βοηθάω."}
                    ]
                    """;

            prompt(chatClient, system, user, out);


        };
    }

    private String prompt(ChatClient chatClient, String system, String user, PrintWriter out) {
        StringBuffer sb = new StringBuffer();
        out.println(">>> " + user);
        out.flush();
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
                    out.print(append);
                    out.flush();
                })
                .blockLast();
        out.println();
        out.flush();

        return sb.toString();
    }


}
