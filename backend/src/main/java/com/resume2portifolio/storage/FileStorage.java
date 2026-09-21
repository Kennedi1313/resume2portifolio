package com.resume2portifolio.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface FileStorage {
    UUID save(InputStream content) throws IOException;

    InputStream read(UUID fileId) throws IOException;

    void delete(UUID fileId) throws IOException;
}
