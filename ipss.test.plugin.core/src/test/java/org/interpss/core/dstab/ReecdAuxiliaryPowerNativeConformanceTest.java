package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.ReecdAuxiliaryPowerReference;
import org.interpss.core.dstab.reference.ReecdAuxiliaryPowerReference.Profile;
import org.interpss.core.dstab.reference.WtgwgoaNativeReference;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Native Paux active-path checks, not complete REECD or WTGIBFFRA conformance. */
public class ReecdAuxiliaryPowerNativeConformanceTest extends CorePluginTestSetup {
    private static final Path BASE=Path.of("testData/adpter/psse/v33/renewable");
    private static final String[] KEYS={"Vmeas","Pmeas","PIQ","PIV","Q_V","Pord","Vcomp"};
    @Test void exactRecordedVoltageAndPauxReplayRetainsTheAlgebraicEventSide() throws Exception {
        double[] coarse=null;
        for(Profile p:ReecdAuxiliaryPowerReference.profiles())if(p.kind().equals("playback")){
            p.verify();var context=load();var source=controller(context.getDStabilityNet());
            var m=new Reecd1Model(source.getData());m.configureIntegrationStep(p.step());m.initialize(p.initialP(),p.initialQ(),p.initialV());
            var inputs=p.inputs().lines().map(ReecdAuxiliaryPowerNativeConformanceTest::numbers).toList();assertEquals(p.samples(),inputs.size());
            var observed=new ArrayList<double[]>();
            for(int row=0;row<inputs.size();row++){
                double[] input=inputs.get(row);m.setAuxiliaryPower(input[1]);
                if(row>0&&!p.isPost(row))m.step(p.step(),p.initialP(),p.initialQ(),input[0],1);
                observed.add(signals(m,input[1],input[0],0,0,0,0));
            }
            double[] maximum=compare(p,observed,false);
            if(coarse==null)coarse=maximum;
            else for(int channel:new int[]{0,4,7,8})assertTrue(maximum[channel]<.65*coarse[channel],
                    "Recorded-input channel "+channel+" must improve under step halving");
        }
    }
    @Test void coupledThreeBusPauxStepMatchesNativeAtBothIntegrationSteps() throws Exception {
        double[] coarse=null;
        for(Profile p:ReecdAuxiliaryPowerReference.profiles())if(p.kind().equals("coupled")){
            p.verify();var context=load();var net=context.getDStabilityNet();net.setAllowGenWithoutMach(true);
            var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
            assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            algorithm.setSimuStepSec(p.step());algorithm.setTotalSimuTimeSec(2.5);algorithm.setSimuOutputHandler(new StateMonitor());
            assertTrue(algorithm.initialization());var m=controller(net);var observed=new ArrayList<double[]>();var csv=new ArrayList<String>();
            csv.add("time_s,Vmeas,Pmeas,PIQ,PIV,Q_V,Pord,Vcomp,Ipcmd,native_positive_Q_command,Paux,V1,V2,V3,P,Q");
            int post=p.postRows().getFirst(),changeTick=p.tick(post);double auxiliary=0;
            for(int tick=0;tick<=p.tick(p.samples()-1);tick++){
                if(tick>0)assertTrue(algorithm.solveDEqnStep(true));
                double[] values=networkSignals(net,m,auxiliary);observed.add(values);csv.add(csv(algorithm.getSimuTime(),values));
                if(tick==changeTick){auxiliary=.1;m.setAuxiliaryPower(auxiliary);
                    values=networkSignals(net,m,auxiliary);observed.add(values);csv.add(csv(algorithm.getSimuTime(),values));}
            }
            assertEquals(p.samples(),observed.size());double[] maximum=compare(p,observed,true);
            if(coarse==null)coarse=maximum;else assertTrue(maximum[13]<.65*coarse[13],"Coupled P must improve under step halving");
            Path output=Path.of("target","reecd-paux-smib");Files.createDirectories(output);Files.write(output.resolve(p.name()+".csv"),csv);
        }
    }
    @Test void frozenReferencesRejectMutationAndHaveIndependentBundleIdentity(){
        var bundle=new StringBuilder();var definitions=new StringBuilder();
        for(Profile p:ReecdAuxiliaryPowerReference.profiles()){
            p.verify();bundle.append(p.rawSha256()).append('|').append(p.inputsSha256()).append('|').append(p.checkpointsSha256()).append('\n');
            definitions.append(p.name()).append('|').append(p.kind()).append('|');
            for(double value:new double[]{p.step(),p.initialP(),p.initialQ(),p.initialV()})
                definitions.append(String.format("%016x",Double.doubleToRawLongBits(value))).append('|');
            definitions.append(p.samples()).append('|').append(String.join(",",p.postRows().stream().map(Object::toString).toList())).append('|')
                    .append(p.rawSha256()).append('|').append(p.inputsSha256()).append('|').append(p.checkpointsSha256()).append('\n');
            assertThrows(IllegalArgumentException.class,()->p.verify(p.inputs(),"9"+p.checkpoints().substring(1)));
            assertThrows(UnsupportedOperationException.class,()->p.postRows().add(0));
        }
        assertEquals("ec3c8a44f4679534c2dfe1c17a5783f4850ef39cdf51c920be5aa7559589c251",WtgwgoaNativeReference.digest(bundle.toString()));
        assertEquals("9577b86353ace5af91bf4e12cc0e1377d8dbff1cb255e4597fb372c55ff5c4a3",WtgwgoaNativeReference.digest(definitions.toString()));
    }
    private static double[] compare(Profile p,List<double[]> observed,boolean coupled){
        double[] maximum=new double[15],at=new double[15];int posts=0,count=0;
        for(String line:p.checkpoints().lines().toList()){
            double[] expected=numbers(line);int row=(int)expected[0];double[] actual=observed.get(row);if(p.isPost(row))posts++;
            // Native bypassed Vcomp storage and inactive voltage PI initialize
            // to zero, whereas InterPSS retains algebraic/unused coordinates.
            assertEquals(0,expected[5],0);assertEquals(0,expected[8],0);
            for(int c=0;c<15;c++){
                if(c==3||c==6||!coupled&&c>=11)continue;
                assertTrue(Double.isFinite(actual[c]));double error=Math.abs(actual[c]-expected[c+2]);
                if(error>maximum[c]){maximum[c]=error;at[c]=expected[1];}
            }
            count++;
        }
        assertEquals(1,posts);assertTrue(count>500);
        double[] bounds=coupled?new double[]{p.step()==.0005?8e-6:6.5e-6,1e-12,1e-12,0,1.25e-6,2e-8,0,
                p.step()==.0005?2.7e-6:2.2e-6,1.25e-6,2e-9,1.3e-5,8e-6,1e-6,p.step()==.0005?7.8e-4:4e-4,1.35e-6}
                :new double[]{p.step()==.0005?3.1e-3:1.55e-3,1e-12,1e-12,0,p.step()==.0005?2.1e-4:1.05e-4,1e-10,0,
                    p.step()==.0005?8.1e-4:4e-4,p.step()==.0005?2.1e-4:1.05e-4,1e-14,1e-14,0,0,0,0};
        for(int c=0;c<15;c++)if(c!=3&&c!=6&&(coupled||c<11))assertTrue(maximum[c]<=bounds[c],
                p.name()+" active channel "+c+": "+maximum[c]+" > "+bounds[c]);
        System.out.println("REECD Paux "+p.name()+" active-path max="+Arrays.toString(maximum)+" at="+Arrays.toString(at)
                +"; both reference-update sides retained; inactive PI/bypass coordinates not claimed as native storage parity");
        return maximum;
    }
    private static double[] signals(Reecd1Model m,double aux,double v1,double v2,double v3,double p,double q){
        var result=new double[15];var states=m.getNamedStates();for(int i=0;i<7;i++)result[i]=states.get(KEYS[i]);
        result[7]=m.getIpcmd();result[8]=-m.getIqcmd();result[9]=aux;result[10]=v1;result[11]=v2;result[12]=v3;result[13]=p;result[14]=q;return result;
    }
    private static double[] networkSignals(BaseDStabNetwork<?,?> net,Reecd1Model m,double aux){
        var gen=(DStabGen)net.getBus("Bus1").getContributeGen("1");var converter=(Regca1Model)gen.getDynamicGenDevice();var values=converter.getNamedStates();
        return signals(m,aux,net.getBus("Bus1").getVoltageMag(),net.getBus("Bus2").getVoltageMag(),net.getBus("Bus3").getVoltageMag(),
                values.get("Active Power"),values.get("Reactive Power"));
    }
    private static Reecd1Model controller(BaseDStabNetwork<?,?> net){
        var gen=(DStabGen)net.getBus("Bus1").getContributeGen("1");return (Reecd1Model)((Regca1Model)gen.getDynamicGenDevice()).getActiveElectricalController();
    }
    private static com.interpss.simu.SimuContext load() throws Exception {
        return new PSSEMultiFileLoader().loadDStab(BASE.resolve("regca_reeca_repca_bus1062.raw").toString(),BASE.resolve("regca_reecd_paux_bus1062.dyr").toString());
    }
    private static double[] numbers(String line){return Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();}
    private static String csv(double time,double[] values){return time+","+String.join(",",Arrays.stream(values).mapToObj(Double::toString).toList());}
}
