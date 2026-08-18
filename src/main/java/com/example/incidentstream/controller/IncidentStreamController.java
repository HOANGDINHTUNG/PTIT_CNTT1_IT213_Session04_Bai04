package com.example.incidentstream.controller;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/incident")
public class IncidentStreamController {

    private final ChatModel chatModel;

    public IncidentStreamController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamIncident(
            @RequestParam("rawMessage") String rawMessage,
            @RequestParam(value = "temp", defaultValue = "0.5") Double temp,
            @RequestParam(value = "maxTokens", defaultValue = "1000") Integer maxTokens) {
        
        // Build dynamic chat options per request
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withTemperature(temp.floatValue())
                .withMaxTokens(maxTokens)
                .build();

        // Create prompt combining the raw message and specific options
        Prompt prompt = new Prompt(rawMessage, options);

        // Stream the response and map to just the string content
        return chatModel.stream(prompt)
                .map(chatResponse -> {
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null && chatResponse.getResult().getOutput().getContent() != null) {
                        return chatResponse.getResult().getOutput().getContent();
                    }
                    return "";
                })
                .filter(content -> !content.isEmpty());
    }
}
