package io.lemadane.piped.template.engine.codegen;

import io.lemadane.piped.template.engine.ast.ASTNode;
import io.lemadane.piped.template.engine.ast.BlockNode;
import io.lemadane.piped.template.engine.ast.ExpressionNode;
import io.lemadane.piped.template.engine.ast.TextNode;
import java.util.concurrent.atomic.AtomicInteger;

public final class JavaCodeGenerator {
    private static final AtomicInteger COUNTER = new AtomicInteger();

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

    private void generateNodeSource(ASTNode node, StringBuilder sb, String indent) {
        if (node instanceof TextNode textNode) {
            sb.append(indent).append("writer.write(").append(escapeStringLiteral(textNode.getText())).append(");\n");
        } else if (node instanceof ExpressionNode exprNode) {
            sb.append(indent).append("writer.write(engine.evaluateExpression(")
                    .append(escapeStringLiteral(exprNode.getOutputExpression().expression())).append(", ")
                    .append(escapeStringLiteral(exprNode.getOutputExpression().mode().name())).append(", context));\n");
        } else if (node instanceof BlockNode blockNode) {
            for (ASTNode child : blockNode.getChildren()) {
                generateNodeSource(child, sb, indent);
            }
        } else if (node instanceof io.lemadane.piped.template.engine.ast.FieldNode fieldNode) {
            sb.append(indent).append("new io.lemadane.piped.template.engine.ast.FieldNode(")
                    .append(escapeStringLiteral(fieldNode.getPropertyPath())).append(", new io.lemadane.piped.template.engine.expression.ExpressionEvaluator()).render(context, writer);\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.DisplayNode displayNode) {
            sb.append(indent).append("new io.lemadane.piped.template.engine.ast.DisplayNode(")
                    .append(escapeStringLiteral(displayNode.getPropertyPath())).append(", new io.lemadane.piped.template.engine.expression.ExpressionEvaluator()).render(context, writer);\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.EditorNode editorNode) {
            sb.append(indent).append("new io.lemadane.piped.template.engine.ast.EditorNode(")
                    .append(escapeStringLiteral(editorNode.getPropertyPath())).append(", new io.lemadane.piped.template.engine.expression.ExpressionEvaluator()).render(context, writer);\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.MacroNode macroNode) {
            sb.append(indent).append("new io.lemadane.piped.template.engine.ast.MacroNode(")
                    .append(escapeStringLiteral(macroNode.getName())).append(", ")
                    .append("java.util.List.of(");
            for (int i = 0; i < macroNode.getParameters().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(escapeStringLiteral(macroNode.getParameters().get(i)));
            }
            sb.append("), null).render(context, writer);\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.CallMacroNode callNode) {
            sb.append(indent).append("new io.lemadane.piped.template.engine.ast.CallMacroNode(")
                    .append(escapeStringLiteral(callNode.getMacroName())).append(", ")
                    .append("java.util.List.of(");
            for (int i = 0; i < callNode.getArgumentExpressions().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(escapeStringLiteral(callNode.getArgumentExpressions().get(i)));
            }
            sb.append("), new io.lemadane.piped.template.engine.expression.ExpressionEvaluator()).render(context, writer);\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.SeparatorNode sepNode) {
            generateNodeSource(sepNode.getBody(), sb, indent);
        } else if (node instanceof io.lemadane.piped.template.engine.ast.FragmentNode fragNode) {
            generateNodeSource(fragNode.getBody(), sb, indent);
        } else if (node instanceof io.lemadane.piped.template.engine.ast.MinifyNode minifyNode) {
            sb.append(indent).append("{\n");
            sb.append(indent).append("    java.io.StringWriter sw = new java.io.StringWriter();\n");
            sb.append(indent).append("    java.io.Writer prevWriter = writer;\n");
            sb.append(indent).append("    writer = sw;\n");
            generateNodeSource(minifyNode.getBody(), sb, indent + "    ");
            sb.append(indent).append("    writer = prevWriter;\n");
            sb.append(indent).append("    writer.write(io.lemadane.piped.template.engine.utils.HtmlFormatter.minifyHtml(sw.toString()));\n");
            sb.append(indent).append("}\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.AttemptNode attemptNode) {
            sb.append(indent).append("{\n");
            sb.append(indent).append("    java.io.StringWriter sw = new java.io.StringWriter();\n");
            sb.append(indent).append("    java.io.Writer prevWriter = writer;\n");
            sb.append(indent).append("    writer = sw;\n");
            sb.append(indent).append("    try {\n");
            generateNodeSource(attemptNode.getBody(), sb, indent + "        ");
            sb.append(indent).append("        prevWriter.write(sw.toString());\n");
            sb.append(indent).append("    } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {\n");
            sb.append(indent).append("        throw e;\n");
            sb.append(indent).append("    } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {\n");
            sb.append(indent).append("        throw e;\n");
            sb.append(indent).append("    } catch (Exception e) {\n");
            if (attemptNode.getRecoverBlock() != null) {
                sb.append(indent).append("        io.lemadane.piped.template.engine.expression.TemplateContext nextCtx = context;\n");
                if (attemptNode.getErrorVarName() != null && !attemptNode.getErrorVarName().isEmpty()) {
                    sb.append(indent).append("        String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();\n");
                    sb.append(indent).append("        nextCtx = context.with(").append(escapeStringLiteral(attemptNode.getErrorVarName())).append(", errorMsg);\n");
                }
                sb.append(indent).append("        java.io.Writer recoverWriter = prevWriter;\n");
                sb.append(indent).append("        {\n");
                sb.append(indent).append("            io.lemadane.piped.template.engine.expression.TemplateContext prevCtx = context;\n");
                sb.append(indent).append("            context = nextCtx;\n");
                sb.append(indent).append("            writer = recoverWriter;\n");
                generateNodeSource(attemptNode.getRecoverBlock(), sb, indent + "            ");
                sb.append(indent).append("            context = prevCtx;\n");
                sb.append(indent).append("        }\n");
            }
            sb.append(indent).append("    } finally {\n");
            sb.append(indent).append("        writer = prevWriter;\n");
            sb.append(indent).append("    }\n");
            sb.append(indent).append("}\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.PWANode pwaNode) {
            sb.append(indent).append("new io.lemadane.piped.template.engine.ast.PWANode(")
                    .append(escapeStringLiteral(pwaNode.getName())).append(", ")
                    .append(escapeStringLiteral(pwaNode.getManifest())).append(", ")
                    .append(escapeStringLiteral(pwaNode.getTheme())).append(", ")
                    .append(escapeStringLiteral(pwaNode.getIcon())).append(", ")
                    .append(escapeStringLiteral(pwaNode.getSW())).append(", ")
                    .append(escapeStringLiteral(pwaNode.getStatusColor())).append(").render(context, writer);\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.HTMXNode htmxNode) {
            sb.append(indent).append("new io.lemadane.piped.template.engine.ast.HTMXNode(")
                    .append(escapeStringLiteral(htmxNode.getSrc())).append(", ")
                    .append("java.util.List.of(");
            for (int i = 0; i < htmxNode.getExtensions().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(escapeStringLiteral(htmxNode.getExtensions().get(i)));
            }
            sb.append("), ")
                    .append(escapeStringLiteral(htmxNode.getConfig())).append(", ")
                    .append(htmxNode.isIndicator()).append(").render(context, writer);\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.HXAttrNode hxAttrNode) {
            sb.append(indent).append("new io.lemadane.piped.template.engine.ast.HXAttrNode(")
                    .append(escapeStringLiteral(hxAttrNode.getMethod())).append(", ")
                    .append(escapeStringLiteral(hxAttrNode.getUrl())).append(", ")
                    .append(escapeStringLiteral(hxAttrNode.getTarget())).append(", ")
                    .append(escapeStringLiteral(hxAttrNode.getSwap())).append(", ")
                    .append(escapeStringLiteral(hxAttrNode.getIndicator())).append(", ")
                    .append(escapeStringLiteral(hxAttrNode.getTrigger())).append(").render(context, writer);\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.AlpineNode alpineNode) {
            sb.append(indent).append("new io.lemadane.piped.template.engine.ast.AlpineNode(")
                    .append(escapeStringLiteral(alpineNode.getSrc())).append(", ")
                    .append("java.util.List.of(");
            for (int i = 0; i < alpineNode.getPlugins().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(escapeStringLiteral(alpineNode.getPlugins().get(i)));
            }
            sb.append("), ")
                    .append(alpineNode.isCloak()).append(").render(context, writer);\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.StateNode stateNode) {
            sb.append(indent).append("{\n");
            sb.append(indent).append("    java.util.Map<String, String> stateMap = new java.util.HashMap<>();\n");
            for (var entry : stateNode.getStateMap().entrySet()) {
                sb.append(indent).append("    stateMap.put(")
                        .append(escapeStringLiteral(entry.getKey())).append(", ")
                        .append(escapeStringLiteral(entry.getValue())).append(");\n");
            }
            sb.append(indent).append("    new io.lemadane.piped.template.engine.ast.StateNode(stateMap).render(context, writer);\n");
            sb.append(indent).append("}\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.AlpineAttrNode alpineAttrNode) {
            sb.append(indent).append("new io.lemadane.piped.template.engine.ast.AlpineAttrNode(")
                    .append(escapeStringLiteral(alpineAttrNode.getDirective())).append(", ")
                    .append(escapeStringLiteral(alpineAttrNode.getValue())).append(").render(context, writer);\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.IfNode ifNode) {
            sb.append(indent).append("if (engine.evaluateBoolean(").append(escapeStringLiteral(ifNode.getIfCondition())).append(", context)) {\n");
            generateNodeSource(ifNode.getThenBlock(), sb, indent + "    ");
            for (var elseIf : ifNode.getElseIfBranches()) {
                sb.append(indent).append("} else if (engine.evaluateBoolean(").append(escapeStringLiteral(elseIf.condition())).append(", context)) {\n");
                generateNodeSource(elseIf.block(), sb, indent + "    ");
            }
            if (ifNode.getElseBlock() != null) {
                sb.append(indent).append("} else {\n");
                generateNodeSource(ifNode.getElseBlock(), sb, indent + "    ");
            }
            sb.append(indent).append("}\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.BreakNode) {
            sb.append(indent).append("throw new io.lemadane.piped.template.engine.exceptions.LoopBreakException();\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.ContinueNode) {
            sb.append(indent).append("throw new io.lemadane.piped.template.engine.exceptions.LoopContinueException();\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.ForNode forNode) {
            sb.append(indent).append("{\n");
            sb.append(indent).append("    io.lemadane.piped.template.engine.expression.ExpressionEvaluator eval = new io.lemadane.piped.template.engine.expression.ExpressionEvaluator();\n");
            sb.append(indent).append("    Object rawStart = eval.evaluate(").append(escapeStringLiteral(forNode.getStartExpression())).append(", context);\n");
            sb.append(indent).append("    int start = rawStart instanceof Number n ? n.intValue() : Integer.parseInt(rawStart.toString().trim());\n");
            sb.append(indent).append("    Object rawEnd = eval.evaluate(").append(escapeStringLiteral(forNode.getEndExpression())).append(", context);\n");
            sb.append(indent).append("    int end = rawEnd instanceof Number n ? n.intValue() : Integer.parseInt(rawEnd.toString().trim());\n");
            if (forNode.getStepExpression() != null && !forNode.getStepExpression().isEmpty()) {
                sb.append(indent).append("    Object rawStep = eval.evaluate(").append(escapeStringLiteral(forNode.getStepExpression())).append(", context);\n");
                sb.append(indent).append("    int step = rawStep instanceof Number n ? n.intValue() : Integer.parseInt(rawStep.toString().trim());\n");
            } else {
                sb.append(indent).append("    int step = 1;\n");
            }
            sb.append(indent).append("    if (step <= 0) throw new io.lemadane.piped.template.engine.exceptions.TemplateRenderException(\"Step must be a positive integer, got: \" + step);\n");
            sb.append(indent).append("    boolean executedAtLeastOnce = false;\n");
            sb.append(indent).append("    if (start < end) {\n");
            sb.append(indent).append("        for (int current = start; current <= end; current += step) {\n");
            sb.append(indent).append("            executedAtLeastOnce = true;\n");
            sb.append(indent).append("            io.lemadane.piped.template.engine.expression.TemplateContext subCtx = context.with(").append(escapeStringLiteral(forNode.getVarName())).append(", current);\n");
            sb.append(indent).append("            io.lemadane.piped.template.engine.expression.TemplateContext prevCtx = context;\n");
            sb.append(indent).append("            context = subCtx;\n");
            sb.append(indent).append("            try {\n");
            generateNodeSource(forNode.getBodyBlock(), sb, indent + "                ");
            sb.append(indent).append("            } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {\n");
            sb.append(indent).append("            } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {\n");
            sb.append(indent).append("                context = prevCtx;\n");
            sb.append(indent).append("                break;\n");
            sb.append(indent).append("            } finally {\n");
            sb.append(indent).append("                context = prevCtx;\n");
            sb.append(indent).append("            }\n");
            sb.append(indent).append("        }\n");
            sb.append(indent).append("    } else if (start > end) {\n");
            sb.append(indent).append("        for (int current = start; current >= end; current -= step) {\n");
            sb.append(indent).append("            executedAtLeastOnce = true;\n");
            sb.append(indent).append("            io.lemadane.piped.template.engine.expression.TemplateContext subCtx = context.with(").append(escapeStringLiteral(forNode.getVarName())).append(", current);\n");
            sb.append(indent).append("            io.lemadane.piped.template.engine.expression.TemplateContext prevCtx = context;\n");
            sb.append(indent).append("            context = subCtx;\n");
            sb.append(indent).append("            try {\n");
            generateNodeSource(forNode.getBodyBlock(), sb, indent + "                ");
            sb.append(indent).append("            } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {\n");
            sb.append(indent).append("            } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {\n");
            sb.append(indent).append("                context = prevCtx;\n");
            sb.append(indent).append("                break;\n");
            sb.append(indent).append("            } finally {\n");
            sb.append(indent).append("                context = prevCtx;\n");
            sb.append(indent).append("            }\n");
            sb.append(indent).append("        }\n");
            sb.append(indent).append("    } else {\n");
            sb.append(indent).append("        executedAtLeastOnce = true;\n");
            sb.append(indent).append("        io.lemadane.piped.template.engine.expression.TemplateContext subCtx = context.with(").append(escapeStringLiteral(forNode.getVarName())).append(", start);\n");
            sb.append(indent).append("        io.lemadane.piped.template.engine.expression.TemplateContext prevCtx = context;\n");
            sb.append(indent).append("        context = subCtx;\n");
            sb.append(indent).append("        try {\n");
            generateNodeSource(forNode.getBodyBlock(), sb, indent + "            ");
            sb.append(indent).append("        } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {\n");
            sb.append(indent).append("        } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {\n");
            sb.append(indent).append("        } finally {\n");
            sb.append(indent).append("            context = prevCtx;\n");
            sb.append(indent).append("        }\n");
            sb.append(indent).append("    }\n");
            if (forNode.getElseBlock() != null) {
                sb.append(indent).append("    if (!executedAtLeastOnce) {\n");
                generateNodeSource(forNode.getElseBlock(), sb, indent + "        ");
                sb.append(indent).append("    }\n");
            }
            sb.append(indent).append("}\n");
        } else if (node instanceof io.lemadane.piped.template.engine.ast.SwitchNode switchNode) {
            sb.append(indent).append("{\n");
            sb.append(indent).append("    io.lemadane.piped.template.engine.expression.ExpressionEvaluator eval = new io.lemadane.piped.template.engine.expression.ExpressionEvaluator();\n");
            sb.append(indent).append("    Object switchVal = eval.evaluate(").append(escapeStringLiteral(switchNode.getSwitchExpression())).append(", context);\n");
            sb.append(indent).append("    boolean matched = false;\n");
            sb.append(indent).append("    boolean fallthrough = false;\n");
            for (var sc : switchNode.getCases()) {
                sb.append(indent).append("    if (!matched) {\n");
                sb.append(indent).append("        Object caseVal = eval.evaluate(").append(escapeStringLiteral(sc.caseExpression())).append(", context);\n");
                sb.append(indent).append("        if (fallthrough || eval.valuesEqual(switchVal, caseVal)) {\n");
                sb.append(indent).append("            matched = true;\n");
                generateNodeSource(sc.body(), sb, indent + "            ");
                if (sc.hasFallthrough()) {
                    sb.append(indent).append("            fallthrough = true;\n");
                }
                sb.append(indent).append("        }\n");
                sb.append(indent).append("    } else if (fallthrough) {\n");
                generateNodeSource(sc.body(), sb, indent + "        ");
                if (!sc.hasFallthrough()) {
                    sb.append(indent).append("        fallthrough = false;\n");
                }
                sb.append(indent).append("    }\n");
            }
            if (switchNode.getDefaultBlock() != null) {
                sb.append(indent).append("    if (!matched || fallthrough) {\n");
                generateNodeSource(switchNode.getDefaultBlock(), sb, indent + "        ");
                sb.append(indent).append("    }\n");
            }
            sb.append(indent).append("}\n");
        }
    }

    public static String generateUniqueClassName() {
        return "Template_Gen_" + System.currentTimeMillis() + "_" + COUNTER.incrementAndGet();
    }

    private String escapeStringLiteral(String value) {
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
