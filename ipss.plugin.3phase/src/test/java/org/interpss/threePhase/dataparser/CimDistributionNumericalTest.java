package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.acsc.PhaseCode;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Physical-unit regressions for conversion errors exposed by public CIM parity. */
class CimDistributionNumericalTest {
    @TempDir Path temporary;
    @BeforeAll static void init() { IpssCorePlugin.init(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void smallPositiveImpedanceDoesNotEraseActivePhases(boolean twoPhases) throws Exception {
        var network = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
        ThreePhaseObjectFactory.create3PDStabBus("from", network);
        ThreePhaseObjectFactory.create3PDStabBus("to", network);
        var branch = ThreePhaseObjectFactory.create3PBranch("from", "to", "1", network);
        branch.setBranchCode(AclfBranchCode.LINE);
        branch.setPhaseCode(twoPhases ? PhaseCode.AC : PhaseCode.C);
        var z = new Complex3x3();
        z.cc = new Complex(2e-10, 1e-10);
        if (twoPhases) {
            z.aa = new Complex(3e-10, 2e-10);
            z.ac = new Complex(1e-11, 2e-11); z.ca = z.ac;
        }
        branch.setZabc(z);
        var product = z.multiply(branch.getBranchYabc());
        assertEquals(0, product.cc.subtract(Complex.ONE).abs(), 1e-12);
        assertEquals(0, product.aa.subtract(twoPhases ? Complex.ONE : Complex.ZERO).abs(), 1e-12);
        assertEquals(0, product.ac.abs(), 1e-12);
        assertEquals(0, product.ca.abs(), 1e-12);
        assertEquals(Complex.ZERO, product.bb);
    }

    @Test void offNominalTransformerRetainsPhysicalLeakageImpedance() throws Exception {
        var parser = parse("""
                new transformer.test phases=3 windings=2 buses=[source load] conns=[wye wye] kvs=[13.2 .48] kvas=[1000 1000] %rs=[.5 .5] xhl=4
                new load.test bus1=load.1 phases=1 kv=.277128129211 kw=30 kvar=12 model=1
                """);
        var branch = parser.getBranchByName("test");
        var phaseBranch = (com.interpss.core.threephase.IBranch3Phase) branch;
        double impedanceBase = Math.pow(branch.getFromBus().getBaseVoltage(), 2) / 10e6;
        Complex physicalY = phaseBranch.getYffabc().aa.divide(impedanceBase);
        Complex expected = new Complex(.01, .04).multiply(13.2 * 13.2).reciprocal();
        assertEquals(0, physicalY.subtract(expected).abs(), 1e-10,
                "Rated-to-bus voltage ratio must appear exactly once in physical terminal admittance");
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 5, 8})
    void singlePhaseLoadUsesRatedVoltageForPhysicalPower(int model) throws Exception {
        var parser = parse("new load.test bus1=source.1 phases=1 conn=wye kv=6 kw=30 kvar=12 model="
                + model + " vminpu=0 vmaxpu=100 zipv=[.2 .3 .5 .2 .3 .5 0]\n");
        var bus = parser.getDistNetwork().getBus("source");
        bus.set3PhaseVotlages(new Complex3x1(Complex.ONE,
                new Complex(-.5, -Math.sqrt(3)/2), new Complex(-.5, Math.sqrt(3)/2)));
        double ratio = bus.getBaseVoltage() / Math.sqrt(3) / 6000;
        double scale = switch (model) { case 2 -> ratio * ratio; case 5 -> ratio;
            case 8 -> .2 * ratio * ratio + .3 * ratio + .5; default -> 1; };
        Complex power = bus.calcLoad3PhEquivCurInj().a_0.conjugate().multiply(-10000.0/3);
        assertEquals(30 * scale, power.getReal(), 1e-8);
        assertEquals(12 * scale, power.getImaginary(), 1e-8);
    }

    private OpenDSSDataParser parse(String equipment) throws Exception {
        Path file = temporary.resolve("case.dss");
        Files.writeString(file, "clear\nnew circuit.test bus1=source basekv=12.47 pu=1 phases=3 r1=.01 x1=.05 r0=.01 x0=.05\n" + equipment);
        var parser = new OpenDSSDataParser();
        parser.setNormalizeSinglePhaseLoadVoltageBases(true);
        assertTrue(parser.parseFeederData(temporary.toString(), "case.dss"));
        assertTrue(parser.calcVoltageBases());
        assertTrue(parser.convertActualValuesToPU(10));
        return parser;
    }
}
