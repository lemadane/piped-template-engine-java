package io.lemadane.piped.template.engine;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import io.lemadane.piped.template.engine.escapers.AttributeEscaper;
import io.lemadane.piped.template.engine.escapers.HtmlEscaper;
import io.lemadane.piped.template.engine.escapers.JsonEscaper;
import io.lemadane.piped.template.engine.escapers.UrlEscaper;
import io.lemadane.piped.template.engine.exceptions.TemplateNotFoundException;
import io.lemadane.piped.template.engine.exceptions.TemplateRenderException;
import io.lemadane.piped.template.engine.exceptions.TemplateSyntaxException;
import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.OutputMode;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import io.lemadane.piped.template.engine.metadata.EachMetadata;
import io.lemadane.piped.template.engine.parsers.EachStatementParser;
import io.lemadane.piped.template.engine.parsers.OutputExpressionParser;
import io.lemadane.piped.template.engine.res.*;
import io.lemadane.piped.template.engine.statements.EachStatement;

public final class TemplateEngine {
    public enum ExecutionMode {
        BYTECODE,
        AST_FALLBACK
    }

    static final Set<String> CONDITIONAL_ATTRIBUTE_LITERALS = Set.of(
            "allowfullscreen",
            "async",
            "autofocus",
            "autoplay",
            "checked",
            "controls",
            "default",
            "defer",
            "disabled",
            "formnovalidate",
            "hidden",
            "inert",
            "ismap",
            "itemscope",
            "loop",
            "multiple",
            "muted",
            "nomodule",
            "novalidate",
            "open",
            "playsinline",
            "readonly",
            "required",
            "reversed",
            "selected",
            "aria-current");

    final ExpressionEvaluator expressionEvaluator;
    final OutputExpressionParser outputExpressionParser;
    final EachStatementParser eachStatementParser;
    final HtmlEscaper htmlEscaper;
    final AttributeEscaper attributeEscaper;
    final UrlEscaper urlEscaper;
    final JsonEscaper jsonEscaper;
    final Path templateRoot;
    final ThreadLocal<ArrayDeque<String>> templateStack;
    final ThreadLocal<ArrayDeque<Map<String, String>>> sectionStack;
    final ThreadLocal<ArrayDeque<Map<String, String>>> slotStack;
    final ThreadLocal<Integer> loopDepth;
    final Map<String, String> includedTemplates;
    final TemplateSourceResolver templateSourceResolver;
    final io.lemadane.piped.template.engine.compiler.TemplateCache templateCache;
    final io.lemadane.piped.template.engine.compiler.Lexer lexer;
    final io.lemadane.piped.template.engine.compiler.Parser parser;
    boolean minify = false;
    boolean prettify = false;

    public boolean isMinify() { return minify; }
    public void setMinify(boolean minify) { this.minify = minify; }

    public boolean isPrettify() { return prettify; }
    public void setPrettify(boolean prettify) { this.prettify = prettify; }

    public ExecutionMode getExecutionMode() {
        return io.lemadane.piped.template.engine.codegen.InMemoryBytecodeCompiler.isAvailable()
                ? ExecutionMode.BYTECODE
                : ExecutionMode.AST_FALLBACK;
    }

    public TemplateEngine() {
        this(null, Map.of(), null);
    }

    public TemplateEngine(Path templateRoot) {
        this(templateRoot, Map.of(), null);
    }

    public TemplateEngine(Map<String, String> includedTemplates) {
        this(null, includedTemplates, null);
    }

    public TemplateEngine(Path templateRoot, Map<String, String> includedTemplates) {
        this(templateRoot, includedTemplates, null);
    }

    public TemplateEngine(TemplateSourceResolver customResolver) {
        this(null, Map.of(), customResolver);
    }

    public TemplateEngine(Path templateRoot, Map<String, String> includedTemplates, TemplateSourceResolver customResolver) {
        this.expressionEvaluator = new ExpressionEvaluator();
        this.outputExpressionParser = new OutputExpressionParser();
        this.eachStatementParser = new EachStatementParser();
        this.htmlEscaper = new HtmlEscaper();
        this.attributeEscaper = new AttributeEscaper();
        this.urlEscaper = new UrlEscaper();
        this.jsonEscaper = new JsonEscaper();
        this.templateCache = new io.lemadane.piped.template.engine.compiler.TemplateCache();
        this.lexer = new io.lemadane.piped.template.engine.compiler.Lexer();
        this.parser = new io.lemadane.piped.template.engine.compiler.Parser();
        Path rootPath = templateRoot != null ? templateRoot.toAbsolutePath().normalize() : Path.of("src/main/resources/pte-templates").toAbsolutePath().normalize();
        this.templateRoot = rootPath;

        this.templateStack = ThreadLocal.withInitial(ArrayDeque::new);
        this.sectionStack = ThreadLocal.withInitial(ArrayDeque::new);
        this.slotStack = ThreadLocal.withInitial(ArrayDeque::new);
        this.loopDepth = ThreadLocal.withInitial(() -> 0);
        this.includedTemplates = normalizeIncludedTemplates(includedTemplates);

        List<TemplateSourceResolver> resolvers = new ArrayList<>();
        if (customResolver != null) {
            resolvers.add(customResolver);
        }
        if (this.includedTemplates != null && !this.includedTemplates.isEmpty()) {
            resolvers.add(new InMemoryTemplateSourceResolver(this.includedTemplates));
        }
        resolvers.add(new FileSystemTemplateSourceResolver(rootPath.toString(), ".pte"));
        resolvers.add(new ClasspathTemplateSourceResolver("classpath:/pte-templates/", ".pte"));

        this.templateSourceResolver = new CompositeTemplateSourceResolver(resolvers);
    }

    public TemplateSourceResolver getTemplateSourceResolver() {
        return templateSourceResolver;
    }

    public io.lemadane.piped.template.engine.compiler.CompiledTemplate compile(String template) {
        return templateCache.computeIfAbsent(template, source -> parser.parse(lexer.tokenize(source)));
    }

    public io.lemadane.piped.template.engine.compiler.CompiledTemplate compileTemplate(String templateOrTemplateName) {
        String source = loadTemplateSource(templateOrTemplateName);
        return compile(source);
    }

    public io.lemadane.piped.template.engine.codegen.CompiledTemplateExecutable compileToBytecode(String template) {
        if (!io.lemadane.piped.template.engine.codegen.InMemoryBytecodeCompiler.isAvailable()) {
            return (context, writer, engine) -> compile(template).render(context, writer);
        }
        try {
            var ast = compile(template).getRootNode();
            String className = io.lemadane.piped.template.engine.codegen.JavaCodeGenerator.generateUniqueClassName();
            String javaSource = new io.lemadane.piped.template.engine.codegen.JavaCodeGenerator().generateClassSource(ast, className);
            if ("true".equalsIgnoreCase(System.getProperty("pte.debug")) || "true".equalsIgnoreCase(System.getenv("pte.debug"))) {
                System.out.println(javaSource);
            }
            Class<?> clazz = new io.lemadane.piped.template.engine.codegen.InMemoryBytecodeCompiler().compile(className, javaSource);
            return (io.lemadane.piped.template.engine.codegen.CompiledTemplateExecutable) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            if (e instanceof TemplateSyntaxException || e instanceof TemplateRenderException) {
                throw (RuntimeException) e;
            }
            throw new TemplateRenderException("Bytecode code-generation or compilation failed for template", e);
        }
    }

    public boolean evaluateBoolean(String expression, TemplateContext context) {
        return expressionEvaluator.evaluateBoolean(expression, context);
    }

    public String evaluateExpression(String expression, String modeName, TemplateContext context) {
        return evaluateExpression(expression, modeName, context, null);
    }

    public String evaluateExpression(String expression, String modeName, TemplateContext context, java.io.Writer writer) {
        OutputMode defaultMode = OutputMode.valueOf(modeName);
        var conditionalOutputExpression = parseConditionalOutputExpression(expression);
        var outputExpression = outputExpressionParser.parse(conditionalOutputExpression.outputSource());
        OutputMode mode = defaultMode != OutputMode.HTML_ESCAPED ? defaultMode : outputExpression.mode();

        if (conditionalOutputExpression.conditionExpression() != null
                && !expressionEvaluator.evaluateBoolean(conditionalOutputExpression.conditionExpression(), context)) {
            if (mode == OutputMode.ATTRIBUTE_ESCAPED && writer instanceof java.io.StringWriter sw) {
                StringBuffer sb = sw.getBuffer();
                while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
                    sb.deleteCharAt(sb.length() - 1);
                }
            }
            return "";
        }

        if (conditionalOutputExpression.conditionExpression() != null && mode == OutputMode.ATTRIBUTE_ESCAPED) {
            String attributeOutput = renderConditionalAttributeOutput(outputExpression.expression(), context);
            if (attributeOutput != null) {
                if (writer instanceof java.io.StringWriter sw) {
                    StringBuffer sb = sw.getBuffer();
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ' && sb.charAt(sb.length() - 1) != '<') {
                        sb.append(' ');
                    }
                }
                return attributeOutput;
            }
        }

        Object value = expressionEvaluator.evaluate(outputExpression.expression(), context);
        return renderValue(mode, value);
    }

    Map<String, String> normalizeIncludedTemplates(Map<String, String> includedTemplates) {
        if (includedTemplates == null || includedTemplates.isEmpty()) {
            return Map.of();
        }

        final var values = new LinkedHashMap<String, String>();

        for (final var entry : includedTemplates.entrySet()) {
            values.put(
                    normalizeTemplateName(entry.getKey()),
                    entry.getValue());
        }

        return Collections.unmodifiableMap(values);
    }

    public String renderNamedTemplate(String templateName, Map<String, Object> model, RenderOptions options) {
        TemplateSource source = templateSourceResolver.resolve(templateName);
        return renderStringWithResolver(source.getContent(), model, templateSourceResolver, options);
    }

    public String renderNamedTemplate(String templateName, TemplateContext context) {
        TemplateSource source = templateSourceResolver.resolve(templateName);
        if (context.getResolver() == null) {
            context = context.withResolver(templateSourceResolver);
        }
        if (context.getEngine() == null) {
            TemplateContext.EngineRenderDelegate delegate = new TemplateContext.EngineRenderDelegate() {
                @Override
                public String renderStringWithContext(String templateContent, TemplateContext context) {
                    return TemplateEngine.this.renderStringWithContext(templateContent, context);
                }
                @Override
                public String renderComponentTemplate(String templateContent, TemplateContext context) {
                    return TemplateEngine.this.renderComponentTemplate(templateContent, context);
                }
            };
            context = context.withEngine(delegate);
        }
        return renderStringWithContext(source.getContent(), context);
    }

    public String render(String templateOrTemplateName, Map<String, Object> values) {
        TemplateSource source = null;
        if (isTemplateReference(templateOrTemplateName)) {
            try {
                source = templateSourceResolver.resolve(templateOrTemplateName);
            } catch (TemplateNotFoundException ignored) {
            }
        }
        if (source != null) {
            return renderStringWithResolver(source.getContent(), values, templateSourceResolver, RenderOptions.DEFAULT);
        }
        return renderString(templateOrTemplateName, values);
    }

    public String renderString(String template, Map<String, Object> values) {
        return renderStringWithResolver(template, values, templateSourceResolver, RenderOptions.DEFAULT);
    }

    public String renderStringWithResolver(String template, Map<String, Object> values, TemplateSourceResolver resolver, RenderOptions options) {
        TemplateContext.EngineRenderDelegate delegate = new TemplateContext.EngineRenderDelegate() {
            @Override
            public String renderStringWithContext(String templateContent, TemplateContext context) {
                return TemplateEngine.this.renderStringWithContext(templateContent, context);
            }
            @Override
            public String renderComponentTemplate(String templateContent, TemplateContext context) {
                return TemplateEngine.this.renderComponentTemplate(templateContent, context);
            }
        };
        TemplateContext context = new TemplateContext(values)
                .withResolver(resolver)
                .withEngine(delegate);
        return renderStringWithContext(template, context, options);
    }

    public String renderComponentTemplate(String template, TemplateContext context) {
        List<io.lemadane.piped.template.engine.compiler.Token> tokens = lexer.tokenize(template);
        var compiled = parser.parse(tokens, true);
        java.io.StringWriter writer = new java.io.StringWriter();
        try {
            compiled.render(context, writer);
        } catch (IOException e) {
            throw new TemplateRenderException("Failed to render component template", e);
        }
        return writer.toString();
    }

    public String renderStringWithContext(String template, TemplateContext context) {
        return renderStringWithContext(template, context, RenderOptions.DEFAULT);
    }

    public String renderStringWithContext(String template, TemplateContext context, RenderOptions options) {
        var executable = compileToBytecode(template);
        java.io.StringWriter writer = new java.io.StringWriter();
        try {
            executable.render(context, writer, this);
        } catch (IOException e) {
            throw new TemplateRenderException("Failed to render template execution", e);
        }
        String output = writer.toString();

        boolean useMinify = options.minify() || this.minify;
        boolean usePrettify = options.prettify() || this.prettify;

        if (useMinify) {
            output = io.lemadane.piped.template.engine.utils.HtmlFormatter.minifyHtml(output);
        } else if (usePrettify) {
            output = io.lemadane.piped.template.engine.utils.HtmlFormatter.prettifyHtml(output);
        }

        return output;
    }

    public String loadTemplateSource(String templateOrTemplateName) {
        if (templateOrTemplateName == null) {
            return "";
        }
        if (!isTemplateReference(templateOrTemplateName)) {
            return templateOrTemplateName;
        }
        try {
            return templateSourceResolver.resolve(templateOrTemplateName).getContent();
        } catch (Exception e) {
            if (!templateOrTemplateName.endsWith(".pte") && !templateOrTemplateName.contains("/") && !templateOrTemplateName.contains("\\")) {
                return templateOrTemplateName;
            }
            throw e;
        }
    }

    public String renderPartial(String templateName, Object value) {
        return renderNamedTemplate(templateName, createContextFromValue(value));
    }

    public String renderFragment(String templateOrTemplateName, String fragmentName, Map<String, Object> values) {
        String templateSource = loadTemplateSource(templateOrTemplateName);
        var compiled = compile(templateSource);
        var rootNode = compiled.getRootNode();
        var context = new TemplateContext(values);

        io.lemadane.piped.template.engine.ast.FragmentNode fragmentNode = findFragmentNode(rootNode, fragmentName);
        if (fragmentNode == null) {
            throw new IllegalArgumentException("Fragment '" + fragmentName + "' not found in template.");
        }

        java.io.StringWriter sw = new java.io.StringWriter();
        try {
            fragmentNode.render(context, sw);
        } catch (IOException e) {
            throw new TemplateRenderException("Failed to render fragment: " + fragmentName, e);
        }
        return sw.toString();
    }

    io.lemadane.piped.template.engine.ast.FragmentNode findFragmentNode(io.lemadane.piped.template.engine.ast.ASTNode node, String name) {
        if (node == null) {
            return null;
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.FragmentNode frag) {
            if (name.equals(frag.getName())) {
                return frag;
            }
            var found = findFragmentNode(frag.getBody(), name);
            if (found != null) return found;
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.BlockNode block) {
            for (io.lemadane.piped.template.engine.ast.ASTNode child : block.getChildren()) {
                var found = findFragmentNode(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.IfNode ifNode) {
            var found = findFragmentNode(ifNode.getThenBlock(), name);
            if (found != null) return found;
            for (io.lemadane.piped.template.engine.ast.IfNode.ElseIfBranch branch : ifNode.getElseIfBranches()) {
                found = findFragmentNode(branch.block(), name);
                if (found != null) return found;
            }
            if (ifNode.getElseBlock() != null) {
                found = findFragmentNode(ifNode.getElseBlock(), name);
                if (found != null) return found;
            }
        }
        if (node instanceof io.lemadane.piped.template.engine.ast.EachNode eachNode) {
            var found = findFragmentNode(eachNode.getBodyBlock(), name);
            if (found != null) return found;
            if (eachNode.getSeparatorNode() != null) {
                found = findFragmentNode(eachNode.getSeparatorNode(), name);
                if (found != null) return found;
            }
        }
        return null;
    }



    String renderTemplateSource(String template, TemplateContext context) {
        final var layoutDirective = findLayoutDirective(template);

        String result;
        if (layoutDirective != null) {
            result = renderTemplateWithLayout(template, context, layoutDirective);
        } else {
            result = renderRange(template, context, 0, template.length());
        }

        if (minify) {
            result = io.lemadane.piped.template.engine.utils.HtmlFormatter.minifyHtml(result);
        } else if (prettify) {
            result = io.lemadane.piped.template.engine.utils.HtmlFormatter.prettifyHtml(result);
        }
        return result;
    }

    String renderInclude(String source, TemplateContext context) {
        final var includeStatement = parseIncludeStatement(source);

        if (includeStatement.modelExpression() == null) {
            return renderNamedTemplate(includeStatement.templateName(), context);
        }

        final var value = expressionEvaluator.evaluate(includeStatement.modelExpression(), context);

        return renderNamedTemplate(
                includeStatement.templateName(),
                createContextFromValue(value));
    }

    IncludeStatement parseIncludeStatement(String source) {
        final var body = source.substring("include ".length()).trim();

        if (body.isBlank()) {
            throw new TemplateSyntaxException("|include| template name must not be empty.");
        }

        if (body.endsWith(" with")) {
            final var templateName = body.substring(0, body.length() - " with".length()).trim();

            if (templateName.isBlank()) {
                throw new TemplateSyntaxException("|include| template name must not be empty.");
            }

            throw new TemplateSyntaxException("|include ... with| expression must not be empty.");
        }

        final var withIndex = findIncludeWithIndex(body);

        if (withIndex == -1) {
            return new IncludeStatement(
                    body,
                    null);
        }

        final var templateName = body.substring(0, withIndex).trim();
        final var modelExpression = body.substring(withIndex + " with ".length()).trim();

        if (templateName.isBlank()) {
            throw new TemplateSyntaxException("|include| template name must not be empty.");
        }

        if (modelExpression.isBlank()) {
            throw new TemplateSyntaxException("|include ... with| expression must not be empty.");
        }

        return new IncludeStatement(
                templateName,
                modelExpression);
    }

    int findIncludeWithIndex(String body) {
        boolean insideSingleQuote = false;
        boolean insideDoubleQuote = false;
        int parenthesisDepth = 0;

        for (int index = 0; index <= body.length() - " with ".length(); index++) {
            final var current = body.charAt(index);

            if (current == '\'' && !insideDoubleQuote) {
                insideSingleQuote = !insideSingleQuote;
                continue;
            }

            if (current == '"' && !insideSingleQuote) {
                insideDoubleQuote = !insideDoubleQuote;
                continue;
            }

            if (insideSingleQuote || insideDoubleQuote) {
                continue;
            }

            if (current == '(') {
                parenthesisDepth++;
                continue;
            }

            if (current == ')') {
                parenthesisDepth--;
                continue;
            }

            if (parenthesisDepth == 0 && body.startsWith(" with ", index)) {
                return index;
            }
        }

        return -1;
    }



    Path resolveTemplatePath(String templateName) {
        final var relativePath = Path.of(templateName + ".pte");

        if (relativePath.isAbsolute()) {
            throw new TemplateSyntaxException("Template name must not be absolute: " + templateName);
        }

        final var resolvedPath = templateRoot.resolve(relativePath).normalize();

        if (!resolvedPath.startsWith(templateRoot)) {
            throw new TemplateSyntaxException("Template name must not escape template root: " + templateName);
        }

        return resolvedPath;
    }

    String normalizeTemplateName(String templateName) {
        final var normalizedName = templateName.trim()
                .replace('\\', '/');

        if (normalizedName.isBlank()) {
            throw new TemplateSyntaxException("Template name must not be empty.");
        }

        if (normalizedName.startsWith("/")) {
            throw new TemplateSyntaxException("Template name must not start with '/': " + templateName);
        }

        if (normalizedName.endsWith(".pte")) {
            return normalizedName.substring(0, normalizedName.length() - ".pte".length());
        }

        return normalizedName;
    }

    String buildCircularIncludeMessage(ArrayDeque<String> stack, String repeatedTemplateName) {
        final var message = new StringBuilder();

        for (final var templateName : stack) {
            message.append(templateName).append(" -> ");
        }

        message.append(repeatedTemplateName);

        return message.toString();
    }

    boolean isTemplateReference(String value) {
        return !value.contains("|")
                && !value.contains("\n")
                && !value.contains("<");
    }

    TemplateContext createContextFromValue(Object value) {
        if (value == null) {
            return new TemplateContext(Map.of());
        }

        if (value instanceof Map<?, ?> map) {
            final var values = new LinkedHashMap<String, Object>();

            for (final var entry : map.entrySet()) {
                values.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            return new TemplateContext(values);
        }

        return new TemplateContext(Map.of(
                "it", value));
    }

    record IncludeStatement(
            String templateName,
            String modelExpression) {
    }

    String renderRange(
            String template,
            TemplateContext context,
            int startIndex,
            int endIndex) {
        final var output = new StringBuilder();

        int index = startIndex;

        while (index < endIndex) {
            final var current = template.charAt(index);

            if (current != '|') {
                output.append(current);
                index++;
                continue;
            }

            if (isCommentStart(template, index)) {
                index = findCommentEndIndex(template, index);
                continue;
            }

            final var closingPipeIndex = template.indexOf('|', index + 1);

            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateRenderException(
                        "Missing closing pipe for expression starting at index " + index + ".");
            }

            final var source = template.substring(index + 1, closingPipeIndex).trim();

            if (source.startsWith("component ")) {
                final var componentBlock = findComponentBlock(
                        template,
                        closingPipeIndex + 1,
                        endIndex);

                output.append(renderComponent(
                        template,
                        context,
                        source,
                        closingPipeIndex + 1,
                        componentBlock));

                index = componentBlock.endEndIndex();
                continue;
            }

            if (source.startsWith("slot ")) {
                output.append(renderSlot(source));

                index = closingPipeIndex + 1;
                continue;
            }

            if ("/slot".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/slot| without matching |slot| at index " + index + ".");
            }

            if ("/component".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/component| without matching |component| at index " + index + ".");
            }

            if (source.isEmpty()) {
                throw new TemplateRenderException(
                        "Empty expression is not allowed at index " + index + ".");
            }

            if (source.startsWith("include ")) {
                output.append(renderInclude(source, context));

                index = closingPipeIndex + 1;
                continue;
            }

            if (source.startsWith("yield ")) {
                output.append(renderYield(source));

                index = closingPipeIndex + 1;
                continue;
            }

            if ("minify".equals(source)) {
                final var minifyBlock = findMinifyBlock(template, closingPipeIndex + 1, endIndex);
                final var innerHtml = renderRange(
                        template,
                        context,
                        closingPipeIndex + 1,
                        minifyBlock.bodyEndIndex());
                output.append(io.lemadane.piped.template.engine.utils.HtmlFormatter.minifyHtml(innerHtml));
                index = minifyBlock.endEndIndex();
                continue;
            }

            if ("/minify".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/minify| without matching |minify| at index " + index + ".");
            }

            if ("attempt".equals(source)) {
                final var attemptBlock = findAttemptBlock(template, closingPipeIndex + 1, endIndex);
                try {
                    String attemptHtml = renderRange(
                            template,
                            context,
                            closingPipeIndex + 1,
                            attemptBlock.attemptBodyEndIndex());
                    output.append(attemptHtml);
                } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException | io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                    throw e;
                } catch (Exception e) {
                    if (attemptBlock.hasRecover()) {
                        TemplateContext nextContext = context;
                        if (attemptBlock.errorVarName() != null && !attemptBlock.errorVarName().isEmpty()) {
                            String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                            nextContext = context.with(attemptBlock.errorVarName(), errorMsg);
                        }
                        String recoverHtml = renderRange(
                                template,
                                nextContext,
                                attemptBlock.recoverBodyStartIndex(),
                                attemptBlock.recoverBodyEndIndex());
                        output.append(recoverHtml);
                    }
                }
                index = attemptBlock.endEndIndex();
                continue;
            }

            if (source.startsWith("recover")) {
                throw new TemplateSyntaxException(
                        "Unexpected |recover| without matching |attempt| at index " + index + ".");
            }

            if ("/attempt".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/attempt| without matching |attempt| at index " + index + ".");
            }

            if (source.startsWith("macro ")) {
                final var header = source.substring("macro ".length()).trim();
                int openParen = header.indexOf('(');
                int closeParen = header.lastIndexOf(')');
                if (openParen == -1 || closeParen == -1 || closeParen < openParen) {
                    throw new TemplateSyntaxException("Invalid macro signature: " + header);
                }
                String macroName = header.substring(0, openParen).trim();
                String paramsStr = header.substring(openParen + 1, closeParen).trim();
                List<String> parameters = new ArrayList<>();
                if (!paramsStr.isEmpty()) {
                    for (String p : paramsStr.split(",")) {
                        parameters.add(p.trim());
                    }
                }
                final var macroBlock = findMacroBlock(template, closingPipeIndex + 1, endIndex);
                final int bodyStart = closingPipeIndex + 1;
                final int bodyEnd = macroBlock.bodyEndIndex();
                final String templ = template;
                io.lemadane.piped.template.engine.ast.ASTNode macroBodyNode = new io.lemadane.piped.template.engine.ast.ASTNode() {
                    @Override
                    public void render(TemplateContext ctx, java.io.Writer w) throws IOException {
                        w.write(renderRange(templ, ctx, bodyStart, bodyEnd));
                    }
                };
                io.lemadane.piped.template.engine.ast.MacroNode macroNode = new io.lemadane.piped.template.engine.ast.MacroNode(macroName, parameters, macroBodyNode);
                context.pushLocal("_macro_" + macroName, macroNode);
                index = macroBlock.endEndIndex();
                continue;
            }

            if ("/macro".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/macro| without matching |macro| at index " + index + ".");
            }

            if (source.startsWith("call ")) {
                final var callHeader = source.substring("call ".length()).trim();
                int openParen = callHeader.indexOf('(');
                int closeParen = callHeader.lastIndexOf(')');
                if (openParen == -1 || closeParen == -1 || closeParen < openParen) {
                    throw new TemplateSyntaxException("Invalid macro call syntax: " + callHeader);
                }
                String macroName = callHeader.substring(0, openParen).trim();
                String argsStr = callHeader.substring(openParen + 1, closeParen).trim();
                List<String> argumentExpressions = new ArrayList<>();
                if (!argsStr.isEmpty()) {
                    argumentExpressions = expressionEvaluator.splitByTopLevelComma(argsStr);
                }
                
                java.io.StringWriter sw = new java.io.StringWriter();
                io.lemadane.piped.template.engine.ast.CallMacroNode callNode = new io.lemadane.piped.template.engine.ast.CallMacroNode(macroName, argumentExpressions, expressionEvaluator);
                try {
                    callNode.render(context, sw);
                } catch (IOException e) {
                    throw new TemplateRenderException("Failed to render macro", e);
                }
                output.append(sw.toString());
                index = closingPipeIndex + 1;
                continue;
            }

            if ("separator".equals(source)) {
                Object eachVal = context.get("each");
                if (eachVal == null) {
                    throw new TemplateSyntaxException("Unexpected |separator| outside |each| loop.");
                }
                final var separatorBlock = findSeparatorBlock(template, closingPipeIndex + 1, endIndex);
                if (!isLastEachItem(context)) {
                    String separatorHtml = renderRange(
                            template,
                            context,
                            closingPipeIndex + 1,
                            separatorBlock.bodyEndIndex());
                    output.append(separatorHtml);
                }
                index = separatorBlock.endEndIndex();
                continue;
            }

            if ("/separator".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/separator| without matching |separator| at index " + index + ".");
            }

            if (source.startsWith("field ")) {
                final var propertyPath = source.substring("field ".length()).trim();
                java.io.StringWriter sw = new java.io.StringWriter();
                var fieldNode = new io.lemadane.piped.template.engine.ast.FieldNode(propertyPath, expressionEvaluator);
                try {
                    fieldNode.render(context, sw);
                } catch (IOException e) {
                    throw new TemplateRenderException("Failed to render field binding", e);
                }
                output.append(sw.toString());
                index = closingPipeIndex + 1;
                continue;
            }

            if (source.startsWith("display ")) {
                final var propertyPath = source.substring("display ".length()).trim();
                java.io.StringWriter sw = new java.io.StringWriter();
                var displayNode = new io.lemadane.piped.template.engine.ast.DisplayNode(propertyPath, expressionEvaluator);
                try {
                    displayNode.render(context, sw);
                } catch (IOException e) {
                    throw new TemplateRenderException("Failed to render display template", e);
                }
                output.append(sw.toString());
                index = closingPipeIndex + 1;
                continue;
            }

            if (source.startsWith("editor ")) {
                final var propertyPath = source.substring("editor ".length()).trim();
                java.io.StringWriter sw = new java.io.StringWriter();
                var editorNode = new io.lemadane.piped.template.engine.ast.EditorNode(propertyPath, expressionEvaluator);
                try {
                    editorNode.render(context, sw);
                } catch (IOException e) {
                    throw new TemplateRenderException("Failed to render editor template", e);
                }
                output.append(sw.toString());
                index = closingPipeIndex + 1;
                continue;
            }

            if (source.startsWith("model ")) {
                index = closingPipeIndex + 1;
                continue;
            }

            if (source.startsWith("fragment ")) {
                final var fragBlock = findFragmentBlock(template, closingPipeIndex + 1, endIndex);
                String fragHtml = renderRange(
                        template,
                        context,
                        closingPipeIndex + 1,
                        fragBlock.bodyEndIndex());
                output.append(fragHtml);
                index = fragBlock.endEndIndex();
                continue;
            }

            if ("/fragment".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/fragment| without matching |fragment| at index " + index + ".");
            }

            if ("pwa".equals(source) || source.startsWith("pwa ")) {
                String val = source.trim();
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

                var pwaNode = new io.lemadane.piped.template.engine.ast.PWANode(
                    name,
                    manifest,
                    theme,
                    icon,
                    sw,
                    statusColor
                );
                java.io.StringWriter swWriter = new java.io.StringWriter();
                try {
                    pwaNode.render(context, swWriter);
                } catch (IOException e) {
                    throw new TemplateRenderException("Failed to render pwa node", e);
                }
                output.append(swWriter.toString());
                index = closingPipeIndex + 1;
                continue;
            }

            if ("htmx".equals(source) || source.startsWith("htmx ")) {
                String val = source.trim();
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
                var htmxNode = new io.lemadane.piped.template.engine.ast.HTMXNode(
                    attrs.get("src"),
                    extensions,
                    attrs.get("config"),
                    indicator
                );
                java.io.StringWriter sw = new java.io.StringWriter();
                try {
                    htmxNode.render(context, sw);
                } catch (IOException e) {
                    throw new TemplateRenderException("Failed to render htmx node", e);
                }
                output.append(sw.toString());
                index = closingPipeIndex + 1;
                continue;
            }

            if (source.startsWith("htmx-get ") || source.startsWith("htmx-post ") || source.startsWith("htmx-put ") || source.startsWith("htmx-delete ") || source.startsWith("htmx-patch ")) {
                String val = source.trim();
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
                var hxAttrNode = new io.lemadane.piped.template.engine.ast.HXAttrNode(
                    method,
                    urlPath,
                    attrs.get("target"),
                    attrs.get("swap"),
                    attrs.get("indicator"),
                    attrs.get("trigger")
                );
                java.io.StringWriter sw = new java.io.StringWriter();
                try {
                    hxAttrNode.render(context, sw);
                } catch (IOException e) {
                    throw new TemplateRenderException("Failed to render htmx attr node", e);
                }
                output.append(sw.toString());
                index = closingPipeIndex + 1;
                continue;
            }

            if ("alpine".equals(source) || source.startsWith("alpine ")) {
                String val = source.trim();
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

                var alpineNode = new io.lemadane.piped.template.engine.ast.AlpineNode(
                    attrs.get("src"),
                    plugins,
                    cloak
                );
                java.io.StringWriter sw = new java.io.StringWriter();
                try {
                    alpineNode.render(context, sw);
                } catch (IOException e) {
                    throw new TemplateRenderException("Failed to render alpine node", e);
                }
                output.append(sw.toString());
                index = closingPipeIndex + 1;
                continue;
            }

            if ("alpine-data".equals(source) || source.startsWith("alpine-data ")) {
                String val = source.trim();
                if (val.startsWith("alpine-data")) {
                    val = val.substring(11).trim();
                }
                java.util.Map<String, String> attrs = parseKeyValuePairs(val);
                var stateNode = new io.lemadane.piped.template.engine.ast.StateNode(attrs);
                java.io.StringWriter sw = new java.io.StringWriter();
                try {
                    stateNode.render(context, sw);
                } catch (IOException e) {
                    throw new TemplateRenderException("Failed to render state node", e);
                }
                output.append(sw.toString());
                index = closingPipeIndex + 1;
                continue;
            }

            if (source.startsWith("alpine-")) {
                String val = source.trim();
                String[] parts = val.split("\\s+", 2);
                String dir = parts[0];
                String expr = "";
                if (parts.length > 1) {
                    expr = parts[1].trim();
                    if (expr.length() > 1 && ((expr.startsWith("'") && expr.endsWith("'")) || (expr.startsWith("\"") && expr.endsWith("\"")))) {
                        expr = expr.substring(1, expr.length() - 1);
                    }
                }
                var alpineAttrNode = new io.lemadane.piped.template.engine.ast.AlpineAttrNode(dir, expr);
                java.io.StringWriter sw = new java.io.StringWriter();
                try {
                    alpineAttrNode.render(context, sw);
                } catch (IOException e) {
                    throw new TemplateRenderException("Failed to render alpine attr node", e);
                }
                output.append(sw.toString());
                index = closingPipeIndex + 1;
                continue;
            }

            if (source.startsWith("layout ")) {
                throw new TemplateSyntaxException(
                        "|layout| must be the first directive in a template.");
            }

            if (source.startsWith("section ")) {
                throw new TemplateSyntaxException(
                        "Unexpected |section| outside a layout page.");
            }

            if ("/section".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/section| without matching |section|.");
            }

            if (source.startsWith("if ")) {
                final var ifExpression = source.substring("if ".length()).trim();
                final var ifBlock = findIfBlock(template, closingPipeIndex + 1, endIndex);

                try {
                    output.append(renderIfBlock(
                            template,
                            context,
                            ifExpression,
                            closingPipeIndex + 1,
                            ifBlock));
                } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {
                    output.append(e.getPartialOutput());
                    throw new io.lemadane.piped.template.engine.exceptions.LoopContinueException(output.toString());
                } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                    output.append(e.getPartialOutput());
                    throw new io.lemadane.piped.template.engine.exceptions.LoopBreakException(output.toString());
                }

                index = ifBlock.endEndIndex();
                continue;
            }

            if (source.startsWith("for ")) {
                final var forBlock = findForBlock(template, closingPipeIndex + 1, endIndex);
                output.append(renderForBlock(
                        template,
                        context,
                        source,
                        forBlock,
                        closingPipeIndex + 1));

                index = forBlock.endEndIndex();
                continue;
            }

            if ("/for".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/for| without matching |for| at index " + index + ".");
            }

            if ("continue".equals(source)) {
                if (loopDepth.get() == 0) {
                    throw new TemplateSyntaxException(
                            "|continue| is only allowed inside a loop at index " + index + ".");
                }
                throw new io.lemadane.piped.template.engine.exceptions.LoopContinueException(output.toString());
            }

            if ("break".equals(source)) {
                if (loopDepth.get() == 0) {
                    throw new TemplateSyntaxException(
                            "|break| is only allowed inside a loop at index " + index + ".");
                }
                throw new io.lemadane.piped.template.engine.exceptions.LoopBreakException(output.toString());
            }

            if (source.startsWith("each ")) {
                final var eachStatement = eachStatementParser.parse(source);
                final var eachBlock = findEachBlock(template, closingPipeIndex + 1, endIndex);
                output.append(renderEachBlock(
                        template,
                        context,
                        eachStatement,
                        eachBlock,
                        closingPipeIndex + 1));

                index = eachBlock.endEndIndex();
                continue;
            }
            if (source.startsWith("switch ")) {
                final var switchExpression = source.substring("switch ".length()).trim();
                final var switchBlock = findSwitchBlock(template, closingPipeIndex + 1, endIndex);
                try {
                    output.append(renderSwitchBlock(
                            template,
                            context,
                            switchExpression,
                            switchBlock));
                } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {
                    output.append(e.getPartialOutput());
                    throw new io.lemadane.piped.template.engine.exceptions.LoopContinueException(output.toString());
                } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                    output.append(e.getPartialOutput());
                    throw new io.lemadane.piped.template.engine.exceptions.LoopBreakException(output.toString());
                }

                index = switchBlock.endEndIndex();
                continue;
            }

            if (isElseIfSource(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |else-if| without matching |if| at index " + index + ".");
            }

            if ("else".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |else| without matching block at index " + index + ".");
            }

            if ("/if".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/if| without matching |if| at index " + index + ".");
            }

            if ("/each".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/each| without matching |each| at index " + index + ".");
            }

            if ("case".equals(source) || source.startsWith("case ")) {
                throw new TemplateSyntaxException(
                        "Unexpected |case| without matching |switch| at index " + index + ".");
            }

            if ("default".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |default| without matching |switch| at index " + index + ".");
            }

            if ("fallthrough".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |fallthrough| without matching |switch| case at index " + index + ".");
            }

            if ("/switch".equals(source)) {
                throw new TemplateSyntaxException(
                        "Unexpected |/switch| without matching |switch| at index " + index + ".");
            }
            final var conditionalOutputExpression = parseConditionalOutputExpression(source);
            final var outputExpression = outputExpressionParser.parse(
                    conditionalOutputExpression.outputSource());
            if (conditionalOutputExpression.conditionExpression() != null
                    && !expressionEvaluator.evaluateBoolean(
                            conditionalOutputExpression.conditionExpression(),
                            context)) {
                if (outputExpression.mode() == OutputMode.ATTRIBUTE_ESCAPED) {
                    removeTrailingAttributeWhitespace(output);
                    index = skipWhitespaceBeforeTagClose(
                            template,
                            closingPipeIndex + 1);
                } else {
                    index = closingPipeIndex + 1;
                }
                continue;
            }
            if (conditionalOutputExpression.conditionExpression() != null
                    && outputExpression.mode() == OutputMode.ATTRIBUTE_ESCAPED) {
                final var attributeOutput = renderConditionalAttributeOutput(
                        outputExpression.expression(),
                        context);
                if (attributeOutput != null) {
                    output.append(attributeOutput);
                    index = closingPipeIndex + 1;
                    continue;
                }
            }
            final var value = expressionEvaluator.evaluate(
                    outputExpression.expression(),
                    context);
            output.append(renderValue(outputExpression.mode(), value));
            index = closingPipeIndex + 1;
        }
        return output.toString();
    }

    String renderIfBlock(
            String template,
            TemplateContext context,
            String ifExpression,
            int ifBodyStartIndex,
            IfBlock ifBlock) {
        if (expressionEvaluator.evaluateBoolean(ifExpression, context)) {
            return renderRange(
                    template,
                    context,
                    ifBodyStartIndex,
                    ifBlock.ifBodyEndIndex());
        }

        for (final var elseIfBlock : ifBlock.elseIfBlocks()) {
            if (expressionEvaluator.evaluateBoolean(elseIfBlock.expression(), context)) {
                return renderRange(
                        template,
                        context,
                        elseIfBlock.bodyStartIndex(),
                        elseIfBlock.bodyEndIndex());
            }
        }

        if (ifBlock.elseBlock() == null) {
            return "";
        }

        return renderRange(
                template,
                context,
                ifBlock.elseBlock().bodyStartIndex(),
                ifBlock.elseBlock().bodyEndIndex());
    }

    String renderEachBlock(
            String template,
            TemplateContext context,
            EachStatement eachStatement,
            EachBlock eachBlock,
            int bodyStartIndex) {
        final var collectionValue = expressionEvaluator.evaluate(
                eachStatement.collectionExpression(),
                context);

        if (eachStatement.mapLoop()) {
            return renderMapEachBlock(
                    template,
                    context,
                    eachStatement,
                    eachBlock,
                    bodyStartIndex,
                    collectionValue);
        }

        return renderCollectionEachBlock(
                template,
                context,
                eachStatement,
                eachBlock,
                bodyStartIndex,
                collectionValue);
    }

    String renderCollectionEachBlock(
            String template,
            TemplateContext context,
            EachStatement eachStatement,
            EachBlock eachBlock,
            int bodyStartIndex,
            Object collectionValue) {
        if (collectionValue instanceof Map<?, ?> map) {
            return renderMapEntryEachBlock(
                    template,
                    context,
                    eachStatement,
                    eachBlock,
                    bodyStartIndex,
                    map);
        }

        final var items = toList(collectionValue);

        if (items.isEmpty()) {
            if (!eachBlock.hasElse()) {
                return "";
            }

            return renderRange(
                    template,
                    context,
                    eachBlock.elseEndIndex(),
                    eachBlock.endStartIndex());
        }

        final var itemBodyEndIndex = eachBlock.hasElse()
                ? eachBlock.elseStartIndex()
                : eachBlock.endStartIndex();

        final var output = new StringBuilder();

        int currentDepth = loopDepth.get();
        loopDepth.set(currentDepth + 1);
        try {
            for (int index = 0; index < items.size(); index++) {
                final var childValues = new HashMap<String, Object>();
                childValues.put(eachStatement.itemName(), items.get(index));
                childValues.put("each", EachMetadata.of(index, items.size()));

                try {
                    output.append(renderRange(
                            template,
                            context.withAll(childValues),
                            bodyStartIndex,
                            itemBodyEndIndex));
                } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {
                    output.append(e.getPartialOutput());
                } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                    output.append(e.getPartialOutput());
                    break;
                }
            }
        } finally {
            loopDepth.set(currentDepth);
        }

        return output.toString();
    }

    String renderMapEntryEachBlock(
            String template,
            TemplateContext context,
            EachStatement eachStatement,
            EachBlock eachBlock,
            int bodyStartIndex,
            Map<?, ?> map) {
        final var entries = new ArrayList<>(map.entrySet());

        if (entries.isEmpty()) {
            if (!eachBlock.hasElse()) {
                return "";
            }

            return renderRange(
                    template,
                    context,
                    eachBlock.elseEndIndex(),
                    eachBlock.endStartIndex());
        }

        final var itemBodyEndIndex = eachBlock.hasElse()
                ? eachBlock.elseStartIndex()
                : eachBlock.endStartIndex();

        final var output = new StringBuilder();

        int currentDepth = loopDepth.get();
        loopDepth.set(currentDepth + 1);
        try {
            for (int index = 0; index < entries.size(); index++) {
                final var childValues = new HashMap<String, Object>();
                childValues.put(eachStatement.itemName(), entries.get(index));
                childValues.put("each", EachMetadata.of(index, entries.size()));

                try {
                    output.append(renderRange(
                            template,
                            context.withAll(childValues),
                            bodyStartIndex,
                            itemBodyEndIndex));
                } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {
                    output.append(e.getPartialOutput());
                } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                    output.append(e.getPartialOutput());
                    break;
                }
            }
        } finally {
            loopDepth.set(currentDepth);
        }

        return output.toString();
    }

    String renderMapEachBlock(
            String template,
            TemplateContext context,
            EachStatement eachStatement,
            EachBlock eachBlock,
            int bodyStartIndex,
            Object collectionValue) {
        if (collectionValue == null) {
            if (!eachBlock.hasElse()) {
                return "";
            }

            return renderRange(
                    template,
                    context,
                    eachBlock.elseEndIndex(),
                    eachBlock.endStartIndex());
        }

        if (!(collectionValue instanceof Map<?, ?> map)) {
            throw new TemplateRenderException(
                    "Expected map for expression '" + eachStatement.collectionExpression() + "'.");
        }

        final var entries = new ArrayList<>(map.entrySet());

        if (entries.isEmpty()) {
            if (!eachBlock.hasElse()) {
                return "";
            }

            return renderRange(
                    template,
                    context,
                    eachBlock.elseEndIndex(),
                    eachBlock.endStartIndex());
        }

        final var itemBodyEndIndex = eachBlock.hasElse()
                ? eachBlock.elseStartIndex()
                : eachBlock.endStartIndex();

        final var output = new StringBuilder();

        int currentDepth = loopDepth.get();
        loopDepth.set(currentDepth + 1);
        try {
            for (int index = 0; index < entries.size(); index++) {
                final var entry = entries.get(index);

                final var childValues = new HashMap<String, Object>();
                childValues.put(eachStatement.keyName(), entry.getKey());
                childValues.put(eachStatement.valueName(), entry.getValue());
                childValues.put("each", EachMetadata.of(index, entries.size()));

                try {
                    output.append(renderRange(
                            template,
                            context.withAll(childValues),
                            bodyStartIndex,
                            itemBodyEndIndex));
                } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {
                    output.append(e.getPartialOutput());
                } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                    output.append(e.getPartialOutput());
                    break;
                }
            }
        } finally {
            loopDepth.set(currentDepth);
        }

        return output.toString();
    }

    record ForBlock(
            int elseStartIndex,
            int elseEndIndex,
            int endStartIndex,
            int endEndIndex) {
        public boolean hasElse() {
            return elseStartIndex != -1;
        }
    }

    ForBlock findForBlock(String template, int searchStartIndex, int endIndex) {
        int forDepth = 1;
        int eachDepth = 0;
        int ifDepth = 0;
        int switchDepth = 0;
        int index = searchStartIndex;

        int elseStartIndex = -1;
        int elseEndIndex = -1;

        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);

            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }

            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }

            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);

            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index "
                                + openingPipeIndex
                                + ".");
            }

            final var source = template.substring(
                    openingPipeIndex + 1,
                    closingPipeIndex)
                    .trim();

            if (source.startsWith("switch ")) {
                switchDepth++;
            } else if ("/switch".equals(source)) {
                switchDepth--;
            } else if (source.startsWith("if ")) {
                ifDepth++;
            } else if ("/if".equals(source)) {
                ifDepth--;
            } else if (source.startsWith("each ")) {
                eachDepth++;
            } else if ("/each".equals(source)) {
                eachDepth--;
            } else if (source.startsWith("for ")) {
                forDepth++;
            } else if ("/for".equals(source)) {
                forDepth--;

                if (forDepth == 0 && eachDepth == 0 && ifDepth == 0 && switchDepth == 0) {
                    return new ForBlock(
                            elseStartIndex,
                            elseEndIndex,
                            openingPipeIndex,
                            closingPipeIndex + 1);
                }
            } else if ("else".equals(source) && forDepth == 1 && eachDepth == 0 && ifDepth == 0 && switchDepth == 0) {
                if (elseStartIndex != -1) {
                    throw new TemplateSyntaxException(
                            "Only one |else| is allowed inside a |for| block.");
                }

                elseStartIndex = openingPipeIndex;
                elseEndIndex = closingPipeIndex + 1;
            }

            index = closingPipeIndex + 1;
        }

        throw new TemplateSyntaxException("Missing closing |/for|.");
    }

    String renderForBlock(
            String template,
            TemplateContext context,
            String source,
            ForBlock forBlock,
            int bodyStartIndex) {
        String statement = source.substring("for ".length()).trim();
        int fromIndex = statement.indexOf(" from ");
        if (fromIndex == -1) {
            throw new TemplateSyntaxException("Invalid for statement format. Missing 'from' keyword.");
        }

        String varName = statement.substring(0, fromIndex).trim();
        if (varName.isEmpty()) {
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

        Object rawStart = expressionEvaluator.evaluate(startExpr, context);
        int start = toInt(rawStart, startExpr);

        Object rawEnd = expressionEvaluator.evaluate(endExpr, context);
        int end = toInt(rawEnd, endExpr);

        int step = 1;
        if (stepExpr != null && !stepExpr.isEmpty()) {
            Object rawStep = expressionEvaluator.evaluate(stepExpr, context);
            step = toInt(rawStep, stepExpr);
        }

        if (step <= 0) {
            throw new TemplateRenderException("Step must be a positive integer, got: " + step);
        }

        final var itemBodyEndIndex = forBlock.hasElse()
                ? forBlock.elseStartIndex()
                : forBlock.endStartIndex();

        final var output = new StringBuilder();
        boolean executedAtLeastOnce = false;

        int currentDepth = loopDepth.get();
        loopDepth.set(currentDepth + 1);
        try {
            if (start < end) {
                for (int current = start; current <= end; current += step) {
                    executedAtLeastOnce = true;
                    TemplateContext subContext = context.with(varName, current);
                    try {
                        output.append(renderRange(
                                template,
                                subContext,
                                bodyStartIndex,
                                itemBodyEndIndex));
                    } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {
                        output.append(e.getPartialOutput());
                    } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                        output.append(e.getPartialOutput());
                        break;
                    }
                }
            } else if (start > end) {
                for (int current = start; current >= end; current -= step) {
                    executedAtLeastOnce = true;
                    TemplateContext subContext = context.with(varName, current);
                    try {
                        output.append(renderRange(
                                template,
                                subContext,
                                bodyStartIndex,
                                itemBodyEndIndex));
                    } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {
                        output.append(e.getPartialOutput());
                    } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                        output.append(e.getPartialOutput());
                        break;
                    }
                }
            } else {
                // start == end
                executedAtLeastOnce = true;
                TemplateContext subContext = context.with(varName, start);
                try {
                    output.append(renderRange(
                            template,
                            subContext,
                            bodyStartIndex,
                            itemBodyEndIndex));
                } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {
                    output.append(e.getPartialOutput());
                } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                    output.append(e.getPartialOutput());
                }
            }
        } finally {
            loopDepth.set(currentDepth);
        }

        if (!executedAtLeastOnce && forBlock.hasElse()) {
            return renderRange(
                    template,
                    context,
                    forBlock.elseEndIndex(),
                    forBlock.endStartIndex());
        }

        return output.toString();
    }

    int toInt(Object val, String expr) {
        if (val == null) {
            throw new TemplateRenderException("Expression '" + expr + "' evaluated to null");
        }
        if (val instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            throw new TemplateRenderException("Invalid integer value for expression '" + expr + "': " + val);
        }
    }

    String renderSwitchBlock(
            String template,
            TemplateContext context,
            String switchExpression,
            SwitchBlock switchBlock) {
        final var switchValue = expressionEvaluator.evaluate(switchExpression, context);
        final var output = new StringBuilder();

        boolean matched = false;
        boolean fallthrough = false;

        for (final var switchCase : switchBlock.cases()) {
            final var caseValue = expressionEvaluator.evaluate(switchCase.caseExpression(), context);
            final var caseMatches = fallthrough || expressionEvaluator.valuesEqual(switchValue, caseValue);

            if (!caseMatches) {
                continue;
            }

            matched = true;

            output.append(renderSwitchSection(
                    template,
                    context,
                    switchCase.bodyStartIndex(),
                    switchCase.bodyEndIndex(),
                    switchCase.fallthroughStartIndex(),
                    switchCase.fallthroughEndIndex()));

            if (!switchCase.hasFallthrough()) {
                return output.toString();
            }

            fallthrough = true;
        }

        if ((fallthrough || !matched) && switchBlock.defaultBlock() != null) {
            final var defaultBlock = switchBlock.defaultBlock();

            output.append(renderSwitchSection(
                    template,
                    context,
                    defaultBlock.bodyStartIndex(),
                    defaultBlock.bodyEndIndex(),
                    defaultBlock.fallthroughStartIndex(),
                    defaultBlock.fallthroughEndIndex()));
        }

        return output.toString();
    }

    String renderSwitchSection(
            String template,
            TemplateContext context,
            int bodyStartIndex,
            int bodyEndIndex,
            int fallthroughStartIndex,
            int fallthroughEndIndex) {
        if (fallthroughStartIndex == -1) {
            return renderRange(template, context, bodyStartIndex, bodyEndIndex);
        }

        return renderRange(template, context, bodyStartIndex, fallthroughStartIndex)
                + renderRange(template, context, fallthroughEndIndex, bodyEndIndex);
    }

    List<Object> toList(Object value) {
        if (value == null) {
            return List.of();
        }

        if (value instanceof Iterable<?> iterable) {
            final var items = new ArrayList<>();

            for (final var item : iterable) {
                items.add(item);
            }

            return items;
        }

        if (value.getClass().isArray()) {
            final var length = Array.getLength(value);
            final var items = new ArrayList<>();

            for (int index = 0; index < length; index++) {
                items.add(Array.get(value, index));
            }

            return items;
        }

        throw new TemplateRenderException(
                "Value is not iterable: " + value.getClass().getName());
    }

    IfBlock findIfBlock(String template, int searchStartIndex, int endIndex) {
        int ifDepth = 1;
        int eachDepth = 0;
        int switchDepth = 0;
        int forDepth = 0;
        int index = searchStartIndex;

        int ifBodyEndIndex = -1;
        var currentBodyStartIndex = searchStartIndex;
        String currentElseIfExpression = null;
        boolean insideElse = false;

        final var elseIfBlocks = new ArrayList<ElseIfBlock>();
        ElseBlock elseBlock = null;

        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);

            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }

            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }

            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);

            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }

            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();
            final var topLevelIfControl = ifDepth == 1 && eachDepth == 0 && switchDepth == 0 && forDepth == 0;

            if (topLevelIfControl && isElseIfSource(source)) {
                if (insideElse) {
                    throw new TemplateSyntaxException("|else-if| is not allowed after |else|.");
                }

                if (currentElseIfExpression == null) {
                    ifBodyEndIndex = openingPipeIndex;
                } else {
                    elseIfBlocks.add(new ElseIfBlock(
                            currentElseIfExpression,
                            currentBodyStartIndex,
                            openingPipeIndex));
                }

                currentElseIfExpression = extractElseIfExpression(source);
                currentBodyStartIndex = closingPipeIndex + 1;

                if (currentElseIfExpression.isBlank()) {
                    throw new TemplateSyntaxException("|else-if| expression must not be empty.");
                }

                index = closingPipeIndex + 1;
                continue;
            }

            if (topLevelIfControl && "else".equals(source)) {
                if (insideElse) {
                    throw new TemplateSyntaxException("Only one |else| is allowed inside |if|.");
                }

                if (currentElseIfExpression == null) {
                    ifBodyEndIndex = openingPipeIndex;
                } else {
                    elseIfBlocks.add(new ElseIfBlock(
                            currentElseIfExpression,
                            currentBodyStartIndex,
                            openingPipeIndex));
                }

                insideElse = true;
                currentElseIfExpression = null;
                currentBodyStartIndex = closingPipeIndex + 1;

                index = closingPipeIndex + 1;
                continue;
            }

            if (topLevelIfControl && "/if".equals(source)) {
                if (insideElse) {
                    elseBlock = new ElseBlock(
                            currentBodyStartIndex,
                            openingPipeIndex);
                } else if (currentElseIfExpression == null) {
                    ifBodyEndIndex = openingPipeIndex;
                } else {
                    elseIfBlocks.add(new ElseIfBlock(
                            currentElseIfExpression,
                            currentBodyStartIndex,
                            openingPipeIndex));
                }

                return new IfBlock(
                        ifBodyEndIndex,
                        List.copyOf(elseIfBlocks),
                        elseBlock,
                        openingPipeIndex,
                        closingPipeIndex + 1);
            }

            if (source.startsWith("if ")) {
                ifDepth++;
            } else if ("/if".equals(source)) {
                ifDepth--;
            } else if (source.startsWith("each ")) {
                eachDepth++;
            } else if ("/each".equals(source)) {
                eachDepth--;
            } else if (source.startsWith("for ")) {
                forDepth++;
            } else if ("/for".equals(source)) {
                forDepth--;
            } else if (source.startsWith("switch ")) {
                switchDepth++;
            } else if ("/switch".equals(source)) {
                switchDepth--;
            }

            index = closingPipeIndex + 1;
        }

        throw new TemplateSyntaxException("Missing closing |/if|.");
    }

    boolean isElseIfSource(String source) {
        return source.startsWith("else-if ");
    }

    String extractElseIfExpression(String source) {
        return source.substring("else-if ".length()).trim();
    }

    EachBlock findEachBlock(String template, int searchStartIndex, int endIndex) {
        int eachDepth = 1;
        int ifDepth = 0;
        int switchDepth = 0;
        int forDepth = 0;
        int index = searchStartIndex;

        int elseStartIndex = -1;
        int elseEndIndex = -1;

        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);

            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }

            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }

            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);

            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index "
                                + openingPipeIndex
                                + ".");
            }

            final var source = template.substring(
                    openingPipeIndex + 1,
                    closingPipeIndex)
                    .trim();

            if (source.startsWith("switch ")) {
                switchDepth++;
            } else if ("/switch".equals(source)) {
                switchDepth--;
            } else if (source.startsWith("if ")) {
                ifDepth++;
            } else if ("/if".equals(source)) {
                ifDepth--;
            } else if (source.startsWith("for ")) {
                forDepth++;
            } else if ("/for".equals(source)) {
                forDepth--;
            } else if (source.startsWith("each ")) {
                eachDepth++;
            } else if ("/each".equals(source)) {
                eachDepth--;

                if (eachDepth == 0 && ifDepth == 0 && switchDepth == 0 && forDepth == 0) {
                    return new EachBlock(
                            elseStartIndex,
                            elseEndIndex,
                            openingPipeIndex,
                            closingPipeIndex + 1);
                }
            } else if ("else".equals(source) && eachDepth == 1 && ifDepth == 0 && switchDepth == 0 && forDepth == 0) {
                if (elseStartIndex != -1) {
                    throw new TemplateSyntaxException(
                            "Only one |else| is allowed inside an |each| block.");
                }

                elseStartIndex = openingPipeIndex;
                elseEndIndex = closingPipeIndex + 1;
            }

            index = closingPipeIndex + 1;
        }

        throw new TemplateSyntaxException("Missing closing |/each|.");
    }

    SwitchBlock findSwitchBlock(String template, int searchStartIndex, int endIndex) {
        int switchDepth = 1;
        int ifDepth = 0;
        int eachDepth = 0;
        int forDepth = 0;
        int index = searchStartIndex;

        final var cases = new ArrayList<SwitchCaseBlock>();
        SwitchDefaultBlock defaultBlock = null;
        SwitchSectionBuilder currentSection = null;

        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);

            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }

            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }

            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);

            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }

            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();

            final var topLevelSwitchControl = switchDepth == 1 && ifDepth == 0 && eachDepth == 0 && forDepth == 0;

            if (topLevelSwitchControl && source.startsWith("case ")) {
                if (defaultBlock != null || currentSection != null && currentSection.defaultSection) {
                    throw new TemplateSyntaxException("|case| is not allowed after |default|.");
                }

                if (currentSection != null) {
                    currentSection.bodyEndIndex = openingPipeIndex;
                    cases.add(currentSection.toCaseBlock());
                }

                final var caseExpression = source.substring("case ".length()).trim();

                if (caseExpression.isBlank()) {
                    throw new TemplateSyntaxException("|case| expression must not be empty.");
                }

                currentSection = SwitchSectionBuilder.caseSection(
                        caseExpression,
                        closingPipeIndex + 1);

                index = closingPipeIndex + 1;
                continue;
            }

            if (topLevelSwitchControl && "default".equals(source)) {
                if (defaultBlock != null || currentSection != null && currentSection.defaultSection) {
                    throw new TemplateSyntaxException("Only one |default| is allowed inside |switch|.");
                }

                if (currentSection != null) {
                    currentSection.bodyEndIndex = openingPipeIndex;
                    cases.add(currentSection.toCaseBlock());
                }

                currentSection = SwitchSectionBuilder.defaultSection(closingPipeIndex + 1);

                index = closingPipeIndex + 1;
                continue;
            }

            if (topLevelSwitchControl && "fallthrough".equals(source)) {
                if (currentSection == null) {
                    throw new TemplateSyntaxException("|fallthrough| must be inside a |case| or |default|.");
                }

                if (currentSection.fallthroughStartIndex != -1) {
                    throw new TemplateSyntaxException("Only one |fallthrough| is allowed per switch section.");
                }

                currentSection.fallthroughStartIndex = openingPipeIndex;
                currentSection.fallthroughEndIndex = closingPipeIndex + 1;

                index = closingPipeIndex + 1;
                continue;
            }

            if (source.startsWith("if ")) {
                ifDepth++;
            } else if ("/if".equals(source)) {
                ifDepth--;
            } else if (source.startsWith("each ")) {
                eachDepth++;
            } else if ("/each".equals(source)) {
                eachDepth--;
            } else if (source.startsWith("for ")) {
                forDepth++;
            } else if ("/for".equals(source)) {
                forDepth--;
            } else if (source.startsWith("switch ")) {
                switchDepth++;
            } else if ("/switch".equals(source)) {
                switchDepth--;

                if (switchDepth == 0 && ifDepth == 0 && eachDepth == 0 && forDepth == 0) {
                    if (currentSection != null) {
                        currentSection.bodyEndIndex = openingPipeIndex;

                        if (currentSection.defaultSection) {
                            defaultBlock = currentSection.toDefaultBlock();
                        } else {
                            cases.add(currentSection.toCaseBlock());
                        }
                    }

                    return new SwitchBlock(
                            List.copyOf(cases),
                            defaultBlock,
                            openingPipeIndex,
                            closingPipeIndex + 1);
                }
            }

            index = closingPipeIndex + 1;
        }

        throw new TemplateSyntaxException("Missing closing |/switch|.");
    }

    boolean isCommentStart(String template, int openingPipeIndex) {
        return openingPipeIndex + 1 < template.length()
                && template.charAt(openingPipeIndex + 1) == '#';
    }

    int findCommentEndIndex(String template, int openingPipeIndex) {
        final var commentStartIndex = openingPipeIndex + 2;
        final var singleLineEndIndex = template.indexOf('|', commentStartIndex);
        final var blockEndIndex = template.indexOf("#|", commentStartIndex);

        if (blockEndIndex != -1
                && isBlockComment(template, commentStartIndex, singleLineEndIndex, blockEndIndex)) {
            return blockEndIndex + 2;
        }

        if (singleLineEndIndex == -1) {
            throw new TemplateSyntaxException(
                    "Missing closing pipe for comment starting at index " + openingPipeIndex + ".");
        }

        return singleLineEndIndex + 1;
    }

    boolean isBlockComment(
            String template,
            int commentStartIndex,
            int singleLineEndIndex,
            int blockEndIndex) {
        if (singleLineEndIndex == blockEndIndex + 1) {
            return true;
        }

        final var firstLineBreakIndex = findFirstLineBreakIndex(template, commentStartIndex);

        return firstLineBreakIndex != -1
                && (singleLineEndIndex == -1 || firstLineBreakIndex < singleLineEndIndex);
    }

    int findFirstLineBreakIndex(String template, int startIndex) {
        for (int index = startIndex; index < template.length(); index++) {
            final var current = template.charAt(index);

            if (current == '\n' || current == '\r') {
                return index;
            }
        }

        return -1;
    }

    String renderValue(OutputMode mode, Object value) {
        return switch (mode) {
            case HTML_ESCAPED -> htmlEscaper.escape(value);
            case TRUSTED_HTML -> value == null ? "" : String.valueOf(value);
            case ATTRIBUTE_ESCAPED -> attributeEscaper.escape(value);
            case JSON_ENCODED -> jsonEscaper.escape(value);
            case URL_ENCODED -> urlEscaper.escape(value);
            default -> throw new IllegalArgumentException("Unexpected value: " + mode);
        };
    }

    String renderTemplateWithLayout(
            String template,
            TemplateContext context,
            LayoutDirective layoutDirective) {
        final var sections = collectSections(
                template,
                context,
                layoutDirective.endIndex(),
                template.length());

        final var stack = sectionStack.get();
        stack.addLast(sections);

        try {
            return renderNamedTemplate(layoutDirective.templateName(), context);
        } finally {
            stack.removeLast();
        }
    }

    LayoutDirective findLayoutDirective(String template) {
        int index = 0;

        while (index < template.length() && Character.isWhitespace(template.charAt(index))) {
            index++;
        }

        if (index >= template.length() || template.charAt(index) != '|') {
            return null;
        }

        final var closingPipeIndex = template.indexOf('|', index + 1);

        if (closingPipeIndex == -1) {
            final var remainingSource = template.substring(index + 1).trim();
            if ("layout".equals(remainingSource)
                    || remainingSource.startsWith("layout ")) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for |layout| directive.");
            }
            return null;
        }

        final var source = template.substring(index + 1, closingPipeIndex).trim();

        if (!source.startsWith("layout ")) {
            return null;
        }

        final var templateName = source.substring("layout ".length()).trim();

        if (templateName.isBlank()) {
            throw new TemplateSyntaxException("|layout| template name must not be empty.");
        }

        return new LayoutDirective(
                templateName,
                index,
                closingPipeIndex + 1);
    }

    Map<String, String> collectSections(
            String template,
            TemplateContext context,
            int startIndex,
            int endIndex) {
        final var sections = new LinkedHashMap<String, String>();
        int index = startIndex;

        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);

            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                validateOnlyWhitespaceOutsideSections(
                        template,
                        index,
                        endIndex);

                break;
            }

            validateOnlyWhitespaceOutsideSections(
                    template,
                    index,
                    openingPipeIndex);

            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }

            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);

            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }

            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();

            if (!source.startsWith("section ")) {
                throw new TemplateSyntaxException(
                        "Only |section name| blocks are allowed after |layout|.");
            }

            final var sectionName = source.substring("section ".length()).trim();

            if (sectionName.isBlank()) {
                throw new TemplateSyntaxException("|section| name must not be empty.");
            }

            if (sections.containsKey(sectionName)) {
                throw new TemplateSyntaxException("Duplicate section: " + sectionName);
            }

            final var sectionBlock = findSectionBlock(
                    template,
                    closingPipeIndex + 1,
                    endIndex);

            final var sectionHtml = renderRange(
                    template,
                    context,
                    closingPipeIndex + 1,
                    sectionBlock.bodyEndIndex());

            sections.put(sectionName, sectionHtml);

            index = sectionBlock.endEndIndex();
        }

        return Map.copyOf(sections);
    }

    void validateOnlyWhitespaceOutsideSections(
            String template,
            int startIndex,
            int endIndex) {
        if (startIndex >= endIndex) {
            return;
        }

        final var text = template.substring(startIndex, endIndex);

        if (!text.isBlank()) {
            throw new TemplateSyntaxException(
                    "Text outside |section| blocks is not allowed in a template that uses |layout|.");
        }
    }

    SectionBlock findSectionBlock(String template, int searchStartIndex, int endIndex) {
        int ifDepth = 0;
        int eachDepth = 0;
        int switchDepth = 0;
        int index = searchStartIndex;

        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);

            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }

            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }

            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);

            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }

            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();
            final var topLevelSectionControl = ifDepth == 0 && eachDepth == 0 && switchDepth == 0;

            if (topLevelSectionControl && source.startsWith("section ")) {
                throw new TemplateSyntaxException("Nested |section| blocks are not allowed.");
            }

            if (topLevelSectionControl && "/section".equals(source)) {
                return new SectionBlock(
                        searchStartIndex,
                        openingPipeIndex,
                        openingPipeIndex,
                        closingPipeIndex + 1);
            }

            if (source.startsWith("if ")) {
                ifDepth++;
            } else if ("/if".equals(source)) {
                ifDepth--;
            } else if (source.startsWith("each ")) {
                eachDepth++;
            } else if ("/each".equals(source)) {
                eachDepth--;
            } else if (source.startsWith("switch ")) {
                switchDepth++;
            } else if ("/switch".equals(source)) {
                switchDepth--;
            }

            index = closingPipeIndex + 1;
        }

        throw new TemplateSyntaxException("Missing closing |/section|.");
    }

    String renderYield(String source) {
        final var sectionName = source.substring("yield ".length()).trim();

        if (sectionName.isBlank()) {
            throw new TemplateSyntaxException("|yield| section name must not be empty.");
        }

        final var stack = sectionStack.get();

        if (stack.isEmpty()) {
            throw new TemplateSyntaxException("|yield| can only be used inside a layout template.");
        }

        return stack.peekLast().getOrDefault(sectionName, "");
    }

    String renderComponent(
            String template,
            TemplateContext context,
            String source,
            int bodyStartIndex,
            ComponentBlock componentBlock) {
        final var componentName = source.substring("component ".length()).trim();

        if (componentName.isBlank()) {
            throw new TemplateSyntaxException("|component| template name must not be empty.");
        }

        final var slots = collectSlots(
                template,
                context,
                bodyStartIndex,
                componentBlock.endStartIndex());

        final var stack = slotStack.get();
        stack.addLast(slots);

        try {
            return renderNamedTemplate(componentName, context);
        } finally {
            stack.removeLast();
        }
    }

    String renderSlot(String source) {
        final var slotName = source.substring("slot ".length()).trim();

        if (slotName.isBlank()) {
            throw new TemplateSyntaxException("|slot| name must not be empty.");
        }

        final var stack = slotStack.get();

        if (stack.isEmpty()) {
            throw new TemplateSyntaxException("|slot| can only be rendered inside a component template.");
        }

        return stack.peekLast().getOrDefault(slotName, "");
    }

    Map<String, String> collectSlots(
            String template,
            TemplateContext context,
            int startIndex,
            int endIndex) {
        final var slots = new LinkedHashMap<String, String>();
        int index = startIndex;

        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);

            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                validateOnlyWhitespaceOutsideSlots(
                        template,
                        index,
                        endIndex);

                break;
            }

            validateOnlyWhitespaceOutsideSlots(
                    template,
                    index,
                    openingPipeIndex);

            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }

            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);

            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }

            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();

            if (!source.startsWith("slot ")) {
                throw new TemplateSyntaxException(
                        "Only |slot name| blocks are allowed inside |component|.");
            }

            final var slotName = source.substring("slot ".length()).trim();

            if (slotName.isBlank()) {
                throw new TemplateSyntaxException("|slot| name must not be empty.");
            }

            if (slots.containsKey(slotName)) {
                throw new TemplateSyntaxException("Duplicate slot: " + slotName);
            }

            final var slotBlock = findSlotBlock(
                    template,
                    closingPipeIndex + 1,
                    endIndex);

            final var slotHtml = renderRange(
                    template,
                    context,
                    closingPipeIndex + 1,
                    slotBlock.bodyEndIndex());

            slots.put(slotName, slotHtml);

            index = slotBlock.endEndIndex();
        }

        return Map.copyOf(slots);
    }

    void validateOnlyWhitespaceOutsideSlots(
            String template,
            int startIndex,
            int endIndex) {
        if (startIndex >= endIndex) {
            return;
        }

        final var text = template.substring(startIndex, endIndex);

        if (!text.isBlank()) {
            throw new TemplateSyntaxException(
                    "Text outside |slot| blocks is not allowed inside |component|.");
        }
    }

    ComponentBlock findComponentBlock(String template, int searchStartIndex, int endIndex) {
        int componentDepth = 1;
        int index = searchStartIndex;

        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);

            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }

            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }

            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);

            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }

            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();

            if (source.startsWith("component ")) {
                componentDepth++;
            } else if ("/component".equals(source)) {
                componentDepth--;

                if (componentDepth == 0) {
                    return new ComponentBlock(
                            searchStartIndex,
                            openingPipeIndex,
                            openingPipeIndex,
                            closingPipeIndex + 1);
                }
            }

            index = closingPipeIndex + 1;
        }

        throw new TemplateSyntaxException("Missing closing |/component|.");
    }

    SlotBlock findSlotBlock(String template, int searchStartIndex, int endIndex) {
        int ifDepth = 0;
        int eachDepth = 0;
        int switchDepth = 0;
        int componentDepth = 0;
        int index = searchStartIndex;

        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);

            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }

            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }

            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);

            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }

            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();
            final var topLevelSlotControl = ifDepth == 0
                    && eachDepth == 0
                    && switchDepth == 0
                    && componentDepth == 0;

            if (topLevelSlotControl && source.startsWith("slot ")) {
                throw new TemplateSyntaxException("Nested |slot| blocks are not allowed.");
            }

            if (topLevelSlotControl && "/slot".equals(source)) {
                return new SlotBlock(
                        searchStartIndex,
                        openingPipeIndex,
                        openingPipeIndex,
                        closingPipeIndex + 1);
            }

            if (source.startsWith("if ")) {
                ifDepth++;
            } else if ("/if".equals(source)) {
                ifDepth--;
            } else if (source.startsWith("each ")) {
                eachDepth++;
            } else if ("/each".equals(source)) {
                eachDepth--;
            } else if (source.startsWith("switch ")) {
                switchDepth++;
            } else if ("/switch".equals(source)) {
                switchDepth--;
            } else if (source.startsWith("component ")) {
                componentDepth++;
            } else if ("/component".equals(source)) {
                componentDepth--;
            }

            index = closingPipeIndex + 1;
        }

        throw new TemplateSyntaxException("Missing closing |/slot|.");
    }

    ConditionalOutputExpression parseConditionalOutputExpression(String source) {
        final var ifIndex = findOutputIfIndex(source);

        if (ifIndex == -1) {
            return new ConditionalOutputExpression(
                    source,
                    null);
        }

        final var outputSource = source.substring(0, ifIndex).trim();
        final var conditionExpression = source.substring(ifIndex + "if".length()).trim();

        if (outputSource.isBlank()) {
            throw new TemplateSyntaxException("Conditional output expression must not be empty.");
        }

        if (conditionExpression.isBlank()) {
            throw new TemplateSyntaxException("Conditional output condition must not be empty.");
        }

        return new ConditionalOutputExpression(
                outputSource,
                conditionExpression);
    }

    int findOutputIfIndex(String source) {
        boolean insideSingleQuote = false;
        boolean insideDoubleQuote = false;
        int parenthesisDepth = 0;

        for (int index = 0; index <= source.length() - "if".length(); index++) {
            final var current = source.charAt(index);

            if (current == '\'' && !insideDoubleQuote) {
                insideSingleQuote = !insideSingleQuote;
                continue;
            }

            if (current == '"' && !insideSingleQuote) {
                insideDoubleQuote = !insideDoubleQuote;
                continue;
            }

            if (insideSingleQuote || insideDoubleQuote) {
                continue;
            }

            if (current == '(') {
                parenthesisDepth++;
                continue;
            }

            if (current == ')') {
                parenthesisDepth--;
                continue;
            }

            if (parenthesisDepth != 0) {
                continue;
            }

            if (!source.startsWith("if", index)) {
                continue;
            }

            final var beforeIsBoundary = index == 0
                    || Character.isWhitespace(source.charAt(index - 1));

            final var afterIndex = index + "if".length();
            final var afterIsBoundary = afterIndex >= source.length()
                    || Character.isWhitespace(source.charAt(afterIndex));

            if (beforeIsBoundary && afterIsBoundary) {
                return index;
            }
        }

        return -1;
    }

    record ConditionalOutputExpression(
            String outputSource,
            String conditionExpression) {
    }

    record ComponentBlock(
            int bodyStartIndex,
            int bodyEndIndex,
            int endStartIndex,
            int endEndIndex) {
    }

    record SlotBlock(
            int bodyStartIndex,
            int bodyEndIndex,
            int endStartIndex,
            int endEndIndex) {
    }

    record LayoutDirective(
            String templateName,
            int startIndex,
            int endIndex) {
    }

    record SectionBlock(
            int bodyStartIndex,
            int bodyEndIndex,
            int endStartIndex,
            int endEndIndex) {
    }

    record IfBlock(
            int ifBodyEndIndex,
            List<ElseIfBlock> elseIfBlocks,
            ElseBlock elseBlock,
            int endStartIndex,
            int endEndIndex) {
    }

    record ElseIfBlock(
            String expression,
            int bodyStartIndex,
            int bodyEndIndex) {
    }

    record ElseBlock(
            int bodyStartIndex,
            int bodyEndIndex) {
    }

    record EachBlock(
            int elseStartIndex,
            int elseEndIndex,
            int endStartIndex,
            int endEndIndex) {
        boolean hasElse() {
            return elseStartIndex != -1;
        }
    }

    record SwitchBlock(
            List<SwitchCaseBlock> cases,
            SwitchDefaultBlock defaultBlock,
            int endStartIndex,
            int endEndIndex) {
    }

    record SwitchCaseBlock(
            String caseExpression,
            int bodyStartIndex,
            int bodyEndIndex,
            int fallthroughStartIndex,
            int fallthroughEndIndex) {
        boolean hasFallthrough() {
            return fallthroughStartIndex != -1;
        }
    }

    record SwitchDefaultBlock(
            int bodyStartIndex,
            int bodyEndIndex,
            int fallthroughStartIndex,
            int fallthroughEndIndex) {
    }

    static final class SwitchSectionBuilder {
        final boolean defaultSection;
        final String caseExpression;
        final int bodyStartIndex;

        int bodyEndIndex = -1;
        int fallthroughStartIndex = -1;
        int fallthroughEndIndex = -1;

        SwitchSectionBuilder(
                boolean defaultSection,
                String caseExpression,
                int bodyStartIndex) {
            this.defaultSection = defaultSection;
            this.caseExpression = caseExpression;
            this.bodyStartIndex = bodyStartIndex;
        }

        static SwitchSectionBuilder caseSection(String caseExpression, int bodyStartIndex) {
            return new SwitchSectionBuilder(false, caseExpression, bodyStartIndex);
        }

        static SwitchSectionBuilder defaultSection(int bodyStartIndex) {
            return new SwitchSectionBuilder(true, null, bodyStartIndex);
        }

        SwitchCaseBlock toCaseBlock() {
            return new SwitchCaseBlock(
                    caseExpression,
                    bodyStartIndex,
                    bodyEndIndex,
                    fallthroughStartIndex,
                    fallthroughEndIndex);
        }

        SwitchDefaultBlock toDefaultBlock() {
            return new SwitchDefaultBlock(
                    bodyStartIndex,
                    bodyEndIndex,
                    fallthroughStartIndex,
                    fallthroughEndIndex);
        }

    }

    boolean isConditionalAttributeLiteral(String expression) {
        final var attributeName = expression.trim();

        if (attributeName.isBlank()) {
            return false;
        }

        if (!attributeName.matches("[A-Za-z_:][A-Za-z0-9_:.\\-]*")) {
            return false;
        }

        return CONDITIONAL_ATTRIBUTE_LITERALS.contains(
                attributeName.toLowerCase(Locale.ROOT));
    }

    String renderConditionalAttributeOutput(
            String expression,
            TemplateContext context) {
        final var trimmedExpression = expression.trim();

        if (isConditionalAttributeLiteral(trimmedExpression)) {
            return attributeEscaper.escape(trimmedExpression);
        }

        final var equalsIndex = findTopLevelEqualsIndex(trimmedExpression);

        if (equalsIndex == -1) {
            return null;
        }

        final var attributeName = trimmedExpression.substring(0, equalsIndex).trim();
        final var valueExpression = trimmedExpression.substring(equalsIndex + 1).trim();

        if (!isValidAttributeName(attributeName)) {
            return null;
        }

        if (valueExpression.isBlank()) {
            throw new TemplateSyntaxException("Conditional attribute value must not be empty.");
        }

        final var value = expressionEvaluator.evaluate(valueExpression, context);

        return attributeName + "=\"" + attributeEscaper.escape(value) + "\"";
    }

    int findTopLevelEqualsIndex(String expression) {
        boolean insideSingleQuote = false;
        boolean insideDoubleQuote = false;
        int parenthesisDepth = 0;

        for (int index = 0; index < expression.length(); index++) {
            final var current = expression.charAt(index);

            if (current == '\'' && !insideDoubleQuote) {
                insideSingleQuote = !insideSingleQuote;
                continue;
            }

            if (current == '"' && !insideSingleQuote) {
                insideDoubleQuote = !insideDoubleQuote;
                continue;
            }

            if (insideSingleQuote || insideDoubleQuote) {
                continue;
            }

            if (current == '(') {
                parenthesisDepth++;
                continue;
            }

            if (current == ')') {
                parenthesisDepth--;
                continue;
            }

            if (parenthesisDepth == 0 && current == '=') {
                return index;
            }
        }

        return -1;
    }

    boolean isValidAttributeName(String attributeName) {
        return attributeName.matches("[A-Za-z_:][A-Za-z0-9_:.\\-]*");
    }

    void removeTrailingAttributeWhitespace(StringBuilder output) {
        while (!output.isEmpty()
                && Character.isWhitespace(output.charAt(output.length() - 1))) {
            output.deleteCharAt(output.length() - 1);
        }
    }

    int skipWhitespaceBeforeTagClose(String template, int index) {
        var currentIndex = index;

        while (currentIndex < template.length()
                && Character.isWhitespace(template.charAt(currentIndex))) {
            currentIndex++;
        }

        if (currentIndex < template.length()
                && template.charAt(currentIndex) == '>') {
            return currentIndex;
        }

        if (currentIndex + 1 < template.length()
                && template.charAt(currentIndex) == '/'
                && template.charAt(currentIndex + 1) == '>') {
            return currentIndex;
        }

        return index;
    }

    record MinifyBlock(
            int bodyStartIndex,
            int bodyEndIndex,
            int endStartIndex,
            int endEndIndex) {
    }

    MinifyBlock findMinifyBlock(String template, int searchStartIndex, int endIndex) {
        int depth = 1;
        int index = searchStartIndex;
        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);
            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }
            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }
            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);
            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }
            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();
            if ("minify".equals(source)) {
                depth++;
            } else if ("/minify".equals(source)) {
                depth--;
                if (depth == 0) {
                    return new MinifyBlock(
                            searchStartIndex,
                            openingPipeIndex,
                            openingPipeIndex,
                            closingPipeIndex + 1);
                }
            }
            index = closingPipeIndex + 1;
        }
        throw new TemplateSyntaxException("Missing closing |/minify|.");
    }

    record AttemptBlock(
            int attemptBodyEndIndex,
            int recoverBodyStartIndex,
            int recoverBodyEndIndex,
            String errorVarName,
            int endStartIndex,
            int endEndIndex) {
        public boolean hasRecover() {
            return recoverBodyStartIndex != -1;
        }
    }

    AttemptBlock findAttemptBlock(String template, int searchStartIndex, int endIndex) {
        int depth = 1;
        int index = searchStartIndex;

        int recoverStartIndex = -1;
        int recoverEndIndex = -1;
        String errorVarName = null;

        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);
            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }
            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }
            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);
            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }
            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();
            if ("attempt".equals(source)) {
                depth++;
            } else if ("/attempt".equals(source)) {
                depth--;
                if (depth == 0) {
                    int attemptBodyEnd = recoverStartIndex != -1 ? recoverStartIndex : openingPipeIndex;
                    int recoverBodyStart = recoverEndIndex != -1 ? recoverEndIndex : -1;
                    int recoverBodyEnd = recoverStartIndex != -1 ? openingPipeIndex : -1;
                    return new AttemptBlock(
                            attemptBodyEnd,
                            recoverBodyStart,
                            recoverBodyEnd,
                            errorVarName,
                            openingPipeIndex,
                            closingPipeIndex + 1);
                }
            } else if (source.startsWith("recover") && depth == 1) {
                if (recoverStartIndex != -1) {
                    throw new TemplateSyntaxException("Only one |recover| is allowed inside an |attempt| block.");
                }
                recoverStartIndex = openingPipeIndex;
                recoverEndIndex = closingPipeIndex + 1;
                if (source.startsWith("recover as ")) {
                    errorVarName = source.substring("recover as ".length()).trim();
                } else if (source.equals("recover")) {
                    errorVarName = null;
                } else {
                    throw new TemplateSyntaxException("Invalid recover syntax: " + source);
                }
            }
            index = closingPipeIndex + 1;
        }
        throw new TemplateSyntaxException("Missing closing |/attempt|.");
    }

    record MacroBlock(
            int bodyStartIndex,
            int bodyEndIndex,
            int endStartIndex,
            int endEndIndex) {
    }

    MacroBlock findMacroBlock(String template, int searchStartIndex, int endIndex) {
        int depth = 1;
        int index = searchStartIndex;
        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);
            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }
            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }
            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);
            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }
            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();
            if (source.startsWith("macro ")) {
                depth++;
            } else if ("/macro".equals(source)) {
                depth--;
                if (depth == 0) {
                    return new MacroBlock(
                            searchStartIndex,
                            openingPipeIndex,
                            openingPipeIndex,
                            closingPipeIndex + 1);
                }
            }
            index = closingPipeIndex + 1;
        }
        throw new TemplateSyntaxException("Missing closing |/macro|.");
    }

    record SeparatorBlock(
            int bodyStartIndex,
            int bodyEndIndex,
            int endStartIndex,
            int endEndIndex) {
    }

    SeparatorBlock findSeparatorBlock(String template, int searchStartIndex, int endIndex) {
        int depth = 1;
        int index = searchStartIndex;
        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);
            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }
            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }
            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);
            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }
            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();
            if ("separator".equals(source)) {
                depth++;
            } else if ("/separator".equals(source)) {
                depth--;
                if (depth == 0) {
                    return new SeparatorBlock(
                            searchStartIndex,
                            openingPipeIndex,
                            openingPipeIndex,
                            closingPipeIndex + 1);
                }
            }
            index = closingPipeIndex + 1;
        }
        throw new TemplateSyntaxException("Missing closing |/separator|.");
    }

    boolean isLastEachItem(TemplateContext context) {
        Object eachVal = context.get("each");
        if (eachVal instanceof io.lemadane.piped.template.engine.metadata.EachMetadata meta) {
            return meta.last();
        }
        if (eachVal instanceof Map<?, ?> map) {
            Object lastVal = map.get("last");
            if (lastVal instanceof Boolean b) {
                return b;
            }
        }
        return false;
    }

    record FragmentBlock(
            int bodyStartIndex,
            int bodyEndIndex,
            int endStartIndex,
            int endEndIndex) {
    }

    FragmentBlock findFragmentBlock(String template, int searchStartIndex, int endIndex) {
        int depth = 1;
        int index = searchStartIndex;
        while (index < endIndex) {
            final var openingPipeIndex = template.indexOf('|', index);
            if (openingPipeIndex == -1 || openingPipeIndex >= endIndex) {
                break;
            }
            if (isCommentStart(template, openingPipeIndex)) {
                index = findCommentEndIndex(template, openingPipeIndex);
                continue;
            }
            final var closingPipeIndex = template.indexOf('|', openingPipeIndex + 1);
            if (closingPipeIndex == -1 || closingPipeIndex >= endIndex) {
                throw new TemplateSyntaxException(
                        "Missing closing pipe for expression starting at index " + openingPipeIndex + ".");
            }
            final var source = template.substring(openingPipeIndex + 1, closingPipeIndex).trim();
            if (source.startsWith("fragment ")) {
                depth++;
            } else if ("/fragment".equals(source)) {
                depth--;
                if (depth == 0) {
                    return new FragmentBlock(
                            searchStartIndex,
                            openingPipeIndex,
                            openingPipeIndex,
                            closingPipeIndex + 1);
                }
            }
            index = closingPipeIndex + 1;
        }
        throw new TemplateSyntaxException("Missing closing |/fragment|.");
    }

    String getFirstPWAAttr(java.util.Map<String, String> attrs, String... keys) {
        for (String key : keys) {
            String val = attrs.get(key);
            if (val != null && !val.isEmpty()) {
                return val;
            }
        }
        return null;
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
}