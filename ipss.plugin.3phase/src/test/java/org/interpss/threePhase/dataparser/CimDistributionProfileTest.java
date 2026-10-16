package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.threePhase.dataParser.cim.CimDistributionDataParser;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CimDistributionProfileTest {
    private static final Path INPUT = Path.of("src/test/resources/cim/mixed.xml");
    @TempDir Path temp;
    @BeforeAll static void init() { IpssCorePlugin.init(); }
    private String uri(String id) { return "https://interpss.org/cim/distribution#" + id; }
    private Path changed(String original, String replacement) throws Exception {
        String xml = Files.readString(INPUT);
        assertTrue(xml.contains(original), "Mutation must affect the fixture");
        Path file = temp.resolve("changed.xml");
        Files.writeString(file, xml.replace(original, replacement));
        return file;
    }
    private void solve(CimDistributionDataParser parser) {
        var pf = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(parser.getDistNetwork());
        pf.setPFMethod(DistributionPFMethod.Fixed_Point); pf.setTolerance(1e-9); pf.setMaxIteration(200);
        assertTrue(pf.powerflow());
    }
    @Test void mixedPhasesTapsZipShuntsAndGenerationMatchNativeDss() throws Exception {
        var p = new CimDistributionDataParser();var net = p.parse(INPUT);
        assertEquals(6, net.getNoBus());assertEquals(6, net.getNoBranch());
        solve(p);
        double max = 0;int count = 0;
        for (String row : Files.readAllLines(Path.of("src/test/resources/cim/mixed-voltage.csv")).stream().skip(1).toList()) {
            String[] f = row.split(","); var bus = net.getBus(p.getBusIds().get(uri(f[0])));
            assertNotNull(bus, f[0]);var v = bus.get3PhaseVotlages();
            Complex actual = switch (Integer.parseInt(f[1])) {case 1 -> v.a_0; case 2 -> v.b_1; default -> v.c_2;};
            Complex expected = new Complex(Double.parseDouble(f[2]), Double.parseDouble(f[3])).divide(bus.getBaseVoltage()/Math.sqrt(3));
            double error = actual.subtract(expected).abs();max = Math.max(max,error);count++;
            assertTrue(error < 1e-5, f[0]+"."+f[1]+" complex voltage error="+error+" actual="+actual+" expected="+expected);
        }
        assertEquals(10,count);
        var matrix = net.getBranchList().stream().filter(b -> b.getName().equals(p.getEquipmentIds().get(uri("matrixline")))).findFirst().orElseThrow();
        double zbase = 12470.0*12470/1e7;
        assertEquals(.1/zbase, matrix.getZabc().bb.getReal(), 1e-12);
        assertEquals(.08/zbase, matrix.getZabc().cc.getReal(), 1e-12);
        assertEquals(.02/zbase, matrix.getZabc().bc.getReal(), 1e-12);
        var tank = net.getBranchList().stream().filter(b -> b.getName().equals(p.getEquipmentIds().get(uri("tank")))).findFirst().orElseThrow();
        assertEquals(1.0125,tank.getToTurnRatio(),1e-12);
        var tie = net.getBranchList().stream().filter(b -> b.getName().equals(p.getEquipmentIds().get(uri("tie")))).findFirst().orElseThrow();
        assertFalse(tie.isActive());
        assertTrue(p.getWarnings().stream().anyMatch(s -> s.contains("Fixed P/Q")));
        assertThrows(UnsupportedOperationException.class, () -> p.getBusIds().clear());
        System.out.println("CIM_REFERENCE mixed max_complex_voltage_error_pu="+max);
    }
    @Test void baseMvaChangesUnitsButNotPhysicalVoltages() throws Exception {
        var a = new CimDistributionDataParser();var b = new CimDistributionDataParser();
        a.parse(List.of(INPUT), null, 1);b.parse(List.of(INPUT), null, 20);solve(a);solve(b);
        for (String id : a.getBusIds().values()) assertTrue(a.getDistNetwork().getBus(id).get3PhaseVotlages()
                .subtract(b.getDistNetwork().getBus(id).get3PhaseVotlages()).absMax()<1e-7);
    }
    @Test void splitEquipmentAndSteadyStateProfilesMergeInEitherOrder() throws Exception {
        Path eq = changed("<cim:TapChanger.step>2</cim:TapChanger.step>", "");
        Path state = temp.resolve("state.xml");
        Files.writeString(state,"""
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:cim="http://iec.ch/TC57/CIM100#">
                <rdf:Description rdf:about="#tap"><cim:TapChanger.step>2</cim:TapChanger.step></rdf:Description></rdf:RDF>
                """);
        var a=new CimDistributionDataParser();var b=new CimDistributionDataParser();
        a.parse(List.of(eq,state),null,10);b.parse(List.of(state,eq),null,10);
        assertEquals(a.getMappedDss(), b.getMappedDss());solve(a);solve(b);
        assertEquals(a.getBusIds(), b.getBusIds());
    }
    @Test void feederSelectionIsExplicitWhenThereAreMultipleFeeders() throws Exception {
        Path file=changed("</rdf:RDF>","<cim:Feeder rdf:ID=\"other\"/></rdf:RDF>");
        var p=new CimDistributionDataParser();
        assertThrows(IllegalArgumentException.class,()->p.parse(file));assertNull(p.getDistNetwork());
        assertNotNull(p.parse(List.of(file),uri("feeder"),10));
        assertThrows(IllegalArgumentException.class,()->p.parse(List.of(file),"missing",10));
    }
    @ParameterizedTest
    @CsvSource(delimiter='|',value={
        "<cim:Conductor.length>200</cim:Conductor.length>|<cim:Conductor.length>-1</cim:Conductor.length>|must be positive",
        "<cim:PhaseImpedanceData.row>2</cim:PhaseImpedanceData.row>|<cim:PhaseImpedanceData.row>9</cim:PhaseImpedanceData.row>|matrix cell",
        "<cim:PhaseImpedanceData.row>2</cim:PhaseImpedanceData.row>|<cim:PhaseImpedanceData.row>1.5</cim:PhaseImpedanceData.row>|integer",
        "<cim:PhaseImpedanceData.x>0.0008</cim:PhaseImpedanceData.x>|<cim:PhaseImpedanceData.x>NaN</cim:PhaseImpedanceData.x>|finite number",
        "<cim:TapChanger.step>2</cim:TapChanger.step>|<cim:TapChanger.step>20</cim:TapChanger.step>|Tap step",
        "<cim:LoadResponseCharacteristic.pConstantPower>50</cim:LoadResponseCharacteristic.pConstantPower>|<cim:LoadResponseCharacteristic.pConstantPower>5</cim:LoadResponseCharacteristic.pConstantPower>|ZIP fractions",
        "<cim:LoadResponseCharacteristic.exponentModel>false</cim:LoadResponseCharacteristic.exponentModel>|<cim:LoadResponseCharacteristic.exponentModel>true</cim:LoadResponseCharacteristic.exponentModel>|Exponential",
        "SinglePhaseKind.C|SinglePhaseKind.s3|Unsupported",
        "<cim:Switch.open>true</cim:Switch.open>|<cim:Switch.open>perhaps</cim:Switch.open>|Invalid boolean",
        "rdf:resource=\"#n3\"|rdf:resource=\"#missing\"|Unresolved resource",
        "<cim:ShortCircuitTest.loss>0.4</cim:ShortCircuitTest.loss>|<cim:ShortCircuitTest.loss>1000</cim:ShortCircuitTest.loss>|impedance magnitude",
        "<cim:NoLoadTest.loss>0.1</cim:NoLoadTest.loss>|<cim:NoLoadTest.loss>1000</cim:NoLoadTest.loss>|no-load losses",
        "<cim:TransformerEnd.grounded>true</cim:TransformerEnd.grounded>|<cim:TransformerEnd.grounded>false</cim:TransformerEnd.grounded>|solidly grounded"
    })
    void invalidElectricalDataFailsWithoutPublishingPartialNetwork(String old, String replacement, String message) throws Exception {
        var p=new CimDistributionDataParser();p.parse(INPUT);
        Exception ex=assertThrows(IllegalArgumentException.class,()->p.parse(changed(old,replacement)));
        assertTrue(ex.getMessage().contains(message),ex.toString());assertNull(p.getDistNetwork());assertTrue(p.getBusIds().isEmpty());
    }
    @Test void xmlEntitiesAreRejected() throws Exception {
        Path file=changed("<rdf:RDF", "<!DOCTYPE rdf:RDF [<!ENTITY external SYSTEM 'file:///does-not-exist'>]><rdf:RDF");
        var p=new CimDistributionDataParser();assertThrows(java.io.IOException.class,()->p.parse(file));assertNull(p.getDistNetwork());
    }
    @Test void unknownConductingEquipmentIsRejected() throws Exception {
        Path file=changed("</rdf:RDF>","""
            <cim:SeriesCompensator rdf:ID="unknown"><cim:Equipment.EquipmentContainer rdf:resource="#feeder"/></cim:SeriesCompensator>
            <cim:Terminal rdf:ID="ut"><cim:Terminal.ConductingEquipment rdf:resource="#unknown"/><cim:Terminal.ConnectivityNode rdf:resource="#n1"/></cim:Terminal></rdf:RDF>
            """);
        var ex=assertThrows(IllegalArgumentException.class,()->new CimDistributionDataParser().parse(file));
        assertTrue(ex.getMessage().contains("Unsupported conducting equipment"));
    }
    @Test void instanceSequenceParametersEqualPerLengthParameters() throws Exception {
        Path file=changed("<cim:ACLineSegment.PerLengthImpedance rdf:resource=\"#seq\" />", """
            <cim:ACLineSegment.r>0.05</cim:ACLineSegment.r><cim:ACLineSegment.x>0.1</cim:ACLineSegment.x>
            <cim:ACLineSegment.r0>0.15</cim:ACLineSegment.r0><cim:ACLineSegment.x0>0.3</cim:ACLineSegment.x0>
            <cim:ACLineSegment.bch>0.0000005</cim:ACLineSegment.bch>
            """);
        var a=new CimDistributionDataParser();var b=new CimDistributionDataParser();
        a.parse(INPUT);b.parse(file);solve(a);solve(b);
        for(String id:a.getBusIds().values()) assertTrue(a.getDistNetwork().getBus(id).get3PhaseVotlages()
                .subtract(b.getDistNetwork().getBus(id).get3PhaseVotlages()).absMax()<1e-9);
    }
    @Test void inconsistentTerminalPhaseRecordsAreRejected() throws Exception {
        Path file=changed("<cim:Terminal rdf:ID=\"matrixlinet1\">", """
            <cim:Terminal rdf:ID="matrixlinet1"><cim:Terminal.phases rdf:resource="http://iec.ch/TC57/CIM100#PhaseCode.AB"/>
            """);
        var ex=assertThrows(IllegalArgumentException.class,()->new CimDistributionDataParser().parse(file));
        assertTrue(ex.getMessage().contains("Terminal phases disagree"));
    }
    @Test void disconnectedTransformerTerminalIsRejected() throws Exception {
        Path file=changed("<cim:Terminal rdf:ID=\"tankt1\">", """
            <cim:Terminal rdf:ID="tankt1"><cim:ACDCTerminal.connected>false</cim:ACDCTerminal.connected>
            """);
        var ex=assertThrows(IllegalArgumentException.class,()->new CimDistributionDataParser().parse(file));
        assertTrue(ex.getMessage().contains("Disconnected terminals"));
    }
    @Test void incompleteMatrixAndConflictingProfilesAreRejected() throws Exception {
        Path file=changed("<cim:PhaseImpedanceData.PhaseImpedance rdf:resource=\"#matrix\" />", "");
        var ex=assertThrows(IllegalArgumentException.class,()->new CimDistributionDataParser().parse(file));
        assertTrue(ex.getMessage().contains("Incomplete phase impedance matrix"));
        Path state=temp.resolve("conflicting.xml");
        Files.writeString(state,"""
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:cim="http://iec.ch/TC57/CIM100#">
            <rdf:Description rdf:about="#tap"><cim:TapChanger.step>3</cim:TapChanger.step></rdf:Description></rdf:RDF>
            """);
        var conflict=assertThrows(IllegalArgumentException.class,()->new CimDistributionDataParser().parse(List.of(INPUT,state),null,10));
        assertTrue(conflict.getMessage().contains("Conflicting values"));
    }
    @Test void invalidBaseAndEmptyInputsAreRejected() {
        var p=new CimDistributionDataParser();
        assertThrows(IllegalArgumentException.class,()->p.parse(List.of(),null,10));
        assertThrows(IllegalArgumentException.class,()->p.parse(List.of(INPUT),null,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->p.parse(List.of(INPUT),null,0));
    }
}
