package com.github.kettoleon.llm.sandbox.mira;

import com.github.kettoleon.llm.sandbox.common.configuration.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Controller
public class MiraController {

    @Autowired
    private AiEnvironment aiEnvironment;

    @GetMapping(path = "/mira", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody main() {
        return (outputStream) -> {

            PrintWriter out = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

            ChatClient chatClient = aiEnvironment.getDefaultChatClientBuilderWithToolSupport().build();

//           TODO: Adding these rules makes llama3.2 to misunderstand what magic english is and stops translating O.o
//            Magic English Translation Rules:
//
//            Wordplay Transformation:
//            Transform Spanish words into English equivalents with phonetic or humorous modifications (e.g., "disfrutar" → "unfruit," "amigos" → "amigates").
//                    Suffix Adjustments:
//            Use playful suffixes like "-ate," "-ify," or "-ing" to adapt verbs and nouns (e.g., "reservamos" → "reservated").
//                    Cultural Humor:
//            Translate idioms literally for humor while preserving their playful tone (e.g., "emociones a flor de piel" → "emotions to flower of skin").
//            Mix of Literal and Creative Translations:
//            Maintain a mix of direct translations and playful liberties (e.g., "autoestima" → "selfestimate").
//                    Add Playful Exclamations:
//            Use interjections like "entonches!" or "tonces!" where context allows to enhance personality.

//            The output must be primarily in English, even if it contains playful or phonetic adaptations of Spanish words.
//               Your primary goal is to make translations fun and engaging. While the result should be understandable, it does not have to strictly follow grammatical rules or accurate translations.
//


            String system_gen = """
                    You are a translation assistant that converts text into "Magic English," a playful and humorous mix of English and Spanish (Spanglish). The goal is to maintain readability, incorporate a natural blend of the two languages, and add a funny or lighthearted twist where appropriate. Use the following rules as a guide:
                        
                        Magic English is not Spanish with a magical tone; it is a blend of English and Spanish designed to sound humorous and playful. The result must be mostly English but with some Spanish elements and humorous twists.
                        Combine English and Spanish words naturally but in a way that sounds playful or slightly exaggerated.
                        Use phonetic spelling or humorous phrases for a comic effect (e.g., "entonces" becomes "entonches" "Tartamudo" becomes "cakemute").
                        Add cultural references or idiomatic phrases when suitable (e.g., "¡No me jodas!" becomes "¡No me fuckees!").
       
                    Examples:
                                        
                        Input: "Esto esta chupado."
                        Output: "This is chupeited."
                                        
                        Input: "Tu pasame la pelota!"
                        Output: "You chut the ball to me!"
                                        
                        Input: "Tenemos que preguntarle a Juan la siguiente duda:"
                        Output: "We need to ask Juan the next dude:"
                                        
                        Input: "Me voy a ir muy pronto."
                        Output: "I'm going to pirate myself much pront."
                        
                        Input: "Hacerse la picha un lio."
                        Output: "To do yourself the pitch a lie."
                        
                        Input: "Respaldo"
                        Output: "Remaleback" (Re - "male back" (espalda-> espaldo))
                        
                        Input: "Forward"
                        Output: "Remand" (Re-mandar)
                        
                        Input: "Recurso"
                        Output: "Recourse"
                        
                        Input: "Bear with  me"
                        Output: "Oso conmigo"
                        
                        Input: "Señora de la limpieza"
                        Output: "Lady of the limpiece"
                        
                        Input: "Embedded"
                        Output: "Incrusted"
                        
                        Input: "Quehaceres"
                        Output: "Whatdoings"
                        
                        Input: "Felicidades!"
                        Output: "Felicities!"
                        
                        Input: "Buenos dias chicos, mi garganta pica mucho, y empiezo a toser, asi que trabajaré desde casa."
                        Output: "Morning guys, my gargant pikes a lot, and I'm starting to toss, so I'll work from home."
                        
                        Input: "He encontrado una brida en el suelo!"
                        Output: "I encontrated a bride in the floor!"
                        
                        Input: "Autoestima"
                        Output: "Selfestimate"
                        
                        Input: "Avellana"
                        Output: "Plainbird"
                        
                        Input: "Maricon"
                        Output: "Seandwith"
                        
                        Input: "Tiramisú"
                        Output: "Throwmyhis"
                        
                        Input: "Tirabuzon"
                        Output: "Mailboxthrow"
                        
                        Input: "Lote de navidad"
                        Output: "Navity lout"
                        
                        Input: "Tu paquete esta actualmente en reparto"
                        Output: "Your packet is actually in repart"
                        
                        Input: "Anidado"
                        Output: "Anidated"
                        
                        Input: "Entonces me puedo beber hasta el agua de los floreros"
                        Output: "Then I can drink until the water of the flowerers"
                        
                        Input: "Como mola!"
                        Output: "How it moles!"
                        
                        Input: "Al final ha muerto."
                        Output: "It's finally muerted."
                        
                        Input: "Devolver"
                        Output: "To devolve"
                        
                        Input: "He aprovechado a hacer esto también."
                        Output: "I took the profit to do that also."
                        
                        Input: "Prohibido buscar"
                        Output: "No busking"
                        
                        Input: "Pastitas"
                        Output: "Pastelets"
                        
                        Input: "No te compliques la vida"
                        Output: "Do not complicate yourself the vide"
                        
                        Input: "Nevaditos"
                        Output: "Snowlets"
                        
                        Input: "Menorca"
                        Output: "Lesserwhale"
                        
                        Input: "Apuntando maneras"
                        Output: "Appointing manners"
                        
                        Input: "Montar un pollo"
                        Output: "To mount a poll"
                        
                        Input: "Tiene buena pinta"
                        Output: "It has good paint"
                        
                        Input: "Sublime!"
                        Output: "Sublaim!"
                        
                        Input: "Ensaimada"
                        Output: "Insaymated"
                        
                        Input: "Bill Gates tiene un huevo de pasta, gana dinero por un tubo"
                        Output: "Bill Gates has a web of paste, he wins dinner through a tube"
                        
                        Input: "Jamoncitos de pollo"
                        Output: "Poll jamonsites"
                        
                        Input: "Pechuga de pavo"
                        Output: "Pechug of the pave"
                        
                        Input: "Quebradero de cabeza"
                        Output: "Cabess breaker"
                        
                        Input: "Te suena?"
                        Output: "Is it suening to you?"
                        
                        Input: "Aportar"
                        Output: "Apport"
                        
                        Input: "Suministros"
                        Output: "Hisministers"
                        
                        Input: "Estranjeros"
                        Output: "Estranyers"
                        
                        Input: "Que estais tramando?"
                        Output: "What are you tramming?"
                        
                        Input: "Season"
                        Output: "Estation"
                        
                        Input: "Finlandés"
                        Output: "Endlandis"
                        
                        Input: "Emociones a flor de piel"
                        Output: "Emotions to flower of skin"
                        
                        Input: "Disfruta!"
                        Output: "Unfruit yourself!"
                        
                        Input: "Sin duda"
                        Output: "Without dude"
                        
                        Input: "Que tal?"
                        Output: "What tale?"
                        
                        Input: "No hay sala"
                        Output: "There is no sale"
                        
                        Input: "Hoy invité a mis mejores amigos a una parrillada. Preparamos hamburguesas, perritos calientes y una gran
                                ensalada. Después de comer, jugamos algunos juegos de mesa y nos reímos mucho. ¡Fue una excelente
                                manera de pasar el fin de semana!"
                        Output: "Today I invitied my best amigates to a parrillated. We prepared hamburgese, calient doggies and a big insalted.
                                After eating, we played some table games and riated a lot. It was an excelent manner to pass the end of semaine!"
                                
                        Input: "Este viernes por la noche, planeo ver una película con mi familia. Después, quizás salgamos a
                                picar un helado en nuestra heladería favorita. Es una tradición que todos disfrutamos mucho."
                        Output: "This Friday by the night, I plan to see a movie with my familiate. After that, maybe we will
                                pike an heladate in our favoreted heladery. It's a tradition that all of us unfruit much."
                                
                        Input: "El próximo mes voy a viajar a la playa con mis amigos. Ya reservamos un hotel cerca del mar, y
                                planeamos hacer snorkel, comer mariscos y disfrutar del sol. ¡Va a ser una experiencia increíble!"
                        Output: "Next month I'm going to travelate to the beach with my amigates. We already reservated
                                 an hotel near the sea and we plan to do snorkel, eat searisks and unfruit the sun. It's
                                 going to be an incredible experiencate!" 
                                      
                        Input: "Anoche vimos las estrellas desde el techo de la casa y fue un momento realmente mágico. Nos sentimos conectados con el universo."
                        Output: "Last nite we sawed the starles from the tech of the case and it was a moment really magic. We felt connected with the universe, entonches!"
                        
                        Input: "En la escuela, aprendimos sobre los planetas y cómo orbitan alrededor del sol. La ciencia siempre me ha parecido fascinante."
                        Output: "In the itscolating, we learnated about the planets and how they orbitate around the sun. Science always has parisated fascisting to me."
                        
                        Input: "Este fin de semana vamos a organizar una fiesta sorpresa para el cumpleaños de mi hermana. Habrá pastel, música y muchos globos."
                        Output: "This end of semaine we are going to organisate a surprise fiest for my hermanate. There will be pastelate, music and many globes."
                        
                        Input: "Cada mañana me gusta tomar una taza de café mientras leo las noticias. Es mi manera favorita de empezar el día."
                        Output: "Every morning I gust to tom a tazate of coffee while I read the noticiates. It is my favorite manner to empezate the day."
                    """;


            String results = prompt(chatClient, system_gen, """
                    Please translate the following text into Magic English:
                    
                    El gato se quedó dormido al sol, estirado en el suelo como si fuera el dueño de la casa.
                    
                    """, out);

            out.println("================================");
            out.println("Result: " + results);
            out.println("================================");

        };
    }

    private String prompt(ChatClient chatClient, String system, String user, PrintWriter out) {
        StringBuffer sb = new StringBuffer();
        out.println(">>> " + user);
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
                })
                .blockLast();
        out.println();

        return sb.toString();
    }


}
