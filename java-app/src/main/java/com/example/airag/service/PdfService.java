package com.example.airag.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class PdfService {
    public String extractText(String path) throws IOException {
        File f = new File(path);
        if (!f.exists()) {
            throw new IOException("PDF file '" + path + "' not found.");
        }

        try (PDDocument doc = PDDocument.load(f)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }
}
