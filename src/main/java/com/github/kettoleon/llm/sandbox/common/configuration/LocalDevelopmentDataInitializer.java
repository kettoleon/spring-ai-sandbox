package com.github.kettoleon.llm.sandbox.common.configuration;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(name = "localdev", havingValue = "true")
@Configuration
public class LocalDevelopmentDataInitializer {


    @Bean
    public CommandLineRunner init() {
        return args -> {

            //TODO add here any database initialisation needed

        };
    }

}
