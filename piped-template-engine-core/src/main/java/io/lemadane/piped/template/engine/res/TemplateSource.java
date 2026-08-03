package io.lemadane.piped.template.engine.res;

public final class TemplateSource {
    final String name;
    final String content;
    final long lastModified;

    public TemplateSource(String name, String content, long lastModified) {
        this.name = name;
        this.content = content;
        this.lastModified = lastModified;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public long getLastModified() {
        return lastModified;
    }
}
