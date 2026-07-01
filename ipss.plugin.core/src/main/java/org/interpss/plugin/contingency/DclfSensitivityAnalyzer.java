package org.interpss.plugin.contingency;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.solver.DclfSensitivityAnalyzer}.
 */
@Deprecated
public class DclfSensitivityAnalyzer extends com.interpss.core.algo.dclf.solver.DclfSensitivityAnalyzer {
    public DclfSensitivityAnalyzer() {
        super();
    }

    public DclfSensitivityAnalyzer(boolean cacheDclfAlgorithm) {
        super(cacheDclfAlgorithm);
    }
}
