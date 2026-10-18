package org.interpss;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * Model-unit and independent small-network/SMIB verification, without full
 * Texas2k, WECC or IEEE39 dynamic simulations. Model-specific acceptance still
 * requires the evidence inventory; discovery and a green suite alone do not
 * prove that every parameter or limiter branch has independent coverage.
 */
@Suite
@IncludeEngines("junit-jupiter")
@SelectPackages({"org.interpss.core.adapter.builder.dstab", "org.interpss.core.dstab"})
@IncludeClassNamePatterns({
        "org\\.interpss\\.core\\.adapter\\.builder\\.dstab\\..*Tests?",
        "org\\.interpss\\.core\\.dstab\\.(cml|mach)\\..*Tests?",
        "org\\.interpss\\.core\\.dstab\\.[^.]*Smib[^.]*",
        "org\\.interpss\\.core\\.dstab\\.[^.]*(Conformance|IndependentTrajectory|NativeTrajectory)Test",
        "org\\.interpss\\.core\\.dstab\\.(Regca.*|Pss3cNumericalStage|ZeroTimePsseBoundaryReplay|ReferenceArtifactManifestIntegrity|EmbeddedTrajectoryCoverageInventory|PsseRunQuality)Test",
        "org\\.interpss\\.core\\.dstab\\.reference\\.EmbeddedTrajectoryReferenceTest"
})
@ExcludeClassNamePatterns({
        ".*\\.Texas2k[^.]*", ".*\\.Wecc[^.]*", ".*\\.Ieee39[^.]*", ".*\\.DStab_[^.]*"
})
public class DynamicModelSmibTestSuite {
}
