package org.interpss.plugin.contingency;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.DclfContingencyConfig}.
 */
@Deprecated
public class DclfContingencyConfig extends com.interpss.core.algo.dclf.DclfContingencyConfig {
    public static DclfContingencyConfig createDefaultConfig() {
        return new DclfContingencyConfig();
    }
}
