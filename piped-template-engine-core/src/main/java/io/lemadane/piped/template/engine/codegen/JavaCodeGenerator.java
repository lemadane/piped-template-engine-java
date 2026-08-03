package io.lemadane.piped.template.engine.codegen;

import io.lemadane.piped.template.engine.ast.ASTNode;
import io.lemadane.piped.template.engine.ast.BlockNode;
import io.lemadane.piped.template.engine.ast.ExpressionNode;
import io.lemadane.piped.template.engine.ast.TextNode;
import java.util.concurrent.atomic.AtomicInteger;

public final class JavaCodeGenerator {
    static final AtomicInteger COUNTER = new AtomicInteger();

    public String generateClassSource(ASTNode rootNode, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("package io.lemadane.piped.template.engine.codegen.generated;\n\n");
        sb.append("import io.lemadane.piped.template.engine.TemplateEngine;\n");
        sb.append("import io.lemadane.piped.template.engine.codegen.CompiledTemplateExecutable;\n");
        sb.append("import io.lemadane.piped.template.engine.expression.TemplateContext;\n");
        sb.append("import java.io.IOException;\n");
        sb.append("import java.io.Writer;\n");
        sb.append("import java.util.Map;\n\n");
        sb.append("public final class ").append(className).append(" implements CompiledTemplateExecutable {\n");
        sb.append("    @Override\n");
        sb.append("    public void render(TemplateContext context, Writer writer, TemplateEngine engine) throws IOException {\n");

        generateNodeSource(rootNode, sb, "        ");

        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    void generateNodeSource(ASTNode node, StringBuilder sb, String indent) {
        if (node instanceof TextNode textNode) {
            sb.append(indent).append("writer.write(").append(escapeStringLiteral(textNode.getText())).append(");\n");
        } else if (node instanceof ExpressionNode exprNode) {
            sb.append(indent).append("writer.write(engine.evaluateExpression(")
                    .append(escapeStringLiteral(exprNode.getOutputExpression().expression())).append(", ")
                    .append(escapeStringLiteral(exprNode.getOutputExpression().mode().name())).append(", context, writer));\n");
        } else if (node instanceof BlockNode blockNode) {
            for (ASTNode child : blockNode.getChildren()) {
                generateNodeSource(child, sb, indent);
            }
        } else if (node instanceof io.lemadane.piped.template.engine.ast.ModelNode) {
            // Ignore metadata node during rendering
        } else {
            sb.append(indent).append(generateASTNodeInstantiation(node)).append(".render(context, writer);\n");
        }
    }

    public static String generateUniqueClassName() {
        return "Template_Gen_" + System.currentTimeMillis() + "_" + COUNTER.incrementAndGet();
    }

    public String generateASTNodeInstantiation(io.lemadane.piped.template.engine.ast.ASTNode node) {
        if (node == null) return "null";
        if (node instanceof io.lemadane.piped.template.engine.ast.TextNode textNode) {
            return "new io.lemadane.piped.template.engine.ast.TextNode(" + escapeStringLiteral(textNode.getText()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.ExpressionNode exprNode) {
            return "new io.lemadane.piped.template.engine.ast.ExpressionNode(new io.lemadane.piped.template.engine.parsers.OutputExpressionParser().parse("
                    + escapeStringLiteral(exprNode.getOutputExpression().expression()) + "), new io.lemadane.piped.template.engine.expression.ExpressionEvaluator())";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.BlockNode blockNode) {
            StringBuilder sb = new StringBuilder("new io.lemadane.piped.template.engine.ast.BlockNode(java.util.List.of(");
            for (int i = 0; i < blockNode.getChildren().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(generateASTNodeInstantiation(blockNode.getChildren().get(i)));
            }
            sb.append("))");
            return sb.toString();
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.SlotNode slotNode) {
            return "new io.lemadane.piped.template.engine.ast.SlotNode(" + escapeStringLiteral(slotNode.getSlotName()) + ", " + generateASTNodeInstantiation(slotNode.getBody()) + ", " + slotNode.isOutlet() + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.SectionNode secNode) {
            return "new io.lemadane.piped.template.engine.ast.SectionNode(" + escapeStringLiteral(secNode.getSectionName()) + ", " + generateASTNodeInstantiation(secNode.getBody()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.YieldNode yieldNode) {
            return "new io.lemadane.piped.template.engine.ast.YieldNode(" + escapeStringLiteral(yieldNode.getSectionName()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.MacroNode macroNode) {
            StringBuilder sb = new StringBuilder("new io.lemadane.piped.template.engine.ast.MacroNode(");
            sb.append(escapeStringLiteral(macroNode.getName())).append(", java.util.List.of(");
            for (int i = 0; i < macroNode.getParameters().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(escapeStringLiteral(macroNode.getParameters().get(i)));
            }
            sb.append("), ").append(generateASTNodeInstantiation(macroNode.getBody())).append(")");
            return sb.toString();
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.IncludeNode incNode) {
            return "new io.lemadane.piped.template.engine.ast.IncludeNode(" + escapeStringLiteral(incNode.getTemplatePath()) + ", " + escapeStringLiteral(incNode.getModelExpression()) + ", new io.lemadane.piped.template.engine.expression.ExpressionEvaluator())";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.ComponentNode compNode) {
            return "new io.lemadane.piped.template.engine.ast.ComponentNode(" + escapeStringLiteral(compNode.getComponentPath()) + ", " + escapeStringLiteral(compNode.getModelExpression()) + ", " + generateASTNodeInstantiation(compNode.getBody()) + ", new io.lemadane.piped.template.engine.expression.ExpressionEvaluator())";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.LayoutNode layoutNode) {
            return "new io.lemadane.piped.template.engine.ast.LayoutNode(" + escapeStringLiteral(layoutNode.getLayoutPath()) + ", " + generateASTNodeInstantiation(layoutNode.getBody()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.IfNode ifNode) {
            StringBuilder sb = new StringBuilder("new io.lemadane.piped.template.engine.ast.IfNode(");
            sb.append(escapeStringLiteral(ifNode.getIfCondition())).append(", ");
            sb.append(generateASTNodeInstantiation(ifNode.getThenBlock())).append(", java.util.List.of(");
            for (int i = 0; i < ifNode.getElseIfBranches().size(); i++) {
                if (i > 0) sb.append(", ");
                var branch = ifNode.getElseIfBranches().get(i);
                sb.append("new io.lemadane.piped.template.engine.ast.IfNode.ElseIfBranch(")
                  .append(escapeStringLiteral(branch.condition())).append(", ")
                  .append(generateASTNodeInstantiation(branch.block())).append(")");
            }
            sb.append("), ").append(generateASTNodeInstantiation(ifNode.getElseBlock())).append(", new io.lemadane.piped.template.engine.expression.ExpressionEvaluator())");
            return sb.toString();
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.EachNode eachNode) {
            return "new io.lemadane.piped.template.engine.ast.EachNode("
                    + escapeStringLiteral(eachNode.getItemName()) + ", "
                    + escapeStringLiteral(eachNode.getCollectionExpression()) + ", "
                    + generateASTNodeInstantiation(eachNode.getBodyBlock()) + ", "
                    + generateASTNodeInstantiation(eachNode.getElseBlock()) + ", "
                    + generateASTNodeInstantiation(eachNode.getSeparatorNode()) + ", "
                    + "new io.lemadane.piped.template.engine.expression.ExpressionEvaluator())";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.ForNode forNode) {
            return "new io.lemadane.piped.template.engine.ast.ForNode("
                    + escapeStringLiteral(forNode.getVarName()) + ", "
                    + escapeStringLiteral(forNode.getStartExpression()) + ", "
                    + escapeStringLiteral(forNode.getEndExpression()) + ", "
                    + escapeStringLiteral(forNode.getStepExpression()) + ", "
                    + generateASTNodeInstantiation(forNode.getBodyBlock()) + ", "
                    + generateASTNodeInstantiation(forNode.getElseBlock()) + ", "
                    + "new io.lemadane.piped.template.engine.expression.ExpressionEvaluator())";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.SwitchNode switchNode) {
            StringBuilder sb = new StringBuilder("new io.lemadane.piped.template.engine.ast.SwitchNode(");
            sb.append(escapeStringLiteral(switchNode.getSwitchExpression())).append(", java.util.List.of(");
            for (int i = 0; i < switchNode.getCases().size(); i++) {
                if (i > 0) sb.append(", ");
                var sc = switchNode.getCases().get(i);
                sb.append("new io.lemadane.piped.template.engine.ast.SwitchNode.SwitchCase(")
                  .append(escapeStringLiteral(sc.caseExpression())).append(", ")
                  .append(generateASTNodeInstantiation(sc.body())).append(", ")
                  .append(sc.hasFallthrough()).append(")");
            }
            sb.append("), ").append(generateASTNodeInstantiation(switchNode.getDefaultBlock())).append(", new io.lemadane.piped.template.engine.expression.ExpressionEvaluator())");
            return sb.toString();
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.AttemptNode attNode) {
            return "new io.lemadane.piped.template.engine.ast.AttemptNode(" + generateASTNodeInstantiation(attNode.getBody()) + ", " + generateASTNodeInstantiation(attNode.getRecoverBlock()) + ", " + escapeStringLiteral(attNode.getErrorVarName()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.MinifyNode minNode) {
            return "new io.lemadane.piped.template.engine.ast.MinifyNode(" + generateASTNodeInstantiation(minNode.getBody()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.FragmentNode fragNode) {
            return "new io.lemadane.piped.template.engine.ast.FragmentNode(" + escapeStringLiteral(fragNode.getName()) + ", " + generateASTNodeInstantiation(fragNode.getBody()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.PWANode pwaNode) {
            return "new io.lemadane.piped.template.engine.ast.PWANode(" + escapeStringLiteral(pwaNode.getName()) + ", " + escapeStringLiteral(pwaNode.getManifest()) + ", " + escapeStringLiteral(pwaNode.getTheme()) + ", " + escapeStringLiteral(pwaNode.getIcon()) + ", " + escapeStringLiteral(pwaNode.getSW()) + ", " + escapeStringLiteral(pwaNode.getStatusColor()) + ", " + escapeStringLiteral(pwaNode.getRegistrationScript()) + ", " + escapeStringLiteral(pwaNode.getNonce()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.HTMXNode htmxNode) {
            StringBuilder sb = new StringBuilder("new io.lemadane.piped.template.engine.ast.HTMXNode(");
            sb.append(escapeStringLiteral(htmxNode.getSrc())).append(", java.util.List.of(");
            for (int i = 0; i < htmxNode.getExtensions().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(escapeStringLiteral(htmxNode.getExtensions().get(i)));
            }
            sb.append("), ").append(escapeStringLiteral(htmxNode.getConfig())).append(", ").append(htmxNode.isIndicator()).append(")");
            return sb.toString();
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.HXAttrNode hxNode) {
            return "new io.lemadane.piped.template.engine.ast.HXAttrNode(" + escapeStringLiteral(hxNode.getMethod()) + ", " + escapeStringLiteral(hxNode.getUrl()) + ", " + escapeStringLiteral(hxNode.getTarget()) + ", " + escapeStringLiteral(hxNode.getSwap()) + ", " + escapeStringLiteral(hxNode.getIndicator()) + ", " + escapeStringLiteral(hxNode.getTrigger()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.AlpineNode alpineNode) {
            StringBuilder sb = new StringBuilder("new io.lemadane.piped.template.engine.ast.AlpineNode(");
            sb.append(escapeStringLiteral(alpineNode.getSrc())).append(", java.util.List.of(");
            for (int i = 0; i < alpineNode.getPlugins().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(escapeStringLiteral(alpineNode.getPlugins().get(i)));
            }
            sb.append("), ").append(alpineNode.isCloak()).append(")");
            return sb.toString();
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.StateNode stateNode) {
            StringBuilder sb = new StringBuilder("new io.lemadane.piped.template.engine.ast.StateNode(java.util.Map.of(");
            int idx = 0;
            for (var entry : stateNode.getStateMap().entrySet()) {
                if (idx > 0) sb.append(", ");
                sb.append(escapeStringLiteral(entry.getKey())).append(", ").append(escapeStringLiteral(entry.getValue()));
                idx++;
            }
            sb.append("))");
            return sb.toString();
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.AlpineAttrNode alpineNode) {
            return "new io.lemadane.piped.template.engine.ast.AlpineAttrNode(" + escapeStringLiteral(alpineNode.getDirective()) + ", " + escapeStringLiteral(alpineNode.getValue()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.BreakNode) {
            return "new io.lemadane.piped.template.engine.ast.BreakNode()";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.ContinueNode) {
            return "new io.lemadane.piped.template.engine.ast.ContinueNode()";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.ModelNode modelNode) {
            return "new io.lemadane.piped.template.engine.ast.ModelNode(" + escapeStringLiteral(modelNode.getModelClassName()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.FieldNode fieldNode) {
            return "new io.lemadane.piped.template.engine.ast.FieldNode(" + escapeStringLiteral(fieldNode.getPropertyPath()) + ", new io.lemadane.piped.template.engine.expression.ExpressionEvaluator())";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.DisplayNode displayNode) {
            return "new io.lemadane.piped.template.engine.ast.DisplayNode(" + escapeStringLiteral(displayNode.getPropertyPath()) + ", new io.lemadane.piped.template.engine.expression.ExpressionEvaluator())";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.EditorNode editorNode) {
            return "new io.lemadane.piped.template.engine.ast.EditorNode(" + escapeStringLiteral(editorNode.getPropertyPath()) + ", new io.lemadane.piped.template.engine.expression.ExpressionEvaluator())";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.SeparatorNode sepNode) {
            return "new io.lemadane.piped.template.engine.ast.SeparatorNode(" + generateASTNodeInstantiation(sepNode.getBody()) + ")";
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.CallMacroNode callNode) {
            StringBuilder sb = new StringBuilder("new io.lemadane.piped.template.engine.ast.CallMacroNode(");
            sb.append(escapeStringLiteral(callNode.getMacroName())).append(", java.util.List.of(");
            for (int i = 0; i < callNode.getArgumentExpressions().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(escapeStringLiteral(callNode.getArgumentExpressions().get(i)));
            }
            sb.append("), new io.lemadane.piped.template.engine.expression.ExpressionEvaluator())");
            return sb.toString();
        }
        return "new io.lemadane.piped.template.engine.ast.TextNode(\"\")";
    }

    String escapeStringLiteral(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\")
                           .replace("\"", "\\\"")
                           .replace("\n", "\\n")
                           .replace("\r", "\\r")
                           .replace("\t", "\\t") + "\"";
    }
}
