package org.interpss.fadapter.psse.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.net.OriginalDataFormat;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.util.sample.SampleTestingCases;

class AcContingencyMonitorReportTest {
    @Test
    void checksRateBChangeFiltersDirectedInterfacesAndCapacity() throws Exception {
        var base = network();
        var after = network();
        var branch = base.getBranch("1", "2", "1");
        String id = branch.getId();
        var post = after.getBranch(id);
        branch.setRatingMva1(5000);
        post.setRatingMva1(5000);
        branch.setRatingMva2(20);
        post.setRatingMva2(20);
        base.getBus("2").setVoltage(new Complex(0.98, 0));
        after.getBus("2").setVoltage(new Complex(0.86, -0.15));

        var monitors = new MonitoredElements();
        monitors.thermalBranchIds().add(id);
        monitors.addVoltageRange("2", MonVoltageDirective.forBus(2, 0.95, 1.05));
        monitors.addInterface("reverse", new MonitoredElements.InterfaceDefinition(10,
                List.of(new MonitoredElements.InterfaceMember(id, false)), true));
        monitors.addInterface("incomplete", new MonitoredElements.InterfaceDefinition(10, List.of(), false));
        var report = new AcContingencyMonitorReport(base, monitors, AcContingencyMonitorReport.Options.defaults());

        var result = report.inspect("changed", after);
        assertEquals(1, result.overloads().size());
        assertEquals(20.0, result.overloads().getFirst().rating());
        assertEquals(1, result.voltageViolations().size());
        assertEquals(post.powerTo2From(UnitType.mVA).getReal(),
                result.interfaces().getFirst().contingencyMw(), 1e-10);
        assertEquals(List.of("incomplete"), result.incompleteInterfaces());
        assertEquals(1, result.interfaceOverloads().size());
        assertTrue(report.availableCapacity().stream().anyMatch(row ->
                row.id().equals(id) && row.limitingCase().equals("changed") && row.available() < 0));

        var unchanged = report.inspect("unchanged", base);
        assertTrue(unchanged.overloads().isEmpty());
        assertTrue(unchanged.voltageViolations().isEmpty());
        assertTrue(unchanged.interfaceOverloads().isEmpty());

        post.setStatus(false);
        assertEquals(0.0, report.inspect("open", after).interfaces().getFirst().contingencyMw());
    }

    @Test
    void explicitThreeWindingMonitorMetersTheNamedWinding(@TempDir Path temp) throws Exception {
        var base = SampleTestingCases.createNet3WXfr(OriginalDataFormat.PSSE);
        var transformer = base.get3WXfr("Bus2", "Bus3", "Bus4");
        transformer.getFromBus().setNumber(2);
        transformer.getToBus().setNumber(3);
        transformer.getTertiaryBus().setNumber(4);

        var file = temp.resolve("study.mon");
        Files.writeString(file, """
                MONITOR INTERFACE 'third winding' RATING 100 MW
                MONITOR BRANCH FROM BUS 4 TO BUS 2 TO BUS 3
                END
                MONITOR INTERFACE 'missing branch' RATING 200 MW
                MONITOR BRANCH FROM BUS 5 TO BUS 2 CKT 8
                END
                """);
        var monitors = new MonElementHelper(null, base).resolve(new MonFileParser().parse(file));
        var defined = monitors.getInterfaceDefinitions().get("third winding");
        assertTrue(defined.complete());
        assertEquals(transformer.getTertAclfBranch().getId(), defined.members().getFirst().branchId());
        assertEquals(transformer.getTertAclfBranch().getFromBus().getNumber() == 4,
                defined.members().getFirst().fromTerminal());
        assertEquals(List.of("5 to 2 circuit 8"),
                monitors.getInterfaceDefinitions().get("missing branch").unresolvedMembers());
    }

    private static AclfNetwork network() throws Exception {
        var network = CoreObjectFactory.createAclfNetwork();
        SampleTestingCases.load_LF_5BusSystem(network);
        return network;
    }
}
