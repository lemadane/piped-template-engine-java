package io.lemadane.piped.template.engine.compiler;

import java.util.HashMap;
import java.util.Map;

public final class ParseContext {
    int loopDepth = 0;
    int eachDepth = 0;
    int switchSectionDepth = 0;
    int attemptDepth = 0;
    boolean inSwitch = false;
    final Map<String, Object> metadata = new HashMap<>();

    public int getLoopDepth() { return loopDepth; }
    public void incrementLoopDepth() { loopDepth++; }
    public void decrementLoopDepth() { loopDepth--; }

    public int getEachDepth() { return eachDepth; }
    public void incrementEachDepth() { eachDepth++; }
    public void decrementEachDepth() { eachDepth--; }

    public int getSwitchSectionDepth() { return switchSectionDepth; }
    public void incrementSwitchSectionDepth() { switchSectionDepth++; }
    public void decrementSwitchSectionDepth() { switchSectionDepth--; }

    public int getAttemptDepth() { return attemptDepth; }
    public void incrementAttemptDepth() { attemptDepth++; }
    public void decrementAttemptDepth() { attemptDepth--; }

    public boolean isInSwitch() { return inSwitch; }
    public void setInSwitch(boolean inSwitch) { this.inSwitch = inSwitch; }

    boolean inSection = false;
    boolean inSlot = false;
    final java.util.Set<String> definedSections = new java.util.HashSet<>();

    public boolean isInSection() { return inSection; }
    public void setInSection(boolean inSection) { this.inSection = inSection; }
    public boolean isInSlot() { return inSlot; }
    public void setInSlot(boolean inSlot) { this.inSlot = inSlot; }
    public java.util.Set<String> getDefinedSections() { return definedSections; }

    int componentDepth = 0;

    public int getComponentDepth() { return componentDepth; }
    public void incrementComponentDepth() { componentDepth++; }
    public void decrementComponentDepth() { componentDepth--; }

    boolean inComponentTemplate = false;

    public boolean isInComponentTemplate() { return inComponentTemplate; }
    public void setInComponentTemplate(boolean inComponentTemplate) { this.inComponentTemplate = inComponentTemplate; }

    public Map<String, Object> getMetadata() { return metadata; }
}
