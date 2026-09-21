package com.resume2portifolio.extraction;

import java.io.IOException;
import java.io.InputStream;

public interface ResumeTextExtractor {
    String extract(InputStream pdf) throws IOException;
}
