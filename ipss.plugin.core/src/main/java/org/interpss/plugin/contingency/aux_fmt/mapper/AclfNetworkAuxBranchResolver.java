package org.interpss.plugin.contingency.aux_fmt.mapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

public class AclfNetworkAuxBranchResolver implements AuxBranchResolver {
    private static final Pattern BRANCH_OBJECT = Pattern.compile("(?i)^\\s*BRANCH\\s+'([^']+)'\\s*$");
    private static final Pattern DESC_TAGS = Pattern.compile("\\[(.*)]");

    private final Map<String, AclfBranch> branchesByKey = new HashMap<>();
    private final Set<String> ambiguousKeys = new HashSet<>();

    public AclfNetworkAuxBranchResolver(AclfNetwork network) {
        for (AclfBranch branch : network.getBranchList()) {
            add(branch.getId(), branch);
            add(branch.getName(), branch);
            add(branch.getExtUID(), branch);
            addDescTags(branch, branch.getDesc());
        }
    }

    @Override
    public Optional<ResolvedBranchRef> resolve(String objectReference) {
        String key = branchReferenceKey(objectReference);
        AclfBranch branch = lookup(key);
        if (branch == null && key != null) {
            branch = lookup(stripTrailingCircuit(key));
        }
        if (branch == null) {
            return Optional.empty();
        }
        return Optional.of(toResolvedRef(branch));
    }

    private void addDescTags(AclfBranch branch, String desc) {
        if (desc == null || desc.isBlank()) {
            return;
        }
        Matcher matcher = DESC_TAGS.matcher(desc);
        if (!matcher.find()) {
            return;
        }
        for (String tag : matcher.group(1).split(",")) {
            add(tag, branch);
        }
    }

    private void add(String value, AclfBranch branch) {
        String key = normalize(value);
        if (key == null) {
            return;
        }
        AclfBranch existing = branchesByKey.putIfAbsent(key, branch);
        if (existing != null && existing != branch) {
            ambiguousKeys.add(key);
        }
    }

    private AclfBranch lookup(String key) {
        String normalized = normalize(key);
        if (normalized == null || ambiguousKeys.contains(normalized)) {
            return null;
        }
        return branchesByKey.get(normalized);
    }

    private static String branchReferenceKey(String objectReference) {
        if (objectReference == null || objectReference.isBlank()) {
            return null;
        }
        Matcher matcher = BRANCH_OBJECT.matcher(objectReference);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return objectReference.trim();
    }

    private static String stripTrailingCircuit(String value) {
        if (value == null) {
            return null;
        }
        int lastSpace = value.lastIndexOf(' ');
        if (lastSpace > 0 && lastSpace + 1 < value.length()) {
            return value.substring(0, lastSpace).trim();
        }
        return value;
    }

    private static ResolvedBranchRef toResolvedRef(AclfBranch branch) {
        double baseKv = branch.getFromBus() != null ? branch.getFromBus().getBaseVoltage() * 0.001 : 0.0;
        return new ResolvedBranchRef(
                branch.getFromBusId(),
                branch.getToBusId(),
                branch.getCircuitNumber(),
                baseKv);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
