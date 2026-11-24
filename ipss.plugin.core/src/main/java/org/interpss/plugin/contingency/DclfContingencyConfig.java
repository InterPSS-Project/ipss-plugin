package org.interpss.plugin.contingency;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.DclfContingencyConfig}.
 */
@Deprecated
public class DclfContingencyConfig extends com.interpss.core.algo.dclf.DclfContingencyConfig {
    /**
     * Uses the shared Core defaults, including high-impedance replay for
     * automatically dispatched contingency definitions.
     */
    public static DclfContingencyConfig createDefaultConfig() {
        return new DclfContingencyConfig();
    }
}
