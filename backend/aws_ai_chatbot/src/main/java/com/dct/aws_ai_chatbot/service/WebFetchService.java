package com.dct.aws_ai_chatbot.service;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class WebFetchService {
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    // Simple URL detector (http/https)
    public static boolean looksLikeUrl(String s) {
        return s != null && s.matches("(?i)\\bhttps?://\\S+");
    }

    public String fetchText(String url) {
        try {
            var req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "ai-chatbot/1.0 (+https://localhost)")
                    .GET()
                    .build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 400) return "[fetch failed " + resp.statusCode() + " for " + url + "]";
            var ctype = resp.headers().firstValue("content-type").orElse("application/octet-stream").toLowerCase();
            byte[] body = resp.body();

            // PDF -> use PDFBox (you already added it)
            if (ctype.contains("pdf") || url.toLowerCase().endsWith(".pdf")) {
                try (var doc = org.apache.pdfbox.Loader.loadPDF(body)) {
                    var stripper = new org.apache.pdfbox.text.PDFTextStripper();
                    return stripper.getText(doc);
                }
            }

            // HTML -> parse text with jsoup
            if (ctype.contains("html") || ctype.contains("xml") || ctype.contains("xhtml")) {
                String html = new String(body, java.nio.charset.StandardCharsets.UTF_8);
                return Jsoup.parse(html).text(); // visible text only
            }

            // Plain text
            if (ctype.startsWith("text/")) {
                return new String(body, java.nio.charset.StandardCharsets.UTF_8);
            }

            // Unknown/binary
            return "[unsupported content-type: " + ctype + " for " + url + "]";
        } catch (Exception e) {
            return "[error fetching " + url + ": " + e.getMessage() + "]";
        }
    }

    public static String normalizeUrl(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.matches("(?i)^https?://.+")) return t;
        if (t.matches("(?i)^www\\..+"))     return "https://" + t;
        return null;
    }
    public String fetchTextFromPossiblyBareUrl(String s) {
        String url = normalizeUrl(s);
        if (url == null) return "";
        return fetchText(url); // your existing fetcher that returns plain text
    }
}
