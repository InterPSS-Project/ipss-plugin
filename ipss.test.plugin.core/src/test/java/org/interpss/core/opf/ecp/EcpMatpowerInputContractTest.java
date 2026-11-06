package org.interpss.core.opf.ecp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.core.algo.ecf.EcfNetworkModel;
import com.interpss.opf.ecp.*;
import org.interpss.fadapter.matpower.MatpowerOpfDirectParser;

public class EcpMatpowerInputContractTest {
    @TempDir Path temporary;
    private EcpPowerFlowComparisonTest helper() {var h=new EcpPowerFlowComparisonTest();h.temporary=temporary;return h;}
    @Test void importerPreservesFiniteAngleLimitsForExplicitUnsupportedStatus() throws Exception {
        var input=EcpPowerFlowComparisonTest.reference().getAsJsonObject("case_inputs").getAsJsonObject("case9").getAsJsonObject("input").deepCopy();
        var row=input.getAsJsonArray("branch").get(0).getAsJsonArray();row.set(11,new com.google.gson.JsonPrimitive(-5));row.set(12,new com.google.gson.JsonPrimitive(10));
        var network=new MatpowerOpfDirectParser().parse(helper().caseFile("angle_case",input).toString());
        assertEquals(Math.toRadians(-5),network.getBranchList().getFirst().getAngleDifferenceLimit().getMin());
        assertEquals(Math.toRadians(10),network.getBranchList().getFirst().getAngleDifferenceLimit().getMax());
        assertEquals(EcpOpfResult.Status.UNSUPPORTED_MODEL,new EcpOpfAlgorithm().solve(network).status());
    }
    @Test void transformerChargingAndPhaseShiftMatchDirectComplexTapEquations() throws Exception {
        var input=EcpPowerFlowComparisonTest.reference().getAsJsonObject("case_inputs").getAsJsonObject("case9").getAsJsonObject("input").deepCopy();
        var row=input.getAsJsonArray("branch").get(0).getAsJsonArray();
        row.set(4,new com.google.gson.JsonPrimitive(.04));row.set(8,new com.google.gson.JsonPrimitive(1.07));row.set(9,new com.google.gson.JsonPrimitive(7.5));
        var network=new MatpowerOpfDirectParser().parse(helper().caseFile("tap_case",input).toString());
        var branch=EcfNetworkModel.compileForOptimization(network,false).branches().getFirst();
        var y=Complex.ONE.divide(new Complex(row.get(2).getAsDouble(),row.get(3).getAsDouble()));
        var tap=new Complex(1.07*Math.cos(Math.toRadians(7.5)),1.07*Math.sin(Math.toRadians(7.5)));
        assertEquals(0,branch.yff().subtract(y.add(new Complex(0,.02)).divide(1.07*1.07)).abs(),1e-12);
        assertEquals(0,branch.ytt().subtract(y.add(new Complex(0,.02))).abs(),1e-12);
        assertEquals(0,branch.yft().subtract(y.negate().divide(tap.conjugate())).abs(),1e-12);
        assertEquals(0,branch.ytf().subtract(y.negate().divide(tap)).abs(),1e-12);
    }
    @Test void extraReactiveCostRowsAndCapabilityCurvesAreRejectedInsteadOfDiscarded() throws Exception {
        var input=EcpPowerFlowComparisonTest.reference().getAsJsonObject("case_inputs").getAsJsonObject("case9").getAsJsonObject("input").deepCopy();
        var costs=input.getAsJsonArray("gencost");int size=costs.size();for(int i=0;i<size;i++)costs.add(costs.get(i).deepCopy());
        var exception=assertThrows(com.interpss.common.exp.InterpssException.class,()->new MatpowerOpfDirectParser().parse(helper().caseFile("reactive_cost",input).toString()));
        assertTrue(exception.getMessage().contains("Reactive generation costs"));
        var capability=EcpPowerFlowComparisonTest.reference().getAsJsonObject("case_inputs").getAsJsonObject("case9").getAsJsonObject("input").deepCopy();
        capability.getAsJsonArray("gen").get(0).getAsJsonArray().set(10,new com.google.gson.JsonPrimitive(10));
        var second=assertThrows(com.interpss.common.exp.InterpssException.class,()->new MatpowerOpfDirectParser().parse(helper().caseFile("capability",capability).toString()));
        assertTrue(second.getMessage().contains("capability curves"));
    }
    @Test void explicitInfiniteReactiveLimitsUseTheConfiguredFiniteProxy() throws Exception {
        var input=EcpPowerFlowComparisonTest.reference().getAsJsonObject("case_inputs").getAsJsonObject("case9").getAsJsonObject("input").deepCopy();
        var gen=input.getAsJsonArray("gen").get(0).getAsJsonArray();
        gen.set(3,new com.google.gson.JsonPrimitive("Inf"));gen.set(4,new com.google.gson.JsonPrimitive("-Inf"));
        var file=helper().caseFile("infinite_q",input);
        for(double proxy:new double[]{MatpowerOpfDirectParser.DEFAULT_UNBOUNDED_Q_LIMIT_MVAR,50000}) {
            var network=new MatpowerOpfDirectParser(proxy).parse(file.toString());
            var generator=network.getBus("Bus1").getContributeGenList().getFirst();
            assertEquals(proxy/100,generator.getQGenLimit().getMax());assertEquals(-proxy/100,generator.getOpfLimits().getQLimit().getMin());
            var snapshot=com.interpss.opf.ecp.impl.EcpOpfInputAdapter.snapshot(network,EcpOpfConfig.defaults());
            assertTrue(snapshot.generators().stream().allMatch(g->Double.isFinite(g.q().min()) && Double.isFinite(g.q().max())));
        }
        assertThrows(IllegalArgumentException.class,()->new MatpowerOpfDirectParser(Double.POSITIVE_INFINITY));
    }

}
