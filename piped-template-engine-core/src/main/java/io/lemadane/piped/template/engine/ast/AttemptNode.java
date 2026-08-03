package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.io.StringWriter;

public final class AttemptNode implements ASTNode {
    private final ASTNode body;
    private final ASTNode recoverBlock;
    private final String errorVarName;

    public AttemptNode(ASTNode body, ASTNode recoverBlock, String errorVarName) {
        this.body = body;
        this.recoverBlock = recoverBlock;
        this.errorVarName = errorVarName;
    }

    public ASTNode getBody() {
        return body;
    }

    public ASTNode getRecoverBlock() {
        return recoverBlock;
    }

    public String getErrorVarName() {
        return errorVarName;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        StringWriter sw = new StringWriter();
        try {
            body.render(context, sw);
            writer.write(sw.toString());
        } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException | io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
            throw e;
        } catch (Exception e) {
            if (recoverBlock != null) {
                TemplateContext nextContext = context;
                if (errorVarName != null && !errorVarName.isEmpty()) {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                    nextContext = context.with(errorVarName, errorMsg);
                }
                recoverBlock.render(nextContext, writer);
            }
        }
    }
}
