package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1ExciterData.FieldVoltageLimitConvention;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;

/** Source-model lower field limits, including negative field current. */
public class PsseExst1FieldLimitTest extends CorePluginTestSetup {
    @TempDir Path tempDir;

    @Test
    void importedExst1CompensatesBothFieldLimits() throws Exception {
        checkLimits("EXST1", "0 .1 -.1 0 0 261 0 5 -5 .15 0 1",
                FieldVoltageLimitConvention.EXST1);
    }

    @Test
    void importedEsst1aCompensatesOnlyTheUpperFieldLimit() throws Exception {
        checkLimits("ESST1A", "1 1 0 .1 -.1 0 0 0 0 261 0 999 -999 5 -5 .15 0 1 0 0",
                FieldVoltageLimitConvention.ESST1A);
    }

    private void checkLimits(String name, String parameters, FieldVoltageLimitConvention convention)
            throws Exception {
        for (double ifd : new double[] {2.0, -5.5, 0.0}) {
            var builder = DStabBuilderTestFixture.createWithMachine();
            Path dyr = tempDir.resolve(name + ".dyr");
            Files.writeString(dyr, "1 '" + name + "' 1 " + parameters + " /\n");
            var parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
            parser.parseDynFile(dyr.toString());
            assertTrue(parser.getLastImportReport().isStrictlyComplete());
            var source = builder.getDStabNetwork().getMachine("Bus1-mach1");
            var exciter = (IEEE1981ST1Exciter) source.getExciter();
            assertEquals(convention, exciter.getData().getFieldVoltageLimitConvention());
            var machine = new BoundaryMachine(ifd);
            machine.setMachData(EcoreUtil.copy(source.getMachData()));
            machine.setParentGen(source.getParentGen());
            machine.setId(source.getId());
            machine.setStatus(true);
            machine.setSpeed(1.0);
            machine.setEfd(1.0);
            machine.getDStabBus().setVoltage(new Complex(1.4, 0.0));
            exciter.setMachine(machine);
            assertTrue(exciter.initStates(machine.getDStabBus(), machine));

            double lower = -7.0 - (convention == FieldVoltageLimitConvention.EXST1 ? .15 * ifd : 0.0);
            double upper = 7.0 - .15 * ifd;
            exciter.setRefPoint(-100.0);
            assertTrue(exciter.nextStep(0.0, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertEquals(lower, exciter.getOutput(machine), 1e-10, name + " lower bound, Ifd=" + ifd);
            exciter.setRefPoint(100.0);
            assertTrue(exciter.nextStep(0.0, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertEquals(upper, exciter.getOutput(machine), 1e-10, name + " upper bound, Ifd=" + ifd);
            if (convention == FieldVoltageLimitConvention.ESST1A && ifd > 0.) {
                // A severe voltage dip can put the rectifier upper bound below
                // the lower bound. Preserve the source block's upper-first rule.
                machine.getDStabBus().setVoltage(new Complex(.01, 0.));
                assertTrue(exciter.nextStep(0., DynamicSimuMethod.MODIFIED_EULER, machine, 0));
                double dippedUpper=.05-.15*ifd;
                assertTrue(exciter.getFieldVoltageUpperLimit()<exciter.getFieldVoltageLowerLimit());
                assertEquals(dippedUpper,exciter.getOutput(machine),1e-10);
                assertEquals(dippedUpper,exciter.getStaggeredFieldVoltage(),1e-10);
            }
        }
    }

    private static final class BoundaryMachine extends RoundRotorMachineImpl {
        private final double ifd;
        BoundaryMachine(double ifd) { this.ifd = ifd; }
        @Override public double calculateIfd(MachineIfdBase base) {
            assertEquals(MachineIfdBase.EXCITER, base);
            return ifd;
        }
    }
}
