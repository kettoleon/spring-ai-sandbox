package com.example.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.example.demo.WorldBuilder.getWorld;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner main(ChatClient.Builder builder, ChatModel chatModel) {
        return (args) -> {

//            String prompt = "Write a short poem about AI";
//            System.out.println(">>> " + prompt);
//            chatModel.stream(new Prompt(prompt))
//                    .doOnEach(cr -> System.out.print(Optional.ofNullable(cr.get())
//                                    .map(ChatResponse::getResult)
//                                    .map(Generation::getOutput)
//                                    .map(AssistantMessage::getContent)
//                            .orElse("")))
//                    .blockLast();

            ChatClient chatClient = builder.build();

//            String prompt = "What are the current temperatures in Amsterdam and Paris? Calculate the average between the two as well.";
//            System.out.println(">>> " + prompt);
//            String resp = chatClient
//                    .prompt()
//                    .user(prompt)
//                    .functions("weatherFunction", "calculator") // reference by bean name.
//                    .call()
//                    .content();
//
//            System.out.println(resp);

//            ChatClient chatClient = builder.build();
//            prompt = "How much is 95+5*5/25+3985*8/4? Use the calculator function provided. Don't try to do it yourself.";
//            System.out.println(">>> " + prompt);
//
//            resp = chatClient.prompt()
//                    .user(prompt)
//                    .functions("calculator")
//                    .call()
//                    .content()
//                    ;
//
//            System.out.println(resp);

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
                System.out.println(">>> " + prompt);


                List<String> functions = new ArrayList<>();
                functions.add("devSuggestion");
                functions.add("moveTo");
                if (helgar.getIntention() == null) {
                    functions.add("setIntention");
                } else {
                    functions.add("setIntentionOutcome");
                }
                functions.addAll(helgar.getLocation().getAvailableFunctions());

                System.err.println("Functions: " + String.join(", ", functions));
                String resp = chatClient
                        .prompt()
                        .system(system)
                        .user(prompt)
                        .functions(functions.toArray(new String[]{})) // reference by bean name.
                        .call()
                        .content();

                System.out.println(resp);
                System.out.println("====== DONE ======");
            }


        };
    }

    @Bean
    @Description("When you would like to have more information available, or perform an action, but it does not exist, use this function to suggest the developers to implement it.")
    public Function<SuggestionRequest, SuggestionResponse> devSuggestion() {
        return suggestion -> {
            System.err.println("Suggestion:" + suggestion.suggestion);
            System.exit(0);
            return null;
        };
    }

    @Bean
    @Description("Use this function to move to another location in the world")
    public Function<MoveRequest, MoveResponse> moveTo() {
        return move -> {
            System.err.println("MoveTo: " + move.destination + " (" + move.type.name() + ")");
            LocationNode dest = getWorld().findChildByFullName(move.destination);
            Character.getHelgar().setLocation(dest);
            Character.getHelgar().getLog().add("You moved to: " + dest.getFullName());
            return null;
        };
    }

    @Bean
    @Description("Use this function to set the character's current intention")
    public Function<IntentionRequest, IntentionResponse> setIntention() {
        return intent -> {
            System.err.println("Intention: " + intent.description);
            Character.getHelgar().setIntention(intent.description);
            Character.getHelgar().getLog().add("You set your current intention to: " + intent.description);
            return null;
        };
    }

    @Bean
    @Description("Use this function when an intention or goal has been fulfilled or can't be fulfilled")
    public Function<IntentionOutcomeRequest, IntentionOutcomeResponse> setIntentionOutcome() {
        return intent -> {
            System.err.println("Intention Outcome: " + intent.outcome.name() + " " + intent.reason);
            Character.getHelgar().getLog().add("Your intention to: " + Character.getHelgar().getIntention() + " was " + intent.outcome.name() + " (reason: " + intent.reason + ")");
            Character.getHelgar().setIntention(null);
            return null;
        };
    }

    @Bean
    @Description("Use this function to cook a meal in the kitchen")
    public Function<CookRequest, CookResponse> cook() {
        return intent -> {
            System.err.println("Cooking: " + intent.meal + " with " + intent.ingredients + " for " + intent.timeInSeconds + " seconds.");
            Character.getHelgar().getLog().add("You cooked: " + intent.meal);
            return null;
        };
    }

    @Bean
    @Description("Get the weather in location")
    public Function<MockWeatherService.WeatherRequest, MockWeatherService.WeatherResponse> weatherFunction() {
        return new MockWeatherService();
    }

    @Bean
    @Description("Calculate simple math expressions")
    public Function<Calculator.CalculatorRequest, Calculator.CalculatorResponse> calculator() {
        return new Calculator();
    }

    public enum MoveType {
        WALK,
        RUN
    }

    public static class Calculator implements Function<Calculator.CalculatorRequest, Calculator.CalculatorResponse> {

        @Override
        public CalculatorResponse apply(CalculatorRequest s) {
            System.err.println("Calculator invoked!: " + s.expression);
            return new CalculatorResponse(27.6);
        }

        public record CalculatorRequest(String expression) {
        }

        public record CalculatorResponse(Double result) {
        }
    }

    public static class MockWeatherService implements Function<MockWeatherService.WeatherRequest, MockWeatherService.WeatherResponse> {

        @Override
        public WeatherResponse apply(WeatherRequest request) {
            System.err.println("Calling weather function " + request.location);
            double temperature = request.location().contains("Amsterdam") ? 20 : 25;
            return new WeatherResponse(temperature, request.unit);
        }

        public record WeatherRequest(String location, String unit) {
        }

        public record WeatherResponse(double temp, String unit) {
        }
    }

    public record SuggestionRequest(
            String suggestion
    ) {
    }

    public record SuggestionResponse() {
    }

    public record CookRequest(
            String meal,
            String ingredients,
            int timeInSeconds
    ) {
    }

    public record CookResponse() {
    }


    public record IntentionRequest(
            String description
    ) {
    }

    public record IntentionResponse() {
    }

    public record IntentionOutcomeRequest(
            IntentionOutcome outcome,
            String reason
    ) {
    }

    public record IntentionOutcomeResponse() {
    }

    public record MoveRequest(
            MoveType type,
            String destination
    ) {

    }

    public record MoveResponse() {
    }

}
