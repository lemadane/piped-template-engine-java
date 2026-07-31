package io.lemadane.piped.template.engine.exceptions;

public final class LoopBreakException extends RuntimeException {
    private final String partialOutput;

    public LoopBreakException() {
        this("");
    }

    public LoopBreakException(String partialOutput) {
        super(null, null, false, false);
        this.partialOutput = partialOutput != null ? partialOutput : "";
    }

    public String getPartialOutput() {
        return partialOutput;
    }
}
