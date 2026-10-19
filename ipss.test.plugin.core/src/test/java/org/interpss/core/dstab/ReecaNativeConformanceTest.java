package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.core.dstab.reference.ReecaNativeReference;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.cache.StateMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Recorded-input localization; these diagnostics are not coupled trajectory acceptance. */
public class ReecaNativeConformanceTest extends CorePluginTestSetup {
    @Test void everyColdPairedLocalProfileImportsAndInitializesTheNativeConvention(@TempDir Path staged)throws Exception {
        for(var p:ReecaNativeReference.profiles()) {
            var directory=staged.resolve(p.name());Files.createDirectories(directory);
            Files.writeString(directory.resolve("network.raw"),p.network());Files.writeString(directory.resolve("dynamic.dyr"),p.input());
            var context=new PSSEMultiFileLoader().loadDStab(directory.resolve("network.raw").toString());
            var network=context.getDStabilityNet();network.setBypassDataCheck(true);network.setAllowGenWithoutMach(true);
            var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(network)).setStrictImport(true);
            parser.parseDynFile(directory.resolve("dynamic.dyr").toString());
            assertTrue(parser.getLastImportReport().isStrictlyComplete());assertEquals(3,parser.getLastImportReport().totalRecordCount());
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(p.step());algorithm.setSimuOutputHandler(new StateMonitor());
            algorithm.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);
            if(p.hasFault())network.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus2",network,
                    com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,
                    // The reference declares 100 ohm on this 230 kV, 100 MVA network.
                    new org.apache.commons.math3.complex.Complex(0,100.0/(230.0*230.0/100.0)),null,.05,.05),"ElectricalFault");
            assertTrue(algorithm.initialization());
            var converter=(Regca1Model)((DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
            var model=converter.getReeca1Controller();assertTrue(model.isNativeRecordConvention());
            var before=model.getNamedStates();assertEquals(6,model.getNativeStates().length);assertEquals(before,model.getNamedStates());
            if(p.name().startsWith("pf-voltage"))assertEquals(network.getBus("Bus1").getVoltageMag()-converter.getInitialReactivePower(),model.getInitializedVoltageBias(),1e-7);
            var trace=new StringBuilder("time_s,V1,V2,V3");for(int i=0;i<6;i++)trace.append(",state").append(i);trace.append('\n');
            for(int tick=0;tick<=Math.round(.5/p.step());tick++) {
                trace.append(tick*p.step());
                for(int bus=1;bus<=3;bus++){double value=network.getBus("Bus"+bus).getVoltageMag();assertTrue(Double.isFinite(value));trace.append(',').append(value);}
                for(double state:model.getNativeStates()){assertTrue(Double.isFinite(state));trace.append(',').append(state);}trace.append('\n');
                if(tick<Math.round(.5/p.step()))assertTrue(algorithm.solveDEqnStep(true));
            }
            var file=Path.of("target/reeca-coupled-diagnostics",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
        }
    }
    @Test void allNativeElectricalStatesHaveCompleteRecordedInputReplays()throws Exception {
        for(var profile:ReecaNativeReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(profile.body());
            assertEquals(profile.samples(),rows.size());assertEquals(34,rows.getFirst().size());
            for(boolean history:new boolean[]{false,true}) {
                var first=rows.getFirst();var previous=first;
                var model=Reeca1Model.fromNativeRecord(profile.data(),null);
                model.initialize(first.get("P_PU"),first.get("Q_PU"),first.get("V1"));
                double[] maximum=new double[6],at=new double[6];int events=0;
                var trace=new StringBuilder("time_s");
                for(int i=0;i<6;i++)trace.append(",ipss_state").append(i).append(",native_state").append(i);
                trace.append('\n');
                for(var row:rows) {
                    double elapsed=row.get("time_s")-previous.get("time_s");
                    if(elapsed>0) {
                        if(history)model.stepTwoStep(profile.step(),previous.get("P_PU"),previous.get("Q_PU"),previous.get("V1"),1);
                        else {
                            model.step(profile.step(),previous.get("P_PU"),previous.get("Q_PU"),previous.get("V1"),1,0);
                            model.step(profile.step(),row.get("P_PU"),row.get("Q_PU"),row.get("V1"),1,1);
                        }
                    }else if(row!=first&&row.get("time_s")>=0) {
                        events++;
                        if(history){model.rebaseNextTwoStep();model.stepTwoStep(0,row.get("P_PU"),row.get("Q_PU"),row.get("V1"),1);}
                        else model.step(0,row.get("P_PU"),row.get("Q_PU"),row.get("V1"),1,1);
                    }
                    trace.append(row.get("time_s"));double[] actual=model.getNativeStates();
                    for(int i=0;i<6;i++) {
                        double target=row.get("ELEC_STATE"+i),error=Math.abs(actual[i]-target);
                        assertTrue(Double.isFinite(actual[i]));
                        if(error>maximum[i]){maximum[i]=error;at[i]=row.get("time_s");}
                        trace.append(',').append(actual[i]).append(',').append(target);
                    }
                    trace.append('\n');previous=row;
                }
                assertEquals(2,events,"Both run boundaries retain their duplicate rows, including flat profiles");
                var file=Path.of("target/reeca-native-replay",profile.name()+(history?"-history":"-staged")+".csv");
                Files.createDirectories(file.getParent());Files.writeString(file,trace);
                System.out.println("REECA replay "+profile.name()+" history="+history+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
                double[] bounds=history?profile.historyBounds():profile.stagedBounds();
                for(int i=0;i<6;i++)assertTrue(maximum[i]<=bounds[i],profile.name()+" history="+history+" state "+i+": "+maximum[i]+" > "+bounds[i]);
            }
        }
    }

    @Test void firstFaultVoltageFilterIncrementUsesTheIndependentNativeThreeQuarterHistoryWitness() {
        int witnesses=0;
        for(var p:ReecaNativeReference.profiles())if(p.name().startsWith("q-")) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());
            for(int i=1;i+1<rows.size();i++) {
                var before=rows.get(i-1);var now=rows.get(i);var next=rows.get(i+1);
                if(now.get("time_s").equals(before.get("time_s"))&&now.get("V1")<before.get("V1")-.1) {
                    double expected=.75*p.step()/p.data().trv()*(now.get("V1")-now.get("ELEC_STATE0"));
                    assertEquals(expected,next.get("ELEC_STATE0")-now.get("ELEC_STATE0"),6e-8);witnesses++;
                }
            }
        }
        assertEquals(4,witnesses);
    }
}
