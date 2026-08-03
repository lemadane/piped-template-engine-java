package io.lemadane.piped.template.engine.compiler;

import io.lemadane.piped.template.engine.ast.*;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.parsers.OutputExpressionParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Parser {
    final OutputExpressionParser outputExpressionParser = new OutputExpressionParser();
    final ExpressionEvaluator evaluator = new ExpressionEvaluator();

    public CompiledTemplate parse(List<Token> tokens) {
        return parse(tokens, false);
    }

    public CompiledTemplate parse(List<Token> tokens, boolean inComponentTemplate) {
        Cursor cursor = new Cursor(tokens);
        ParseContext ctx = new ParseContext();
        ctx.setInComponentTemplate(inComponentTemplate);

        BlockNode root = parseBlock(cursor, ctx, null, null);

        if (cursor.hasNext()) {
            Token t = cursor.peek();
            throw new TemplateSyntaxException(String.format("Unexpected token |%s| at line %d, column %d.",
                    t.value(), t.line(), t.column()));
        }

        boolean hasLayout = root.getChildren().stream().anyMatch(c -> c instanceof LayoutNode);
        boolean hasSection = root.getChildren().stream().anyMatch(c -> c instanceof SectionNode);

        if (hasSection && !hasLayout) {
            throw new TemplateSyntaxException("Section directive is only allowed in templates that specify a |layout|.");
        }

        if (hasLayout) {
            boolean seenNonWhitespace = false;
            for (ASTNode child : root.getChildren()) {
                if (child instanceof TextNode tn && tn.getText().isBlank()) {
                    continue;
                }
                if (child instanceof LayoutNode) {
                    if (seenNonWhitespace) {
                        throw new TemplateSyntaxException("Layout directive must be the first directive in a template.");
                    }
                } else {
                    seenNonWhitespace = true;
                }
            }
        }

        return new CompiledTemplate(root, ctx.getMetadata());
    }

    BlockNode parseBlock(Cursor cursor, ParseContext ctx, TokenType stopToken, String directiveName) {
        List<ASTNode> nodes = new ArrayList<>();

        while (cursor.hasNext()) {
            Token token = cursor.peek();

            if (stopToken != null && token.type() == stopToken) {
                break;
            }

            if (ctx.getSwitchSectionDepth() > 0 && (token.type() == TokenType.CASE || token.type() == TokenType.DEFAULT || token.type() == TokenType.END_SWITCH)) {
                break;
            }

            if (ctx.getAttemptDepth() > 0 && (token.type() == TokenType.RECOVER || token.type() == TokenType.END_ATTEMPT)) {
                break;
            }

            if (token.type() == TokenType.ELSE || token.type() == TokenType.ELSE_IF) {
                break;
            }

            cursor.next();

            switch (token.type()) {
                case TEXT -> nodes.add(new TextNode(token.value()));
                case COMMENT -> { /* Ignore comments */ }
                case EXPRESSION -> {
                    var outputExpr = outputExpressionParser.parse(token.value());
                    nodes.add(new ExpressionNode(outputExpr, evaluator));
                }
                case IF -> nodes.add(parseIf(token, cursor, ctx));
                case EACH -> nodes.add(parseEach(token, cursor, ctx));
                case FOR -> nodes.add(parseFor(token, cursor, ctx));
                case SWITCH -> nodes.add(parseSwitch(token, cursor, ctx));
                case CASE -> throw new TemplateSyntaxException(String.format("Unexpected |case| at line %d, column %d without matching |switch|.", token.line(), token.column()));
                case DEFAULT -> throw new TemplateSyntaxException(String.format("Unexpected |default| at line %d, column %d without matching |switch|.", token.line(), token.column()));
                case FALLTHROUGH -> {
                    if (!ctx.isInSwitch()) {
                        throw new TemplateSyntaxException(String.format("Unexpected |fallthrough| at line %d, column %d outside of a |switch| block.", token.line(), token.column()));
                    }
                    nodes.add(new FallthroughNode());
                }
                case END_SWITCH -> throw new TemplateSyntaxException(String.format("Unexpected |/switch| at line %d, column %d without matching |switch|.", token.line(), token.column()));
                case RECOVER -> throw new TemplateSyntaxException(String.format("Unexpected |recover| at line %d, column %d without matching |attempt|.", token.line(), token.column()));
                case END_ATTEMPT -> throw new TemplateSyntaxException(String.format("Unexpected |/attempt| at line %d, column %d without matching |attempt|.", token.line(), token.column()));
                case END_IF -> throw new TemplateSyntaxException(String.format("Unexpected |/if| at line %d, column %d without matching |if|.", token.line(), token.column()));
                case END_EACH -> throw new TemplateSyntaxException(String.format("Unexpected |/each| at line %d, column %d without matching |each|.", token.line(), token.column()));
                case END_FOR -> throw new TemplateSyntaxException(String.format("Unexpected |/for| at line %d, column %d without matching |for|.", token.line(), token.column()));
                case END_MACRO -> throw new TemplateSyntaxException(String.format("Unexpected |/macro| at line %d, column %d without matching |macro|.", token.line(), token.column()));
                case END_FRAGMENT -> throw new TemplateSyntaxException(String.format("Unexpected |/fragment| at line %d, column %d without matching |fragment|.", token.line(), token.column()));
                case END_MINIFY -> throw new TemplateSyntaxException(String.format("Unexpected |/minify| at line %d, column %d without matching |minify|.", token.line(), token.column()));
                case END_SECTION -> throw new TemplateSyntaxException(String.format("Unexpected |/section| at line %d, column %d without matching |section|.", token.line(), token.column()));
                case END_COMPONENT -> throw new TemplateSyntaxException(String.format("Unexpected |/component| at line %d, column %d without matching |component|.", token.line(), token.column()));
                case END_SLOT -> throw new TemplateSyntaxException(String.format("Unexpected |/slot| at line %d, column %d without matching |slot|.", token.line(), token.column()));
                case END_SEPARATOR -> throw new TemplateSyntaxException(String.format("Unexpected |/separator| at line %d, column %d without matching |separator|.", token.line(), token.column()));
                case END_JS -> throw new TemplateSyntaxException(String.format("Unexpected |/js| at line %d, column %d without matching |js|.", token.line(), token.column()));
                case END_CSS -> throw new TemplateSyntaxException(String.format("Unexpected |/css| at line %d, column %d without matching |css|.", token.line(), token.column()));
                case BREAK -> {
                    if (ctx.getLoopDepth() == 0) {
                        throw new TemplateSyntaxException(String.format("|break| is only allowed inside a loop (line %d, column %d).", token.line(), token.column()));
                    }
                    nodes.add(new BreakNode());
                }
                case CONTINUE -> {
                    if (ctx.getLoopDepth() == 0) {
                        throw new TemplateSyntaxException(String.format("|continue| is only allowed inside a loop (line %d, column %d).", token.line(), token.column()));
                    }
                    nodes.add(new ContinueNode());
                }
                case MODEL -> nodes.add(new ModelNode(token.value().substring("model ".length()).trim()));
                case FIELD -> nodes.add(new FieldNode(token.value().substring("field ".length()).trim(), evaluator));
                case DISPLAY -> nodes.add(new DisplayNode(token.value().substring("display ".length()).trim(), evaluator));
                case EDITOR -> nodes.add(new EditorNode(token.value().substring("editor ".length()).trim(), evaluator));
                case MACRO -> nodes.add(parseMacro(token, cursor, ctx));
                case CALL -> nodes.add(parseCallMacro(token));
                case SEPARATOR -> {
                    if (ctx.getEachDepth() == 0) {
                        throw new TemplateSyntaxException(String.format("|separator| is only allowed directly inside an |each| loop (line %d, column %d).", token.line(), token.column()));
                    }
                    ASTNode sepBody = parseBlock(cursor, ctx, TokenType.END_SEPARATOR, "separator");
                    if (cursor.hasNext() && cursor.peek().type() == TokenType.END_SEPARATOR) {
                        cursor.next();
                    } else {
                        throw new TemplateSyntaxException(formatUnclosedError("separator", token, "/separator"));
                    }
                    nodes.add(new SeparatorNode(sepBody));
                }
                case FRAGMENT -> nodes.add(parseFragment(token, cursor, ctx));
                case MINIFY -> nodes.add(parseMinify(token, cursor, ctx));
                case PAGE -> parsePageMetadata(token, ctx);
                case ATTEMPT -> nodes.add(parseAttempt(token, cursor, ctx));
                case INCLUDE -> nodes.add(parseInclude(token));
                case LAYOUT -> nodes.add(parseLayout(token, cursor, ctx));
                case SECTION -> nodes.add(parseSection(token, cursor, ctx));
                case YIELD -> nodes.add(parseYield(token));
                case COMPONENT -> nodes.add(parseComponent(token, cursor, ctx));
                case SLOT -> nodes.add(parseSlot(token, cursor, ctx));
                case PWA -> nodes.add(parsePWA(token));
                case HTMX -> nodes.add(parseHTMX(token));
                case HX_ATTR -> nodes.add(parseHXAttr(token));
                case ALPINE -> nodes.add(parseAlpine(token));
                case STATE -> nodes.add(parseState(token));
                case ALPINE_ATTR -> nodes.add(parseAlpineAttr(token));
                case JS -> nodes.add(parseJs(token, cursor, ctx));
                case CSS -> nodes.add(parseCss(token, cursor, ctx));
                default -> {
                    var outputExpr = outputExpressionParser.parse(token.value());
                    nodes.add(new ExpressionNode(outputExpr, evaluator));
                }
            }
        }

        return new BlockNode(nodes);
    }

    IfNode parseIf(Token ifToken, Cursor cursor, ParseContext ctx) {
        String condition = ifToken.value().substring("if ".length()).trim();
        if (condition.isBlank()) {
            throw new TemplateSyntaxException(String.format("|if| condition must not be empty at line %d, column %d.", ifToken.line(), ifToken.column()));
        }

        ASTNode thenBlock = parseBlock(cursor, ctx, TokenType.END_IF, "if");

        List<IfNode.ElseIfBranch> elseIfBranches = new ArrayList<>();
        ASTNode elseBlock = null;
        boolean hasElse = false;

        while (cursor.hasNext() && cursor.peek().type() != TokenType.END_IF) {
            Token current = cursor.peek();
            if (current.type() == TokenType.ELSE_IF) {
                if (hasElse) {
                    throw new TemplateSyntaxException(String.format("|else if| is not allowed after |else| at line %d, column %d.", current.line(), current.column()));
                }
                cursor.next();
                String elseIfCondition = current.value().substring("else if ".length()).trim();
                if (elseIfCondition.isBlank()) {
                    throw new TemplateSyntaxException(String.format("|else if| condition must not be empty at line %d, column %d.", current.line(), current.column()));
                }
                ASTNode elseIfBody = parseBlock(cursor, ctx, TokenType.END_IF, "if");
                elseIfBranches.add(new IfNode.ElseIfBranch(elseIfCondition, elseIfBody));
            } else if (current.type() == TokenType.ELSE) {
                if (hasElse) {
                    throw new TemplateSyntaxException(String.format("Duplicate |else| block inside |if| at line %d, column %d.", current.line(), current.column()));
                }
                cursor.next();
                hasElse = true;
                elseBlock = parseBlock(cursor, ctx, TokenType.END_IF, "if");
            } else {
                break;
            }
        }

        if (cursor.hasNext() && cursor.peek().type() == TokenType.END_IF) {
            cursor.next();
        } else {
            throw new TemplateSyntaxException(formatUnclosedError("if", ifToken, "/if"));
        }

        return new IfNode(condition, thenBlock, elseIfBranches, elseBlock, evaluator);
    }

    EachNode parseEach(Token eachToken, Cursor cursor, ParseContext ctx) {
        ctx.incrementEachDepth();
        ctx.incrementLoopDepth();
        try {
            String statement = eachToken.value().substring("each ".length()).trim();
            int inIndex = statement.indexOf(" in ");
            if (inIndex == -1) {
                throw new TemplateSyntaxException(String.format("Invalid each statement format at line %d, column %d. Expected '|each item in items|'", eachToken.line(), eachToken.column()));
            }

            String itemName = statement.substring(0, inIndex).trim();
            if (itemName.contains(",")) {
                for (String part : itemName.split(",")) {
                    TemplateIdentifierValidator.validateIdentifier("each", part.trim(), eachToken.line(), eachToken.column());
                }
            } else {
                TemplateIdentifierValidator.validateIdentifier("each", itemName, eachToken.line(), eachToken.column());
            }
            String collectionExpr = statement.substring(inIndex + 4).trim();
            if (collectionExpr.isBlank()) {
                throw new TemplateSyntaxException(String.format("Invalid each statement syntax at line %d, column %d.", eachToken.line(), eachToken.column()));
            }

            ASTNode bodyBlock = parseBlock(cursor, ctx, TokenType.END_EACH, "each");
            ASTNode elseBlock = null;

            if (cursor.hasNext() && cursor.peek().type() == TokenType.ELSE) {
                cursor.next();
                elseBlock = parseBlock(cursor, ctx, TokenType.END_EACH, "each");
                if (cursor.hasNext() && cursor.peek().type() == TokenType.ELSE) {
                    throw new TemplateSyntaxException(String.format("Multiple |else| blocks inside loop at line %d, column %d.", cursor.peek().line(), cursor.peek().column()));
                }
            }

            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_EACH) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException(formatUnclosedError("each", eachToken, "/each"));
            }

            ASTNode separatorNode = null;
            if (bodyBlock instanceof BlockNode blockNode) {
                List<ASTNode> bodyChildren = new ArrayList<>();
                for (ASTNode child : blockNode.getChildren()) {
                    if (child instanceof SeparatorNode sep) {
                        separatorNode = sep;
                    } else {
                        bodyChildren.add(child);
                    }
                }
                bodyBlock = new BlockNode(bodyChildren);
            }

            return new EachNode(itemName, collectionExpr, bodyBlock, elseBlock, separatorNode, evaluator);
        } finally {
            ctx.decrementEachDepth();
            ctx.decrementLoopDepth();
        }
    }

    ForNode parseFor(Token forToken, Cursor cursor, ParseContext ctx) {
        ctx.incrementEachDepth();
        ctx.incrementLoopDepth();
        try {
            String statement = forToken.value().substring("for ".length()).trim();
            int fromIndex = statement.indexOf(" from ");
            if (fromIndex == -1) {
                throw new TemplateSyntaxException(String.format("Invalid for statement format at line %d, column %d. Missing 'from' keyword.", forToken.line(), forToken.column()));
            }

            String itemName = statement.substring(0, fromIndex).trim();
            TemplateIdentifierValidator.validateIdentifier("for", itemName, forToken.line(), forToken.column());

            String fromRemainder = statement.substring(fromIndex + 6).trim();
            int toIndex = fromRemainder.indexOf(" to ");
            if (toIndex == -1) {
                throw new TemplateSyntaxException(String.format("Invalid for statement format at line %d, column %d. Missing 'to' boundary.", forToken.line(), forToken.column()));
            }

            String startExpr = fromRemainder.substring(0, toIndex).trim();
            if (startExpr.isEmpty()) {
                throw new TemplateSyntaxException(String.format("Missing start expression in for directive at line %d, column %d.", forToken.line(), forToken.column()));
            }

            String toRemainder = fromRemainder.substring(toIndex + 4).trim();
            String endExpr;
            String stepExpr = null;

            int stepIndex = toRemainder.indexOf(" step ");
            if (stepIndex != -1) {
                endExpr = toRemainder.substring(0, stepIndex).trim();
                stepExpr = toRemainder.substring(stepIndex + 6).trim();
                if (stepExpr.isEmpty()) {
                    throw new TemplateSyntaxException(String.format("Missing step expression in for directive at line %d, column %d.", forToken.line(), forToken.column()));
                }
            } else {
                endExpr = toRemainder.trim();
            }

            if (endExpr.isEmpty()) {
                throw new TemplateSyntaxException(String.format("Missing end expression in for directive at line %d, column %d.", forToken.line(), forToken.column()));
            }

            ASTNode bodyBlock = parseBlock(cursor, ctx, TokenType.END_FOR, "for");
            ASTNode elseBlock = null;

            if (cursor.hasNext() && cursor.peek().type() == TokenType.ELSE) {
                cursor.next();
                elseBlock = parseBlock(cursor, ctx, TokenType.END_FOR, "for");
                if (cursor.hasNext() && cursor.peek().type() == TokenType.ELSE) {
                    throw new TemplateSyntaxException(String.format("Multiple |else| blocks inside loop at line %d, column %d.", cursor.peek().line(), cursor.peek().column()));
                }
            }

            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_FOR) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException(formatUnclosedError("for", forToken, "/for"));
            }

            return new ForNode(itemName, startExpr, endExpr, stepExpr, bodyBlock, elseBlock, evaluator);
        } finally {
            ctx.decrementEachDepth();
            ctx.decrementLoopDepth();
        }
    }

    ASTNode parseSwitchSectionBlock(Cursor cursor, ParseContext ctx) {
        ctx.incrementSwitchSectionDepth();
        try {
            return parseBlock(cursor, ctx, null, null);
        } finally {
            ctx.decrementSwitchSectionDepth();
        }
    }

    SwitchNode parseSwitch(Token switchToken, Cursor cursor, ParseContext ctx) {
        String switchExpr = switchToken.value().substring("switch ".length()).trim();
        if (switchExpr.isBlank()) {
            throw new TemplateSyntaxException(String.format("|switch| expression must not be empty at line %d, column %d.", switchToken.line(), switchToken.column()));
        }

        boolean prevInSwitch = ctx.isInSwitch();
        ctx.setInSwitch(true);

        List<SwitchNode.SwitchCase> cases = new ArrayList<>();
        ASTNode defaultBlock = null;

        try {
            while (cursor.hasNext() && cursor.peek().type() != TokenType.END_SWITCH) {
                Token token = cursor.peek();
                if (token.type() == TokenType.CASE) {
                    cursor.next();
                    String caseExpr = token.value().substring("case ".length()).trim();
                    if (caseExpr.isBlank()) {
                        throw new TemplateSyntaxException(String.format("|case| expression must not be empty at line %d, column %d.", token.line(), token.column()));
                    }
                    if (defaultBlock != null) {
                        throw new TemplateSyntaxException(String.format("|case| is not allowed after |default| at line %d, column %d.", token.line(), token.column()));
                    }

                    ASTNode caseBody = parseSwitchSectionBlock(cursor, ctx);
                    boolean hasFallthrough = validateAndCheckFallthrough(caseBody, false, token);
                    cases.add(new SwitchNode.SwitchCase(caseExpr, caseBody, hasFallthrough));
                } else if (token.type() == TokenType.DEFAULT) {
                    cursor.next();
                    if (defaultBlock != null) {
                        throw new TemplateSyntaxException(String.format("Only one |default| is allowed inside |switch| at line %d, column %d.", token.line(), token.column()));
                    }
                    ASTNode defBody = parseSwitchSectionBlock(cursor, ctx);
                    validateAndCheckFallthrough(defBody, true, token);
                    defaultBlock = defBody;
                } else if (token.type() == TokenType.TEXT && token.value().isBlank()) {
                    cursor.next();
                } else if (token.type() == TokenType.COMMENT) {
                    cursor.next();
                } else {
                    throw new TemplateSyntaxException(String.format("Unexpected content before first case in |switch| at line %d, column %d: %s", token.line(), token.column(), token.value()));
                }
            }

            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_SWITCH) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException(formatUnclosedError("switch", switchToken, "/switch"));
            }

            if (cases.isEmpty() && defaultBlock == null) {
                throw new TemplateSyntaxException(String.format("Empty |switch| block without cases or default at line %d, column %d.", switchToken.line(), switchToken.column()));
            }

            return new SwitchNode(switchExpr, cases, defaultBlock, evaluator);
        } finally {
            ctx.setInSwitch(prevInSwitch);
        }
    }

    boolean validateAndCheckFallthrough(ASTNode block, boolean isDefaultBlock, Token caseToken) {
        if (!(block instanceof BlockNode blockNode)) {
            return false;
        }

        List<ASTNode> children = blockNode.getChildren();
        int fallthroughCount = 0;
        int fallthroughIndex = -1;

        for (int i = 0; i < children.size(); i++) {
            ASTNode child = children.get(i);
            if (child instanceof FallthroughNode) {
                fallthroughCount++;
                fallthroughIndex = i;
            }
        }

        if (fallthroughCount == 0) {
            return false;
        }

        if (isDefaultBlock) {
            throw new TemplateSyntaxException(String.format("|fallthrough| is forbidden inside |default| case at line %d, column %d.", caseToken.line(), caseToken.column()));
        }

        if (fallthroughCount > 1) {
            throw new TemplateSyntaxException(String.format("Multiple |fallthrough| directives found in single case at line %d, column %d.", caseToken.line(), caseToken.column()));
        }

        // Ensure fallthrough is terminal (only trailing whitespace or comments follow)
        for (int i = fallthroughIndex + 1; i < children.size(); i++) {
            ASTNode trailing = children.get(i);
            if (trailing instanceof TextNode tn && tn.getText().isBlank()) {
                continue;
            }
            throw new TemplateSyntaxException(String.format("Non-terminal |fallthrough| directive found at line %d, column %d.", caseToken.line(), caseToken.column()));
        }

        return true;
    }

    MacroNode parseMacro(Token macroToken, Cursor cursor, ParseContext ctx) {
        String raw = macroToken.value().trim();
        String val = raw.startsWith("macro ") ? raw.substring("macro ".length()).trim() : "";
        int openParen = val.indexOf('(');
        int closeParen = val.indexOf(')');

        String name;
        List<String> params = new ArrayList<>();
        if (openParen != -1 && closeParen > openParen) {
            name = val.substring(0, openParen).trim();
            String argsStr = val.substring(openParen + 1, closeParen).trim();
            if (!argsStr.isEmpty()) {
                String[] parts = argsStr.split(",");
                for (String p : parts) {
                    String param = p.trim();
                    if (param.isEmpty()) {
                        throw new TemplateSyntaxException(String.format("Malformed parameter list in macro declaration at line %d, column %d.", macroToken.line(), macroToken.column()));
                    }
                    params.add(param);
                }
            }
        } else if (openParen != -1 || closeParen != -1) {
            throw new TemplateSyntaxException(String.format("Malformed macro declaration at line %d, column %d.", macroToken.line(), macroToken.column()));
        } else {
            name = val;
        }

        TemplateIdentifierValidator.validateIdentifier("macro", name, macroToken.line(), macroToken.column());

        ASTNode body = parseBlock(cursor, ctx, TokenType.END_MACRO, "macro");
        if (cursor.hasNext() && cursor.peek().type() == TokenType.END_MACRO) {
            cursor.next();
        } else {
            throw new TemplateSyntaxException(formatUnclosedError("macro", macroToken, "/macro"));
        }

        return new MacroNode(name, params, body);
    }

    CallMacroNode parseCallMacro(Token callToken) {
        String raw = callToken.value().trim();
        String val = raw.startsWith("call ") ? raw.substring("call ".length()).trim() : "";
        int openParen = val.indexOf('(');
        int closeParen = val.lastIndexOf(')');

        String name;
        List<String> args = new ArrayList<>();
        if (openParen != -1 && closeParen > openParen) {
            name = val.substring(0, openParen).trim();
            String argsStr = val.substring(openParen + 1, closeParen).trim();
            if (!argsStr.isEmpty()) {
                for (String arg : argsStr.split(",")) {
                    args.add(arg.trim());
                }
            }
        } else {
            name = val;
        }

        TemplateIdentifierValidator.validateIdentifier("call", name, callToken.line(), callToken.column());

        return new CallMacroNode(name, args, evaluator);
    }

    FragmentNode parseFragment(Token fragmentToken, Cursor cursor, ParseContext ctx) {
        String raw = fragmentToken.value().trim();
        String name = raw.startsWith("fragment ") ? raw.substring("fragment ".length()).trim() : "";
        TemplateIdentifierValidator.validateIdentifier("fragment", name, fragmentToken.line(), fragmentToken.column());

        ASTNode body = parseBlock(cursor, ctx, TokenType.END_FRAGMENT, "fragment");
        if (cursor.hasNext() && cursor.peek().type() == TokenType.END_FRAGMENT) {
            cursor.next();
        } else {
            throw new TemplateSyntaxException(formatUnclosedError("fragment", fragmentToken, "/fragment"));
        }
        return new FragmentNode(name, body);
    }

    MinifyNode parseMinify(Token minifyToken, Cursor cursor, ParseContext ctx) {
        ASTNode body = parseBlock(cursor, ctx, TokenType.END_MINIFY, "minify");
        if (cursor.hasNext() && cursor.peek().type() == TokenType.END_MINIFY) {
            cursor.next();
        } else {
            throw new TemplateSyntaxException(formatUnclosedError("minify", minifyToken, "/minify"));
        }
        return new MinifyNode(body);
    }

    JsNode parseJs(Token jsToken, Cursor cursor, ParseContext ctx) {
        String val = jsToken.value().trim();
        if (val.equals("js")) {
            ASTNode body = parseBlock(cursor, ctx, TokenType.END_JS, "js");
            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_JS) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException(formatUnclosedError("js", jsToken, "/js"));
            }
            return new JsNode(body);
        } else {
            String expr = val.substring("js ".length()).trim();
            if (expr.isBlank()) {
                throw new TemplateSyntaxException(String.format("JavaScript expression must not be empty at line %d, column %d.", jsToken.line(), jsToken.column()));
            }
            return new JsNode(expr, evaluator);
        }
    }

    CssNode parseCss(Token cssToken, Cursor cursor, ParseContext ctx) {
        String val = cssToken.value().trim();
        if (val.equals("css")) {
            ASTNode body = parseBlock(cursor, ctx, TokenType.END_CSS, "css");
            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_CSS) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException(formatUnclosedError("css", cssToken, "/css"));
            }
            return new CssNode(body);
        } else {
            String expr = val.substring("css ".length()).trim();
            if (expr.isBlank()) {
                throw new TemplateSyntaxException(String.format("CSS expression must not be empty at line %d, column %d.", cssToken.line(), cssToken.column()));
            }
            return new CssNode(expr, evaluator);
        }
    }

    IncludeNode parseInclude(Token token) {
        String raw = token.value().trim();
        String statement = raw.startsWith("include ") ? raw.substring("include ".length()).trim() : "";
        if (statement.isBlank()) {
            throw new TemplateSyntaxException(String.format("Include path must not be empty at line %d, column %d.", token.line(), token.column()));
        }
        if (statement.endsWith(" with")) {
            throw new TemplateSyntaxException(String.format("Include expression after 'with' must not be empty at line %d, column %d.", token.line(), token.column()));
        }
        int withIndex = statement.indexOf(" with ");
        String templatePath;
        String modelExpr = null;
        if (withIndex != -1) {
            templatePath = statement.substring(0, withIndex).trim();
            modelExpr = statement.substring(withIndex + 6).trim();
            if (modelExpr.isBlank()) {
                throw new TemplateSyntaxException(String.format("Include expression after 'with' must not be empty at line %d, column %d.", token.line(), token.column()));
            }
        } else {
            templatePath = statement;
        }
        TemplateIdentifierValidator.validateTemplatePath("include", templatePath, token.line(), token.column());
        return new IncludeNode(templatePath, modelExpr, evaluator);
    }

    LayoutNode parseLayout(Token token, Cursor cursor, ParseContext ctx) {
        String raw = token.value().trim();
        String layoutPath = raw.startsWith("layout ") ? raw.substring("layout ".length()).trim() : "";
        TemplateIdentifierValidator.validateTemplatePath("layout", layoutPath, token.line(), token.column());
        ASTNode body = parseBlock(cursor, ctx, null, null);
        if (body instanceof BlockNode blockNode) {
            for (ASTNode child : blockNode.getChildren()) {
                if (child instanceof TextNode tn) {
                    if (!tn.getText().isBlank()) {
                        throw new TemplateSyntaxException("Content outside section blocks is forbidden in layout templates.");
                    }
                } else if (!(child instanceof SectionNode)) {
                    throw new TemplateSyntaxException("Directives outside section blocks are forbidden in layout templates.");
                }
            }
        }
        return new LayoutNode(layoutPath, body);
    }

    SectionNode parseSection(Token token, Cursor cursor, ParseContext ctx) {
        if (ctx.isInSection()) {
            throw new TemplateSyntaxException(String.format("Nested |section| is not allowed at line %d, column %d.", token.line(), token.column()));
        }
        String raw = token.value().trim();
        String sectionName = raw.startsWith("section ") ? raw.substring("section ".length()).trim() : "";
        TemplateIdentifierValidator.validateIdentifier("section", sectionName, token.line(), token.column());
        if (!ctx.getDefinedSections().add(sectionName)) {
            throw new TemplateSyntaxException(String.format("Duplicate section '%s' at line %d, column %d.", sectionName, token.line(), token.column()));
        }
        ctx.setInSection(true);
        try {
            ASTNode body = parseBlock(cursor, ctx, TokenType.END_SECTION, "section");
            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_SECTION) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException(formatUnclosedError("section", token, "/section"));
            }
            return new SectionNode(sectionName, body);
        } finally {
            ctx.setInSection(false);
        }
    }

    YieldNode parseYield(Token token) {
        String raw = token.value().trim();
        String sectionName = raw.startsWith("yield ") ? raw.substring("yield ".length()).trim() : "";
        TemplateIdentifierValidator.validateIdentifier("yield", sectionName, token.line(), token.column());
        return new YieldNode(sectionName);
    }

    ComponentNode parseComponent(Token token, Cursor cursor, ParseContext ctx) {
        String raw = token.value().trim();
        String statement = raw.startsWith("component ") ? raw.substring("component ".length()).trim() : "";
        if (statement.isBlank()) {
            throw new TemplateSyntaxException(String.format("Component directive must specify path at line %d, column %d.", token.line(), token.column()));
        }
        int withIndex = statement.indexOf(" with ");
        String componentPath;
        String modelExpr = null;
        if (withIndex != -1) {
            componentPath = statement.substring(0, withIndex).trim();
            modelExpr = statement.substring(withIndex + 6).trim();
        } else {
            componentPath = statement;
        }

        TemplateIdentifierValidator.validateTemplatePath("component", componentPath, token.line(), token.column());

        ctx.incrementComponentDepth();
        try {
            ASTNode body = parseBlock(cursor, ctx, TokenType.END_COMPONENT, "component");
            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_COMPONENT) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException(formatUnclosedError("component", token, "/component"));
            }
            if (body instanceof BlockNode blockNode) {
                boolean hasSlots = blockNode.getChildren().stream().anyMatch(c -> c instanceof SlotNode);
                if (hasSlots) {
                    java.util.Set<String> seenSlots = new java.util.HashSet<>();
                    for (ASTNode child : blockNode.getChildren()) {
                        if (child instanceof SlotNode sn) {
                            if (!seenSlots.add(sn.getSlotName())) {
                                throw new TemplateSyntaxException("Duplicate slot '" + sn.getSlotName() + "' declaration in component caller.");
                            }
                        } else if (child instanceof TextNode tn) {
                            if (!tn.getText().isBlank()) {
                                throw new TemplateSyntaxException("Content outside slot blocks is forbidden inside component caller.");
                            }
                        } else {
                            throw new TemplateSyntaxException("Directives outside slot blocks are forbidden inside component caller.");
                        }
                    }
                }
            }
            return new ComponentNode(componentPath, modelExpr, body, evaluator);
        } finally {
            ctx.decrementComponentDepth();
        }
    }

    SlotNode parseSlot(Token token, Cursor cursor, ParseContext ctx) {
        if (ctx.isInSlot()) {
            throw new TemplateSyntaxException("Nested slot declarations are forbidden at line " + token.line() + ", column " + token.column() + ".");
        }
        String raw = token.value().trim();
        String slotName = raw.startsWith("slot ") ? raw.substring("slot ".length()).trim() : "";
        TemplateIdentifierValidator.validateIdentifier("slot", slotName, token.line(), token.column());
        if (ctx.getComponentDepth() == 0 && !ctx.isInComponentTemplate()) {
            throw new TemplateSyntaxException("Slot declaration outside component context at line " + token.line() + ", column " + token.column() + ".");
        }
        if (ctx.getComponentDepth() == 0 && ctx.isInComponentTemplate()) {
            return new SlotNode(slotName, new TextNode(""), true);
        }

        ctx.setInSlot(true);
        try {
            ASTNode body = parseBlock(cursor, ctx, TokenType.END_SLOT, "slot");
            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_SLOT) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException(formatUnclosedError("slot", token, "/slot"));
            }
            return new SlotNode(slotName, body, false);
        } finally {
            ctx.setInSlot(false);
        }
    }

    AttemptNode parseAttempt(Token attemptToken, Cursor cursor, ParseContext ctx) {
        ctx.incrementAttemptDepth();
        try {
            ASTNode body = parseBlock(cursor, ctx, TokenType.RECOVER, "attempt");

            ASTNode recoverBlock = null;
            String errorVarName = null;

            if (cursor.hasNext() && cursor.peek().type() == TokenType.RECOVER) {
                Token recoverToken = cursor.next();
                String val = recoverToken.value().substring("recover".length()).trim();
                if (val.startsWith("as ")) {
                    errorVarName = val.substring("as ".length()).trim();
                    TemplateIdentifierValidator.validateIdentifier("recover", errorVarName, recoverToken.line(), recoverToken.column());
                } else if (!val.isEmpty()) {
                    throw new TemplateSyntaxException(String.format("Invalid recover directive syntax at line %d, column %d: %s", recoverToken.line(), recoverToken.column(), recoverToken.value()));
                }

                recoverBlock = parseBlock(cursor, ctx, TokenType.END_ATTEMPT, "attempt");
                if (cursor.hasNext() && cursor.peek().type() == TokenType.RECOVER) {
                    throw new TemplateSyntaxException(String.format("Multiple |recover| blocks inside |attempt| at line %d, column %d.", cursor.peek().line(), cursor.peek().column()));
                }
            }

            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_ATTEMPT) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException(formatUnclosedError("attempt", attemptToken, "/attempt"));
            }

            return new AttemptNode(body, recoverBlock, errorVarName);
        } finally {
            ctx.decrementAttemptDepth();
        }
    }

    void parsePageMetadata(Token token, ParseContext ctx) {
        String val = token.value();
        if (val.length() <= 4) {
            return;
        }
        val = val.substring(4).trim();
        if (val.isBlank()) {
            return;
        }
        Map<String, Object> attrs = DirectiveAttributeParser.parseAttributes("page", val);
        for (Map.Entry<String, Object> entry : attrs.entrySet()) {
            TemplateIdentifierValidator.validateIdentifier("page", entry.getKey(), token.line(), token.column());
            ctx.getMetadata().put(entry.getKey(), entry.getValue());
        }
    }

    Object parseMetadataValue(String str) {
        if (str.startsWith("\"") && str.endsWith("\"")) {
            return str.substring(1, str.length() - 1);
        }
        if (str.startsWith("'") && str.endsWith("'")) {
            return str.substring(1, str.length() - 1);
        }
        if ("true".equalsIgnoreCase(str)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(str)) {
            return Boolean.FALSE;
        }
        if (str.startsWith("[") && str.endsWith("]")) {
            String inner = str.substring(1, str.length() - 1).trim();
            if (inner.isEmpty()) {
                return List.of();
            }
            List<Object> items = new ArrayList<>();
            for (String item : inner.split(",")) {
                items.add(parseMetadataValue(item.trim()));
            }
            return items;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            // Ignore
        }
        return str;
    }

    PWANode parsePWA(Token token) {
        String val = token.value().trim();
        if (val.startsWith("pwa")) {
            val = val.substring(3).trim();
        }
        Map<String, Object> attrs = DirectiveAttributeParser.parseAttributes("pwa", val);
        String name = getFirstPWAAttr(attrs, "name", "title", "appName", "app-name", "app_name");
        String manifest = getFirstPWAAttr(attrs, "manifest", "manifestUrl", "manifest-url", "manifest_url");
        String theme = getFirstPWAAttr(attrs, "theme", "themeColor", "theme-color", "theme_color");
        String icon = getFirstPWAAttr(attrs, "icon", "iconUrl", "icon-url", "icon_url", "apple-touch-icon");
        String sw = getFirstPWAAttr(attrs, "sw", "serviceWorker", "service-worker", "service_worker");
        String statusColor = getFirstPWAAttr(attrs, "statusColor", "status-color", "status_color", "statusbar-color", "statusbarColor");
        String registrationScript = getFirstPWAAttr(attrs, "registrationScript", "registration-script", "registration_script");
        String nonce = getFirstPWAAttr(attrs, "nonce");
        String mode = getFirstPWAAttr(attrs, "mode", "registrationMode", "registration-mode", "registration_mode");

        return new PWANode(name, manifest, theme, icon, sw, statusColor, registrationScript, nonce, mode);
    }

    String getFirstPWAAttr(Map<String, Object> attrs, String... keys) {
        for (String key : keys) {
            Object val = attrs.get(key);
            if (val != null) {
                return String.valueOf(val);
            }
        }
        return null;
    }

    HTMXNode parseHTMX(Token token) {
        String val = token.value().trim();
        if (val.startsWith("htmx")) {
            val = val.substring(4).trim();
        }
        Map<String, Object> attrs = DirectiveAttributeParser.parseAttributes("htmx", val);
        List<String> extensions = new ArrayList<>();
        Object extObj = attrs.get("ext") != null ? attrs.get("ext") : attrs.get("extensions");
        if (extObj != null) {
            for (String e : String.valueOf(extObj).split(",")) {
                String trimmed = e.trim();
                if (!trimmed.isEmpty()) {
                    extensions.add(trimmed);
                }
            }
        }
        boolean indicator = false;
        Object indVal = attrs.get("indicator");
        if (indVal != null) {
            indicator = Boolean.TRUE.equals(indVal) || "true".equalsIgnoreCase(String.valueOf(indVal)) || "1".equals(String.valueOf(indVal));
        }
        String src = attrs.get("src") != null ? String.valueOf(attrs.get("src")) : null;
        String config = attrs.get("config") != null ? String.valueOf(attrs.get("config")) : null;
        return new HTMXNode(src, extensions, config, indicator);
    }

    HXAttrNode parseHXAttr(Token token) {
        String val = token.value().trim();
        String method = "get";
        if (val.startsWith("htmx-post ")) {
            method = "post";
            val = val.substring(10).trim();
        } else if (val.startsWith("htmx-put ")) {
            method = "put";
            val = val.substring(9).trim();
        } else if (val.startsWith("htmx-delete ")) {
            method = "delete";
            val = val.substring(12).trim();
        } else if (val.startsWith("htmx-patch ")) {
            method = "patch";
            val = val.substring(11).trim();
        } else if (val.startsWith("htmx-get ")) {
            val = val.substring(9).trim();
        }

        String urlPath = val;
        String attrsStr = "";
        int spaceIdx = val.indexOf(' ');
        if (spaceIdx != -1) {
            urlPath = val.substring(0, spaceIdx).trim();
            attrsStr = val.substring(spaceIdx + 1).trim();
        }

        urlPath = unquote(urlPath);
        Map<String, Object> attrs = DirectiveAttributeParser.parseAttributes("htmx-" + method, attrsStr);
        String target = attrs.get("target") != null ? String.valueOf(attrs.get("target")) : null;
        String swap = attrs.get("swap") != null ? String.valueOf(attrs.get("swap")) : null;
        String indicator = attrs.get("indicator") != null ? String.valueOf(attrs.get("indicator")) : null;
        String trigger = attrs.get("trigger") != null ? String.valueOf(attrs.get("trigger")) : null;

        return new HXAttrNode(method, urlPath, target, swap, indicator, trigger);
    }

    String unquote(String s) {
        if (s == null) return "";
        s = s.trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            if (s.length() >= 2) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    AlpineNode parseAlpine(Token token) {
        String val = token.value().trim();
        if (val.startsWith("alpine")) {
            val = val.substring(6).trim();
        }

        Map<String, Object> attrs = DirectiveAttributeParser.parseAttributes("alpine", val);
        List<String> plugins = new ArrayList<>();
        Object pluginObj = attrs.get("plugins");
        if (pluginObj != null) {
            for (String pl : String.valueOf(pluginObj).split(",")) {
                String trimmed = pl.trim();
                if (!trimmed.isEmpty()) {
                    plugins.add(trimmed);
                }
            }
        }

        boolean cloak = true;
        Object cVal = attrs.get("cloak");
        if (cVal != null) {
            cloak = Boolean.TRUE.equals(cVal) || "true".equalsIgnoreCase(String.valueOf(cVal)) || "1".equals(String.valueOf(cVal));
        }

        String src = attrs.get("src") != null ? String.valueOf(attrs.get("src")) : null;
        return new AlpineNode(src, plugins, cloak);
    }

    StateNode parseState(Token token) {
        String val = token.value().trim();
        if (val.startsWith("alpine-data")) {
            val = val.substring(11).trim();
        }
        java.util.Map<String, String> attrs = parseKeyValuePairs(val);
        return new StateNode(attrs);
    }

    AlpineAttrNode parseAlpineAttr(Token token) {
        String val = token.value().trim();
        String[] parts = val.split("\\s+", 2);
        String dir = parts[0];
        String expr = "";
        if (parts.length > 1) {
            expr = parts[1].trim();
            if (expr.length() > 1 && ((expr.startsWith("'") && expr.endsWith("'")) || (expr.startsWith("\"") && expr.endsWith("\"")))) {
                expr = expr.substring(1, expr.length() - 1);
            }
        }
        return new AlpineAttrNode(dir, expr);
    }

    String formatUnclosedError(String directive, Token openToken, String expectedClose) {
        return String.format("Unclosed |%s| at line %d, column %d; expected |%s| before end of template.",
                directive, openToken.line(), openToken.column(), expectedClose);
    }

    java.util.Map<String, String> parseKeyValuePairs(String input) {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        int i = 0;
        while (i < input.length()) {
            while (i < input.length() && Character.isWhitespace(input.charAt(i))) {
                i++;
            }
            if (i >= input.length()) {
                break;
            }

            int eqIdx = input.indexOf('=', i);
            if (eqIdx == -1) {
                break;
            }
            String key = input.substring(i, eqIdx).trim();
            i = eqIdx + 1;

            while (i < input.length() && Character.isWhitespace(input.charAt(i))) {
                i++;
            }
            if (i >= input.length()) {
                break;
            }

            String val;
            if (input.charAt(i) == '\'' || input.charAt(i) == '"') {
                char quote = input.charAt(i);
                i++;
                int end = input.indexOf(quote, i);
                if (end == -1) {
                    val = input.substring(i);
                    i = input.length();
                } else {
                    val = input.substring(i, end);
                    i = end + 1;
                }
            } else {
                int end = i;
                while (end < input.length() && !Character.isWhitespace(input.charAt(end))) {
                    end++;
                }
                val = input.substring(i, end);
                i = end;
            }
            result.put(key, val);
        }
        return result;
    }

    static final class Cursor {
        final List<Token> tokens;
        int pos = 0;

        Cursor(List<Token> tokens) {
            this.tokens = tokens;
        }

        boolean hasNext() {
            return pos < tokens.size();
        }

        Token peek() {
            return tokens.get(pos);
        }

        Token next() {
            return tokens.get(pos++);
        }

        int position() {
            return pos;
        }
    }
}
