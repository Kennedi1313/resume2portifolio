package com.resume2portifolio.ai;

import com.resume2portifolio.domain.ResumeDocument;

public interface ResumeAIExtractor {
    ResumeDocument extract(String text);
}
