package io.lemadane.piped.template.engine.compiler;

import io.lemadane.piped.template.engine.ast.*;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.parsers.OutputExpressionParser;
import java.util.ArrayList;
import java.util.List;

public final class Parser {
    private final OutputExpressionParser outputExpressionParser = new OutputExpressionParser();
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
    private final ThreadLocal<java.util.Map<String, Object>> threadLocalMetadata = ThreadLocal.withInitial(java.util.HashMap::new);

    private int loopDepth = 0;

    public CompiledTemplate parse(List<Token> tokens) {
        Cursor cursor = new Cursor(tokens);
        threadLocalMetadata.get().clear();
        BlockNode root = parseBlock(cursor, null);
        if (cursor.hasNext()) {
            Token t = cursor.peek();
            if (t.type() == TokenType.ELSE) {
                throw new TemplateSyntaxException("Unexpected |else| without matching block at index " + t.position());
            } else if (t.type() == TokenType.END_IF) {
                throw new TemplateSyntaxException("Unexpected |/if| without matching |if| at index " + t.position());
            } else if (t.type() == TokenType.END_EACH) {
                throw new TemplateSyntaxException("Unexpected |/each| without matching |each| at index " + t.position());
            } else if (t.type() == TokenType.END_FOR) {
                throw new TemplateSyntaxException("Unexpected |/for| without matching |for| at index " + t.position());
            }
        }
        java.util.Map<String, Object> metadata = new java.util.HashMap<>(threadLocalMetadata.get());
        threadLocalMetadata.get().clear();
        return new CompiledTemplate(root, metadata);
    }

    private BlockNode parseBlock(Cursor cursor, TokenType stopToken) {
        List<ASTNode> nodes = new ArrayList<>();

        while (cursor.hasNext()) {
            Token token = cursor.peek();

            if (stopToken != null && token.type() == stopToken) {
                break;
            }

            if (switchSectionDepth > 0 && (token.type() == TokenType.CASE || token.type() == TokenType.DEFAULT || token.type() == TokenType.END_SWITCH)) {
                break;
            }

            if (attemptDepth > 0 && (token.type() == TokenType.RECOVER || token.type() == TokenType.END_ATTEMPT)) {
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
                case IF -> nodes.add(parseIf(token, cursor));
                case EACH -> nodes.add(parseEach(token, cursor));
                case FOR -> nodes.add(parseFor(token, cursor));
                case SWITCH -> nodes.add(parseSwitch(token, cursor));
                case CASE -> throw new TemplateSyntaxException("Unexpected |case| without matching |switch|.");
                case DEFAULT -> throw new TemplateSyntaxException("Unexpected |default| without matching |switch|.");
                case FALLTHROUGH -> nodes.add(new io.lemadane.piped.template.engine.ast.FallthroughNode());
                case END_SWITCH -> throw new TemplateSyntaxException("Unexpected |/switch| without matching |switch|.");
                case RECOVER -> throw new TemplateSyntaxException("Unexpected |recover| without matching |attempt|.");
                case END_ATTEMPT -> throw new TemplateSyntaxException("Unexpected |/attempt| without matching |attempt|.");
                case BREAK -> {
                    if (loopDepth == 0) {
                        throw new TemplateSyntaxException("|break| is only allowed inside a loop.");
                    }
                    nodes.add(new io.lemadane.piped.template.engine.ast.BreakNode());
                }
                case CONTINUE -> {
                    if (loopDepth == 0) {
                        throw new TemplateSyntaxException("|continue| is only allowed inside a loop.");
                    }
                    nodes.add(new io.lemadane.piped.template.engine.ast.ContinueNode());
                }
                case END_FOR -> throw new TemplateSyntaxException("Unexpected |/for| without matching |for|.");
                case MODEL -> nodes.add(new ModelNode(token.value().substring("model ".length()).trim()));
                case FIELD -> nodes.add(new FieldNode(token.value().substring("field ".length()).trim(), evaluator));
                case DISPLAY -> nodes.add(new DisplayNode(token.value().substring("display ".length()).trim(), evaluator));
                case EDITOR -> nodes.add(new EditorNode(token.value().substring("editor ".length()).trim(), evaluator));
                case MACRO -> nodes.add(parseMacro(token, cursor));
                case CALL -> nodes.add(parseCallMacro(token));
                case SEPARATOR -> {
                    if (eachDepth == 0) {
                        throw new TemplateSyntaxException("|separator| is only allowed directly inside an |each| loop.");
                    }
                    ASTNode sepBody = parseBlock(cursor, TokenType.END_SEPARATOR);
                    if (cursor.hasNext() && cursor.peek().type() == TokenType.END_SEPARATOR) {
                        cursor.next();
                    }
                    nodes.add(new io.lemadane.piped.template.engine.ast.SeparatorNode(sepBody));
                }
                case FRAGMENT -> nodes.add(parseFragment(token, cursor));
                case MINIFY -> nodes.add(parseMinify(token, cursor));
                case PAGE -> parsePageMetadata(token);
                case ATTEMPT -> nodes.add(parseAttempt(token, cursor));
                case PWA -> nodes.add(parsePWA(token));
                case HTMX -> nodes.add(parseHTMX(token));
                case HX_ATTR -> nodes.add(parseHXAttr(token));
                case ALPINE -> nodes.add(parseAlpine(token));
                case STATE -> nodes.add(parseState(token));
                case ALPINE_ATTR -> nodes.add(parseAlpineAttr(token));
                default -> {
                    var outputExpr = outputExpressionParser.parse(token.value());
                    nodes.add(new ExpressionNode(outputExpr, evaluator));
                }
            }
        }

        return new BlockNode(nodes);
    }

    private IfNode parseIf(Token ifToken, Cursor cursor) {
        String condition = ifToken.value().substring("if ".length()).trim();
        ASTNode thenBlock = parseBlock(cursor, TokenType.END_IF);

        List<IfNode.ElseIfBranch> elseIfBranches = new ArrayList<>();
        ASTNode elseBlock = null;

        while (cursor.hasNext() && cursor.peek().type() != TokenType.END_IF) {
            Token current = cursor.peek();
            if (current.type() == TokenType.ELSE_IF) {
                cursor.next();
                String elseIfCondition = current.value().startsWith("else-if ")
                        ? current.value().substring("else-if ".length()).trim()
                        : current.value().substring("else if ".length()).trim();
                ASTNode elseIfBody = parseBlock(cursor, TokenType.END_IF);
                elseIfBranches.add(new IfNode.ElseIfBranch(elseIfCondition, elseIfBody));
            } else if (current.type() == TokenType.ELSE) {
                cursor.next();
                elseBlock = parseBlock(cursor, TokenType.END_IF);
                break;
            } else {
                break;
            }
        }

        if (cursor.hasNext() && cursor.peek().type() == TokenType.END_IF) {
            cursor.next();
        }

        return new IfNode(condition, thenBlock, elseIfBranches, elseBlock, evaluator);
    }

    private int eachDepth = 0;

    private EachNode parseEach(Token eachToken, Cursor cursor) {
        eachDepth++;
        loopDepth++;
        try {
            String statement = eachToken.value().substring("each ".length()).trim();
            int inIndex = statement.indexOf(" in ");
            if (inIndex == -1) {
                throw new TemplateSyntaxException("Invalid each statement format. Expected '|each item in items|'");
            }

            String itemName = statement.substring(0, inIndex).trim();
            String collectionExpr = statement.substring(inIndex + 4).trim();

            ASTNode bodyBlock = parseBlock(cursor, TokenType.END_EACH);
            ASTNode elseBlock = null;

            if (cursor.hasNext() && cursor.peek().type() == TokenType.ELSE) {
                cursor.next();
                elseBlock = parseBlock(cursor, TokenType.END_EACH);
                if (cursor.hasNext() && cursor.peek().type() == TokenType.ELSE) {
                    throw new TemplateSyntaxException("Multiple |else| blocks inside loop.");
                }
            }

            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_EACH) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException("Missing closing |/each|.");
            }

            ASTNode separatorNode = null;
            if (bodyBlock instanceof BlockNode blockNode) {
                List<ASTNode> bodyChildren = new ArrayList<>();
                for (ASTNode child : blockNode.getChildren()) {
                    if (child instanceof io.lemadane.piped.template.engine.ast.SeparatorNode sep) {
                        separatorNode = sep;
                    } else {
                        bodyChildren.add(child);
                    }
                }
                bodyBlock = new BlockNode(bodyChildren);
            }

            return new EachNode(itemName, collectionExpr, bodyBlock, elseBlock, separatorNode, evaluator);
        } finally {
            eachDepth--;
            loopDepth--;
        }
    }

    private io.lemadane.piped.template.engine.ast.ForNode parseFor(Token forToken, Cursor cursor) {
        eachDepth++;
        loopDepth++;
        try {
            String statement = forToken.value().substring("for ".length()).trim();
            int fromIndex = statement.indexOf(" from ");
            if (fromIndex == -1) {
                throw new TemplateSyntaxException("Invalid for statement format. Missing 'from' keyword.");
            }

            String itemName = statement.substring(0, fromIndex).trim();
            if (itemName.isEmpty()) {
                throw new TemplateSyntaxException("Missing loop variable in for directive.");
            }

            String fromRemainder = statement.substring(fromIndex + 6).trim();
            int toIndex = fromRemainder.indexOf(" to ");
            if (toIndex == -1) {
                throw new TemplateSyntaxException("Invalid for statement format. Missing 'to' boundary.");
            }

            String startExpr = fromRemainder.substring(0, toIndex).trim();
            if (startExpr.isEmpty()) {
                throw new TemplateSyntaxException("Missing start expression in for directive.");
            }

            String toRemainder = fromRemainder.substring(toIndex + 4).trim();
            String endExpr;
            String stepExpr = null;

            int stepIndex = toRemainder.indexOf(" step ");
            if (stepIndex != -1) {
                endExpr = toRemainder.substring(0, stepIndex).trim();
                stepExpr = toRemainder.substring(stepIndex + 6).trim();
                if (stepExpr.isEmpty()) {
                    throw new TemplateSyntaxException("Missing step expression in for directive.");
                }
            } else {
                endExpr = toRemainder.trim();
            }

            if (endExpr.isEmpty()) {
                throw new TemplateSyntaxException("Missing end expression in for directive.");
            }

            ASTNode bodyBlock = parseBlock(cursor, TokenType.END_FOR);
            ASTNode elseBlock = null;

            if (cursor.hasNext() && cursor.peek().type() == TokenType.ELSE) {
                cursor.next();
                elseBlock = parseBlock(cursor, TokenType.END_FOR);
                if (cursor.hasNext() && cursor.peek().type() == TokenType.ELSE) {
                    throw new TemplateSyntaxException("Multiple |else| blocks inside loop.");
                }
            }

            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_FOR) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException("Missing closing |/for|.");
            }

            return new io.lemadane.piped.template.engine.ast.ForNode(
                    itemName, startExpr, endExpr, stepExpr, bodyBlock, elseBlock, evaluator);
        } finally {
            eachDepth--;
            loopDepth--;
        }
    }

    private int switchSectionDepth = 0;

    private ASTNode parseSwitchSectionBlock(Cursor cursor) {
        switchSectionDepth++;
        try {
            return parseBlock(cursor, null);
        } finally {
            switchSectionDepth--;
        }
    }

    private io.lemadane.piped.template.engine.ast.SwitchNode parseSwitch(Token switchToken, Cursor cursor) {
        String switchExpr = switchToken.value().substring("switch ".length()).trim();
        if (switchExpr.isBlank()) {
            throw new TemplateSyntaxException("|switch| expression must not be empty.");
        }

        List<io.lemadane.piped.template.engine.ast.SwitchNode.SwitchCase> cases = new ArrayList<>();
        ASTNode defaultBlock = null;

        while (cursor.hasNext() && cursor.peek().type() != TokenType.END_SWITCH) {
            Token token = cursor.peek();
            if (token.type() == TokenType.CASE) {
                cursor.next();
                String caseExpr = token.value().substring("case ".length()).trim();
                if (caseExpr.isBlank()) {
                    throw new TemplateSyntaxException("|case| expression must not be empty.");
                }
                if (defaultBlock != null) {
                    throw new TemplateSyntaxException("|case| is not allowed after |default|.");
                }

                ASTNode caseBody = parseSwitchSectionBlock(cursor);
                boolean hasFallthrough = extractAndCheckFallthrough(caseBody);
                cases.add(new io.lemadane.piped.template.engine.ast.SwitchNode.SwitchCase(caseExpr, caseBody, hasFallthrough));
            } else if (token.type() == TokenType.DEFAULT) {
                cursor.next();
                if (defaultBlock != null) {
                    throw new TemplateSyntaxException("Only one |default| is allowed inside |switch|.");
                }
                ASTNode defBody = parseSwitchSectionBlock(cursor);
                extractAndCheckFallthrough(defBody);
                defaultBlock = defBody;
            } else if (token.type() == TokenType.TEXT && token.value().isBlank()) {
                cursor.next();
            } else if (token.type() == TokenType.COMMENT) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException("Unexpected token inside |switch|: " + token.value());
            }
        }

        if (cursor.hasNext() && cursor.peek().type() == TokenType.END_SWITCH) {
            cursor.next();
        } else {
            throw new TemplateSyntaxException("Missing closing |/switch|.");
        }

        return new io.lemadane.piped.template.engine.ast.SwitchNode(switchExpr, cases, defaultBlock, evaluator);
    }

    private boolean extractAndCheckFallthrough(ASTNode block) {
        if (block instanceof BlockNode blockNode) {
            for (ASTNode child : blockNode.getChildren()) {
                if (child instanceof io.lemadane.piped.template.engine.ast.FallthroughNode) {
                    return true;
                }
            }
        }
        return false;
    }

    private io.lemadane.piped.template.engine.ast.MacroNode parseMacro(Token macroToken, Cursor cursor) {
        String val = macroToken.value().substring("macro ".length()).trim();
        int openParen = val.indexOf('(');
        int closeParen = val.indexOf(')');

        String name;
        List<String> params = new ArrayList<>();
        if (openParen != -1 && closeParen > openParen) {
            name = val.substring(0, openParen).trim();
            String argsStr = val.substring(openParen + 1, closeParen).trim();
            if (!argsStr.isEmpty()) {
                for (String p : argsStr.split(",")) {
                    params.add(p.trim());
                }
            }
        } else {
            name = val;
        }

        ASTNode body = parseBlock(cursor, TokenType.END_MACRO);
        if (cursor.hasNext() && cursor.peek().type() == TokenType.END_MACRO) {
            cursor.next();
        }

        return new io.lemadane.piped.template.engine.ast.MacroNode(name, params, body);
    }

    private io.lemadane.piped.template.engine.ast.CallMacroNode parseCallMacro(Token callToken) {
        String val = callToken.value().substring("call ".length()).trim();
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

        return new io.lemadane.piped.template.engine.ast.CallMacroNode(name, args, evaluator);
    }

    private io.lemadane.piped.template.engine.ast.FragmentNode parseFragment(Token fragmentToken, Cursor cursor) {
        String name = fragmentToken.value().substring("fragment ".length()).trim();
        ASTNode body = parseBlock(cursor, TokenType.END_FRAGMENT);
        if (cursor.hasNext() && cursor.peek().type() == TokenType.END_FRAGMENT) {
            cursor.next();
        }
        return new io.lemadane.piped.template.engine.ast.FragmentNode(name, body);
    }

    private io.lemadane.piped.template.engine.ast.MinifyNode parseMinify(Token minifyToken, Cursor cursor) {
        ASTNode body = parseBlock(cursor, TokenType.END_MINIFY);
        if (cursor.hasNext() && cursor.peek().type() == TokenType.END_MINIFY) {
            cursor.next();
        }
        return new io.lemadane.piped.template.engine.ast.MinifyNode(body);
    }

    private void parsePageMetadata(Token token) {
        String val = token.value().substring("page ".length()).trim();
        int eqIndex = val.indexOf('=');
        if (eqIndex != -1) {
            String key = val.substring(0, eqIndex).trim();
            String valueStr = val.substring(eqIndex + 1).trim();
            Object value = parseMetadataValue(valueStr);
            threadLocalMetadata.get().put(key, value);
        }
    }

    private Object parseMetadataValue(String str) {
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
            List<String> items = new ArrayList<>();
            for (String item : inner.split(",")) {
                String trimmed = item.trim();
                if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                    trimmed = trimmed.substring(1, trimmed.length() - 1);
                }
                items.add(trimmed);
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

    private int attemptDepth = 0;

    private io.lemadane.piped.template.engine.ast.AttemptNode parseAttempt(Token attemptToken, Cursor cursor) {
        attemptDepth++;
        try {
            ASTNode body = parseBlock(cursor, null);

            ASTNode recoverBlock = null;
            String errorVarName = null;

            if (cursor.hasNext() && cursor.peek().type() == TokenType.RECOVER) {
                Token recoverToken = cursor.next();
                String val = recoverToken.value().substring("recover".length()).trim();
                if (val.startsWith("as ")) {
                    errorVarName = val.substring("as ".length()).trim();
                } else if (!val.isEmpty()) {
                    throw new TemplateSyntaxException("Invalid recover directive syntax: " + recoverToken.value());
                }

                recoverBlock = parseBlock(cursor, TokenType.END_ATTEMPT);
            }

            if (cursor.hasNext() && cursor.peek().type() == TokenType.END_ATTEMPT) {
                cursor.next();
            } else {
                throw new TemplateSyntaxException("Missing closing |/attempt|.");
            }

            return new io.lemadane.piped.template.engine.ast.AttemptNode(body, recoverBlock, errorVarName);
        } finally {
            attemptDepth--;
        }
    }

    private PWANode parsePWA(Token token) {
        String val = token.value().trim();
        if (val.startsWith("pwa")) {
            val = val.substring(3).trim();
        }
        java.util.Map<String, String> attrs = parseKeyValuePairs(val);
        String name = getFirstPWAAttr(attrs, "name", "title", "appName", "app-name", "app_name");
        String manifest = getFirstPWAAttr(attrs, "manifest", "manifestUrl", "manifest-url", "manifest_url");
        String theme = getFirstPWAAttr(attrs, "theme", "themeColor", "theme-color", "theme_color");
        String icon = getFirstPWAAttr(attrs, "icon", "iconUrl", "icon-url", "icon_url", "apple-touch-icon");
        String sw = getFirstPWAAttr(attrs, "sw", "serviceWorker", "service-worker", "service_worker");
        String statusColor = getFirstPWAAttr(attrs, "statusColor", "status-color", "status_color", "statusbar-color", "statusbarColor");

        return new PWANode(
            name,
            manifest,
            theme,
            icon,
            sw,
            statusColor
        );
    }

    private String getFirstPWAAttr(java.util.Map<String, String> attrs, String... keys) {
        for (String key : keys) {
            String val = attrs.get(key);
            if (val != null && !val.isEmpty()) {
                return val;
            }
        }
        return null;
    }

    private HTMXNode parseHTMX(Token token) {
        String val = token.value().trim();
        if (val.startsWith("htmx")) {
            val = val.substring(4).trim();
        }
        java.util.Map<String, String> attrs = parseKeyValuePairs(val);
        List<String> extensions = new ArrayList<>();
        String extStr = attrs.get("ext");
        if (extStr != null && !extStr.isEmpty()) {
            for (String e : extStr.split(",")) {
                String trimmed = e.trim();
                if (!trimmed.isEmpty()) {
                    extensions.add(trimmed);
                }
            }
        }
        boolean indicator = false;
        String indVal = attrs.get("indicator");
        if (indVal != null) {
            indicator = "true".equals(indVal) || "1".equals(indVal) || indVal.isEmpty();
        }
        return new HTMXNode(
            attrs.get("src"),
            extensions,
            attrs.get("config"),
            indicator
        );
    }

    private HXAttrNode parseHXAttr(Token token) {
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

        String urlPath = "";
        String attrsStr = val;

        if (!val.isEmpty() && (val.charAt(0) == '\'' || val.charAt(0) == '"')) {
            char quote = val.charAt(0);
            int end = val.indexOf(quote, 1);
            if (end != -1) {
                urlPath = val.substring(1, end);
                attrsStr = val.substring(end + 1).trim();
            }
        } else {
            String[] parts = val.split("\\s+");
            if (parts.length > 0) {
                urlPath = parts[0];
                if (val.length() > urlPath.length()) {
                    attrsStr = val.substring(urlPath.length()).trim();
                } else {
                    attrsStr = "";
                }
            }
        }

        java.util.Map<String, String> attrs = parseKeyValuePairs(attrsStr);
        return new HXAttrNode(
            method,
            urlPath,
            attrs.get("target"),
            attrs.get("swap"),
            attrs.get("indicator"),
            attrs.get("trigger")
        );
    }

    private AlpineNode parseAlpine(Token token) {
        String val = token.value().trim();
        if (val.startsWith("alpine")) {
            val = val.substring(6).trim();
        }

        java.util.Map<String, String> attrs = parseKeyValuePairs(val);
        List<String> plugins = new ArrayList<>();
        String pluginStr = attrs.get("plugins");
        if (pluginStr != null && !pluginStr.isEmpty()) {
            for (String pl : pluginStr.split(",")) {
                String trimmed = pl.trim();
                if (!trimmed.isEmpty()) {
                    plugins.add(trimmed);
                }
            }
        }

        boolean cloak = true;
        String cVal = attrs.get("cloak");
        if (cVal != null) {
            cloak = "true".equals(cVal) || "1".equals(cVal) || cVal.isEmpty();
        }

        return new AlpineNode(
            attrs.get("src"),
            plugins,
            cloak
        );
    }

    private StateNode parseState(Token token) {
        String val = token.value().trim();
        if (val.startsWith("alpine-data")) {
            val = val.substring(11).trim();
        }
        java.util.Map<String, String> attrs = parseKeyValuePairs(val);
        return new StateNode(attrs);
    }

    private AlpineAttrNode parseAlpineAttr(Token token) {
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

    private java.util.Map<String, String> parseKeyValuePairs(String input) {
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
                int start = i;
                while (i < input.length() && !Character.isWhitespace(input.charAt(i))) {
                    i++;
                }
                val = input.substring(start, i);
            }

            if (!key.isEmpty()) {
                result.put(key, val);
            }
        }
        return result;
    }

    private static class Cursor {
        private final List<Token> tokens;
        private int index = 0;

        Cursor(List<Token> tokens) {
            this.tokens = tokens;
        }

        boolean hasNext() {
            return index < tokens.size();
        }

        Token peek() {
            return tokens.get(index);
        }

        Token next() {
            return tokens.get(index++);
        }
    }
}
