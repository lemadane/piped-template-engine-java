package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public final class SwitchNode implements ASTNode {
    public record SwitchCase(String caseExpression, ASTNode body, boolean hasFallthrough) {}

    private final String switchExpression;
    private final List<SwitchCase> cases;
    private final ASTNode defaultBlock;
    private final ExpressionEvaluator evaluator;

    public SwitchNode(
            String switchExpression,
            List<SwitchCase> cases,
            ASTNode defaultBlock,
            ExpressionEvaluator evaluator) {
        this.switchExpression = switchExpression;
        this.cases = List.copyOf(cases);
        this.defaultBlock = defaultBlock;
        this.evaluator = evaluator;
    }

    public String getSwitchExpression() {
        return switchExpression;
    }

    public List<SwitchCase> getCases() {
        return cases;
    }

    public ASTNode getDefaultBlock() {
        return defaultBlock;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        Object switchValue = evaluator.evaluate(switchExpression, context);
        boolean matched = false;
        boolean fallthrough = false;

        for (SwitchCase sc : cases) {
            Object caseValue = evaluator.evaluate(sc.caseExpression(), context);
            boolean caseMatches = fallthrough || evaluator.valuesEqual(switchValue, caseValue);

            if (!caseMatches) {
                continue;
            }

            matched = true;
            sc.body().render(context, writer);

            if (!sc.hasFallthrough()) {
                return;
            }

            fallthrough = true;
        }

        if ((fallthrough || !matched) && defaultBlock != null) {
            defaultBlock.render(context, writer);
        }
    }
}
