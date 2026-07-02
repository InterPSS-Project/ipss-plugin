package org.interpss.plugin.contingency.aux_fmt.mapper;

import java.util.Optional;

public interface AuxBranchResolver {
    Optional<ResolvedBranchRef> resolve(String objectReference);
}
