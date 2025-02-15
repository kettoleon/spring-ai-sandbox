package com.github.kettoleon.llm.sandbox.bytecoda;

import com.github.kettoleon.llm.sandbox.bytecoda.repo.*;
import com.github.kettoleon.llm.sandbox.common.configuration.AiEnvironment;
import com.github.kettoleon.llm.sandbox.common.prompt.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.helpers.MessageFormatter;
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
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;

import javax.sql.DataSource;
import java.io.*;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.substringAfterLast;

@Controller
public class ByteCodaController {

    @Autowired
    private AiEnvironment aiEnvironment;

    @Autowired
    private JavaClassRepository javaClassRepository;

    @Autowired
    private JavaMethodRepository javaMethodRepository;

    @Autowired
    private JavaFileRepository javaFileRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private PromptTemplate promptTemplateForAnalysis;
    private VectorStore vectorStore;

    @GetMapping(path = "/bytecoda", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody byteCoda(@RequestParam("q") String user) {
        return new StreamingResponseBody() {

            private PrintWriter out;

            @Override
            public void writeTo(OutputStream outputStream) throws IOException {
                out = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

                vectorStore = aiEnvironment.getDefaultVectorStore();
                ChatClient.Builder builder = aiEnvironment.getDefaultChatClientBuilder();
                promptTemplateForAnalysis = new PromptTemplate(builder.build());
                promptTemplateForAnalysis.setOut(out);

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

                List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(user)
                        .topK(20)
                        .filterExpression("projectId == '" + projectId + "'")
                        .build());

                String context = buildContext(results, projectId);

                out.println(context + "\n\n");

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
                        .doOnEach(cr -> out.print(Optional.ofNullable(cr.get())
                                .map(ChatResponse::getResult)
                                .map(Generation::getOutput)
                                .map(AssistantMessage::getText)
                                .orElse("")))
                        .blockLast();

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

                logToOut("Synchronizing vector store...");

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
                logToOut("Finished synchronizing vector store...");

            }

            private void ingestProject(String projectId, String projectName, Path projectRoot) {

                logToOut("Ingesting project {} from {}", projectName, projectRoot.toAbsolutePath());

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
                out.println("Project " + project.getId() + " structure: ");
                List<JavaFile> files = javaFileRepository.findAllByProject(project);
                for (JavaFile file : files) {
                    out.println("  - " + file.getPath());
                    for (JavaClass jc : javaClassRepository.findAllByFile(file)) {
                        out.println("    - " + jc.getQualifiedName());
                        for (JavaMethod jm : javaMethodRepository.findAllByJavaClass(jc)) {
                            out.println("      - " + jm.getSignature());
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

                logToOut("Analysing {} classes...", javaClassesToAnalyse.size());
                int c = 0;
                for (JavaClass jc : javaClassesToAnalyse) {
                    String functionality = analyseClass(jc);
                    jc.setFunctionality(functionality);
                    javaClassRepository.save(jc);
                    c++;
                    out.println("Analyzed " + c + "/" + javaClassesToAnalyse.size());
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
                logToOut("Analysing {} methods...", methodsToAnalyse.size());
                int c = 0;
                for (JavaMethod jm : methodsToAnalyse) {
                    String functionality = analyseMethod(jm);
                    jm.setFunctionality(functionality);
                    javaMethodRepository.save(jm);
                    c++;
                    out.println("Analyzed " + c + "/" + methodsToAnalyse.size());
                }


            }

            private void logToOut(String msg, Object... args) {
                out.println(MessageFormatter.arrayFormat(msg, args));
            }

            private String analyseClass(JavaClass jc) {
                Optional<FunctionalityAnswer> answer = Optional.empty();
                while (answer.isEmpty()) {
                    answer = promptTemplateForAnalysis.promptToBean(
                            "You are a system that analyses java classes to explain their functionality. Keep answers/explanations concise/short to a single line of text.",
                            buildUserPromptForClassAnalysis(jc, javaMethodRepository.findAllByJavaClass(jc)),
                            FunctionalityAnswer.class,
                            s -> lastResortAnswerParser(s)
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
                            s -> lastResortAnswerParser(s)
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
                        logToOut("Removed java class from database: {}", jc.getQualifiedName());
                    }
                }
                for (String cjc : javaClasses) {
                    if (javaClassRepository.findByFileAndQualifiedName(jf, cjc).isEmpty()) {
                        javaClassRepository.save(JavaClass.builder()
                                .file(jf)
                                .qualifiedName(cjc)
                                .build());
                        logToOut("Added java class to database: {}", cjc);
                    }
                }

                for (CtElement ctElement : model.getElements(el -> el instanceof CtClass<?>)) {
                    CtClass<?> ctClass = (CtClass<?>) ctElement;
                    checkForJavaClassChanges(javaClassRepository.findByFileAndQualifiedName(jf, ctClass.getQualifiedName()).orElseThrow(), ctClass);
                }

            }

            private void checkForJavaClassChanges(JavaClass javaClass, CtClass<?> modelJavaClass) {
                List<String> currentMethodSignatures = extractMethodSignatures(modelJavaClass);

                for (JavaMethod jm : javaMethodRepository.findAllByJavaClass(javaClass)) {
                    if (!currentMethodSignatures.contains(jm.getSignature())) {
                        javaMethodRepository.delete(jm);
                        //TODO also cascade delete the entries from de vector store!
                        logToOut("Removed java method from database: {}", jm.getSignature());
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
                        logToOut("Added java method to database: {}", signature);
                        javaClass.setFunctionality(null);
                        javaClassRepository.save(javaClass);
                    } else {
                        String methodBody = extractMethodBody(modelJavaClass, signature);
                        JavaMethod jm = jmo.get();
                        if (!methodBody.equals(jm.getCode())) {
                            jm.setCode(methodBody);
                            jm.setFunctionality(null);
                            javaMethodRepository.save(jm);
                            logToOut("Noted java method changes to database: {}", signature);
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

            private void checkForFileChanges(Project project, List<Path> javaFiles) {
                for (JavaFile jf : javaFileRepository.findAllByProject(project)) {
                    if (hasBeenRemoved(javaFiles, jf.getPath())) {
                        javaFileRepository.delete(jf);
                        //TODO also cascade delete the entries from de vector store!
                        logToOut("Removed java file from project {}: {}", project.getId(), jf.getPath());
                    }
                }
                for (Path currentJavaFile : javaFiles) {
                    if (javaFileRepository.findByProjectAndPath(project, currentJavaFile.toString()).isEmpty()) {
                        javaFileRepository.save(JavaFile.builder()
                                .project(project)
                                .path(currentJavaFile.toString())
                                .build());
                        logToOut("Added java file to project {}: {}", project.getId(), currentJavaFile);
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


        };
    }


}
