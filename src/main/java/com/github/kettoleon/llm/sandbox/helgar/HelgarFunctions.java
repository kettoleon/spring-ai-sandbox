package com.github.kettoleon.llm.sandbox.helgar;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

import static com.github.kettoleon.llm.sandbox.helgar.WorldBuilder.getWorld;

@Configuration
public class HelgarFunctions {

    @Bean
    @Description("When you would like to have more information available, or perform an action, but it does not exist, use this function to suggest the developers to implement it.")
    public Function<HelgarController.SuggestionRequest, HelgarController.SuggestionResponse> devSuggestion() {
        return suggestion -> {
            System.err.println("Dev suggestion: " + suggestion);
            return null;
        };
    }

    @Bean
    @Description("Use this function to move to another location in the world")
    public Function<HelgarController.MoveRequest, HelgarController.MoveResponse> moveTo() {
        return move -> {
            LocationNode dest = getWorld().findChildByFullName(move.getDestination());
            Character.getHelgar().setLocation(dest);
            Character.getHelgar().getLog().add("You moved to: " + dest.getFullName());
            return null;
        };
    }

    @Bean
    @Description("Use this function to set the character's current intention")
    public Function<HelgarController.IntentionRequest, HelgarController.IntentionResponse> setIntention() {
        return intent -> {
            Character.getHelgar().setIntention(intent.getDescription());
            Character.getHelgar().getLog().add("You set your current intention to: " + intent.getDescription());
            return null;
        };
    }

    @Bean
    @Description("Use this function when an intention or goal has been fulfilled or can't be fulfilled")
    public Function<HelgarController.IntentionOutcomeRequest, HelgarController.IntentionOutcomeResponse> setIntentionOutcome() {
        return intent -> {
            Character.getHelgar().getLog().add("Your intention to: " + Character.getHelgar().getIntention() + " was " + intent.getOutcome().name() + " (reason: " + intent.getReason() + ")");
            Character.getHelgar().setIntention(null);
            return null;
        };
    }

    @Bean
    @Description("Use this function to cook a meal in the kitchen")
    public Function<HelgarController.CookRequest, HelgarController.CookResponse> cook() {
        return intent -> {
            Character.getHelgar().getLog().add("You cooked: " + intent.getMeal());
            return null;
        };
    }


}
