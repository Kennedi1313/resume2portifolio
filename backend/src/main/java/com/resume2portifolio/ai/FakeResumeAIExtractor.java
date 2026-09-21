package com.resume2portifolio.ai;

import com.resume2portifolio.domain.ResumeDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "resume2portifolio.ai.provider", havingValue = "fake", matchIfMissing = true)
public class FakeResumeAIExtractor implements ResumeAIExtractor {
    @Override
    public ResumeDocument extract(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Texto vazio");
        return new ResumeDocument("1.0",
                new ResumeDocument.Basics("Maria Silva", "Software Engineer", "maria@example.com",
                        "+55 11 99999-9999", "São Paulo, SP",
                        List.of(new ResumeDocument.Link("GitHub", "https://github.com/example"))),
                "Profissional de tecnologia com experiência em desenvolvimento de software.",
                List.of(new ResumeDocument.Experience("Empresa Exemplo", "Backend Developer", "2022-01", null,
                        List.of("Desenvolvimento de APIs e automação de processos."),
                        List.of("Java", "Spring Boot", "PostgreSQL"))),
                List.of(new ResumeDocument.Education("Universidade Exemplo", "Sistemas de Informação", "2018-01", "2022-12")),
                List.of("Java", "Spring Boot", "PostgreSQL"),
                List.of(new ResumeDocument.Language("Português", "Nativo")),
                List.of(new ResumeDocument.Project("Projeto Exemplo", "Aplicação para demonstrar processamento assíncrono.",
                        List.of("Java", "RabbitMQ"), null)));
    }
}
