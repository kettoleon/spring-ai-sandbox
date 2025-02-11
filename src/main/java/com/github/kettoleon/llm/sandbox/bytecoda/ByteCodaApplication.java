package com.github.kettoleon.llm.sandbox.bytecoda;

import com.github.kettoleon.llm.sandbox.bytecoda.repo.*;
import com.github.kettoleon.llm.sandbox.common.configuration.*;
import com.github.kettoleon.llm.sandbox.common.prompt.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.*;

@SpringBootApplication
@Import(value = {
        ErrorController.class,
        GlobalTemplateVariables.class,
        JpaNamingStrategy.class,
        LocalDevelopmentDataInitializer.class,
        SecurityConfiguration.class
})
@Slf4j
public class ByteCodaApplication {

    private PromptTemplate promptTemplateForAnalysis;
    private VectorStore vectorStore;

    public static void main(String[] args) {
        SpringApplication.run(ByteCodaApplication.class, args);
    }

    @Bean
    public CommandLineRunner main(ChatClient.Builder builder, EmbeddingModel embeddingModel) {
        return (args) -> {
            GlobalTemplateVariables.setProjectTitle("ByteCoda");

            vectorStore = vectorStore(embeddingModel);
            promptTemplateForAnalysis = new PromptTemplate(builder.build());

            String projectId = "spring-ai-sandbox";

            //Step 1: Ingest/Preprocess

            ingestProject(projectId, "Spring AI Sandbox", Path.of("."));

            //Step 2: Vectorisation/Storage

            synchroniseVectorStore(projectId);

            //Step 3: Query Time Retrieval / LLM Processing

            //Step 4: UI/TPOC

            InMemoryChatMemory chatMemory = new InMemoryChatMemory();
            MessageChatMemoryAdvisor messageChatMemoryAdvisor = new MessageChatMemoryAdvisor(chatMemory);
            ChatClient chatClientForUi = builder
                    .defaultAdvisors(messageChatMemoryAdvisor)
                    .build();


            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String user = br.readLine();
                if (user.equalsIgnoreCase("/quit") || user.equalsIgnoreCase("/bye") || user.equalsIgnoreCase("/exit")) {
                    System.exit(0);
                } else {

                    List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                            .query(user)
                            .topK(20)
                            .filterExpression("projectId == '" + projectId + "'")
                            .build());

                    String context = buildContext(results, projectId);

                    System.out.println(context + "\n\n");

                    chatClientForUi.
                            prompt()
                            .advisors()
                            .system("""
                                    You are a helpful chatbot that answers questions about a java project.
                                    You will be given a context for Retrieval Augmented Generation with a subset of the files of the project.
                                    Try to answer the user question the best you can, and if you see you don't have enough context to answer the question, humbly report that to the user.
                                     
                                    """ + context)
                            .user(user)
                            .stream().chatResponse()
                            .doOnEach(cr -> System.out.print(Optional.ofNullable(cr.get())
                                    .map(ChatResponse::getResult)
                                    .map(Generation::getOutput)
                                    .map(AssistantMessage::getText)
                                    .orElse("")))
                            .blockLast();
                }
            }

        };
    }

    private String buildContext(List<Document> results, String projectId) {
        Set<String> interestingJavaFiles = new HashSet<>();
        Set<String> interestingJavaClasses = new HashSet<>();
        Set<String> interestingJavaMethods = new HashSet<>();

        for (Document d : results) {
            interestingJavaFiles.add((String) d.getMetadata().get("fileId"));
            interestingJavaClasses.add((String) d.getMetadata().get("classId"));
            if (d.getMetadata().containsKey("methodId")) {
                interestingJavaMethods.add((String) d.getMetadata().get("methodId"));
            }
        }

        StringBuffer sb = new StringBuffer();
        sb.append("Here are details of the project files related to the user query:\n\n");
        List<JavaFile> files = javaFileRepository.findAllByProject(projectRepository.findById(projectId).orElseThrow());
        for (JavaFile file : files) {
            if (interestingJavaFiles.contains(file.getId())) {
                sb.append("File: " + file.getPath() + "\n");
                for (JavaClass jc : javaClassRepository.findAllByFile(file)) {
                    if (interestingJavaClasses.contains(jc.getId())) {
                        sb.append("  - Java Class: " + jc.getQualifiedName() + "\n");
                        sb.append("    - " + jc.getFunctionality() + "\n");
                        sb.append("    - Class Methods:\n");
                        for (JavaMethod jm : javaMethodRepository.findAllByJavaClass(jc)) {
                            sb.append("      - Method: " + jm.getSignature() + "\n");
                            sb.append("        - " + jm.getFunctionality() + "\n");
                            if (interestingJavaMethods.contains(jm.getId())) {
                                sb.append("        - Method code:\n\n");
                                sb.append("```java\n");
                                sb.append(jm.getCode());
                                sb.append("```\n\n");
                            }
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private void synchroniseVectorStore(String projectId) {

        log.info("Synchronizing vector store...");

        //TODO get all Ids that should be there, add the ones that are not
        //TODO get all Ids that should be there, remove the ones that are there and should not
        // But for now, clear all entries and populate them again (It is quite fast ^.^")

        vectorStore.delete("projectId == '" + projectId + "'");

        List<JavaFile> files = javaFileRepository.findAllByProject(projectRepository.findById(projectId).get());
        for (JavaFile file : files) {
            for (JavaClass jc : javaClassRepository.findAllByFile(file)) {
                for (JavaMethod jm : javaMethodRepository.findAllByJavaClass(jc)) {
                    vectorStore.add(Arrays.asList(
                            new Document(jm.getCode(), Map.of("projectId", projectId, "methodId", jm.getId(), "classId", jc.getId(), "fileId", file.getId())),
                            new Document(jm.getFunctionality(), Map.of("projectId", projectId, "methodId", jm.getId(), "classId", jc.getId(), "fileId", file.getId()))
                    ));
                }
                vectorStore.add(Arrays.asList(
                        new Document(jc.getFunctionality(), Map.of("projectId", projectId, "classId", jc.getId(), "fileId", file.getId()))
                ));
            }
        }
        log.info("Finished synchronizing vector store...");

    }

    private void ingestProject(String projectId, String projectName, Path projectRoot) {

        log.info("Ingesting project {} from {}", projectName, projectRoot.toAbsolutePath());

        //TODO an interesting idea for the future: Make a call hierarchy graph first, and start analising the node methods
        //TODO from the bottom-up. This way we can provide insights about the dependencies when analising a given method.

        Project project = retrieveOrCreateProject(projectId, projectName, projectRoot);

        try {
            List<Path> javaFiles = Files.walk(projectRoot).filter(p -> p.getFileName().toString().endsWith(".java")).collect(Collectors.toList());

            checkForProjectChangesAndUpdateDatabase(project, javaFiles);

//            printProjectStructure(project);

            analyseMissingMethods(project);
            analyseMissingClasses(project);


        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void printProjectStructure(Project project) {
        System.out.println("Project " + project.getId() + " structure: ");
        List<JavaFile> files = javaFileRepository.findAllByProject(project);
        for (JavaFile file : files) {
            System.out.println("  - " + file.getPath());
            for (JavaClass jc : javaClassRepository.findAllByFile(file)) {
                System.out.println("    - " + jc.getQualifiedName());
                for (JavaMethod jm : javaMethodRepository.findAllByJavaClass(jc)) {
                    System.out.println("      - " + jm.getSignature());
                }
            }
        }
    }

    private void analyseMissingClasses(Project project) {
        List<JavaFile> files = javaFileRepository.findAllByProject(project);
        List<JavaClass> javaClassesToAnalyse = new ArrayList<>();
        for (JavaFile file : files) {
            for (JavaClass jc : javaClassRepository.findAllByFile(file)) {
                if (jc.getFunctionality() == null) {
                    javaClassesToAnalyse.add(jc);
                }
            }
        }

        log.info("Analysing {} classes...", javaClassesToAnalyse.size());
        int c = 0;
        for (JavaClass jc : javaClassesToAnalyse) {
            String functionality = analyseClass(jc);
            jc.setFunctionality(functionality);
            javaClassRepository.save(jc);
            c++;
            System.out.println("Analyzed " + c + "/" + javaClassesToAnalyse.size());
        }

    }

    private void analyseMissingMethods(Project project) {
        List<JavaFile> files = javaFileRepository.findAllByProject(project);
        List<JavaMethod> methodsToAnalyse = new ArrayList<>();
        for (JavaFile file : files) {
            for (JavaClass jc : javaClassRepository.findAllByFile(file)) {
                for (JavaMethod jm : javaMethodRepository.findAllByJavaClass(jc)) {
                    if (jm.getFunctionality() == null) {
                        methodsToAnalyse.add(jm);
                    }
                }
            }
        }
        log.info("Analysing {} methods...", methodsToAnalyse.size());
        int c = 0;
        for (JavaMethod jm : methodsToAnalyse) {
            String functionality = analyseMethod(jm);
            jm.setFunctionality(functionality);
            javaMethodRepository.save(jm);
            c++;
            System.out.println("Analyzed " + c + "/" + methodsToAnalyse.size());
        }


    }

    private String analyseClass(JavaClass jc) {
        Optional<FunctionalityAnswer> answer = Optional.empty();
        while (answer.isEmpty()) {
            answer = promptTemplateForAnalysis.promptToBean(
                    "You are a system that analyses java classes to explain their functionality. Keep answers/explanations concise/short to a single line of text.",
                    buildUserPromptForClassAnalysis(jc, javaMethodRepository.findAllByJavaClass(jc)),
                    FunctionalityAnswer.class,
                    ByteCodaApplication::lastResortAnswerParser
            );
        }
        return answer.get().functionality;
    }

    private String analyseMethod(JavaMethod jm) {
        Optional<FunctionalityAnswer> answer = Optional.empty();
        while (answer.isEmpty()) {
            answer = promptTemplateForAnalysis.promptToBean(
                    "You are a system that analyses java methods to explain their functionality. Keep answers/explanations concise/short to a single line of text.",
                    buildUserPromptForMethodAnalysis(jm.getJavaClass().getQualifiedName(), javaMethodRepository.findAllByJavaClass(jm.getJavaClass()), jm),
                    FunctionalityAnswer.class,
                    ByteCodaApplication::lastResortAnswerParser
                    );
        }
        return answer.get().functionality;
    }

    private static FunctionalityAnswer lastResortAnswerParser(String s) {
        if (s.contains("**Answer:**")) {
            s = substringAfterLast(s, "**Answer:**");
        }
        s = s.trim();
        if (s.split("\\r?\\n").length == 1) {
            return new FunctionalityAnswer(s);
        }
        return null;
    }

    private void checkForProjectChangesAndUpdateDatabase(Project project, List<Path> javaFiles) {
        checkForFileChanges(project, javaFiles);

        for (JavaFile jf : javaFileRepository.findAllByProject(project)) {
            checkJavaFileChanges(jf);
        }
    }

    @Autowired
    private JavaClassRepository javaClassRepository;

    private void checkJavaFileChanges(JavaFile jf) {

        // Initialize the Spoon Launcher.
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setCommentEnabled(false);
        // Set the input resource directory or file.
        launcher.addInputResource(jf.getPath());
        launcher.buildModel();

        // Get the model
        CtModel model = launcher.getModel();

        // Iterate over all java classes in the file
        List<String> javaClasses = new ArrayList<>();
        for (CtElement ctElement : model.getElements(el -> el instanceof CtClass<?>)) {
            CtClass<?> ctClass = (CtClass<?>) ctElement;
            javaClasses.add(ctClass.getQualifiedName());
        }

        for (JavaClass jc : javaClassRepository.findAllByFile(jf)) {
            if (!javaClasses.contains(jc.getQualifiedName())) {
                javaClassRepository.delete(jc);
                //TODO also cascade delete the entries from de vector store!
                log.info("Removed java class from database: {}", jc.getQualifiedName());
            }
        }
        for (String cjc : javaClasses) {
            if (javaClassRepository.findByFileAndQualifiedName(jf, cjc).isEmpty()) {
                javaClassRepository.save(JavaClass.builder()
                        .file(jf)
                        .qualifiedName(cjc)
                        .build());
                log.info("Added java class to database: {}", cjc);
            }
        }

        for (CtElement ctElement : model.getElements(el -> el instanceof CtClass<?>)) {
            CtClass<?> ctClass = (CtClass<?>) ctElement;
            checkForJavaClassChanges(javaClassRepository.findByFileAndQualifiedName(jf, ctClass.getQualifiedName()).orElseThrow(), ctClass);
        }

    }

    @Autowired
    private JavaMethodRepository javaMethodRepository;

    private void checkForJavaClassChanges(JavaClass javaClass, CtClass<?> modelJavaClass) {
        List<String> currentMethodSignatures = extractMethodSignatures(modelJavaClass);

        for (JavaMethod jm : javaMethodRepository.findAllByJavaClass(javaClass)) {
            if (!currentMethodSignatures.contains(jm.getSignature())) {
                javaMethodRepository.delete(jm);
                //TODO also cascade delete the entries from de vector store!
                log.info("Removed java method from database: {}", jm.getSignature());
                javaClass.setFunctionality(null);
                javaClassRepository.save(javaClass);
            }
        }
        for (String signature : currentMethodSignatures) {
            Optional<JavaMethod> jmo = javaMethodRepository.findByJavaClassAndSignature(javaClass, signature);
            if (jmo.isEmpty()) {
                javaMethodRepository.save(JavaMethod.builder()
                        .javaClass(javaClass)
                        .signature(signature)
                        .code(extractMethodBody(modelJavaClass, signature))
                        .build());
                log.info("Added java method to database: {}", signature);
                javaClass.setFunctionality(null);
                javaClassRepository.save(javaClass);
            } else {
                String methodBody = extractMethodBody(modelJavaClass, signature);
                JavaMethod jm = jmo.get();
                if (!methodBody.equals(jm.getCode())) {
                    jm.setCode(methodBody);
                    jm.setFunctionality(null);
                    javaMethodRepository.save(jm);
                    log.info("Noted java method changes to database: {}", signature);
                    javaClass.setFunctionality(null);
                    javaClassRepository.save(javaClass);
                }
            }
        }

    }

    private String extractMethodBody(CtClass<?> modelJavaClass, String signature) {
        for (CtMethod<?> method : modelJavaClass.getMethods()) {
            if (signature.equals(getMethodSignature(method))) {
                return method.toString();
            }
        }
        throw new RuntimeException();
    }

    private List<String> extractMethodSignatures(CtClass<?> modelJavaClass) {
        ArrayList<String> methodSignatures = new ArrayList<>();
        for (CtMethod<?> method : modelJavaClass.getMethods()) {
            methodSignatures.add(getMethodSignature(method));
        }
        return methodSignatures;
    }

    @Autowired
    private JavaFileRepository javaFileRepository;

    private void checkForFileChanges(Project project, List<Path> javaFiles) {
        for (JavaFile jf : javaFileRepository.findAllByProject(project)) {
            if (hasBeenRemoved(javaFiles, jf.getPath())) {
                javaFileRepository.delete(jf);
                //TODO also cascade delete the entries from de vector store!
                log.info("Removed java file from project {}: {}", project.getId(), jf.getPath());
            }
        }
        for (Path currentJavaFile : javaFiles) {
            if (javaFileRepository.findByProjectAndPath(project, currentJavaFile.toString()).isEmpty()) {
                javaFileRepository.save(JavaFile.builder()
                        .project(project)
                        .path(currentJavaFile.toString())
                        .build());
                log.info("Added java file to project {}: {}", project.getId(), currentJavaFile);
            }
        }

    }

    private boolean hasBeenRemoved(List<Path> javaFiles, String path) {
        for (Path jf : javaFiles) {
            if (jf.toString().equals(path)) {
                return false;
            }
        }
        return true;
    }

    @Autowired
    private ProjectRepository projectRepository;

    private Project retrieveOrCreateProject(String projectId, String projectName, Path projectRoot) {
        Optional<Project> retrieved = projectRepository.findById(projectId);
        if (retrieved.isEmpty()) {
            Project prj = Project.builder()
                    .id(projectId)
                    .name(projectName)
                    .path(projectRoot.toAbsolutePath().toString())
                    .build();
            prj = projectRepository.save(prj);
            return prj;
        }
        return retrieved.get();
    }

    public record FunctionalityAnswer(String functionality) {
    }

    private String getMethodSignature(CtMethod<?> method) {
        String methodId = removeAnnotations(method.toString(), method.getAnnotations());
        methodId = StringUtils.substringBefore(methodId, "{").trim();
        return methodId;
    }

    private String buildUserPromptForClassAnalysis(JavaClass javaClass, List<JavaMethod> classMethods) {
        StringBuffer sb = new StringBuffer();
        sb.append("Class: " + javaClass.getQualifiedName() + "\n");
        sb.append("Methods:\n");
        for (JavaMethod cmethod : classMethods) {
            sb.append("  - " + cmethod.getSignature() + ": " + cmethod.getFunctionality() + "\n");
        }
        sb.append("Please analyse the class with the provided description of its methods and very briefly explain its functionality.\n\n");

        return sb.toString();
    }

    private String buildUserPromptForMethodAnalysis(String className, List<JavaMethod> classMethods, JavaMethod methodToAnalyse) {
        StringBuffer sb = new StringBuffer();
        sb.append("Class: " + className + "\n");
        sb.append("Other methods:\n");
        for (JavaMethod cmethod : classMethods) {
            if (!cmethod.equals(methodToAnalyse)) {
                sb.append("  - " + cmethod.getSignature() + "\n");
            }
        }
        sb.append("Please analyse the following method of this class and very briefly explain its functionality:\n\n");

        sb.append(methodToAnalyse.getCode());

        return sb.toString();
    }

    private String removeAnnotations(String methodId, List<CtAnnotation<? extends Annotation>> annotations) {
        for (CtAnnotation annotation : annotations) {
            methodId = methodId.replace(annotation.toString(), "");
        }
        return methodId.trim();
    }

    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(pgJdbcTemplate(), embeddingModel)
                .dimensions(768)                    // Optional: defaults to model dimensions or 1536
//                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
//                .indexType(HNSW)                     // Optional: defaults to HNSW
//                .initializeSchema(true)              // Optional: defaults to false
//                .schemaName("public")                // Optional: defaults to "public"
//                .vectorTableName("vector_store")     // Optional: defaults to "vector_store"
//                .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                .build();
    }

    private JdbcTemplate pgJdbcTemplate() {
        return new JdbcTemplate(pgDataSource());
    }

    private DataSource pgDataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:postgresql://192.168.1.5:5432/postgres")
                .username("postgres")
                .password("postgres")
                .build();
    }

}
