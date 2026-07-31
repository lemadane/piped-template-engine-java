package io.lemadane.piped.template.engine.exceptions;

public final class LoopContinueException extends RuntimeException {
    private final String partialOutput;

    public LoopContinueException() {
        this("");
    }

    public LoopContinueException(String partialOutput) {
        super(null, null, false, false);
        this.partialOutput = partialOutput != null ? partialOutput : "";
    }

    public String getPartialOutput() {
        return partialOutput;
    }
}
