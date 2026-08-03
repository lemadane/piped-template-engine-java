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
    final String itemName;
    final String collectionExpression;
    final ASTNode bodyBlock;
    final ASTNode elseBlock;
    final ASTNode separatorNode;
    final ExpressionEvaluator evaluator;

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

    public String getItemName() {
        return itemName;
    }

    public String getCollectionExpression() {
        return collectionExpression;
    }

    public ASTNode getSeparatorNode() {
        return separatorNode;
    }

    public ASTNode getBodyBlock() {
        return bodyBlock;
    }

    public ASTNode getElseBlock() {
        return elseBlock;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        Object rawValue = evaluator.evaluate(collectionExpression, context);
        if (rawValue instanceof Map<?, ?> mapVal) {
            List<Map.Entry<?, ?>> entryList = new ArrayList<>(mapVal.entrySet());
            int total = entryList.size();
            if (total > 0) {
                String keyVar = itemName;
                String valVar = null;
                if (itemName.contains(",")) {
                    String[] parts = itemName.split(",", 2);
                    keyVar = parts[0].trim();
                    valVar = parts[1].trim();
                }
                for (int i = 0; i < total; i++) {
                    Map.Entry<?, ?> entry = entryList.get(i);
                    boolean isLast = (i == total - 1);
                    Map<String, Object> loopMeta = Map.of(
                        "index", i + 1,
                        "index0", i,
                        "count", i + 1,
                        "first", i == 0,
                        "last", isLast,
                        "even", (i % 2 == 1),
                        "odd", (i % 2 == 0),
                        "total", total
                    );
                    Map<String, Object> scope = new HashMap<>();
                    if (valVar != null) {
                        scope.put(keyVar, entry.getKey() == null ? "" : entry.getKey());
                        scope.put(valVar, entry.getValue() == null ? "" : entry.getValue());
                    } else {
                        scope.put(keyVar, entry);
                    }
                    scope.put("each", loopMeta);
                    TemplateContext subContext = context.subContext(scope);
                    try {
                        bodyBlock.render(subContext, writer);
                        if (separatorNode != null && !isLast) {
                            separatorNode.render(subContext, writer);
                        }
                    } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {
                    } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                        break;
                    }
                }
                return;
            } else if (elseBlock != null) {
                elseBlock.render(context, writer);
                return;
            }
        }

        Iterable<?> items = toIterable(rawValue);
        if (items != null && items.iterator().hasNext()) {
            List<Object> itemList = new ArrayList<>();
            items.forEach(itemList::add);
            int total = itemList.size();

            for (int i = 0; i < total; i++) {
                Object item = itemList.get(i);
                boolean isLast = (i == total - 1);
                Map<String, Object> loopMeta = Map.of(
                    "index", i + 1,
                    "index0", i,
                    "count", i + 1,
                    "first", i == 0,
                    "last", isLast,
                    "even", (i % 2 == 1),
                    "odd", (i % 2 == 0),
                    "total", total
                );

                Map<String, Object> scope = new HashMap<>();
                if (itemName.contains(",")) {
                    String[] parts = itemName.split(",", 2);
                    String keyVar = parts[0].trim();
                    String valVar = parts[1].trim();
                    if (item instanceof Map.Entry<?, ?> entry) {
                        scope.put(keyVar, entry.getKey() == null ? "" : entry.getKey());
                        scope.put(valVar, entry.getValue() == null ? "" : entry.getValue());
                    } else {
                        scope.put(keyVar, item == null ? "" : item);
                        scope.put(valVar, item == null ? "" : item);
                    }
                } else {
                    scope.put(itemName, item == null ? "" : item);
                }
                scope.put("each", loopMeta);

                TemplateContext subContext = context.subContext(scope);
                try {
                    bodyBlock.render(subContext, writer);
                    if (separatorNode != null && !isLast) {
                        separatorNode.render(subContext, writer);
                    }
                } catch (io.lemadane.piped.template.engine.exceptions.LoopContinueException e) {
                } catch (io.lemadane.piped.template.engine.exceptions.LoopBreakException e) {
                    break;
                }
            }
        } else if (elseBlock != null) {
            elseBlock.render(context, writer);
        }
    }

    Iterable<?> toIterable(Object value) {
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
