package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.Tgov1BaseNativeReference;
import org.interpss.core.dstab.reference.Tgov1BaseNativeReference.Profile;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import org.interpss.numeric.datatype.Unit.UnitType;

/** Explicit machine-base/damping references; recorded-input replay is separate from coupled simulation. */
public class Tgov1BaseNativeSmibTest extends CorePluginTestSetup {
    private static List<Map<String,Double>> rows(Profile profile){return HplntdSwitchingNativeSmibTest.rows(profile.body());}

    private static com.interpss.simu.SimuContext load(Profile profile,Path input)throws Exception {
        Files.createDirectories(input);Files.writeString(input.resolve("network.raw"),profile.network());Files.writeString(input.resolve("dynamic.dyr"),profile.input());
        var context=new PSSEMultiFileLoader().loadDStab(input.resolve("network.raw").toString());
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(context.getDStabilityNet())).setStrictImport(true);
        parser.parseDynFile(input.resolve("dynamic.dyr").toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());assertEquals(3,parser.getLastImportReport().totalRecordCount());
        var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(profile.step());algorithm.setSimuOutputHandler(new StateMonitor());assertTrue(algorithm.initialization());
        var machine=context.getDStabilityNet().getMachine("Bus1-mach1");var governor=(PsseTGov1SteamTurGovernor)machine.getGovernor();
        assertEquals(profile.rating(),machine.getRating(UnitType.mVA,context.getDStabilityNet().getBaseKva()),1e-12);
        assertEquals(1,governor.ratingScale,0,"Native seven-constant model is on machine base, without a turbine-rating extension");
        assertEquals(profile.damping(),governor.getData().getDt(),0);assertEquals(0,governor.getData().getTrate(),0);
        assertEquals(50.001/profile.rating(),machine.getPm(),2e-7);return context;
    }

    @Test void everyFixtureRetainsHistoryEventsAndTheMachineBaseIdentity() {
        assertEquals(10,Tgov1BaseNativeReference.profiles().size());
        for(var p:Tgov1BaseNativeReference.profiles()) {
            var rows=rows(p);assertEquals(p.samples(),rows.size());assertEquals(11,rows.getFirst().size());
            assertTrue(rows.getFirst().get("time_s")<0);int duplicates=0;double previous=Double.NEGATIVE_INFINITY;boolean positive=false,negative=false;
            for(var row:rows) {
                double time=row.get("time_s");assertTrue(time>=previous);
                if(time==previous){duplicates++;assertTrue(Math.abs(time-p.apply())<1e-10||Math.abs(time-p.release())<1e-10);}previous=time;
                for(double value:row.values())assertTrue(Double.isFinite(value));
                positive|=row.get("SPEED")>1e-5;negative|=row.get("SPEED")< -1e-5;
                assertTrue(row.get("GOV_STATE_1")>.2&&row.get("GOV_STATE_1")<1.2,"Smooth base profiles must not activate a valve bound");
                double expected=(.1/.3)*row.get("GOV_STATE_1")+row.get("GOV_STATE_2")/.3-p.damping()*row.get("SPEED");
                assertEquals(expected,row.get("PMECH"),3e-7,"Published native storage/damping identity");
            }
            assertEquals(2,duplicates);assertTrue(positive&&negative,"Both damping directions must be exercised");assertEquals(50.001/p.rating(),rows.getFirst().get("PMECH"),2e-7);
            assertEquals(.05*rows.getFirst().get("PMECH"),rows.getFirst().get("GOV_REFERENCE"),1e-8);
        }
    }

    @Test void recordedSpeedReplayChecksEveryValveStorageAndDampedOutput(@TempDir Path input)throws Exception {
        var errors=new LinkedHashMap<String,double[]>();
        for(var p:Tgov1BaseNativeReference.profiles()) {
            var context=load(p,input.resolve(p.name()));var machine=context.getDStabilityNet().getMachine("Bus1-mach1");var governor=(PsseTGov1SteamTurGovernor)machine.getGovernor();
            var rows=rows(p);var old=rows.getFirst();machine.setPm(old.get("PMECH"));machine.setSpeed(1+old.get("SPEED"));assertTrue(governor.initStates(machine.getDStabBus(),machine));
            double increment=0,previous=old.get("time_s");double[] maximum=new double[3],at=new double[3];
            var trace=new StringBuilder("time_s,ipss_valve,reference_valve,ipss_storage,reference_storage,ipss_pm,reference_pm\n");
            for(var row:rows) {
                double time=row.get("time_s");
                if(time>previous) {
                    governor.setRefPoint(old.get("GOV_REFERENCE")+increment);machine.setSpeed(1+old.get("SPEED"));assertTrue(governor.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,machine,0));
                    machine.setSpeed(1+row.get("SPEED"));assertTrue(governor.nextStep(p.step(),DynamicSimuMethod.MODIFIED_EULER,machine,1));
                } else if(time==previous) {
                    if(Math.abs(time-p.apply())<1e-10)increment=.005;
                    if(Math.abs(time-p.release())<1e-10)increment=0;
                    governor.setRefPoint(row.get("GOV_REFERENCE")+increment);machine.setSpeed(1+row.get("SPEED"));
                }
                double[] actual={governor.getValvePosition(),governor.getReheaterStorage(),governor.getOutput(machine)};
                assertEquals(governor.getTurbinePower()-p.damping()*(machine.getSpeed()-1),actual[2],1e-14,"Damping is on machine base, with either speed sign");
                double[] expected={row.get("GOV_STATE_1"),row.get("GOV_STATE_2"),row.get("PMECH")};trace.append(time);
                accumulate(time,actual,expected,maximum,at,trace);trace.append('\n');old=row;previous=time;
                var named=governor.getNamedStates();assertEquals(.3*named.get("t2t3FilterBlock"),governor.getReheaterStorage(),1e-15);assertEquals(named,governor.getNamedStates());
            }
            write(p.name()+"-replay",trace);System.out.println("TGOV1 base replay "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
            double[] bounds=p.name().endsWith("-coarse")?new double[]{3.4e-4,4.6e-7,1.15e-4}:new double[]{1.75e-4,1.6e-7,5.8e-5};
            for(int i=0;i<maximum.length;i++)assertTrue(maximum[i]<bounds[i],p.name()+" replay channel "+i+" max="+maximum[i]+" at="+at[i]);
            errors.put(p.name(),maximum);
        }
        for(String rating:List.of("50","200"))for(String damping:List.of("zero","damped"))for(int i=0;i<3;i++)
            assertTrue(errors.get(rating+"-"+damping+"-fine")[i]<.6*errors.get(rating+"-"+damping+"-coarse")[i],"Recorded-input refinement: "+rating+" "+damping+" channel "+i);
    }

    @Test void fullSolverPredictsMachineBaseDampingAndNetworkResponse(@TempDir Path input)throws Exception {
        for(var p:Tgov1BaseNativeReference.profiles()) {
            var context=load(p,input.resolve(p.name()));var network=context.getDStabilityNet();var machine=network.getMachine("Bus1-mach1");
            var governor=(PsseTGov1SteamTurGovernor)machine.getGovernor();var algorithm=context.getDynSimuAlgorithm();double initial=governor.getRefPoint();List<double[]> actual=new ArrayList<>();
            double referenceEnd=rows(p).getLast().get("time_s");
            int count=(int)Math.ceil(Math.max(.5,referenceEnd)/p.step());
            for(int tick=0;tick<=count;tick++) {
                double time=tick*p.step();if(tick==Math.round(.05/p.step()))governor.setRefPoint(initial+.005);if(tick==Math.round(.20/p.step()))governor.setRefPoint(initial);
                var voltage=machine.getDStabBus().getVoltage();var current=machine.getIgen().subtract(voltage.multiply(machine.getYgen()));var power=voltage.multiply(current.conjugate());
                actual.add(new double[]{time,network.getBus("Bus1").getVoltageMag(),network.getBus("Bus2").getVoltageMag(),power.getReal(),power.getImaginary(),machine.getSpeed()-1,
                    Math.toDegrees(machine.getAngle()),machine.getPm(),governor.getValvePosition(),governor.getReheaterStorage()});
                if(tick<count)assertTrue(algorithm.solveDEqnStep(true));
            }
            double[] maximum=new double[9],at=new double[9];var trace=new StringBuilder("time_s,ipss_v1,reference_v1,ipss_v2,reference_v2,ipss_p,reference_p,ipss_q,reference_q,ipss_speed,reference_speed,ipss_angle_change_deg,reference_angle_change_deg,ipss_pm,reference_pm,ipss_valve,reference_valve,ipss_storage,reference_storage\n");
            String[] labels={"V_BUS1","V_BUS2","P_PU","Q_PU","SPEED","MACH_ANGLE","PMECH","GOV_STATE_1","GOV_STATE_2"};
            double referenceInitialAngle=rows(p).getFirst().get("MACH_ANGLE"),localInitialAngle=actual.getFirst()[6];
            for(var row:rows(p)) {
                double time=row.get("time_s");double[] predicted=interpolate(actual,Math.max(0,time));double[] expected=Arrays.stream(labels).mapToDouble(row::get).toArray();
                double[] values=Arrays.copyOfRange(predicted,1,predicted.length);values[5]-=localInitialAngle;expected[5]-=referenceInitialAngle;
                trace.append(time);accumulate(time,values,expected,maximum,at,trace);trace.append('\n');
            }
            write(p.name()+"-coupled",trace);System.out.println("TGOV1 base coupled "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
            boolean coarse=p.name().endsWith("-coarse");
            double[] bounds={1e-5,1.2e-5,1.1e-4,5.3e-5,7e-7,.0013,coarse?1.15e-4:5.8e-5,coarse?3.4e-4:1.75e-4,coarse?5.5e-7:4.5e-7};
            for(int i=0;i<maximum.length;i++)assertTrue(maximum[i]<bounds[i],p.name()+" coupled "+labels[i]+" max="+maximum[i]+" at="+at[i]);
        }
    }

    private static double[] interpolate(List<double[]> rows,double time) {
        for(int i=0;i<rows.size();i++) {
            var lower=rows.get(i);if(Math.abs(lower[0]-time)<1e-10)return lower;
            if(i+1<rows.size()&&rows.get(i+1)[0]>time) {
                var upper=rows.get(i+1);double f=(time-lower[0])/(upper[0]-lower[0]);double[] result=new double[lower.length];
                for(int j=0;j<result.length;j++)result[j]=lower[j]+f*(upper[j]-lower[j]);return result;
            }
        }
        throw new IllegalArgumentException("Reference time outside calculated trajectory");
    }
    private static void accumulate(double time,double[] actual,double[] expected,double[] maximum,double[] at,StringBuilder trace) {
        for(int i=0;i<actual.length;i++) {
            assertTrue(Double.isFinite(actual[i]));double error=Math.abs(actual[i]-expected[i]);if(error>maximum[i]){maximum[i]=error;at[i]=time;}
            trace.append(',').append(actual[i]).append(',').append(expected[i]);
        }
    }
    private static void write(String name,StringBuilder trace)throws Exception {
        var file=Path.of("target/tgov1-base-comparisons",name+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
    }
}
