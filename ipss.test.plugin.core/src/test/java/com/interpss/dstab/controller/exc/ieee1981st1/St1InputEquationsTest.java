package com.interpss.dstab.controller.exc.ieee1981st1;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.controller.cml.annotate.util.CMLSymbolMapper;
import com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper;
import com.interpss.dstab.datatype.ExpCalculator;
import com.interpss.dstab.controller.exc.ieee1981st1.IEEE1981ST1Exciter;
import com.interpss.dstab.controller.exc.ieee1981st1.St1DirectEvaluation;
import com.interpss.dstab.controller.exc.ieee1981st1.St1InputEquations;

public class St1InputEquationsTest {
    @Test void generatedEquationsMatchLiveGenericReadsAndFloatingPointOrder() throws Exception {
        var model = initializedModel();
        var scalar = new St1DirectEvaluation(model);
        var generic = new St1DirectEvaluation(model, false);
        for (int id=0; id<8; id++)
            assertTrue(St1InputEquations.matches(id, scalar.nodes[id].wrapper.getInputExp()));
        double[] values = {0., -0., 1., -1., 1.e-300, 1.e20, Double.MAX_VALUE,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN};
        for (double value : values) {
            model.setRefPoint(value); model.vuel=value; model.uelError=-.5;
            model.vosError=value; model.vosOutput=-value; model.vuelGate1=value;
            for (int id : new int[]{0, 2, 3, 5})
                ((com.interpss.dstab.controller.cml.field.ICMLControlBlock)scalar.nodes[id].block)
                    .setStateX(id==2 || id==5 ? -value : value);
            for (int id=0; id<8; id++)
                assertEquals(Double.doubleToLongBits(generic.input(id)),
                    Double.doubleToLongBits(scalar.input(id)), "input " + id + " at " + value);
        }
    }

    @Test void changedExpressionUsesGenericFallback() throws Exception {
        var model = initializedModel();
        var expression = new ExpCalculator("this.refPoint * this.vosError - this.vuel");
        CMLSymbolMapper.mapExpressionSymbolStr2Type(expression.getRecList());
        for (var wrapper : model.getFieldWrapperList())
            if (wrapper.getFieldName().equals("gainBlock"))
                ((BlockFieldAnWrapper<?>)wrapper).setInputExp(expression);
        assertFalse(St1InputEquations.matches(1, expression));
        var scalar = new St1DirectEvaluation(model);
        var generic = new St1DirectEvaluation(model, false);
        for (double value : new double[]{-.25, 0., .75}) {
            model.setRefPoint(value); model.vosError=3.; model.vuel=.125;
            assertEquals(value*3.-.125, scalar.input(1), 0.);
            assertEquals(generic.input(1), scalar.input(1), 0.);
        }
    }

    @Test void stabilizerReadsKeepTheirOrderAndRemainLive() throws Exception {
        var model = initializedModel();
        int[] reads={0};
        var stabilizer=new com.interpss.dstab.mach.impl.MachineControllerImpl() {
            @Override public double getOutput(com.interpss.dstab.mach.Machine machine) {
                reads[0]++;
                model.setRefPoint(99.); // The reference operand must already have been read.
                model.vosError=3.; model.vosOutput=-4.; // These operands follow this read.
                return -.125;
            }
        };
        stabilizer.setType(com.interpss.dstab.mach.MachineControllerType.STABILIZER);
        model.getMachine().getControllerList().add(stabilizer);
        var scalar=new St1DirectEvaluation(model);
        var generic=new St1DirectEvaluation(model,false);
        for (int id : new int[]{1, 7}) {
            model.setRefPoint(.25); model.vosError=2.; model.vosOutput=1.; reads[0]=0;
            double expected=generic.input(id);
            assertEquals(1,reads[0]);
            model.setRefPoint(.25); model.vosError=2.; model.vosOutput=1.; reads[0]=0;
            assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(scalar.input(id)));
            assertEquals(1,reads[0]);
        }
    }

    private static IEEE1981ST1Exciter initializedModel() throws Exception {
        IpssCorePlugin.init();
        String base="testData/adpter/psse/v33/SMIB/";
        var context=new PSSEMultiFileLoader().loadDStab(base+"SMIB_v33.raw",
            base+"SMIB_v33_genrou_exst1.dyr");
        var algorithm=context.getDynSimuAlgorithm();
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        return (IEEE1981ST1Exciter)context.getDStabilityNet().getMachine("Bus1-mach1").getExciter();
    }
}
