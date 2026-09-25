package com.ustb.seforge.content.infrastructure;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.hslf.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

final class PhaseThreeDocuments {
    static byte[] create(String name, String text) throws Exception {
        try (var output = new ByteArrayOutputStream()) {
            if (name.endsWith(".pdf")) {
                try (var pdf = new PDDocument()) {
                    var page = new PDPage(); pdf.addPage(page);
                    try (var content = new PDPageContentStream(pdf, page)) {
                        content.beginText(); content.setFont(PDType1Font.HELVETICA, 10);
                        content.newLineAtOffset(30, 700); content.showText(text); content.endText();
                    }
                    pdf.save(output);
                }
            } else if (name.endsWith(".ppt")) {
                try (var ppt = new HSLFSlideShow()) {
                    var slide = ppt.createSlide(); var box = new HSLFTextBox();
                    box.setText(text); slide.addShape(box); ppt.write(output);
                }
            } else if (name.endsWith(".pptx")) {
                try (var ppt = new XMLSlideShow()) { ppt.createSlide().createTextBox().setText(text); ppt.write(output); }
            } else if (name.endsWith(".docx")) {
                try (var doc = new XWPFDocument()) { doc.createParagraph().createRun().setText(text); doc.write(output); }
            } else output.write(text.getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        }
    }
}
