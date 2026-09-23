package com.ustb.seforge.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocumentParserServiceTest {
    private final DocumentParserService parser = new DocumentParserService();

    @Test
    void parsesPdfAndPreservesPageLocation() throws Exception {
        byte[] document;
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(72, 720);
                content.showText("SEForge requirements engineering");
                content.endText();
            }
            pdf.save(output);
            document = output.toByteArray();
        }

        var sections = parser.parse("requirements.pdf", new ByteArrayInputStream(document));

        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().text()).contains("SEForge requirements engineering");
        assertThat(sections.getFirst().page()).isEqualTo(1);
    }

    @Test
    void parsesDocx() throws Exception {
        byte[] document;
        try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            docx.createParagraph().createRun().setText("SEForge design specification");
            docx.write(output);
            document = output.toByteArray();
        }

        assertText("design.docx", document, "SEForge design specification");
    }

    @Test
    void parsesPptx() throws Exception {
        byte[] document;
        try (XMLSlideShow pptx = new XMLSlideShow(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSLFSlide slide = pptx.createSlide();
            XSLFTextBox box = slide.createTextBox();
            box.setText("SEForge architecture presentation");
            pptx.write(output);
            document = output.toByteArray();
        }

        assertText("architecture.pptx", document, "SEForge architecture presentation");
    }

    @Test
    void parsesLegacyPpt() throws Exception {
        byte[] document;
        try (HSLFSlideShow ppt = new HSLFSlideShow(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            HSLFSlide slide = ppt.createSlide();
            HSLFTextBox box = new HSLFTextBox();
            box.setText("SEForge legacy presentation");
            slide.addShape(box);
            ppt.write(output);
            document = output.toByteArray();
        }

        assertText("legacy.ppt", document, "SEForge legacy presentation");
    }

    @Test
    void parsesMarkdownAndPlainTextAsUtf8() throws Exception {
        assertText("guide.md", "# SEForge guide\n\nCourse RAG".getBytes(StandardCharsets.UTF_8),
                "SEForge guide");
        assertText("notes.txt", "SEForge plain text".getBytes(StandardCharsets.UTF_8),
                "SEForge plain text");
    }

    private void assertText(String fileName, byte[] content, String expected) throws Exception {
        var sections = parser.parse(fileName, new ByteArrayInputStream(content));
        assertThat(sections).isNotEmpty();
        assertThat(sections).extracting(DocumentParserService.ParsedSection::text)
                .anySatisfy(text -> assertThat(text).contains(expected));
    }
}
