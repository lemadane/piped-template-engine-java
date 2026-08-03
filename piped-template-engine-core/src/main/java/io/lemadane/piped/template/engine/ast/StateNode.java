package io.lemadane.piped.template.engine.ast;

import io.lemadane.piped.template.engine.expression.TemplateContext;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class StateNode implements ASTNode {
    final Map<String, String> stateMap;

    public StateNode(Map<String, String> stateMap) {
        this.stateMap = stateMap == null ? Map.of() : stateMap;
    }

    public Map<String, String> getStateMap() {
        return stateMap;
    }

    @Override
    public void render(TemplateContext context, Writer writer) throws IOException {
        List<String> keys = new ArrayList<>(stateMap.keySet());
        Collections.sort(keys);

        List<String> pairs = new ArrayList<>();
        for (String key : keys) {
            String val = stateMap.get(key);
            if (val == null) {
                val = "";
            }
            if ("true".equals(val) || "false".equals(val) || isNumeric(val) || val.startsWith("[") || val.startsWith("{")) {
                pairs.add(String.format("%s: %s", key, val));
            } else {
                pairs.add(String.format("%s: '%s'", key, val));
            }
        }

        String jsonState = String.format("{ %s }", String.join(", ", pairs));
        String output = String.format("x-data=\"%s\"", jsonState.replace("\"", "&quot;"));
        writer.write(output);
    }

    boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.matches("-?\\d+(\\.\\d+)?");
    }
}
