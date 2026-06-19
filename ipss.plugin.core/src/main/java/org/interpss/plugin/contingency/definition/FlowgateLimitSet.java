package org.interpss.plugin.contingency.definition;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.definition.FlowgateLimitSet}.
 */
@Deprecated
public class FlowgateLimitSet extends com.interpss.core.algo.dclf.definition.FlowgateLimitSet {
    public FlowgateLimitSet() {
        super();
    }

    public FlowgateLimitSet(Double sourceLimitMW, Double realtimeEffectiveLimitMW, Double initialEffectiveLimitMW) {
        super(sourceLimitMW, realtimeEffectiveLimitMW, initialEffectiveLimitMW);
    }

    public static FlowgateLimitSet realtime(double realtimeEffectiveLimitMW) {
        return new FlowgateLimitSet(null, realtimeEffectiveLimitMW, null);
    }
}
