package com.dct.aws_ai_chatbot.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class ChatDtos {

    public record Message(
            @NotEmpty String role,     // "user" | "assistant"
            @NotEmpty String content
    ) {}

    public record ChatRequest(
            List<Message> messages,
            String system,             // optional system prompt
            Integer maxTokens,         // default 1024 if null
            Double temperature,        // default 0.7 if null
            Double topP                // optional
    ) {}

    public record ChatResponse(String text) {}
}