package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.BaseDStabNetwork;

/** Independent attached-plant input replay; does not certify full network feedback. */
@Tag("extended")
class ReeccPlantReplayConformanceTest {
    @Test void attachedPlantStatesAndAcceptedPortsFollowAllRecordedSamples() throws Exception {
        for(String profile:new String[]{"plant-flat","plant-fault"})for(boolean history:new boolean[]{false,true}) {
            Path folder=Path.of("testData/private/verification-artifacts/reecc1-plant-remote-20261104",profile+"-a");
            org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(folder.resolve("native.csv")), "Private independent reference not installed");
            var rows=org.interpss.core.dstab.reference.RepcaNativeVerification.rows(Files.readString(folder.resolve("native.csv")));
            assertEquals(1205,rows.size(),"Full reference row count");
            var context=new PSSEMultiFileLoader().loadDStab(folder.resolve("network.raw").toString(),folder.resolve("dynamic.dyr").toString());
            var network=context.getDStabilityNet();
            assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
            var converter=(Regca1Model)((DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
            var model=(Reecc1Model)converter.getActiveElectricalController();
            var plant=model.getPlantController();assertNotNull(plant);
            var first=rows.getFirst();var previous=first;boundary(network,first);
            double p0=first.get("P_PU"),q0=first.get("Q_PU");
            model.configureIntegrationStep(.00025);model.initialize(p0,q0,first.get("V1"));
            var names=new ArrayList<String>();
            for(int i=0;i<7;i++)names.add("ELEC_STATE"+i);
            for(int i=0;i<7;i++)names.add("PLANT_STATE"+i);
            names.addAll(List.of("ELEC_VAR2","ELEC_VAR3","ELEC_VAR4","PREF_INPUT","QEXT_INPUT"));
            double[] maximum=new double[names.size()],at=new double[names.size()];
            var trace=new StringBuilder("time_s");for(String name:names)trace.append(',').append(name).append("_actual,").append(name).append("_reference");trace.append(",algebraic_ip,algebraic_iq\n");
            int events=0;
            for(var row:rows) {
                double t=row.get("time_s"),old=previous.get("time_s");
                if(t>=0.&&t>old) {
                    boundary(network,previous);
                    double p=previous.get("P_PU"),q=previous.get("Q_PU"),v=previous.get("V1"),f=1.+previous.get("F1_DEV");
                    if(history)model.stepTwoStep(.00025,p,q,v,f);else model.step(.00025,p,q,v,f);
                } else if(t>=0.&&row!=first&&t==old) {
                    events++;boundary(network,row);
                    if(history){if(profile.endsWith("fault"))model.rebaseNextTwoStep();model.refreshTwoStep(row.get("P_PU"),row.get("Q_PU"),row.get("V1"),1.+row.get("F1_DEV"));}
                }
                var values=new ArrayList<Double>(model.getNamedStates().values());
                for(double value:plant.getNativeStates())values.add(value);
                values.addAll(List.of(history?model.getTwoStepIpcmd():model.getIpcmd(),history?-model.getTwoStepIqcmd():-model.getIqcmd(),model.getStateOfCharge(),p0+plant.getPref(),q0+plant.getQref()));
                trace.append(t);
                for(int i=0;i<names.size();i++) {
                    double value=values.get(i),reference=row.get(names.get(i)),error=Math.abs(value-reference);
                    assertTrue(Double.isFinite(value));
                    assertTrue(error<=ceiling(profile,history,i), profile+" twoStep="+history+" "+names.get(i)+" t="+t+" error="+error+" ceiling="+ceiling(profile,history,i));
                    if(error>maximum[i]){maximum[i]=error;at[i]=t;}
                    trace.append(',').append(value).append(',').append(reference);
                }
                trace.append(',').append(model.getIpcmd()).append(',').append(-model.getIqcmd()).append('\n');previous=row;
            }
            assertEquals(2,events,"Both run-segment boundary duplicates are retained");
            Files.writeString(Path.of("../target/reecc-plant-replay-"+profile+"-"+history+".csv"),trace);
            System.out.println("REECC plant "+profile+" history="+history+" names="+names+" max="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
        }
    }
    // Fixed physical-unit ceilings for this independent input replay. No sample exclusions.
    private static double ceiling(String profile, boolean history, int channel) {
        if (profile.equals("plant-flat")) return 1.e-7;
        if (channel == 1 || channel == 4 || channel == 7) return 1.e-10;
        if (channel == 6) return history ? 1.e-10 : 5.e-9;
        if (channel == 16) return 6.e-8;
        if (history) return channel == 15 ? 1.5e-4 : 3.e-6;
        return switch(channel) {
            case 0 -> 8.e-4;
            case 2, 9, 12 -> 1.e-5;
            case 3 -> 3.e-4;
            case 5, 13, 17 -> 4.e-5;
            case 8 -> 6.e-4;
            case 10 -> 5.e-5;
            case 11, 14 -> 2.5e-4;
            case 15 -> 1.e-3;
            case 18 -> 1.2e-4;
            default -> throw new IllegalArgumentException("Unqualified channel " + channel);
        };
    }
    private static void boundary(BaseDStabNetwork<?,?> network,Map<String,Double> row) {
        for(int i=1;i<=3;i++) {
            double v=row.get("V"+i),angle=Math.toRadians(row.get("A"+i+"_DEG"));
            network.getDStabBus("Bus"+i).setVoltage(new Complex(v*Math.cos(angle),v*Math.sin(angle)));
            network.getDStabBus("Bus"+i).setFreq(1.+row.get("F"+i+"_DEV"));
        }
    }
}
