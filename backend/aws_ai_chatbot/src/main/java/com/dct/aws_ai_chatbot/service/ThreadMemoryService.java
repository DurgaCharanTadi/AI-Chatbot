package com.dct.aws_ai_chatbot.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ThreadMemoryService {
    private static final int MAX_LEN = 120_000; // keep memory bounded
    private record Slot(StringBuilder buf, Instant lastUpdate) {}

    private final Map<String, Slot> mem = new ConcurrentHashMap<>();

    public void append(String threadId, String label, String text) {
        if (threadId == null || threadId.isBlank() || text == null || text.isBlank()) return;
        var slot = mem.computeIfAbsent(threadId, id -> new Slot(new StringBuilder(), Instant.now()));
        var sb = slot.buf();
        if (sb.length() > 0) sb.append("\n\n");
        sb.append("=== ").append(label).append(" ===\n").append(text.trim());
        if (sb.length() > MAX_LEN) {
            // simple head-trim
            int trimTo = (int)(MAX_LEN * 0.8);
            String tail = sb.substring(sb.length() - trimTo);
            sb.setLength(0);
            sb.append("[memory trimmed]\n").append(tail);
        }
    }

    public String snapshot(String threadId) {
        if (threadId == null) return "";
        var slot = mem.get(threadId);
        return (slot == null) ? "" : slot.buf().toString();
    }

    public void clear(String threadId) { if (threadId != null) mem.remove(threadId); }
}
