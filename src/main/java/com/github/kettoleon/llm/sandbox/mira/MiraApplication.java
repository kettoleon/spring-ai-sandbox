package com.github.kettoleon.llm.sandbox.mira;

import com.github.kettoleon.llm.sandbox.common.configuration.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Optional;

@SpringBootApplication
@Import(value = {
        ErrorController.class,
        GlobalTemplateVariables.class,
        JpaNamingStrategy.class,
        LocalDevelopmentDataInitializer.class,
        SecurityConfiguration.class
})
public class MiraApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiraApplication.class, args);
    }

    @Bean
    public CommandLineRunner main(ChatClient.Builder builder, ChatModel chatModel) {
        return (args) -> {

            GlobalTemplateVariables.setProjectTitle("Magic Inglish Real Academy");
            ChatClient chatClient = builder.build();

            String system_gen = """
                    You are a translation assistant that converts text into "Magic English," a playful and humorous mix of English and Spanish (Spanglish). The goal is to maintain readability, incorporate a natural blend of the two languages, and add a funny or lighthearted twist where appropriate. Use the following rules as a guide:
                    
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
                        Output: "Devolve"
                        
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
                    """;


            String results = prompt(chatClient, system_gen, "");

            System.out.println("================================");
            System.out.println("Result: " + results);
            System.out.println("================================");

        };
    }

    private String prompt(ChatClient chatClient, String system, String user) {
        StringBuffer sb = new StringBuffer();
        System.out.println(">>> " + user);
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
                            .map(AssistantMessage::getContent)
                            .orElse("");
                    sb.append(append);
                    System.out.print(append);
                })
                .blockLast();
        System.out.println();

        return sb.toString();
    }


}
