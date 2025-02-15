package com.github.kettoleon.llm.sandbox.mixtenser;

import com.github.kettoleon.llm.sandbox.common.configuration.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Controller
public class MixtenserController {

    @Autowired
    private AiEnvironment aiEnvironment;

    @GetMapping(path = "/mixtenser", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody main() {
        return (outputStream) -> {

            PrintWriter out = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            //1) Ask for a saying or idiom
            // 1.1) Check in the db it is a new one or not used for a long time
            //2) Ask to twist it a few times
            //3) Ask to review/score each of them (together)
            //4) Select the best one (That has not been used yet)

            ChatClient chatClient = aiEnvironment.getDefaultChatClientBuilder().build();

            String saying = prompt(chatClient, "You are a saying or idiom provider. You must provide only the saying, no introduction, notes, explanations or quotation.", "Give me a saying or an idiom in english", out);

            String system_gen = """
                    You are a creative AI specialized in generating humorous and twisted versions of popular sayings, proverbs, or idioms.
                    Your goal is to create new versions that are:
                        1) Witty and Surprising: Add a humorous or unexpected twist while retaining the core structure of the original saying.
                        2) Relatable: Incorporate modern contexts, absurd logic, or clever wordplay where appropriate.
                        3) Clear and Fun: The twisted saying should still make sense or evoke laughter, even if absurd. Avoid being too obscure or confusing.
                    Guidelines:
                        1) Identify the Core of the Saying: The original meaning or structure should still be somewhat recognizable.
                        2) Twist with Humor: Use wordplay, metaphors, or modern references to create something unexpected.
                        3) Surreal or Absurd is Welcome: Push boundaries with creative reinterpretations while ensuring a hint of the original meaning is detectable.
                        4) Modernize or Subvert: Include references to current technology, trends, or cultural quirks for added relatability.
                    Output format:
                        1) You must provide only the saying(s), no introduction, notes, explanations or quotation.
                    
                    The following were created by a spanish colleague and can be used as inspiration:
                    Quien se acuesta con chistorras, come zorras
                    Dios pario a la santa y la santa nos dio a dios
                    La creatividad es algo que esta en desuso
                    Salta como una almeja
                    Te haria de todo menos chupartela
                    Quien mucho disimula, aprieta poco
                    Quien mucho aprieta mucho se encuentra
                    La verdad es decepcionante
                    Tu haces unas preguntas de gato viudo
                    Los cojones son dificiles de dibujar
                    Perro labrador poco mordedor
                    Mas vale maña contenta que vasca feliz.
                    Pues te vas andando a California!!!
                    A las tres de la mañana?! Pero esto es la hora de las gallinas madrugadoras!
                    Si buscas a Jakes, encuentras a Valentino
                    Quien pilla, pilla, y quien no se lo cepilla
                    A ti te engañan como a una piedra
                    Engorda mas un bombón que trece osos comiendo jamón
                    Esto va mas lento que cuarenta viejas juntas
                    A comentarios indecentes, oidos necios.
                    Se pilla antes a un cojo que a un enfermo
                    Quien perdió, se escapó
                    Como el perro del hortelano, ni deja vivir ni mete mano
                    En el potesito pequeñito esta la buena confitura
                    La verdad no existe, es un estado inventado
                    Mejor inventar que ser inventado
                    La mentira es una fase del sueño
                    La vida es como la salsa carbonara
                    """;


            String results = prompt(chatClient, system_gen, "Please create ten twisted variations of the following saying or idiom, one in each line: " + saying, out);

            String system_eval = """
                    You are a sharp, witty evaluator with a preference for absurdity and sarcasm.
                    Your task is to evaluate variations of a given saying based on the following criteria, assigning scores and ranking them from best to worst. Your evaluations should favor creative twists that are absurd, sarcastic, or darkly humorous, while still considering other key qualities.
                    
                    Evaluation Criteria:
                    
                        Absurdity and Sarcasm (35%):
                            Does the variation use absurd, unexpected, or surreal ideas effectively?
                            Does it include sarcastic or ironic undertones that enhance its comedic value?
                    
                        Humor (25%):
                            Is the variation funny, clever, or surprising?
                            Does it evoke laughter, amusement, or delight?
                    
                        Creativity (20%):
                            Does the variation present a unique or fresh perspective?
                            Does it use wordplay, modern references, or unexpected twists effectively?
                    
                        Clarity, Impact, and Brevity (20%):
                            Is the variation concise while delivering its humor or message effectively?
                            Does it avoid unnecessary complexity or verbosity?
                            Is it easy to understand, and does it leave a strong impression?
                    
                    Output Structure:
                    
                         For each variation:
                    
                             Provide a brief analysis based on each criterion.
                             Assign a score from 1 to 10 for each criterion (Absurdity/Sarcasm, Humor, Creativity, Clarity/Brevity).
                             Calculate the final weighted score out of 10 based on the weight of each criterion.
                    
                         Finally, rank all variations from best to worst based on their final scores, and explain why the top-ranked variation stands out.
                    
                    At the end of the output, you must provide the winning variation inside <winner></winner> tags.
                    """;

            String output = prompt(chatClient, system_eval, "Please rate the following twisted sayings and pick the best:\n\n" + results, out);

            String winner = output.split("<winner>")[1].split("</winner>")[0];

            out.println("================================");
            out.println("Result: " + winner);
            out.println("================================");
            out.flush();

        };
    }

    private String prompt(ChatClient chatClient, String system, String user, PrintWriter out) {
        StringBuffer sb = new StringBuffer();
        out.println(">>> " + user);
        out.flush();
        ChatClient.ChatClientRequestSpec chatcc = chatClient.
                prompt()
                .advisors()
                .user(user);

        if (system != null) {
            chatcc = chatcc.system(system);
        }

        chatcc
                .stream().chatResponse()
                .doOnEach(cr -> {
                    String append = Optional.ofNullable(cr.get())
                            .map(ChatResponse::getResult)
                            .map(Generation::getOutput)
                            .map(AssistantMessage::getText)
                            .orElse("");
                    sb.append(append);
                    out.print(append);
                    out.flush();
                })
                .blockLast();
        out.println();
        out.flush();

        return sb.toString();
    }


}
