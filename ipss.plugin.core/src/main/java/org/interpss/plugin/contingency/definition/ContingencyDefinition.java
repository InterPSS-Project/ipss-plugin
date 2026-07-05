package org.interpss.plugin.contingency.definition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContingencyDefinition {
    public String name;
    public List<ContingencyAction> actions = new ArrayList<>();
    public Map<String, String> metadata = new LinkedHashMap<>();

    public ContingencyDefinition() {
    }

    public ContingencyDefinition(String name) {
        this.name = name;
    }

    public ContingencyDefinition(String name, List<ContingencyAction> actions) {
        this.name = name;
        if (actions != null) {
            this.actions.addAll(actions);
        }
    }

    public String getName() {
        return name;
    }

    public List<ContingencyAction> getActions() {
        return actions;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void addAction(ContingencyAction action) {
        this.actions.add(action);
    }
}
