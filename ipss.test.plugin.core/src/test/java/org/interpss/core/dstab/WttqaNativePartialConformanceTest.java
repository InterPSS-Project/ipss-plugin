package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.WttqaNativeReference;
import org.interpss.core.dstab.reference.WttqaNativeReference.Profile;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact torque-state replay, independent of the still-unaccepted complete native wind-stack trajectory. */
public class WttqaNativePartialConformanceTest extends CorePluginTestSetup {
    private static List<Map<String,Double>> rows(Profile profile){return HplntdSwitchingNativeSmibTest.rows(profile.body());}
    private static boolean dip(Map<String,Double> row){return row.get("V1")<row.get("TRQ_VAR1")||row.get("V1")>row.get("TRQ_VAR2");}

    @Test void exactNativeStateInputsAndSelectorsAreNotAnOutputAlias() {
        assertEquals(16,DynamicModelCatalog.find("WTTQA1").orElseThrow().parameterCount());assertEquals(10,WttqaNativeReference.profiles().size());
        for(var p:WttqaNativeReference.profiles()) {
            var rows=rows(p);assertEquals(p.samples(),rows.size());assertEquals(24,rows.getFirst().size());assertTrue(rows.getFirst().get("time_s")<0);
            double firstReference=rows.getFirst().get("PITCH_VAR0");boolean seenDip=false;
            for(var row:rows) {
                for(double value:row.values())assertTrue(Double.isFinite(value));
                assertEquals(firstReference,row.get("PITCH_VAR0"),0,"Stored plant input is not the varying torque-controller output");
                assertTrue(row.get("TRQ_STATE2")>0&&row.get("TRQ_STATE2")<2,"These profiles deliberately isolate an inactive torque limit");
                seenDip|=dip(row);
            }
            assertEquals(p.mode().equals("dip"),seenDip);
            if(p.mode().equals("curve"))assertEquals(.92,1+rows.getFirst().get("SPEED_DEV"),1e-7);
        }
    }

    @Test void replayAllThreeNativeStatesAndPreserveUnacceptedCurveDiagnostics()throws Exception {
        for(var p:WttqaNativeReference.profiles()) {
            var rows=rows(p);var old=rows.getFirst();var model=new Wttqa1Model(p.data());
            // Initialize from physical electrical power and shaft speed, never from recorded controller states.
            model.initialize(old.get("P_PU"),1+old.get("SPEED_DEV"));double previous=old.get("time_s");
            double[] maximum=new double[4],at=new double[4];
            var trace=new StringBuilder("time_s,ipss_power,reference_power,ipss_wref,reference_wref,ipss_integral,reference_integral,ipss_curve,reference_curve\n");
            for(var row:rows) {
                double time=row.get("time_s");
                if(time>previous) {
                    model.step(p.step(),old.get("P_PU"),1+old.get("SPEED_DEV"),old.get("PITCH_VAR0"),dip(old),0);
                    model.step(p.step(),row.get("P_PU"),1+row.get("SPEED_DEV"),row.get("PITCH_VAR0"),dip(row),1);
                } else if(time==previous)model.step(0,row.get("P_PU"),1+row.get("SPEED_DEV"),row.get("PITCH_VAR0"),dip(row),0);
                double[] actual={model.getFilteredPower(),model.getSpeedReference(),model.getTorqueIntegral(),model.speedForPower(model.getFilteredPower())};
                double[] expected={row.get("TRQ_STATE0"),row.get("TRQ_STATE1"),row.get("TRQ_STATE2"),row.get("TRQ_VAR0")};trace.append(time);
                for(int i=0;i<actual.length;i++) {
                    assertTrue(Double.isFinite(actual[i]));double error=Math.abs(actual[i]-expected[i]);if(error>maximum[i]){maximum[i]=error;at[i]=time;}
                    trace.append(',').append(actual[i]).append(',').append(expected[i]);
                }
                var named=model.getNamedStates();assertEquals(model.getTorqueIntegral(),named.get("Torque Integral"),0);assertEquals(named,model.getNamedStates());
                assertTrue(model.getTorque()>=0&&model.getTorque()<=2);trace.append('\n');old=row;previous=time;
            }
            var file=Path.of("target/wttqa-native-replay",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
            System.out.println("WTTQA native replay "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
            if(!p.mode().equals("curve")) {
                double[] bounds=switch(p.name()) {
                    case "flat-coarse","flat-fine" -> new double[]{2e-8,1e-12,2e-8,1e-12};
                    case "speed-coarse" -> new double[]{9.5e-5,1e-12,8.5e-7,1e-12};
                    case "speed-fine" -> new double[]{5e-5,1e-12,2e-6,1e-12};
                    case "power-coarse" -> new double[]{9.5e-5,1e-12,2.6e-7,1e-12};
                    case "power-fine" -> new double[]{5e-5,1e-12,1.25e-6,1e-12};
                    case "dip-coarse" -> new double[]{5.9e-4,1e-12,2.1e-7,1e-12};
                    case "dip-fine" -> new double[]{3.05e-4,1e-12,5.2e-7,1e-12};
                    default -> throw new IllegalArgumentException("Unknown accepted torque profile");
                };
                for(int i=0;i<maximum.length;i++)assertTrue(maximum[i]<bounds[i],p.name()+" channel "+i+" max="+maximum[i]+" at="+at[i]);
            }
        }
    }

    @Test void nativeCurveInputCounterexampleDoesNotChangeThePublishedFilteredPowerPath() {
        for(var p:WttqaNativeReference.profiles())if(p.mode().equals("curve")) {
            var model=new Wttqa1Model(p.data());double rawResidual=0,filteredResidual=0;
            for(var row:rows(p)) {
                rawResidual=Math.max(rawResidual,Math.abs(model.speedForPower(row.get("P_PU"))-row.get("TRQ_VAR0")));
                filteredResidual=Math.max(filteredResidual,Math.abs(model.speedForPower(row.get("TRQ_STATE0"))-row.get("TRQ_VAR0")));
            }
            assertTrue(rawResidual<6e-8,"Native diagnostic follows raw electrical power in this executable");
            assertTrue(filteredResidual>.008,"Retain the source-versus-executable curve-input discrepancy");
            model.initialize(.24,.92);model.step(.001,.4,.92,.24,false,0);
            assertEquals(.248,model.getFilteredPower(),1e-14);assertEquals(.92,model.getSpeedReference(),1e-14,"Published cascade samples the original filtered-power state");
        }
    }

    @Test void exactImportAndFiniteFullSolverProfilesDoNotClaimWholeStackParity(@TempDir Path input)throws Exception {
        for(var p:WttqaNativeReference.profiles()) {
            Path directory=input.resolve(p.name());Files.createDirectories(directory);Files.writeString(directory.resolve("network.raw"),WttqaNativeReference.network());Files.writeString(directory.resolve("dynamic.dyr"),p.input());
            var context=new PSSEMultiFileLoader().loadDStab(directory.resolve("network.raw").toString());var network=context.getDStabilityNet();network.setBypassDataCheck(true);network.setAllowGenWithoutMach(true);
            var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(network)).setStrictImport(true);parser.parseDynFile(directory.resolve("dynamic.dyr").toString());
            assertTrue(parser.getLastImportReport().isStrictlyComplete());assertEquals(7,parser.getLastImportReport().totalRecordCount());
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(p.step());algorithm.setSimuOutputHandler(new StateMonitor());
            if(!p.mode().equals("flat"))network.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus2",network,com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,
                new org.apache.commons.math3.complex.Complex(0,p.mode().equals("dip")?100.0/529.0:1),null,.05,.05),"WttqaSelectorFault");
            assertTrue(algorithm.initialization());var converter=(Regca1Model)((DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();var stack=converter.getReeca1Controller().getWindControlStack();
            assertEquals(p.data(),stack.getTorqueController().getData());
            int count=(int)Math.round(.5/p.step());var trace=new StringBuilder("time_s,V1,V2,speed,torque_integral,pref\n");
            for(int tick=0;tick<=count;tick++) {
                for(double state:stack.getTorqueController().getNamedStates().values())assertTrue(Double.isFinite(state));assertTrue(Double.isFinite(stack.getPref()));assertTrue(stack.getGeneratorSpeed()>.1);
                trace.append(tick*p.step()).append(',').append(network.getBus("Bus1").getVoltageMag()).append(',').append(network.getBus("Bus2").getVoltageMag()).append(',').append(stack.getGeneratorSpeed()).append(',').append(stack.getTorqueController().getTorqueIntegral()).append(',').append(stack.getPref()).append('\n');
                if(tick<count)assertTrue(algorithm.solveDEqnStep(true));
            }
            var file=Path.of("target/wttqa-imported-smib",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
        }
    }
}
