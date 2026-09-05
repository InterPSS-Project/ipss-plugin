package org.interpss.fadapter.psse.dyr;

import java.util.Locale;
import java.util.Objects;

/**
 * One cross-software row from a versioned WECC approved dynamic-model list.
 * Approval and InterPSS implementation status intentionally remain separate.
 */
public record WeccModelApproval(
        String catalogName,
        DynamicModelCategory category,
        String pslfModel,
        String psseModel,
        String powerWorldModel,
        WeccModelApprovalStatus approvalStatus,
        String statusEffective,
        String interpssModel,
        String comments) {

    public WeccModelApproval {
        catalogName = normalize(catalogName);
        Objects.requireNonNull(category, "category");
        pslfModel = clean(pslfModel);
        psseModel = clean(psseModel);
        powerWorldModel = clean(powerWorldModel);
        Objects.requireNonNull(approvalStatus, "approvalStatus");
        statusEffective = clean(statusEffective);
        interpssModel = interpssModel == null || interpssModel.isBlank()
                ? "" : normalize(interpssModel);
        comments = clean(comments);
    }

    public boolean isImplementedExactly() {
        return !interpssModel.isEmpty()
                && DynamicModelCatalog.find(interpssModel)
                        .map(model -> model.supportStatus() == DynamicModelSupportStatus.LOADABLE)
                        .orElse(false);
    }

    private static String normalize(String value) {
        String normalized = Objects.requireNonNull(value, "model name")
                .trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("model name must not be blank");
        return normalized;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
