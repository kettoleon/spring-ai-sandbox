package com.github.kettoleon.llm.sandbox.helgar;

import com.github.kettoleon.llm.sandbox.common.configuration.AiEnvironment;
import lombok.Data;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.github.kettoleon.llm.sandbox.helgar.WorldBuilder.getWorld;

@Controller
public class HelgarController {

    @Autowired
    private AiEnvironment aiEnvironment;

    @GetMapping(path = "/helgar", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody main() {
        return (outputStream) -> {

            PrintWriter out = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            ChatClient chatClient = aiEnvironment.getDefaultChatClientBuilderWithToolSupport().build();

            Character helgar = Character.getHelgar();

            String system = """
                    You are a system that simulates human behaviour for NPCs. You will assume the personality of the
                    specified character and decide what it says or does.
                    Use one function at a time, we will invoke inference again after you use the function with a list
                    of previous actions and current intention.
                    Avoid setting the same intention that you currently have or similar.
                    Only change the intention if the previous one has been fulfilled or can't be fulfilled.
                    You need to move to places for available functions to show up. For example, move first to the kitchen
                    to be able to cook breakfast.
                    DO NOT generate any output, it will be ignored, just call one function.
                    """;
            while (true) {
                StringBuilder promptsb = new StringBuilder();
                promptsb.append("""
                        World:
                        This is a fantasy world with magic.\n""");
                promptsb.append("Locations: (World locations the character knows about and remembers)\n");

                getWorld().getLeaves().forEach(l -> promptsb.append(l.getFullName() + "\n"));

                promptsb.append("Character:\n You are " + helgar.getName() + ". " + helgar.getDescription() + "\n");
                promptsb.append("Your Current Location:\n" + helgar.getLocation().getFullName() + "\n");
                if (helgar.getIntention() != null) {
                    promptsb.append("Your Current Intention or objective:\n" + helgar.getIntention() + "\n");
                }
                promptsb.append("Your most recent events:\n");
                for (String evt : helgar.getLog()) {
                    promptsb.append(evt + "\n");
                }
                promptsb.append("""
                        Question:
                        What do you want to do next? Use the available functions.""");
                String prompt = promptsb.toString();
                out.println(">>> " + prompt);


                List<String> functions = new ArrayList<>();
                functions.add("devSuggestion");
                functions.add("moveTo");
                if (helgar.getIntention() == null) {
                    functions.add("setIntention");
                } else {
                    functions.add("setIntentionOutcome");
                }
                functions.addAll(helgar.getLocation().getAvailableFunctions());

                out.println("Functions: " + String.join(", ", functions));
                String resp = chatClient
                        .prompt()
                        .system(system)
                        .user(prompt)
                        .functions(functions.toArray(new String[]{})) // reference by bean name.
                        .call()
                        .content();

                out.println(resp);
                out.println("====== DONE ======");
            }


        };
    }

    public enum MoveType {
        WALK,
        RUN
    }

    @Data
    public class SuggestionRequest {
        private String suggestion;
    }

    @Data
    public class SuggestionResponse {
    }

    @Data
    public class CookRequest{
       private String meal;
       private String ingredients;
       private int timeInSecond;
    }

    @Data
    public class CookResponse {
    }


    @Data
    public class IntentionRequest {
        private String description;
    }

    @Data
    public class IntentionResponse {
    }

    @Data
    public class IntentionOutcomeRequest {
        private IntentionOutcome outcome;
        private String reason;
    }

    @Data
    public class IntentionOutcomeResponse {
    }

    @Data
    public class MoveRequest {
        private MoveType type;
        private String destination;
    }

    @Data
    public class MoveResponse {
    }

}
