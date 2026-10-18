package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Hashtable;
import org.junit.jupiter.api.Test;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PsseDStabSolver;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.ConstantCurrentLoadVoltageCurve;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

public class PsseStaticCurrentLoadVoltageCurveTest {
    @Test void bothReferenceSolversSelectTheNativeCurveBeforeInitialization() throws Exception {
        for (boolean staggered : new boolean[] {false, true}) {
            IpssCorePlugin.init();
            String base = "testData/adpter/psse/v33/SMIB/";
            var context = new PSSEMultiFileLoader().loadDStab(base + "SMIB_v33.raw",
                    base + "SMIB_v33_genrou_exst1.dyr");
            var net = context.getDStabilityNet();
            var algo = context.getDynSimuAlgorithm();
            net.setStaticLoadModel(StaticLoadModel.CONST_I);
            net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
            net.setStaticLoadIncludedInYMatrix(false);
            var custom = new Hashtable<String, org.apache.commons.math3.complex.Complex>();
            net.setCustomBusCurrInjHashtable(custom);
            algo.getAclfAlgorithm().setTolerance(1e-10);
            assertTrue(algo.getAclfAlgorithm().loadflow());
            algo.setSolver(staggered ? new PsseStaggeredEulerSolver(algo) : new PsseDStabSolver(algo));
            algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            algo.setSimuStepSec(.0005);
            algo.setTotalSimuTimeSec(.01);
            algo.setSimuOutputHandler(new StateMonitor());
            assertEquals(ConstantCurrentLoadVoltageCurve.SINE, net.getConstantCurrentLoadVoltageCurve());
            assertTrue(algo.initialization());
            assertEquals(ConstantCurrentLoadVoltageCurve.ELLIPTIC, net.getConstantCurrentLoadVoltageCurve());
            assertSame(custom, net.getCustomBusCurrInjHashtable(), "Retained external currents must not skip curve selection");
            assertTrue(algo.solveDEqnStep(true));
            net.setConstantCurrentLoadVoltageCurve(ConstantCurrentLoadVoltageCurve.SINE);
            net.setDStabNetInitialized(false);
            assertTrue(algo.getAclfAlgorithm().loadflow());
            assertTrue(algo.initialization());
            assertEquals(ConstantCurrentLoadVoltageCurve.ELLIPTIC, net.getConstantCurrentLoadVoltageCurve());
        }
    }
}
