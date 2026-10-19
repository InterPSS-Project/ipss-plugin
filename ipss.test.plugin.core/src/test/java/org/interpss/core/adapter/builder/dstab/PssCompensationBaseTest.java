package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss7c.Ieee2016PSS7CStabilizer;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnnotateStabilizer;
import com.interpss.dstab.mach.Machine;
import org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss7c.Ieee2016PSS7CStabilizerData;

/** Physical phasor/base identities independent of recorded trajectories. */
public class PssCompensationBaseTest {
    private void check(String model) throws Exception {
        for (double ratedVoltage : new double[]{13200, 16500, 19800}) {
            var builder = DStabBuilderTestFixture.createWithMachine();
            var machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
            machine.setRatedVoltage(ratedVoltage); machine.calMultiFactors();
            var bus = machine.getDStabBus();
            bus.setVoltage(new Complex(.97, .13));
            assertTrue(bus.initStates()); assertTrue(machine.initStates());
            var record = PsseDyrRecordReader.read(Path.of("testData/adpter/psse/v33/SMIB/SMIB_v33_genrou_esst1a_" + model.toLowerCase(java.util.Locale.ROOT) + ".dyr"))
                    .stream().filter(r -> r.canonicalModelName().equalsIgnoreCase(model)).findFirst().orElseThrow();
            double[] parameters = new double[record.parameterCount()];
            for (int i = 0; i < parameters.length; i++) parameters[i] = record.doubleParameter(i);
            parameters[parameters.length - 2] = .217; parameters[parameters.length - 1] = .043;
            Complex voltage = bus.getVoltage();
            Complex current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
            // Xcomp is on machine impedance base. The direct physical phasor
            // construction must agree even when nameplate and bus bases differ.
            double expected = voltage.add(current.multiply(new Complex(0, .217 * machine.getZMultiFactor())))
                    .multiply(voltage.conjugate()).getArgument() / .043;
            assertTrue(Math.abs(expected) > .001, "The compensation path must be active");
            if (model.equals("pss6c")) {
                Ieee2016PSS6CStabilizer pss = builder.addPss6c("Bus1", "1", parameters);
                assertTrue(pss.initStates(bus, machine));
                assertEquals(expected, pss.getNamedStates().get("compensatedFrequencyWashout"), 1e-12, "nameplate=" + ratedVoltage);
            } else {
                Ieee2016PSS7CStabilizer pss = builder.addPss7c("Bus1", "1", parameters);
                assertTrue(pss.initStates(bus, machine));
                assertEquals(expected, pss.getNamedStates().get("compensatedFrequencyWashout"), 1e-12, "nameplate=" + ratedVoltage);
            }
        }
    }

    @Test void pss6cCompensationUsesImpedanceNotCurrentBase() throws Exception { check("pss6c"); }
    @Test void pss7cCompensationUsesImpedanceNotCurrentBase() throws Exception { check("pss7c"); }

    private static double raw(Machine machine,double reactance,double time) {
        Complex voltage=machine.getDStabBus().getVoltage();
        Complex current=machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
        return voltage.add(current.multiply(new Complex(0,reactance*machine.getZMultiFactor())))
                .multiply(voltage.conjugate()).getArgument()/time;
    }

    @Test void predictorCorrectorZeroStepAndReinitializationPreserveTheWashoutContract() throws Exception {
        for(String model:List.of("PSS6C","PSS7C")) {
            var builder=DStabBuilderTestFixture.createWithMachine();
            var machine=builder.getDStabNetwork().getMachine("Bus1-mach1");var bus=machine.getDStabBus();
            assertTrue(bus.initStates());assertTrue(machine.initStates());
            var record=PsseDyrRecordReader.read(Path.of("testData/adpter/psse/v33/SMIB/SMIB_v33_genrou_esst1a_"+model.toLowerCase(java.util.Locale.ROOT)+".dyr"))
                    .stream().filter(r->r.canonicalModelName().equals(model)).findFirst().orElseThrow();
            double[] values=new double[record.parameterCount()];for(int i=0;i<values.length;i++)values[i]=record.doubleParameter(i);
            values[values.length-2]=.217;values[values.length-1]=.043;
            AnnotateStabilizer pss=model.equals("PSS6C")?new Ieee2016PSS6CStabilizer("stage",Ieee2016PSS6CStabilizerData.fromPsseParameters(values),machine):new Ieee2016PSS7CStabilizer("stage",Ieee2016PSS7CStabilizerData.fromPsseParameters(values),machine);
            assertTrue(pss.initStates(bus,machine));String key="compensatedFrequencyWashout";
            double initial=pss.getNamedStates().get(key),dt=.0005;
            bus.setVoltage(bus.getVoltage().multiply(.83));double first=(raw(machine,.217,.043)-initial)/.043;
            assertTrue(Math.abs(first)>1e-3);assertTrue(pss.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,0));
            double predicted=initial+dt*first;assertEquals(predicted,pss.getNamedStates().get(key),1e-12,model+" predictor");
            bus.setVoltage(bus.getVoltage().multiply(1.09));double second=(raw(machine,.217,.043)-predicted)/.043;
            assertTrue(pss.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,machine,1));
            assertEquals(initial+.5*dt*(first+second),pss.getNamedStates().get(key),1e-12,model+" corrector");
            var states=pss.getNamedStates();assertThrows(UnsupportedOperationException.class,()->states.clear());
            assertTrue(pss.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,machine,0));assertTrue(pss.nextStep(0,DynamicSimuMethod.MODIFIED_EULER,machine,1));assertEquals(states,pss.getNamedStates());
            assertTrue(pss.initStates(bus,machine));assertEquals(raw(machine,.217,.043),pss.getNamedStates().get(key),1e-12);
        }
    }

    @Test void pss6cCanonicalIntegratorsAreACascadeNotParallelDirectFeeds() throws Exception {
        var builder=DStabBuilderTestFixture.createWithMachine();var machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1);machine.setPe(.5);
        double[] p={1,0,3,0,.02,.2,.03,.5,.04,.5,.6,.4,.714,.6875,.1,.05,.02,1.375,6.4,.1,.2,.1,.3,.5,2,-2,2,-2,.1,-.1,0,0,.2,.02};
        p[6]=0; // Algebraic input-2 transducer exposes the cascade immediately.
        var pss=builder.addPss6c("Bus1","1",p);assertTrue(pss.initStates(machine.getDStabBus(),machine));
        machine.setPe(.7);assertTrue(pss.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,machine,0));
        var predicted=pss.getNamedStates();assertTrue(Math.abs(predicted.get("canonicalIntegrator1"))>1e-9);
        for(int i=2;i<=4;i++)assertEquals(0,predicted.get("canonicalIntegrator"+i),0,"Only the first cascaded integrator responds in the first predictor");
    }

    @Test void pss7cPublishedLagTailsConvertWithoutChangingSolverStorage() throws Exception {
        for(double t9:new double[]{.05,.2}) {
            var builder=DStabBuilderTestFixture.createWithMachine();var machine=builder.getDStabNetwork().getMachine("Bus1-mach1");machine.setSpeed(1);machine.setPe(.5);
            var record=PsseDyrRecordReader.read(Path.of("testData/adpter/psse/v33/SMIB/SMIB_v33_genrou_esst1a_pss7c.dyr"))
                    .stream().filter(r->r.canonicalModelName().equals("PSS7C")).findFirst().orElseThrow();
            double[] values=new double[record.parameterCount()];for(int i=0;i<values.length;i++)values[i]=record.doubleParameter(i);
            values[0]=1;values[15]=t9;
            var pss=builder.addPss7c("Bus1","1",values);assertTrue(pss.initStates(machine.getDStabBus(),machine));
            var field=Ieee2016PSS7CStabilizer.class.getDeclaredField("active");field.setAccessible(true);double[] storage=(double[])field.get(pss);
            for(int i=0;i<8;i++)storage[6+i]=.01*(i+1);
            double[] original=storage.clone();var named=pss.getNamedStates();
            for(int i=0;i<8;i++)assertEquals(original[6+i]/(i<4?1:t9),named.get("rampTracking"+(i+1)),1e-15);
            assertArrayEquals(original,storage,0,"Observation must not alter integration coordinates");assertEquals(named,pss.getNamedStates());
        }
    }
}
