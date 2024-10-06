package com.github.kettoleon.llm.sandbox.chat;

import com.github.kettoleon.llm.sandbox.common.configuration.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.context.annotation.Import;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;

@SpringBootApplication
@Import(value = {
        ErrorController.class,
        GlobalTemplateVariables.class,
        JpaNamingStrategy.class,
        LocalDevelopmentDataInitializer.class,
        SecurityConfiguration.class
})
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }

    @Bean
    public CommandLineRunner main(ChatClient.Builder builder, ChatModel chatModel) {
        return (args) -> {

            GlobalTemplateVariables.setProjectTitle("MyChatGPT");

            InMemoryChatMemory chatMemory = new InMemoryChatMemory();
            MessageChatMemoryAdvisor messageChatMemoryAdvisor = new MessageChatMemoryAdvisor(chatMemory);
            ChatClient chatClient = builder
                    .defaultAdvisors(messageChatMemoryAdvisor)
                    .build();

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            while(true){
                String user = br.readLine();
                if(user.equalsIgnoreCase("/quit") || user.equalsIgnoreCase("/bye") || user.equalsIgnoreCase("/exit")){
                    System.exit(0);
                } else {
                    chatClient.
                            prompt()
                            .advisors()
                            .user(user)
                            .stream().chatResponse()
                            .doOnEach(cr -> System.out.print(Optional.ofNullable(cr.get())
                                    .map(ChatResponse::getResult)
                                    .map(Generation::getOutput)
                                    .map(AssistantMessage::getContent)
                                    .orElse("")))
                            .blockLast();
                }
            }




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



        };
    }


}
