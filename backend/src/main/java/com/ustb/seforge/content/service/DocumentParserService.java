package com.ustb.seforge.content.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class DocumentParserService {
    public List<ParsedSection> parse(String fileName, InputStream source) throws IOException {
        byte[] bytes = source.readAllBytes();
        String extension = extension(fileName);
        return switch (extension) {
            case "pdf" -> parsePdf(bytes);
            case "ppt", "pptx", "docx" -> List.of(new ParsedSection(
                    normalize(new ApachePoiDocumentParser().parse(new ByteArrayInputStream(bytes)).text()),
                    null, "Document"));
            case "md", "txt" -> List.of(new ParsedSection(
                    normalize(new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "")),
                    null, extension.equals("md") ? "Markdown" : "Text"));
            default -> throw new IOException("Unsupported document format");
        };
    }

    private List<ParsedSection> parsePdf(byte[] bytes) throws IOException {
        List<ParsedSection> sections = new ArrayList<>();
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                if (!text.isBlank()) sections.add(new ParsedSection(text, page, "Page " + page));
            }
        }
        return sections;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public record ParsedSection(String text, Integer page, String section) {
    }
}
