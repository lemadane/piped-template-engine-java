package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public final class SlotNode implements ASTNode {
    final String slotName;
    final ASTNode body;
    final boolean outlet;

    public SlotNode(String slotName, ASTNode body) {
        this(slotName, body, false);
    }

    public SlotNode(String slotName, ASTNode body, boolean outlet) {
        this.slotName = slotName;
        this.body = body;
        this.outlet = outlet;
    }

    public String getSlotName() {
        return slotName;
    }

    public ASTNode getBody() {
        return body;
    }

    public boolean isOutlet() {
        return outlet;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        StringWriter sw = new StringWriter();
        if (body != null) {
            body.render(context, sw);
        }
        String renderedContent = sw.toString();

        if (outlet) {
            if (context.getSlots() != null) {
                String key = (slotName == null || slotName.isBlank()) ? "default" : slotName;
                String slotContent = context.getSlots().get(key);
                if (slotContent == null && !"default".equals(key)) {
                    slotContent = context.getSlots().get("default");
                }
                if (slotContent != null) {
                    writer.write(slotContent);
                } else {
                    writer.write(renderedContent);
                }
            } else {
                writer.write(renderedContent);
            }
        } else {
            if (context.getSlots() != null && slotName != null && !slotName.isEmpty()) {
                context.getSlots().put(slotName, renderedContent);
            } else {
                writer.write(renderedContent);
            }
        }
    }
}
