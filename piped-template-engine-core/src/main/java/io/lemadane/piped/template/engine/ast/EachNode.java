package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.exceptions.TemplateRenderException;
import io.lemadane.piped.template.engine.expression.ExpressionEvaluator;
import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EachNode implements ASTNode {
    private final String itemName;
    private final String collectionExpression;
    private final ASTNode bodyBlock;
    private final ASTNode elseBlock;
    private final ASTNode separatorNode;
    private final ExpressionEvaluator evaluator;

    public EachNode(
            String itemName,
            String collectionExpression,
            ASTNode bodyBlock,
            ASTNode elseBlock,
            ASTNode separatorNode,
            ExpressionEvaluator evaluator) {
        this.itemName = itemName;
        this.collectionExpression = collectionExpression;
        this.bodyBlock = bodyBlock;
        this.elseBlock = elseBlock;
        this.separatorNode = separatorNode;
        this.evaluator = evaluator;
    }

    public EachNode(
            String itemName,
            String collectionExpression,
            ASTNode bodyBlock,
            ASTNode elseBlock,
            ExpressionEvaluator evaluator) {
        this(itemName, collectionExpression, bodyBlock, elseBlock, null, evaluator);
    }

    public ASTNode getSeparatorNode() {
        return separatorNode;
    }

    public ASTNode getBodyBlock() {
        return bodyBlock;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        Object rawValue = evaluator.evaluate(collectionExpression, context);
        Iterable<?> items = toIterable(rawValue);

        if (items != null && items.iterator().hasNext()) {
            List<Object> itemList = new ArrayList<>();
            items.forEach(itemList::add);
            int total = itemList.size();

            for (int i = 0; i < total; i++) {
                Object item = itemList.get(i);
                boolean isLast = (i == total - 1);
                Map<String, Object> loopMeta = Map.of(
                    "index", i,
                    "count", i + 1,
                    "first", i == 0,
                    "last", isLast,
                    "total", total
                );

                Map<String, Object> scope = new HashMap<>();
                scope.put(itemName, item == null ? "" : item);
                scope.put("each", loopMeta);

                TemplateContext subContext = context.subContext(scope);
                try {
                    bodyBlock.render(subContext, writer);

                    if (separatorNode != null && !isLast) {
                        separatorNode.render(subContext, writer);
                    }
                } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {
                    // Continue next iteration
                } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                    break;
                }
            }
        } else if (elseBlock != null) {
            elseBlock.render(context, writer);
        }
    }

    private Iterable<?> toIterable(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Iterable<?> iterable) {
            return iterable;
        }

        if (value instanceof Map<?, ?> map) {
            List<Object> items = new ArrayList<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                items.add(Map.of("key", entry.getKey(), "value", entry.getValue()));
            }
            return items;
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> items = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                items.add(Array.get(value, index));
            }
            return items;
        }

        throw new TemplateRenderException("Value is not iterable: " + value.getClass().getName());
    }
}
