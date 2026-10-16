package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.threePhase.dataParser.cim.CimDistributionDataParser;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CimDistributionDataParserTest {
    private static final Path FIXTURES = Path.of("src/test/resources/cim/cimhub");
    @BeforeAll static void initialize() { IpssCorePlugin.init(); }

    @ParameterizedTest
    @ValueSource(strings={"YYBal", "YYBalCore", "YYUnBal", "DYBal", "DYUnBal", "GYDBal", "GYDUnBal", "DDBal", "DDUnBal"})
    void cimHubPowerFlowMatchesIndependentOpenDss(String name) throws Exception {
        var parser = new CimDistributionDataParser();
        var net = parser.parse(FIXTURES.resolve(name + ".xml"));
        assertEquals(5, net.getNoBus()); // four connectivity nodes plus source Thevenin bus
        assertEquals(4, net.getNoBranch());
        assertEquals(4, parser.getBusIds().size());
        var pf = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
        pf.setPFMethod(org.interpss.threePhase.powerflow.DistributionPFMethod.Fixed_Point);
        pf.setTolerance(1e-8);
        pf.setMaxIteration(200);
        assertTrue(pf.powerflow(), name + " convergence");
        Map<String,String> ids = new HashMap<>();
        parser.getBusIds().forEach((uri, id) -> ids.put(parser.getCimNames().get(uri).toLowerCase(Locale.ROOT), id));
        Map<String, Complex[]> referenceVoltages = new HashMap<>();
        for (String row : Files.readAllLines(FIXTURES.resolve(name + "-voltage.csv")).stream().skip(1).toList()) {
            String[] f = row.split(",");
            referenceVoltages.computeIfAbsent(f[0], k -> new Complex[3])[Integer.parseInt(f[1])-1] = new Complex(Double.parseDouble(f[2]), Double.parseDouble(f[3]));
        }
        double max = 0;
        int count = 0;
        for (String row : Files.readAllLines(FIXTURES.resolve(name + "-voltage.csv")).stream().skip(1).toList()) {
            String[] f = row.split(",");
            var bus = net.getBus(ids.get(f[0]));
            assertNotNull(bus, f[0]);
            var v = bus.get3PhaseVotlages();
            Complex actual = switch (Integer.parseInt(f[1])) { case 1 -> v.a_0; case 2 -> v.b_1; default -> v.c_2; };
            Complex reference = new Complex(Double.parseDouble(f[2]), Double.parseDouble(f[3])).divide(bus.getBaseVoltage() / Math.sqrt(3));
            if ((name.startsWith("GYD") || name.startsWith("DD")) && (f[0].equals("n3") || f[0].equals("n4"))) {
                int i = Integer.parseInt(f[1])-1;
                Complex[] av = {v.a_0, v.b_1, v.c_2};
                actual = av[i].subtract(av[(i+1)%3]).divide(Math.sqrt(3));
                Complex[] rv = referenceVoltages.get(f[0]);
                reference = rv[i].subtract(rv[(i+1)%3]).divide(bus.getBaseVoltage());
            }
            double error = actual.subtract(reference).abs();
            max = Math.max(max, error); count++;
            assertTrue(error < 2e-4, name + " " + f[0] + "." + f[1] + " complex voltage error=" + error + "; actual=" + actual + "; reference=" + reference);
        }
        assertEquals(12, count);
        System.out.println("CIM_REFERENCE " + name + " max_complex_voltage_error_pu=" + max);
    }
}
