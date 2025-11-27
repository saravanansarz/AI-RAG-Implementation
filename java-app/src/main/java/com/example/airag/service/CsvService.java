package com.example.airag.service;

import com.example.airag.model.Question;
import com.opencsv.CSVReaderHeaderAware;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CsvService {
    public List<Question> readQuestions(String csvPath) throws IOException {
        List<Question> out = new ArrayList<>();

        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(csvPath))) {
            Map<String, String> row;
            while ((row = reader.readMap()) != null) {
                String question = row.getOrDefault("question", "").trim();
                String type = row.getOrDefault("type", "").trim();
                String exp = row.getOrDefault("expected values", "").trim();
                List<String> expected = new ArrayList<>();
                if (!exp.isEmpty()) {
                    // some CSVs include quotes; split by comma
                    String cleaned = exp.replace("\"", "");
                    for (String s : cleaned.split(",")) {
                        expected.add(s.trim());
                    }
                }

                out.add(new Question(question, type, expected));
            }
        }

        return out;
    }
}
