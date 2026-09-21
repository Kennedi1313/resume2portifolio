package com.resume2portifolio.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class LocalFileStorage implements FileStorage {
    private final Path uploadsDirectory;

    public LocalFileStorage(@Value("${resume2portifolio.storage.root:./data}") String root) throws IOException {
        this.uploadsDirectory = Path.of(root, "uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadsDirectory);
    }

    @Override
    public UUID save(InputStream content) throws IOException {
        UUID fileId = UUID.randomUUID();
        Files.copy(content, uploadsDirectory.resolve(fileId + ".pdf"));
        return fileId;
    }

    @Override
    public InputStream read(UUID fileId) throws IOException {
        return Files.newInputStream(uploadsDirectory.resolve(fileId + ".pdf"));
    }

    @Override
    public void delete(UUID fileId) throws IOException {
        Files.deleteIfExists(uploadsDirectory.resolve(fileId + ".pdf"));
    }
}
