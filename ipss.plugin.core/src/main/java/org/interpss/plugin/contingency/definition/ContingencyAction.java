package org.interpss.plugin.contingency.definition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ContingencyAction {
    public ContingencyObjectType objectType;
    public ContingencyActionType actionType;
    public String objectId;
    public String extUID;
    public Map<String, String> metadata = new LinkedHashMap<>();

    public ContingencyAction() {
    }

    public ContingencyAction(
            ContingencyObjectType objectType,
            ContingencyActionType actionType,
            String objectId) {
        this.objectType = Objects.requireNonNull(objectType, "objectType");
        this.actionType = Objects.requireNonNull(actionType, "actionType");
        this.objectId = Objects.requireNonNull(objectId, "objectId");
    }

    public ContingencyObjectType getObjectType() {
        return objectType;
    }

    public ContingencyActionType getActionType() {
        return actionType;
    }

    public String getObjectId() {
        return objectId;
    }

    public String getExtUID() {
        return extUID;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
