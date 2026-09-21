package com.resume2portifolio.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class PdfBoxResumeTextExtractor implements ResumeTextExtractor {
    @Override
    public String extract(InputStream pdf) throws IOException {
        byte[] content = pdf.readAllBytes();
        try (var document = Loader.loadPDF(content)) {
            String text = new PDFTextStripper().getText(document);
            return text.replaceAll("\\s+", " ").trim();
        }
    }
}
