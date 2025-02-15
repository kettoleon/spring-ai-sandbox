package com.github.kettoleon.llm.sandbox.common.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class AiEnvironment {

    @Autowired
    private Environment environment;

    private VectorStore defaultVectorStore;

    private EmbeddingModel defaultEmbeddingModel;

    private OllamaApi defaultOllamaApi;

    private OllamaChatModel defaultOllamaChatModel;
    private OllamaChatModel defaultOllamaChatModelWithToolSupport;

    @PostConstruct
    public void init() {
        defaultOllamaApi = createDefaultOllamaApi();
        defaultOllamaChatModel = createDefaultOllamaChatModel();
        defaultOllamaChatModelWithToolSupport = createDefaultOllamaChatModelWithToolSupport();
        defaultEmbeddingModel = createDefaultEmbeddingModel();
        defaultVectorStore = createDefaultVectorStore();
    }

    private OllamaChatModel createDefaultOllamaChatModelWithToolSupport() {
        //TODO environment properties
        return OllamaChatModel.builder()
                .ollamaApi(getDefaultOllamaApi())
                .defaultOptions(OllamaOptions.builder()
                        .model("llama3.1:8b-instruct-q8_0")
                        .build())
                .build();
    }

    private OllamaChatModel createDefaultOllamaChatModel() {
        //TODO environment properties
        return OllamaChatModel.builder()
                .ollamaApi(getDefaultOllamaApi())
                .defaultOptions(OllamaOptions.builder()
                        .model("deepseek-r1:14b")
                        .build())
                .build();
    }

    private OllamaApi createDefaultOllamaApi() {
        return new OllamaApi(); //Localhost
    }

    private EmbeddingModel createDefaultEmbeddingModel() {
        //TODO environment properties
        return OllamaEmbeddingModel.builder()
                .ollamaApi(getDefaultOllamaApi())
                .defaultOptions(OllamaOptions.builder()
                        .model("nomic-embed-text")
                        .build())
                .build();
    }

    private VectorStore createDefaultVectorStore() {
        //TODO environment properties
        return vectorStore(getDefaultEmbeddingModel(), dataSource(
                "jdbc:postgresql://192.168.1.5:5432/postgres",
                "postgres",
                "postgres"
        ));
    }

    private VectorStore vectorStore(EmbeddingModel embeddingModel, DataSource dataSource) {
        return PgVectorStore.builder(jdbcTemplate(dataSource), embeddingModel)
                .dimensions(768)                    // Optional: defaults to model dimensions or 1536
//                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
//                .indexType(HNSW)                     // Optional: defaults to HNSW
//                .initializeSchema(true)              // Optional: defaults to false
//                .schemaName("public")                // Optional: defaults to "public"
//                .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
//                .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                .build();
    }

    private JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private DataSource dataSource(String url, String username, String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }


    public VectorStore getDefaultVectorStore() {
        return defaultVectorStore;
    }

    public EmbeddingModel getDefaultEmbeddingModel() {
        return defaultEmbeddingModel;
    }

    public OllamaApi getDefaultOllamaApi() {
        return defaultOllamaApi;
    }

    public OllamaChatModel getDefaultOllamaChatModel() {
        return defaultOllamaChatModel;
    }

    public OllamaChatModel getDefaultOllamaChatModelWithToolSupport() {
        return defaultOllamaChatModelWithToolSupport;
    }

    public ChatClient.Builder getDefaultChatClientBuilder(){
        return ChatClient.builder(getDefaultOllamaChatModel());
    }

    public ChatClient.Builder getDefaultChatClientBuilderWithToolSupport() {
        return ChatClient.builder(getDefaultOllamaChatModelWithToolSupport());
    }
}
