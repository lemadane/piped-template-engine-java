package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public final class IfNode implements ASTNode {
    public record ElseIfBranch(String condition, ASTNode block) {}

    final String ifCondition;
    final ASTNode thenBlock;
    final List<ElseIfBranch> elseIfBranches;
    final ASTNode elseBlock;
    final ExpressionEvaluator evaluator;

    public IfNode(
            String ifCondition,
            ASTNode thenBlock,
            List<ElseIfBranch> elseIfBranches,
            ASTNode elseBlock,
            ExpressionEvaluator evaluator) {
        this.ifCondition = ifCondition;
        this.thenBlock = thenBlock;
        this.elseIfBranches = elseIfBranches != null ? List.copyOf(elseIfBranches) : List.of();
        this.elseBlock = elseBlock;
        this.evaluator = evaluator;
    }

    public String getIfCondition() {
        return ifCondition;
    }

    public ASTNode getThenBlock() {
        return thenBlock;
    }

    public List<ElseIfBranch> getElseIfBranches() {
        return elseIfBranches;
    }

    public ASTNode getElseBlock() {
        return elseBlock;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        if (evaluator.evaluateBoolean(ifCondition, context)) {
            thenBlock.render(context, writer);
            return;
        }

        for (int i = 0; i < elseIfBranches.size(); i++) {
            ElseIfBranch branch = elseIfBranches.get(i);
            if (evaluator.evaluateBoolean(branch.condition(), context)) {
                branch.block().render(context, writer);
                return;
            }
        }

        if (elseBlock != null) {
            elseBlock.render(context, writer);
        }
    }
}
