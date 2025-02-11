package com.github.kettoleon.llm.sandbox.search;

import com.github.kettoleon.llm.sandbox.common.configuration.*;
import com.github.kettoleon.llm.sandbox.common.prompt.PromptTemplate;
import com.github.kettoleon.llm.sandbox.search.brave.BraveClient;
import com.github.kettoleon.llm.sandbox.search.brave.WebSearchResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.swing.text.html.Option;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
@Import(value = {
        ErrorController.class,
        GlobalTemplateVariables.class,
        JpaNamingStrategy.class,
        LocalDevelopmentDataInitializer.class,
        SecurityConfiguration.class
})
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }

    @Autowired
    private BraveClient braveClient;

    @Bean
    public CommandLineRunner main(ChatClient.Builder builder) {
        return (args) -> {

            GlobalTemplateVariables.setProjectTitle("Chat with Search");

            InMemoryChatMemory chatMemory = new InMemoryChatMemory();
            MessageChatMemoryAdvisor messageChatMemoryAdvisor = new MessageChatMemoryAdvisor(chatMemory);
            ChatClient chatClient = builder
                    .defaultAdvisors(messageChatMemoryAdvisor)
                    .build();

            PromptTemplate searchPromptTemplate = new PromptTemplate(builder.build());
            searchPromptTemplate.setVerbose(true);

            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String user = br.readLine();
                if (user.equalsIgnoreCase("/quit") || user.equalsIgnoreCase("/bye") || user.equalsIgnoreCase("/exit")) {
                    System.exit(0);
                } else {

                    Optional<WebSearchQueries> webSearchQueries = searchPromptTemplate.promptToBean(
                            """
                                    You are a system that decides what web search engine queries to make to try to answer the user query the best way possible.
                                    Do not answer the user query, only provide a list of web search engine queries.
                                    Keep the list of queries to a minimum, if possible to a single one, only use multiple queries if the question is complex enough to require multiple topics of research.
                                    Always assume the user typed the query correctly, the user query is never misspelled.
                                    Never try to correct the user query, always assume it is correct and go with it.
                                    """,
                            """
                                    Provide a list of web search engine queries that we will perform in order to collect data to answer the user query.
                                    Assume the user query is correct, has not been mispelled, and use it as it is.
                                    User query:
                                                                        
                                                                        
                                    """ + user,
                            WebSearchQueries.class,
                            unformatted -> {
                                return searchPromptTemplate.promptToBean(
                                        """
                                                You provided a previous response in the wrong format. Please respond with the specified json format.
                                                """,
                                        """
                                                Here is the answer you gave without the proper format, please use it to return the answer in json format:
                                                                                                    
                                                                                                    
                                                """ + unformatted,
                                        WebSearchQueries.class
                                ).orElse(null);
                            }
                    );

                    if (webSearchQueries.isPresent()) {
                        StringBuffer sb = new StringBuffer();
                        sb.append("\n\n# User query\n");
                        sb.append("Following is the query the user made:\n\n");
                        sb.append(user);
                        sb.append("\n\n");
                        sb.append("# Web search results\n\n");
                        webSearchQueries.get().queries().forEach(q -> {
                            WebSearchResponse response = braveClient.search(q);
                            response.getWeb().getResults().forEach(searchResult -> {
                                sb.append("- [" + searchResult.getTitle() + "](" + searchResult.getUrl() + "): " + searchResult.getDescription() + "\n");
                            });

                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        });

                        Optional<WebScrapingRequest> wsr = searchPromptTemplate.promptToBean(
                                """
                                        You are a system that decides which search results to visit in order to gather information to answer the user query.
                                        You can set the "canAnswerAlready" flag to true, if you think that with the description of the search result there is enough information to answer the user query and no further web scraping is needed.
                                        Refrain from answering the user query, this will be done in another step of the process.
                                        Limit yourself to indicate if the user query can already be answered with the information provided, or a list of urls to scrape for more information.
                                        """,
                                sb.toString() + """
                                        \n\n# Current task
                                        With the information provided above, indicate the web pages we need to visit to collect more information if needed.
                                        Alternatively, you can set the "canAnswerAlready" flag to true, if the user query can be answered from the web search result descriptions alone.
                                        """,
                                WebScrapingRequest.class,
                                unformatted -> {
                                    return searchPromptTemplate.promptToBean(
                                            """
                                                    You provided a previous response in the wrong format. Please respond with the specified json format.
                                                    """,
                                            """
                                                    Here is the answer you gave without the proper format, please use it to return the answer in json format:
                                                                                                        
                                                                                                        
                                                    """ + unformatted,
                                            WebScrapingRequest.class
                                    ).orElse(null);
                                }
                        );

                        if (wsr.isPresent()) {
                            if (wsr.get().canAnswerAlready()) {

                                chatClient.
                                        prompt()
                                        .advisors()
                                        .system("""
                                                You are a system that answers the user query provided, with the help of web search results.
                                                You have, in a previous step, already deemed you have enough information in the web search results to answer the user query.
                                                """)
                                        .user(sb.toString())
                                        .stream().chatResponse()
                                        .doOnEach(cr -> System.out.print(Optional.ofNullable(cr.get())
                                                .map(ChatResponse::getResult)
                                                .map(Generation::getOutput)
                                                .map(AssistantMessage::getText)
                                                .orElse("")))
                                        .blockLast();

                            } else {

                                System.out.println("//TODO scrape the following urls: ");
                                wsr.get().urlsToScrape.forEach(url -> System.out.println("  - " + url));


                            }

                        }

                    }


                }
            }

        };
    }

    record WebSearchQueries(List<String> queries) {
    }

    record WebScrapingRequest(boolean canAnswerAlready, List<String> urlsToScrape) {
    }


}
