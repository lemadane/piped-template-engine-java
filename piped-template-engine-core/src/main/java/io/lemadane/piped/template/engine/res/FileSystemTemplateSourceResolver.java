package io.lemadane.piped.template.engine.res;

import io.lemadane.piped.template.engine.exceptions.TemplateNotFoundException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class FileSystemTemplateSourceResolver implements TemplateSourceResolver {
    final Path rootDirectory;
    final String suffix;

    public FileSystemTemplateSourceResolver(String rootDirectoryPath) {
        this(rootDirectoryPath, ".pte");
    }

    public FileSystemTemplateSourceResolver(String rootDirectoryPath, String suffix) {
        String p = rootDirectoryPath != null ? rootDirectoryPath : ".";
        if (p.startsWith("file:")) {
            p = p.substring("file:".length());
        }
        this.rootDirectory = Paths.get(p).toAbsolutePath().normalize();
        this.suffix = suffix != null ? suffix : "";
    }

    @Override
    public TemplateSource resolve(String name) throws TemplateNotFoundException {
        if (name == null || name.isEmpty()) {
            throw new TemplateNotFoundException("Template name cannot be empty");
        }

        String pathName = name;
        if (pathName.startsWith("file:")) {
            pathName = pathName.substring("file:".length());
        }

        if (pathName.contains("..")) {
            throw new TemplateNotFoundException("Directory traversal forbidden: " + name);
        }

        if (!suffix.isEmpty() && !pathName.endsWith(suffix) && !pathName.endsWith(".html") && !pathName.endsWith(".pte")) {
            pathName = pathName + suffix;
        }

        Path file = rootDirectory.resolve(pathName).normalize();
        if (!file.startsWith(rootDirectory)) {
            throw new TemplateNotFoundException("Access outside root directory forbidden: " + name);
        }

        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new TemplateNotFoundException("Template file not found: " + name);
        }

        try {
            byte[] bytes = Files.readAllBytes(file);
            long lastMod = Files.getLastModifiedTime(file).toMillis();
            return new TemplateSource(name, new String(bytes, StandardCharsets.UTF_8), lastMod);
        } catch (Exception e) {
            throw new TemplateNotFoundException("Failed to read template file: " + name, e);
        }
    }
}
