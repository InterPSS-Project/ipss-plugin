package org.interpss.dstab.analysis;

import java.util.Map;
import java.util.Objects;
import com.interpss.dstab.controller.pss.pss5c.Pss5cNativeData;
import com.interpss.dstab.controller.pss.pss5c.Pss5cNativeStabilizer;

/** Read-only coordinate algebra for the published full-order transducers.
 * These equivalent cascade coordinates are not automatically certified aliases
 * for a reference simulator's raw storage arrays. Reduced-order transducers
 * require different observation maps and are deliberately rejected here.
 */
public final class Pss5cTransducerCoordinates {
    private Pss5cTransducerCoordinates() {}

    public record Companion(double lowPosition, double lowRate, double highPosition,
                            double highRate, double highAcceleration) {
        public Companion { finite(lowPosition,lowRate,highPosition,highRate,highAcceleration); }
    }

    public record Cascade(double lowPosition, double lowRate, double highLagOutput,
                          double highIntegral, double highDerivative) {
        public Cascade { finite(lowPosition,lowRate,highLagOutput,highIntegral,highDerivative); }
    }

    /** r = x + AH*w + BH*z, z = x'/CH, w = x''/CH. */
    public static Cascade toCascade(Pss5cNativeData data, Companion state) {
        fullOrder(data);Objects.requireNonNull(state,"state");
        double z=state.highRate()/data.parameter(7),w=state.highAcceleration()/data.parameter(7);
        double r=state.highPosition()+data.parameter(5)*w+data.parameter(6)*z;
        return new Cascade(state.lowPosition(),state.lowRate(),r,z,w);
    }

    /** x = r - AH*w - BH*z, x' = CH*z, x'' = CH*w. */
    public static Companion toCompanion(Pss5cNativeData data, Cascade state) {
        fullOrder(data);Objects.requireNonNull(state,"state");
        double x=state.highLagOutput()-data.parameter(5)*state.highDerivative()-data.parameter(6)*state.highIntegral();
        return new Companion(state.lowPosition(),state.lowRate(),x,
                data.parameter(7)*state.highIntegral(),data.parameter(7)*state.highDerivative());
    }

    /** Snapshot only: no state updates, controller calls or coordinate replacement. */
    public static Cascade observeCascade(Pss5cNativeStabilizer model) {
        Objects.requireNonNull(model,"model");Map<String,Double> x=model.getNamedStates();
        return toCascade(model.getData(),new Companion(x.get("lowPosition"),x.get("lowRate"),
                x.get("highPosition"),x.get("highRate"),x.get("highAcceleration")));
    }

    private static void fullOrder(Pss5cNativeData data) {
        Objects.requireNonNull(data,"data");
        if(!data.hasFullOrderTransducers())throw new IllegalArgumentException("Full-order PSS5C transducers required for this coordinate map");
    }

    private static void finite(double... values) {
        for(double v:values)if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite PSS5C transducer coordinate");
    }
}
