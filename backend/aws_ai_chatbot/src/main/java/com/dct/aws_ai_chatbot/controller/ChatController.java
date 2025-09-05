package com.dct.aws_ai_chatbot.controller;

import com.dct.aws_ai_chatbot.service.ContentExtractService;
import com.dct.aws_ai_chatbot.dto.ChatDtos.*;
import com.dct.aws_ai_chatbot.service.ClaudeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.dct.aws_ai_chatbot.service.ThreadMemoryService;
import com.dct.aws_ai_chatbot.service.WebFetchService;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chat")
// Optional: if you also configure CORS in API Gateway, this doesn't hurt.
// If you need credentials, replace "*" with your exact origins and add allowCredentials in a global CORS config.
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.POST, RequestMethod.OPTIONS})
public class ChatController {

    private final ClaudeService claude;
    private final ThreadMemoryService memory;
    private final WebFetchService web;
    private final ContentExtractService extractor;

    public ChatController(ClaudeService claude, ThreadMemoryService memory, WebFetchService web, ContentExtractService extractor) {
        this.claude = claude;
        this.memory = memory;
        this.web = web;
        this.extractor = extractor;
    }

    // ----------------------------
    // NON-STREAMING COMPLETION (JSON)
    // ----------------------------
    @PostMapping(
            path = "/completion",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> chatCompletion(
            @RequestBody @Valid ChatRequest req,
            @RequestHeader(value = "X-Thread-Id", required = false) String threadId
    ) {
        if (req.messages() == null || req.messages().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messages must not be empty");
        }

        // 1) Fetch links from the latest user message
        var linkCtx = fetchLinksFromLastTurnAndMaybePersist(req, threadId);

        // 2) Build messages + injected context (persisted + links)
        var wrapped = wrapWithContext(req, threadId, null, linkCtx);

        // 3) Single-shot completion (no SSE)
        String text = claude.chatOnce(wrapped);

        return Map.of(
                "ok", true,
                "text", text,
                "tokensRequested", req.maxTokens(),
                "threadId", threadId == null ? "" : threadId
        );
    }

    // ----------------------------
    // NON-STREAMING COMPLETION WITH UPLOAD (JSON)
    // ----------------------------
    @PostMapping(
            path = "/upload/completion",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> chatUploadCompletion(
            @RequestPart("request") @Valid ChatRequest req,
            @RequestPart(value = "file", required = false) MultipartFile[] files,
            @RequestHeader(value = "X-Thread-Id", required = false) String threadId
    ) {
        if (req.messages() == null || req.messages().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "messages must not be empty");
        }

        // 1) Extract text from uploaded files and (optionally) persist to thread memory
        var fileCtx = extractFilesAndMaybePersist(files, threadId);

        // 2) Fetch links from the latest user message
        var linkCtx = fetchLinksFromLastTurnAndMaybePersist(req, threadId);

        // 3) Build messages + injected context (persisted + files + links)
        var wrapped = wrapWithContext(req, threadId, fileCtx, linkCtx);

        // 4) Single-shot completion (no SSE)
        String text = claude.chatOnce(wrapped);

        return Map.of(
                "ok", true,
                "text", text,
                "tokensRequested", req.maxTokens(),
                "threadId", threadId == null ? "" : threadId
        );
    }

    // ----------------------------
    // UTILITIES
    // ----------------------------

    private StringBuilder fetchLinksFromLastTurnAndMaybePersist(ChatRequest req, String threadId) {
        var urlRe = Pattern.compile("(?i)(https?://\\S+|www\\.\\S+)");
        var linkCtx = new StringBuilder();

        var last = req.messages().get(req.messages().size() - 1);
        var userText = last.content() == null ? "" : last.content();
        var m = urlRe.matcher(userText);

        while (m.find()) {
            String raw = m.group();
            String text = web.fetchTextFromPossiblyBareUrl(raw);
            if (text != null && !text.isBlank()) {
                linkCtx.append("\n\n=== ").append(raw).append(" ===\n").append(text);
                if (threadId != null && !threadId.isBlank()) {
                    memory.append(threadId, raw, text);
                }
            }
        }
        return linkCtx;
    }

    private StringBuilder extractFilesAndMaybePersist(MultipartFile[] files, String threadId) {
        var fileCtx = new StringBuilder();
        if (files != null) {
            for (var f : files) {
                if (f == null || f.isEmpty()) continue;
                String name = (f.getOriginalFilename() == null ? "file" : f.getOriginalFilename());
                String text = extractor.extractText(f); // PDFBox/POI/Textract (your service)
                if (text != null && !text.isBlank()) {
                    fileCtx.append("\n\n=== ").append(name).append(" ===\n").append(text);
                }
            }
        }
        // Persist file text only when thread id provided
        if (fileCtx.length() > 0 && threadId != null && !threadId.isBlank()) {
            memory.append(threadId, "uploaded-files", fileCtx.toString());
        }
        return fileCtx;
    }

    private ChatRequest wrapWithContext(
            ChatRequest req,
            String threadId,
            StringBuilder fileCtx,
            StringBuilder linkCtx
    ) {
        var msgs = new ArrayList<>(req.messages());
        var ctxBuilder = new StringBuilder();

        // Persisted memory snapshot if threadId specified
        if (threadId != null && !threadId.isBlank()) {
            var mem = memory.snapshot(threadId);
            if (mem != null && !mem.isBlank()) {
                ctxBuilder.append("\n\n--- PERSISTED CONTEXT ---\n").append(mem);
            }
        }
        if (fileCtx != null && fileCtx.length() > 0) {
            ctxBuilder.append("\n\n--- FILES UPLOADED THIS TURN ---").append(fileCtx);
        }
        if (linkCtx != null && linkCtx.length() > 0) {
            ctxBuilder.append("\n\n--- LINKS FETCHED THIS TURN ---").append(linkCtx);
        }

        if (ctxBuilder.length() > 0) {
            msgs.add(0, new com.dct.aws_ai_chatbot.dto.ChatDtos.Message(
                    "user",
                    "Use ONLY the following context unless the user asks otherwise:\n" +
                            "====================\n" + ctxBuilder + "\n===================="
            ));
        }

        // Anti-refusal nudge so the model uses provided context
        String effectiveSystem =
                ((req.system() == null || req.system().isBlank()) ? "" : (req.system() + "\n\n")) +
                        "You are offline. Do NOT say you cannot access the internet. " +
                        "Any page or file content is already provided in the conversation context. Use it.";

        return new ChatRequest(
                msgs,
                effectiveSystem,
                req.maxTokens(),
                req.temperature(),
                req.topP()
        );
    }

    // ----------------------------
    // HOUSEKEEPING
    // ----------------------------
    @DeleteMapping("/memory")
    public void clearThreadMemory(@RequestHeader("X-Thread-Id") String threadId) {
        memory.clear(threadId);
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    // Helpful for browser preflight if you want the app itself to respond to OPTIONS.
    // (API Gateway can/should handle OPTIONS too; leaving this in is harmless.)
    @RequestMapping(path = {"/completion", "/upload/completion"}, method = RequestMethod.OPTIONS)
    public void options() { /* no-op */ }
}
