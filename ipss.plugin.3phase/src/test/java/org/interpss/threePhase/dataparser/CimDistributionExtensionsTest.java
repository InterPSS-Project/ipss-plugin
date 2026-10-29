package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.threePhase.dataParser.cim.CimDistributionDataParser;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

class CimDistributionExtensionsTest {
    static final Path FIXTURES = Path.of("src/test/resources/cim/extensions");
    @BeforeAll static void init() { IpssCorePlugin.init(); }
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings={"split_a","split_b","split_c","split_clock","three_yyy","three_dyy","three_ydy","three_yyd",
            "three_mesh","three_dyy_lead","three_ydy_lead",
            "three_tank","three_zero","three_negative","cable_cn","cable_ts","cable_cn_neutral","cable_ts_neutral",
            "cable_cn_one","cable_ts_one","cable_cn_two","cable_ts_two","cable_cn_permuted"})
    void complexVoltagesAndSourcePowerMatchNativeOpenDss(String name) throws Exception {
        var parser = new CimDistributionDataParser();
        var net = parser.parse(FIXTURES.resolve(name + ".xml"));
        var pf = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
        pf.setPFMethod(DistributionPFMethod.Fixed_Point);
        pf.setTolerance(1e-8); pf.setMaxIteration(200);
        assertTrue(pf.powerflow(), name + " convergence");
        Map<String,String> ids = new HashMap<>();
        parser.getBusIds().forEach((uri,id) -> ids.put(parser.getCimNames().get(uri).toLowerCase(Locale.ROOT),id));
        Map<String,Complex[]> reference = new LinkedHashMap<>();
        for (String row : Files.readAllLines(FIXTURES.resolve(name + "-voltage.csv")).stream().skip(1).toList()) {
            String[] f = row.split(",");
            reference.computeIfAbsent(f[0], k -> new Complex[3])[Integer.parseInt(f[1])-1] = new Complex(Double.parseDouble(f[2]),Double.parseDouble(f[3]));
        }
        double max = 0; int checked = 0;
        for (var entry : reference.entrySet()) {
            var bus = net.getBus(ids.get(entry.getKey()));
            assertNotNull(bus, entry.getKey());
            var voltage = bus.get3PhaseVotlages();
            Complex[] actual = {voltage.a_0,voltage.b_1,voltage.c_2};
            boolean delta = (name.startsWith("three_ydy") && entry.getKey().equals("secondary"))
                    || (name.equals("three_yyd") && entry.getKey().equals("tertiary"));
            for (int i = 0; i < 3; i++) if (entry.getValue()[i] != null) {
                Complex a = actual[i], b = entry.getValue()[i].divide(bus.getBaseVoltage()/Math.sqrt(3));
                if (delta) {
                    a = a.subtract(actual[(i+1)%3]).divide(Math.sqrt(3));
                    b = entry.getValue()[i].subtract(entry.getValue()[(i+1)%3]).divide(bus.getBaseVoltage());
                }
                double error = a.subtract(b).abs(); max = Math.max(max,error); checked++;
                assertTrue(error < 1e-6, name + " " + entry.getKey() + "." + (i+1) + " complex error=" + error + "; actual=" + a + "; native=" + b);
            }
        }
        assertTrue(checked >= 4);
        if (name.startsWith("three_")) {
            var branches = parser.getEquipmentBranchIds().values().stream().filter(idsForEquipment -> idsForEquipment.size()==3).findFirst().orElseThrow();
            assertThrows(UnsupportedOperationException.class, () -> branches.add("invalid"));
            assertEquals(3,branches.stream().distinct().count());
        }
        if (name.startsWith("cable")) {
            String id = parser.getEquipmentIds().entrySet().stream().filter(e -> e.getKey().endsWith("#cable")).findFirst().orElseThrow().getValue();
            var branch = net.getBranchList().stream().filter(b -> b.getName().equals(id)).findFirst().orElseThrow();
            var cable = (com.interpss.core.threephase.IBranch3Phase) branch;
            var z = cable.getZabc(); var y = cable.getFromShuntYabc();
            Complex[][] zz = {{z.aa,z.ab,z.ac},{z.ba,z.bb,z.bc},{z.ca,z.cb,z.cc}};
            Complex[][] yy = {{y.aa,y.ab,y.ac},{y.ba,y.bb,y.bc},{y.ca,y.cb,y.cc}};
            int[] order = name.endsWith("_one") ? new int[]{1} : name.endsWith("_two") ? new int[]{0,2}
                    : name.endsWith("_permuted") ? new int[]{2,0} : new int[]{0,1,2};
            double base = Math.pow(net.getBus(branch.getFromBus().getId()).getBaseVoltage(),2)/(net.getBaseMva()*1e6);
            var rows = Files.readAllLines(FIXTURES.resolve(name + "-matrix.csv"));
            for (int i = 0; i < order.length; i++) for (int j = 0; j < order.length; j++) {
                String[] f = rows.get(1+i*order.length+j).split(",");
                Complex ohms = zz[order[i]][order[j]].multiply(base/500);
                assertEquals(Double.parseDouble(f[0]),ohms.getReal(),1e-9,name + " resistance");
                assertEquals(Double.parseDouble(f[1]),ohms.getImaginary(),1e-9,name + " reactance");
                double capacitance = yy[order[i]][order[j]].getImaginary()/base*2/(2*Math.PI*60)*1e9/500;
                assertEquals(Double.parseDouble(f[2]),capacitance,1e-8,name + " capacitance");
            }
        }
        // Native TotalPower is measured at the source's external terminal and
        // excludes its internal Thevenin impedance losses.
        var swing = net.getBus(ids.get("source"));
        String[] power = Files.readAllLines(FIXTURES.resolve(name + "-power.csv")).get(1).split(",");
        var current = new org.interpss.numeric.datatype.Complex3x1();
        var v = swing.get3PhaseVotlages();
        for (var branch : net.getBranchList()) if (branch.isActive() && !net.getBus(branch.getFromBus().getId()).isSwing() && !net.getBus(branch.getToBus().getId()).isSwing()) {
            var three = (com.interpss.core.threephase.IBranch3Phase) branch;
            if (branch.getFromBus().equals(swing)) current = current.add(three.getYffabc().multiply(v)
                    .add(three.getYftabc().multiply(net.getBus(branch.getToBus().getId()).get3PhaseVotlages())));
            else if (branch.getToBus().equals(swing)) current = current.add(three.getYttabc().multiply(v)
                    .add(three.getYtfabc().multiply(net.getBus(branch.getFromBus().getId()).get3PhaseVotlages())));
        }
        Complex generation = v.a_0.multiply(current.a_0.conjugate()).add(v.b_1.multiply(current.b_1.conjugate()))
                .add(v.c_2.multiply(current.c_2.conjugate())).multiply(net.getBaseMva()*1000/3);
        assertEquals(Double.parseDouble(power[0]),generation.getReal(),0.05,name + " source kW");
        assertEquals(Double.parseDouble(power[1]),generation.getImaginary(),0.05,name + " source kvar");
        System.out.println("CIM_EXTENSION " + name + " max_complex_voltage_error_pu=" + max);
    }

    @ParameterizedTest
    @CsvSource(delimiter='|', value={
        "cable_cn|<cim:ConcentricNeutralCableInfo.neutralStrandCount>13|<cim:ConcentricNeutralCableInfo.neutralStrandCount>0|strand count",
        "cable_cn|<cim:WireInfo.insulationThickness>0.005|<cim:WireInfo.insulationThickness>0.02|insulation",
        "cable_ts|<cim:TapeShieldCableInfo.tapeLap>20|<cim:TapeShieldCableInfo.tapeLap>100|tape shield",
        "cable_cn|<cim:WirePosition.xCoord>0.15|<cim:WirePosition.xCoord>0.0|overlap",
        "three_yyy|<cim:TapChanger.step>4|<cim:TapChanger.step>40|Tap step",
        "three_yyy|<cim:PowerTransformerEnd.ratedS>10000000|<cim:PowerTransformerEnd.ratedS>0|positive",
        "three_yyy|<cim:PowerTransformerEnd.phaseAngleClock>0|<cim:PowerTransformerEnd.phaseAngleClock>1.5|integer",
        "three_tank|ShortCircuitTest.GroundedEnds rdf:resource=\"#rating3\"|ShortCircuitTest.GroundedEnds rdf:resource=\"#info\"|Missing short-circuit",
        "split_a|OrderedPhaseCodeKind.Ns2|OrderedPhaseCodeKind.Ns3|Unsupported phase"
    })
    void invalidExtensionDataClearsPreviousResult(String name, String old, String replacement, String message) throws Exception {
        String xml = Files.readString(FIXTURES.resolve(name + ".xml"));
        assertTrue(xml.contains(old),"Fixture mutation must apply");
        Path changed = temporary.resolve("invalid.xml");
        Files.writeString(changed,xml.replace(old,replacement));
        var parser = new CimDistributionDataParser();
        parser.parse(FIXTURES.resolve(name + ".xml"));
        var ex = assertThrows(IllegalArgumentException.class,() -> parser.parse(changed));
        assertTrue(ex.getMessage().contains(message),ex.toString());
        assertNull(parser.getDistNetwork()); assertTrue(parser.getBusIds().isEmpty()); assertTrue(parser.getEquipmentBranchIds().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings={"split_b","three_tank","cable_cn_neutral"})
    void extensionVoltagesAreIndependentOfNetworkPowerBase(String name) throws Exception {
        var a = new CimDistributionDataParser(); var b = new CimDistributionDataParser();
        a.parse(List.of(FIXTURES.resolve(name + ".xml")),null,1);
        b.parse(List.of(FIXTURES.resolve(name + ".xml")),null,100);
        for (var parser : List.of(a,b)) {
            var pf = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(parser.getDistNetwork());
            pf.setPFMethod(DistributionPFMethod.Fixed_Point);pf.setTolerance(1e-8);pf.setMaxIteration(200);
            assertTrue(pf.powerflow());
        }
        for (String id : a.getBusIds().values()) assertTrue(a.getDistNetwork().getBus(id).get3PhaseVotlages()
                .subtract(b.getDistNetwork().getBus(id).get3PhaseVotlages()).absMax()<1e-7);
    }

    @Test void zeroStarArmPrimitiveRemainsFiniteAndConservesCurrent() {
        var y = org.interpss.threePhase.basic.ThreeWindingTransformerAdmittance.perUnit(
                new Complex(.003,.03),new Complex(.004,.04),new Complex(.007,.07));
        for (int i=0;i<3;i++) {
            assertTrue(y[i][0].add(y[i][1]).add(y[i][2]).abs()<1e-12);
            for(int j=0;j<3;j++) { assertTrue(Double.isFinite(y[i][j].abs())); assertEquals(y[i][j],y[j][i]); }
        }
        assertThrows(IllegalArgumentException.class,() -> org.interpss.threePhase.basic.ThreeWindingTransformerAdmittance.perUnit(Complex.ZERO,Complex.ZERO,Complex.ZERO));
    }
}
