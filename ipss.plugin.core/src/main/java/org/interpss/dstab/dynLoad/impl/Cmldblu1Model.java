package org.interpss.dstab.dynLoad.impl;

import java.util.Arrays;
import java.util.Set;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.Cmldznu2Data;
import com.interpss.dstab.BaseDStabBus;

/** Bus/load-specific 132-constant composite load using the shared distribution/motor engine. */
public final class Cmldblu1Model extends Cmldznu2Model {
    public static final int CONSTANT_COUNT = 132;
    public Cmldblu1Model(String id, BaseDStabBus<?,?> bus, Set<String> ids,
            Complex power, double[] constants) {
        super(id,bus,ids,power,legacyData(constants));
        setName("CMLDBLU1");setGroupId("bus:"+bus.getId());
    }
    private static Cmldznu2Data legacyData(double[] constants) {
        if(constants==null || constants.length!=CONSTANT_COUNT)
            throw new IllegalArgumentException("CMLDBLU1 requires exactly 132 constants");
        double[] shared=Arrays.copyOf(constants,133);
        shared[132]=1.0; // Legacy realization restores its full electronic fraction after low voltage.
        return new Cmldznu2Data(shared,true);
    }
    public double[] getLegacyConstants() { return Arrays.copyOf(getData().values(),132); }
}
