# Spring AI Sandbox
This project is a sandbox to create different proof of concept implementations about ideas
or things I want to try with large language models.

## Setup
In order to run the examples, you will need ollama installed locally with a model downloaded,
and change the `application.properties` with your own preferences as explained in [their docs](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html).

Alternatively, if you want to use your OpenAI/Azure/Mistral/Anthropic subscriptions, you will need
to add the dependency in the pom and tweak the `application.properties`. You can find information
about how to do that in the [Chat Models](https://docs.spring.io/spring-ai/reference/api/chatmodel.html) documentation.

Once you have this setup done, you can proceed to run the different examples.

## Examples

### Chat

<table>
  <tr>
    <th>Description</th>
    <td>It aims to be a barebones local replacement of ChatGPT</td>
  </tr>
  <tr>
    <th>Status</th>
    <td>Basic functionality is there, and it is usable, but it still needs some polish</td>
  </tr>
  <tr>
    <th>Running Instructions</th>
    <td>Run the main application and head to <a href="http://localhost:8080">http://localhost:8080</a></td>
  </tr>
</table>

### Helgar

<table>
  <tr>
    <th>Description</th>
    <td>My third attempt to replicate the famous <a href="https://arxiv.org/pdf/2304.03442">Generative Agents: Interactive Simulacra of Human Behavior</a> paper. 
        First time trying to do it with LLM function calling. This time I decided to start small with a single NPC character, Helgar, a hunter in the woods
    </td>
  </tr>
  <tr>
    <th>Status</th>
    <td>Looks promising, but the function calling with llama 3.2 is still unreliable, so I parked it for now</td>
  </tr>
  <tr>
    <th>Running Instructions</th>
    <td>Run the main application and observe what Helgar decides to do after waking up</td>
  </tr>
</table>

### Live Translate

<table>
  <tr>
    <th>Description</th>
    <td>I wondered if an LLM could be used to translate the chat of an international online video game</td>
  </tr>
  <tr>
    <th>Status</th>
    <td>Done. The proof of concept worked fine, If I want to continue I should start the proper project on its own repo</td>
  </tr>
  <tr>
    <th>Running Instructions</th>
    <td>Run the main application and observe the console output</td>
  </tr>
</table>

### Mira

<table>
  <tr>
    <th>Description</th>
    <td>Magic Inglish Real Academy. An attempt at making a translator from spanish/english to our own flavour of spanglish: "Magic English"</td>
  </tr>
  <tr>
    <th>Status</th>
    <td>Looks like LLMs (or at least the biggest model I can run locally - llama 3.2 8Bq8) are not good enough yet to get an idea of what Magic English is and make satisfactory translations</td>
  </tr>
  <tr>
    <th>Running Instructions</th>
    <td>Run the main application and observe the console output</td>
  </tr>
</table>

### Mixtenser

<table>
  <tr>
    <th>Description</th>
    <td>I once had a colleague who was very good at mixing sayings together and making up new ones. So I wanted to try see if LLMs can do the same</td>
  </tr>
  <tr>
    <th>Status</th>
    <td>Done. Seems like LLMs are amazing at doing that. In the example I even tried a chain of thought to make the LLM generate a few variations and self-evaluate to select the best</td>
  </tr>
  <tr>
    <th>Running Instructions</th>
    <td>Run the main application and observe the console output until it gives you a new saying</td>
  </tr>
</table>

### Pathfinder

<table>
  <tr>
    <th>Description</th>
    <td>After reading the books of the Bobiverse series, I was inspired to make a narrative web game where the LLM would serve as a "game master", and the player could chat to trigger actions and drive its own story</td>
  </tr>
  <tr>
    <th>Status</th>
    <td>After doing the first chapter and testing it a few times, I realised that despite the concept seems cool, it also gets old and boring really quickly. Need to rethink, maybe adding other aspects or using a different setting might help to keep it interesting</td>
  </tr>
  <tr>
    <th>Running Instructions</th>
    <td>Run the main application and head to <a href="http://localhost:8080">http://localhost:8080</a></td>
  </tr>
</table>