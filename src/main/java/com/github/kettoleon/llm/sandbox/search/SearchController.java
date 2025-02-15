package com.github.kettoleon.llm.sandbox.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.kettoleon.llm.sandbox.common.configuration.AiEnvironment;
import com.github.kettoleon.llm.sandbox.common.prompt.PromptTemplate;
import com.github.kettoleon.llm.sandbox.search.brave.BraveClient;
import com.github.kettoleon.llm.sandbox.search.brave.WebSearchResponse;
import com.github.kettoleon.llm.sandbox.search.gemini.GeminiClient;
import com.github.kettoleon.llm.sandbox.search.gemini.model.response.GenerateContentResponse;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Controller
public class SearchController {

    public static void main(String[] args) {
        SpringApplication.run(SearchController.class, args);
    }

    @Autowired
    private BraveClient braveClient;

    @Autowired
    private GeminiClient geminiClient;

    @Autowired
    private AiEnvironment aiEnvironment;

    @GetMapping(path = "/search", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody main(@RequestParam("q") String user) {
        return new StreamingResponseBody() {

            private PrintWriter out;

            @Override
            public void writeTo(OutputStream outputStream) throws IOException {

                out = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

                InMemoryChatMemory chatMemory = new InMemoryChatMemory();
                MessageChatMemoryAdvisor messageChatMemoryAdvisor = new MessageChatMemoryAdvisor(chatMemory);
                ChatClient.Builder builder = aiEnvironment.getDefaultChatClientBuilder();
                ChatClient chatClient = builder
                        .defaultAdvisors(messageChatMemoryAdvisor)
                        .build();

                PromptTemplate searchPromptTemplate = new PromptTemplate(builder.build());
                searchPromptTemplate.setVerbose(true);
                searchPromptTemplate.setOut(out);

//                    localApproach(searchPromptTemplate, user, chatClient);

                geminiApproach(user, chatClient);


            }

            private void localApproach(PromptTemplate searchPromptTemplate, String user, ChatClient chatClient) {
                Optional<WebSearchQueries> webSearchQueries = searchPromptTemplate.promptToBean(
                        """
                                You are a system that decides what web search engine queries to make to try to answer the user query the best way possible.
                                Do not answer the user query, only provide a list of web search engine queries.
                                Keep the list of queries to a minimum, if possible to a single one, only use multiple queries if the question is complex enough to require multiple topics of research.
                                Always assume the user typed the query correctly, the user query is never misspelled.
                                Never try to correct the user query, always assume it is correct and go with it.
                                You can also provide a list of direct web urls to visit if you can come up with them without the need for search.
                                """,
                        """
                                Provide a list of web search engine queries that we will perform in order to collect data to answer the user query.
                                You can additionally provide urls that can be visited directly without the need for search.
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

                    localApproachWithBrave(user, webSearchQueries, searchPromptTemplate, chatClient);

                }
            }

            private void geminiApproach(String user, ChatClient chatClient) {

                GenerateContentResponse response = geminiClient.generateContentWithGrounding(
                        """
                                # User Request:
                                                        
                                """
                                + user +
                                """
                                                                                        
                                        # Current task:
                                        You must find information on the internet related to the user request.
                                        Do not try to answer the user request, limit yourself to find and synthesise information needed to answer the user request.
                                        Another system will formulate the final answer based on the information you provide.
                                        """
                );


                try {
                    new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(out, response);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }


            }

            private void localApproachWithBrave(String user, Optional<WebSearchQueries> webSearchQueries, PromptTemplate searchPromptTemplate, ChatClient chatClient) {
                StringBuffer sb = new StringBuffer();
                sb.append("\n\n# User query\n");
                sb.append("Following is the query the user made:\n\n");
                sb.append(user);
                sb.append("\n\n");
                sb.append("# Web search results\n\n");
                Optional.ofNullable(webSearchQueries.get().directWebUrls()).orElse(Collections.emptyList()).forEach(url -> {
                    sb.append("- [" + url + "](" + url + ")");
                });
                Optional.ofNullable(webSearchQueries.get().queries()).orElse(Collections.emptyList()).forEach(q -> {
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
                    if (wsr.get().canAnswerAlready() && (wsr.get().urlsToScrape() == null || wsr.get().urlsToScrape().isEmpty())) {

                        chatClient.
                                prompt()
                                .advisors()
                                .system("""
                                You are a system that answers the user query provided, with the help of web search results.
                                You have, in a previous step, already deemed you have enough information in the web search results to answer the user query.
                                """)
                                .user(sb.toString())
                                .stream().chatResponse()
                                .doOnEach(cr -> out.print(Optional.ofNullable(cr.get())
                                        .map(ChatResponse::getResult)
                                        .map(Generation::getOutput)
                                        .map(AssistantMessage::getText)
                                        .orElse("")))
                                .blockLast();

                    } else {

                        List<String> scrapedInfo = new ArrayList<>();
                        List<String> urlsToScrape = Optional.ofNullable(wsr.get().urlsToScrape).orElse(Collections.emptyList());
                        if (!urlsToScrape.isEmpty()) {
                            out.println("//TODO scrape the following urls: ");

                            BrowserContext context = Playwright.create().firefox()
                                    .launchPersistentContext(Path.of("./firefox-context"), new BrowserType.LaunchPersistentContextOptions()
                                            .setArgs(List.of("--start-maximized"))
                                            .setHeadless(false)
                                            .setSlowMo(50)
                                            .setViewportSize(1900, 1000)
                                    );
                            urlsToScrape.forEach(url -> {
                                out.println("  - " + url);

                                Page page = context.newPage();
                                page.navigate(url);
                                page.waitForLoadState(LoadState.NETWORKIDLE);
                                String markdown = FlexmarkHtmlConverter.builder().build().convert(page.innerHTML("body"));

                                StringBuffer sb2 = new StringBuffer();
                                sb2.append("\n\n# User query\n");
                                sb2.append("Following is the query the user made:\n\n");
                                sb2.append(user);
                                sb2.append("\n\n");
                                sb2.append("\n\n# Web page visited\n");
                                sb2.append("- " + url);
                                sb2.append("\n\n# Web page contents\n");
                                sb2.append(markdown);
                                sb2.append("\n\n# Current task\n");
                                sb2.append("With the information provided above, extract the information from the page contents that is useful towards answering the user query.\n");
                                sb2.append("If not enough information is collected, or the information is incomplete, you can also provide a list of links to follow to analyse them.\n");

                                Optional<WebScrapingResult> webScrapingResult = searchPromptTemplate.promptToBean(
                                        """
                                                You are navigating the web to find enough information to answer the user query.
                                                Extract the useful snippets of information from the page, and decide if there are any links worth visiting to continue our research.
                                                Focus on retrieving information related to the user's query, disregard unrelated information and avoid going into rabbit holes.
                                                When providing the link or button names to click, give the name of the link, not the url. E.g: [Link name](url) -> Link name.
                                                """,
                                        sb2.toString(),
                                        WebScrapingResult.class,
                                        unformatted -> {
                                            return searchPromptTemplate.promptToBean(
                                                    """
                                                            You provided a previous response in the wrong format. Please respond with the specified json format.
                                                            """,
                                                    """
                                                            Here is the answer you gave without the proper format, please use it to return the answer in json format:
                                                                                                                
                                                                                                                
                                                            """ + unformatted,
                                                    WebScrapingResult.class
                                            ).orElse(null);
                                        }
                                );

                                if (webScrapingResult.isPresent()) {
                                    scrapedInfo.addAll(Optional.ofNullable(webScrapingResult.get().usefulInformation()).orElse(Collections.emptyList()));

                                    out.println("===== Info learned from page ======");
                                    Optional.ofNullable(webScrapingResult.get().usefulInformation()).orElse(Collections.emptyList()).forEach(s -> out.println("- " + s));

                                    out.println("===== Next pages to navigate to [TODO] ======");
                                    Optional.ofNullable(webScrapingResult.get().linkOrButtonNamesToClick()).orElse(Collections.emptyList()).forEach(s -> out.println("- " + s));
                                }
                                page.close();
                            });
                            context.close();
                        }

                        StringBuffer sb3 = new StringBuffer();
                        sb3.append("\n\n# User query\n");
                        sb3.append("Following is the query the user made:\n\n");
                        sb3.append(user);
                        sb3.append("\n\n");
                        sb3.append("\n\n# Information extracted from the web\n");
                        scrapedInfo.forEach(s -> sb3.append("- " + s + "\n"));

                        chatClient.
                                prompt()
                                .advisors()
                                .system("""
                                You are a system that answers the user query provided, with the help of snippets of information retrieved through web search results.
                                You have, in a previous step, already deemed you have enough information in the web search results to answer the user query.
                                """)
                                .user(sb3.toString())
                                .stream().chatResponse()
                                .doOnEach(cr -> out.print(Optional.ofNullable(cr.get())
                                        .map(ChatResponse::getResult)
                                        .map(Generation::getOutput)
                                        .map(AssistantMessage::getText)
                                        .orElse("")))
                                .blockLast();

                    }

                }
            }


        };
    }



    record WebScrapingResult(List<String> usefulInformation, List<String> linkOrButtonNamesToClick) {

    }

    record WebSearchQueries(List<String> queries, List<String> directWebUrls) {
    }

    record WebScrapingRequest(boolean canAnswerAlready, List<String> urlsToScrape) {
    }


}
