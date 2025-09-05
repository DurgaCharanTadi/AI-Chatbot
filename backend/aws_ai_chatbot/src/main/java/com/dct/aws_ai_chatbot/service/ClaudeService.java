package com.dct.aws_ai_chatbot.service;

import com.dct.aws_ai_chatbot.dto.ChatDtos.ChatRequest;
import com.dct.aws_ai_chatbot.dto.ChatDtos.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;


import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@Service
public class ClaudeService {

    private final BedrockRuntimeClient sync;
    private final BedrockRuntimeAsyncClient async;

    @Value("${app.bedrock.model-id}")
    private String modelId;

    public ClaudeService(BedrockRuntimeClient client, BedrockRuntimeAsyncClient asyncClient) {
        this.sync = client;
        this.async = asyncClient;
    }

    public String chatOnce(ChatRequest req) {
        var messages = toBedrockMessages(req);

        // Build Converse (sync) request
        ConverseRequest.Builder builder = ConverseRequest.builder()
                .modelId(modelId)
                .messages(messages)
                .inferenceConfig(cfg -> {
                    cfg.maxTokens(req.maxTokens() != null ? req.maxTokens() : 4000);
                    if (req.temperature() != null) cfg.temperature(req.temperature().floatValue());
                    if (req.topP() != null)        cfg.topP(req.topP().floatValue());
                });

        if (req.system() != null && !req.system().isBlank()) {
            builder.system(java.util.List.of(SystemContentBlock.fromText(req.system())));
        }

        try {
            ConverseResponse resp = sync.converse(builder.build());

            // Extract assistant text from output message content blocks
            if (resp.output() != null &&
                    resp.output().message() != null &&
                    resp.output().message().content() != null) {

                StringBuilder sb = new StringBuilder();
                for (ContentBlock b : resp.output().message().content()) {
                    String t = b.text();
                    if (t != null && !t.isEmpty()) sb.append(t);
                }
                return sb.toString();
            }
            return "";
        } catch (Exception e) {
            // Keep it simple and safe for your controller
            return "[Bedrock error] " + e.getMessage();
        }
    }


    /** Streaming chat: writes tokens to SseEmitter as they arrive. */
    public void chatStream(ChatRequest req, SseEmitter emitter) {
        // Turn the app’s ChatRequest into Bedrock’s message format
        var messages = toBedrockMessages(req);

        // Builds a Bedrock streaming request with model + inference params.
        var request = ConverseStreamRequest.builder()
                .modelId(modelId)
                .messages(messages)
                .inferenceConfig(cfg -> {
                    cfg.maxTokens(req.maxTokens() != null ? req.maxTokens() : 4000);
                    if (req.temperature() != null) cfg.temperature(req.temperature().floatValue());
                    if (req.topP() != null) cfg.topP(req.topP().floatValue());
                })
                .build();

        // Handle streaming callbacks from Bedrock.
        var handler = ConverseStreamResponseHandler.builder()
                .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                        .onContentBlockDelta(delta -> {
                            // Anthropic text deltas arrive here; stream them out as SSE "data: ..."
                            String chunk = delta.delta().text();
                            if (chunk != null && !chunk.isEmpty()) {
                                try { emitter.send(SseEmitter.event().data(chunk).reconnectTime(0)); }
                                catch (IOException ignored) {}
                            }
                        })
                        .onMessageStop(stop -> {
                            try { emitter.send(SseEmitter.event().name("done").data("[DONE]")); }
                            catch (IOException ignored) {}
                            emitter.complete();
                        })
                        .build())
                        .onError(ex -> {
                            emitter.completeWithError(ex);
                        })
                        .build();

        // Kick off the async streaming request (non-blocking)
        CompletableFuture<?> fut = async.converseStream(request, handler);

        // If the fut completes with an exception, close the SSE with error.
        fut.whenComplete((ok, ex) -> {
            if (ex != null) emitter.completeWithError(ex);
        });
    }

    private java.util.List<Message> requireMessages(ChatRequest req) {
        if (req.messages() == null || req.messages().isEmpty())
            throw new IllegalArgumentException("messages must not be empty");
        return req.messages();
    }

    private java.util.List<software.amazon.awssdk.services.bedrockruntime.model.Message>
    toBedrockMessages(ChatRequest req) {
        var list = new ArrayList<software.amazon.awssdk.services.bedrockruntime.model.Message>();
        for (var m : requireMessages(req)) {
            String text = (m.content() == null) ? "" : m.content().trim();
            if (text.isEmpty()) continue; // <-- skip empty content blocks

            var role = switch (String.valueOf(m.role()).toLowerCase()) {
                case "user" -> ConversationRole.USER;
                case "assistant" -> ConversationRole.ASSISTANT;
                default -> ConversationRole.USER;
            };

            list.add(software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                    .role(role)
                    .content(ContentBlock.fromText(text))
                    .build());
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("No non-empty messages to send");
        }
        return list;
    }
}
