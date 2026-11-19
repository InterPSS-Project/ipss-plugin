package org.interpss.core.opf.ecp;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Stream;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.google.gson.*;
import com.interpss.core.algo.ecf.*;
import com.interpss.opf.ecp.impl.EcpCostModel;
import com.interpss.opf.ecp.impl.EcpGeneratorModel;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.matpower.MatpowerOpfDirectParser;

/** Importer-to-core comparisons against frozen external-engine inputs/results.
 * OPF checks evaluate reference points; they do not claim an InterPSS OPF solve. */
public class EcpPowerFlowComparisonTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize() { IpssCorePlugin.init(); }
    static Stream<Arguments> configurations() {
        return Stream.of("case9","case14","case30").flatMap(name -> Arrays.stream(EcfPowerFlowConfig.Backend.values())
                .map(backend -> Arguments.of(name,backend)));
    }
    static JsonObject reference() throws Exception {
        try(var stream=EcpPowerFlowComparisonTest.class.getResourceAsStream("/opf/ecp/pypower-baseline.json")) {
            assertNotNull(stream,"Frozen reference fixture must be present");
            return JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    Path caseFile(String name,JsonObject input) throws Exception {
        var text=new StringBuilder("function mpc = "+name+"\nmpc.version = '2';\nmpc.baseMVA = ")
                .append(input.get("baseMVA").getAsDouble()).append(";\n");
        for(String key:new String[]{"bus","gen","branch","gencost"}) {
            text.append("mpc.").append(key).append(" = [\n");
            for(var row:input.getAsJsonArray(key)) {
                for(var value:row.getAsJsonArray()) {
                    String token=value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()?value.getAsString():"";
                    if(token.equals("Inf") || token.equals("-Inf"))text.append(token);
                    else text.append(value.getAsDouble());
                    text.append(' ');
                }
                text.append(";\n");
            }
            text.append("];\n");
        }
        var path=temporary.resolve(name+".m");Files.writeString(path,text);return path;
    }
    @ParameterizedTest @MethodSource("configurations")
    void importerAndCoreCurrentBalanceReproduceEveryReferenceBusAndTerminal(String name,EcfPowerFlowConfig.Backend backend) throws Exception {
        var fixture=reference().getAsJsonObject("case_inputs").getAsJsonObject(name);
        var input=fixture.getAsJsonObject("input");var expected=fixture.getAsJsonObject("power_flow");
        double base=input.get("baseMVA").getAsDouble();
        var network=new MatpowerOpfDirectParser().parse(caseFile(name,input).toString());
        var before=network.getBusList().stream().map(b -> b.getVoltage()).toList();
        var config=new EcfPowerFlowConfig(1e-10,50,.2,20,EcfPowerFlowConfig.Initialization.FLAT,backend,false);
        var actual=new EcfPowerFlowAlgorithm().solve(network,config);
        assertTrue(actual.converged(),actual.detail());
        double maxVoltage=0,maxGeneration=0,maxBranch=0;
        for(var element:expected.getAsJsonArray("bus")) {
            var row=element.getAsJsonArray();String id="Bus"+row.get(0).getAsInt();
            var voltage=polar(row.get(7).getAsDouble(),Math.toRadians(row.get(8).getAsDouble()));
            double error=actual.voltages().get(id).subtract(voltage).abs();
            maxVoltage=Math.max(maxVoltage,error);assertEquals(0,error,1e-8,id);
        }
        var generation=new HashMap<String,Complex>();
        for(var element:expected.getAsJsonArray("gen")) {
            var row=element.getAsJsonArray();if(row.get(7).getAsDouble()<=0)continue;
            generation.merge("Bus"+row.get(0).getAsInt(),new Complex(row.get(1).getAsDouble()/base,row.get(2).getAsDouble()/base),Complex::add);
        }
        for(var entry:generation.entrySet()) {
            double error=actual.generation().get(entry.getKey()).subtract(entry.getValue()).abs();
            maxGeneration=Math.max(maxGeneration,error);assertEquals(0,error,1e-8,entry.getKey());
        }
        var expectedBranches=expected.getAsJsonArray("branch");
        for(int i=0;i<network.getBranchList().size();i++) {
            var branch=network.getBranchList().get(i);var flow=actual.branchFlows().get(branch.getId());var row=expectedBranches.get(i).getAsJsonArray();
            var f=new Complex(row.get(13).getAsDouble()/base,row.get(14).getAsDouble()/base);
            var t=new Complex(row.get(15).getAsDouble()/base,row.get(16).getAsDouble()/base);
            double error=Math.max(f.subtract(flow.fromPower()).abs(),t.subtract(flow.toPower()).abs());
            maxBranch=Math.max(maxBranch,error);assertEquals(0,error,1e-8,branch.getId());
        }
        assertEquals(before,network.getBusList().stream().map(b -> b.getVoltage()).toList());
        System.out.printf("ECF_REFERENCE %s %s iterations=%d maxVoltage=%.3e maxGeneration=%.3e maxTerminalPower=%.3e maxPQ=%.3e%n",
                name,backend,actual.iterations(),maxVoltage,maxGeneration,maxBranch,actual.maximumPowerMismatch());
    }
    static Stream<Arguments> opfConfigurations() {
        return Stream.of("case9","case14","case30").flatMap(name -> Stream.of("I","S").map(mode -> Arguments.of(name,mode)));
    }
    @ParameterizedTest @MethodSource("opfConfigurations")
    void importedCoreEquationsAndIndividualCostsReproduceFrozenOpfReferencePoints(String name,String mode) throws Exception {
        var report=reference();var input=report.getAsJsonObject("case_inputs").getAsJsonObject(name).getAsJsonObject("input");
        JsonObject expected=null;
        for(var item:report.getAsJsonArray("runs")) {
            var run=item.getAsJsonObject();if(name.equals(run.get("case").getAsString()) && mode.equals(run.get("flow_limit").getAsString()))expected=run;
        }
        assertNotNull(expected);double base=input.get("baseMVA").getAsDouble();
        var network=new MatpowerOpfDirectParser().parse(caseFile(name,input).toString());
        var model=EcfNetworkModel.compile(network,false);
        var voltages=new Complex[model.busCount()];var generators=new Complex[model.busCount()];Arrays.fill(generators,Complex.ZERO);
        for(int i=0;i<voltages.length;i++) {var row=expected.getAsJsonArray("bus").get(i).getAsJsonArray();voltages[i]=polar(row.get(7).getAsDouble(),Math.toRadians(row.get(8).getAsDouble()));}
        var index=new HashMap<Integer,Integer>();for(int i=0;i<voltages.length;i++)index.put((int)network.getBusList().get(i).getNumber(),i);
        // Resolve rows by bus and ordinal, not by flattened bus iteration order.
        var ordinals=new HashMap<Integer,Integer>();double cost=0;
        for(var item:expected.getAsJsonArray("gen")) {
            var row=item.getAsJsonArray();int number=row.get(0).getAsInt();int ordinal=ordinals.merge(number,1,Integer::sum)-1;
            if(row.get(7).getAsDouble()<=0)continue;int bus=index.get(number);
            var power=new Complex(row.get(1).getAsDouble()/base,row.get(2).getAsDouble()/base);generators[bus]=generators[bus].add(power);
            var point=EcpGeneratorModel.fromPower(voltages[bus].getReal(),voltages[bus].getImaginary(),power.getReal(),power.getImaginary());
            assertEquals(0,point.residual()[2],1e-12);assertEquals(0,point.residual()[3],1e-12);
            var generator=network.getBus("Bus"+number).getContributeGenList().get(ordinal);
            cost+=EcpCostModel.fromPerUnitCurve(generator.getIncCost().getQuadraticCurve()).value(power.getReal());
        }
        assertEquals(expected.get("objective_per_hour").getAsDouble(),cost,1e-7);
        var currents=model.currents(voltages);double maximum=0;
        for(int i=0;i<voltages.length;i++) {
            var load=model.bus(i).load();var power=voltages[i].multiply(currents[i].conjugate()).add(new Complex(load.p(),load.q())).subtract(generators[i]);
            maximum=Math.max(maximum,power.abs());assertEquals(0,power.abs(),1e-7,model.bus(i).id());
        }
        for(int i=0;i<model.branches().size();i++) {
            var branch=model.branches().get(i);double rating=input.getAsJsonArray("branch").get(i).getAsJsonArray().get(5).getAsDouble()/base;
            if(rating==0)continue;
            for(boolean from:new boolean[]{false,true}) {
                double value=branch.thermal(from,mode.equals("S"),voltages[branch.from()],voltages[branch.to()]).value();
                assertTrue(Math.sqrt(value)<=rating+1e-7,branch.id()+" terminal rating");
            }
        }
        System.out.printf("ECP_REFERENCE_POINT %s %s objective=%.9f maxPQ=%.3e%n",name,mode,cost,maximum);
    }
    private static Complex polar(double magnitude,double angle) {return new Complex(magnitude*Math.cos(angle),magnitude*Math.sin(angle));}
}
