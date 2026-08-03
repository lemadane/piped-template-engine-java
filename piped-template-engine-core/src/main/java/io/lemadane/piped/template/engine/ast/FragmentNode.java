package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;

public final class FragmentNode implements ASTNode {
    final String name;
    final ASTNode body;

    public FragmentNode(String name, ASTNode body) {
        this.name = name;
        this.body = body;
    }

    public String getName() {
        return name;
    }

    public ASTNode getBody() {
        return body;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        body.render(context, writer);
    }

    public FragmentNode findFragment(String targetName) {
        if (targetName.equals(name)) {
            return this;
        }
        return searchFragment(body, targetName);
    }

    FragmentNode searchFragment(ASTNode node, String targetName) {
        if (node == null) {
            return null;
        }
        if (node instanceof FragmentNode frag) {
            if (targetName.equals(frag.getName())) {
                return frag;
            }
            FragmentNode found = searchFragment(frag.getBody(), targetName);
            if (found != null) {
                return found;
            }
        }
        if (node instanceof BlockNode block) {
            for (ASTNode child : block.getChildren()) {
                FragmentNode found = searchFragment(child, targetName);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof IfNode ifNode) {
            FragmentNode found = searchFragment(ifNode.getThenBlock(), targetName);
            if (found != null) return found;
            for (IfNode.ElseIfBranch branch : ifNode.getElseIfBranches()) {
                found = searchFragment(branch.block(), targetName);
                if (found != null) return found;
            }
            if (ifNode.getElseBlock() != null) {
                found = searchFragment(ifNode.getElseBlock(), targetName);
                if (found != null) return found;
            }
        }
        if (node instanceof EachNode eachNode) {
            FragmentNode found = searchFragment(eachNode.getSeparatorNode(), targetName);
            if (found != null) return found;
        }
        return null;
    }
}
