package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.GenqecSalientNativeReference;
import com.interpss.dstab.mach.model.GenqecMachine;
import org.interpss.dstab.validation.DynamicParameterValidator;
import org.interpss.dstab.validation.DynamicParameterValidator.Severity;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.mach.RoundRotorMachine;

/** Startup validation distinguishes the supported missing winding from invalid time constants. */
class GenqecParameterValidationTest extends CorePluginTestSetup {
    private static BaseDStabNetwork<?, ?> load(Path dir) throws Exception {
        var profile = GenqecSalientNativeReference.profiles().stream()
                .filter(p -> p.name().equals("genqec-0-coarse")).findFirst().orElseThrow();
        Files.writeString(dir.resolve("network.raw"), profile.network());
        Files.writeString(dir.resolve("dynamic.dyr"), profile.input());
        return new PSSEMultiFileLoader().loadDStab(dir.resolve("network.raw").toString(),
                dir.resolve("dynamic.dyr").toString()).getDStabilityNet();
    }

    private static boolean hasError(BaseDStabNetwork<?, ?> net, String parameter) {
        return DynamicParameterValidator.validate(net, 1. / 240, 4., machine -> 1).issues().stream()
                .anyMatch(i -> i.parameter().equals(parameter) && i.severity() == Severity.ERROR);
    }

    @Test void supportedZeroBranchIsAcceptedWithoutChangingParameters(@TempDir Path dir) throws Exception {
        var net = load(dir);
        var machine = (GenqecMachine) net.getMachine("Bus1-mach1");
        assertEquals(0., machine.getTq01());
        assertEquals(machine.getXq(), machine.getXq1());
        assertFalse(hasError(net, "Tq0'"));
        assertEquals(0., machine.getTq01());
        assertEquals(machine.getXq(), machine.getXq1());
        machine.setXq1(machine.getXq() - .1);
        assertTrue(hasError(net, "Tq0'"));
    }

    @Test void invalidAndSubtransientTimesRemainRejected(@TempDir Path dir) throws Exception {
        var net = load(dir);
        var machine = (GenqecMachine) net.getMachine("Bus1-mach1");
        for (double value : new double[] {-.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            machine.setTq01(value);
            assertTrue(hasError(net, "Tq0'"));
            assertEquals(Double.doubleToLongBits(value), Double.doubleToLongBits(machine.getTq01()));
        }
        machine.setTq01(0.);
        machine.setTq011(0.);
        assertTrue(hasError(net, "Tq0''"));
    }

    @Test void ordinaryRoundRotorStillRequiresTheTransientWinding() throws Exception {
        String base = "testData/adpter/psse/v33/SMIB/";
        var net = new PSSEMultiFileLoader().loadDStab(base + "SMIB_v33.raw",
                base + "SMIB_v33_genrou.dyr").getDStabilityNet();
        var machine = (RoundRotorMachine) net.getMachine("Bus1-mach1");
        machine.setTq01(0.);
        machine.setXq1(machine.getXq());
        assertTrue(hasError(net, "Tq0'"));
    }
}
