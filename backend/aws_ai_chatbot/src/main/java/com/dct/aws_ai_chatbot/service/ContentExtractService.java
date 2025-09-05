package com.dct.aws_ai_chatbot.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Service
public class ContentExtractService {

    private final TextractOcrService ocr;

    public ContentExtractService(TextractOcrService ocr) {
        this.ocr = ocr;
    }

    public String extractText(MultipartFile file) {
        try {
            String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String lower = name.toLowerCase();
            String ctype = (file.getContentType() == null ? "" : file.getContentType().toLowerCase());
            byte[] bytes = file.getBytes();

            // --- PDFs (keep your current behavior) ---
            if (ctype.contains("pdf") || lower.endsWith(".pdf")) {
                try (var doc = Loader.loadPDF(bytes)) {
                    var stripper = new PDFTextStripper();
                    return stripper.getText(doc);
                }
            }

            // --- DOCX ---
            if (ctype.contains("officedocument.wordprocessingml.document") || lower.endsWith(".docx")) {
                try (var is = new ByteArrayInputStream(bytes); var doc = new XWPFDocument(is)) {
                    var sb = new StringBuilder();
                    doc.getParagraphs().forEach(p -> {
                        var t = p.getText();
                        if (t != null && !t.isBlank()) sb.append(t).append('\n');
                    });
                    doc.getTables().forEach(tbl -> tbl.getRows().forEach(r ->
                            r.getTableCells().forEach(c -> {
                                var t = c.getText();
                                if (t != null && !t.isBlank()) sb.append(t).append('\n');
                            })));
                    return sb.toString();
                }
            }

            // --- Legacy DOC ---
            if (ctype.contains("msword") || lower.endsWith(".doc")) {
                try (var is = new ByteArrayInputStream(bytes); var doc = new HWPFDocument(is)) {
                    try (var ex = new WordExtractor(doc)) {
                        return String.join("\n", ex.getParagraphText());
                    }
                }
            }

            // --- Plain text ---
            if (ctype.startsWith("text/") || lower.endsWith(".txt") || lower.endsWith(".md")) {
                return new String(bytes, StandardCharsets.UTF_8);
            }

            // --- Images (JPEG/PNG/WEBP/etc.) -> OCR via Textract ---
            if (ctype.startsWith("image/") || lower.matches(".*\\.(jpe?g|png|webp|bmp|tiff?)$")) {
                return ocr.detectLines(bytes);
            }

            // Fallback
            return "[unsupported content-type: %s for %s]".formatted(ctype, name);
        } catch (Exception e) {
            return "[error extracting text: " + e.getMessage() + "]";
        }
    }
}
