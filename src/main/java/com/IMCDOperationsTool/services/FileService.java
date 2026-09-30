package com.IMCDOperationsTool.services;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Saves an uploaded file under {@code CFG_DirectoryImport} from {@code dbo.ConfigurationOperationsTool}.
 */
@Service
public class FileService {

    private final JdbcTemplate jdbcTemplate;

    public FileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Path saveFile(String fileName, MultipartFile file) throws IOException {
        AtomicReference<String> pathVariable = new AtomicReference<>();

        jdbcTemplate.query(
                "SELECT TOP (1) CFG_DirectoryImport FROM dbo.ConfigurationOperationsTool",
                (RowCallbackHandler) resultSet -> pathVariable.set(resultSet.getString("CFG_DirectoryImport")));

        String directory = pathVariable.get();
        if (directory == null || directory.isBlank()) {
            throw new IOException("CFG_DirectoryImport is not configured");
        }

        Path root = Paths.get(directory.trim());
        Path filePath = root.resolve(fileName).normalize();

        if (!Files.exists(root)) {
            Files.createDirectories(root);
        }
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return filePath;
    }
}
