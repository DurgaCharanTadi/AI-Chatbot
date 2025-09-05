package com.dct.aws_ai_chatbot.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.util.stream.Collectors;

@Service
public class TextractOcrService {
    private final TextractClient textract;

    public TextractOcrService() {
        // Uses default credentials/region from env or ~/.aws; override if you prefer
        this.textract = TextractClient.builder()
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
                .build();
    }

    public String detectLines(byte[] bytes) {
        var req = DetectDocumentTextRequest.builder()
                .document(Document.builder().bytes(SdkBytes.fromByteArray(bytes)).build())
                .build();
        var resp = textract.detectDocumentText(req);
        return resp.blocks().stream()
                .filter(b -> b.blockType() == BlockType.LINE)
                .map(Block::text)
                .collect(Collectors.joining("\n"));
    }
}
