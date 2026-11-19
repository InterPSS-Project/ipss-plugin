package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.WtaraNativeReference;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Direct aerodynamic torque/pitch/speed boundaries, not inferred downstream cached power. */
public class WtaraNativeConformanceTest extends CorePluginTestSetup {
    @Test void everyRawRowHasAnExplicitAlgebraicEvaluationOrCachedDriverBoundary()throws Exception {
        for(var p:WtaraNativeReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());var first=rows.getFirst();
            double w0=1+first.get("TURBINE_SPEED_DEV"),pm0=first.get("P_PU")+.1*w0*(w0-1);
            var model=new Wtara1Model(p.data());model.initialize(pm0);assertEquals(first.get("AERO_VAR0"),model.getInitialPower(),2e-7);
            double previous=Double.NaN,previousPitch=Double.NaN,previousTorque=Double.NaN;int cached=0,negative=0;double[] maximum=new double[2],at=new double[2];
            var trace=new StringBuilder("time_s,evaluated,pitch_deg,evaluation_pitch_deg,ipss_power,native_power,ipss_torque,native_torque\n");
            for(var row:rows) {
                double time=row.get("time_s"),pitch=row.get("PITCH_DEG"),speed=1+row.get("TURBINE_SPEED_DEV");
                boolean driverBoundary=time==previous&&pitch!=previousPitch;
                if(driverBoundary){cached++;assertEquals(previousTorque,row.get("AERO_TORQUE"),0,"The API changes the input display, not cached torque");}else model.step(pitch);
                double power=model.getMechanicalPower(),torque=power/speed;
                double[] actual={power,torque},expected={row.get("AERO_TORQUE")*speed,row.get("AERO_TORQUE")};
                for(int i=0;i<2;i++){double error=Math.abs(actual[i]-expected[i]);if(error>maximum[i]){maximum[i]=error;at[i]=time;}}
                if(expected[0]<0)negative++;
                assertEquals(first.get("AERO_VAR0"),row.get("AERO_VAR0"),0,"Initial-power VAR is constant, not a dynamic state");
                trace.append(time).append(',').append(driverBoundary?0:1).append(',').append(pitch).append(',').append(model.getPitch())
                        .append(',').append(power).append(',').append(expected[0]).append(',').append(torque).append(',').append(expected[1]).append('\n');
                previous=time;previousPitch=pitch;previousTorque=row.get("AERO_TORQUE");
            }
            var file=Path.of("target/wtara-native-replay",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
            System.out.println("WTARA native replay "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at)+" cached="+cached+" negative="+negative);
            double bound=switch(p.name().substring(0,p.name().lastIndexOf('-'))) {
                case "flat","zero-gain" -> 3e-10;
                case "fault" -> 2.6e-8;
                case "pitch" -> 2.3e-8;
                case "nonzero" -> 3.1e-8;
                case "gain" -> 1.9e-8;
                case "negative" -> 6.6e-8;
                case "nonunit" -> 4.6e-8;
                default -> throw new IllegalArgumentException(p.name());
            };
            for(int i=0;i<2;i++)assertTrue(maximum[i]<bound,p.name()+" channel "+i+": "+maximum[i]);
            assertEquals(p.hasPitchDriver()?3:0,cached);
            if(p.name().startsWith("negative-"))assertTrue(negative>300,"Negative mechanical power is retained, not clipped");
        }
    }

    @Test void nonzeroInitialPitchDiscriminatesSquaredDeviationAndRadians() {
        for(var p:WtaraNativeReference.profiles())if(p.theta0()!=0) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());double squared=0,radians=0;
            for(var row:rows) {
                double theta=row.get("PITCH_DEG"),pm0=row.get("AERO_VAR0"),delta=theta-p.theta0();
                double correct=pm0-p.ka()*theta*delta;
                squared=Math.max(squared,Math.abs(correct-(pm0-p.ka()*delta*delta)));
                radians=Math.max(radians,Math.abs(correct-(pm0-p.ka()*Math.toRadians(theta)*Math.toRadians(delta))));
            }
            assertTrue(squared>.06);assertTrue(radians>.09);
        }
    }

    @Test void strictImportRoutesInitialMechanicalPowerAndRunsTheDeclaredSmallNetworkDrivers(@TempDir Path input)throws Exception {
        for(var p:WtaraNativeReference.profiles()) {
            Path directory=input.resolve(p.name());Files.createDirectories(directory);Files.writeString(directory.resolve("network.raw"),p.network());Files.writeString(directory.resolve("dynamic.dyr"),p.input());
            var context=new PSSEMultiFileLoader().loadDStab(directory.resolve("network.raw").toString());var network=context.getDStabilityNet();network.setBypassDataCheck(true);network.setAllowGenWithoutMach(true);
            var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(network)).setStrictImport(true);parser.parseDynFile(directory.resolve("dynamic.dyr").toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());assertEquals(7,parser.getLastImportReport().totalRecordCount());
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(p.step());algorithm.setSimuOutputHandler(new StateMonitor());
            if(p.hasFault())network.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus2",network,com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new org.apache.commons.math3.complex.Complex(0,1),null,.05,.05),"AerodynamicFault");
            assertTrue(algorithm.initialization());var converter=(Regca1Model)((DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();var stack=converter.getReeca1Controller().getWindControlStack();var model=stack.getAerodynamics();assertEquals(p.data(),model.getData());
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(rows.getFirst().get("AERO_VAR0"),model.getInitialPower(),2e-7);
            int count=(int)Math.ceil(Math.max(.5,rows.getLast().get("time_s"))/p.step());var actual=new ArrayList<double[]>();int event=0;double[] times={.15,.25,.35},angles=p.pitchDriver();
            for(int tick=0;tick<=count;tick++) {
                double time=tick*p.step();
                if(p.hasPitchDriver()&&event<3&&Math.abs(time-times[event])<1e-10) {
                    // This zero-gain upstream fixture supplies the input; never overwrite WTARA output.
                    stack.getPitchController().initialize(angles[event++],stack.getDriveTrain().getInitialSpeed());
                }
                var values=converter.getStates(null);double power=model.getMechanicalPower();double[] row={time,network.getBus("Bus1").getVoltageMag(),network.getBus("Bus2").getVoltageMag(),network.getBus("Bus3").getVoltageMag(),
                    ((Number)values.get("REGCA1_P")).doubleValue(),((Number)values.get("REGCA1_Q")).doubleValue(),stack.getGeneratorSpeed()-1,model.getPitch(),power,power/stack.getTurbineSpeed()};
                for(double value:row)assertTrue(Double.isFinite(value));actual.add(row);if(tick<count)assertTrue(algorithm.solveDEqnStep(true));
            }
            String[] labels={"V1","V2","V3","P_PU","Q_PU","SPEED_DEV","PITCH_DEG","AERO_POWER","AERO_TORQUE"};double[] maximum=new double[9],at=new double[9];var trace=new StringBuilder("time_s");
            for(String label:labels)trace.append(",ipss_").append(label).append(",reference_").append(label);trace.append('\n');
            for(var expected:rows) {
                double time=expected.get("time_s");double[] local=interpolate(actual,Math.max(0,time));trace.append(time);
                for(int i=0;i<9;i++){double target=i==7?expected.get("AERO_TORQUE")*(1+expected.get("TURBINE_SPEED_DEV")):expected.get(labels[i]),error=Math.abs(local[i+1]-target);if(error>maximum[i]){maximum[i]=error;at[i]=time;}trace.append(',').append(local[i+1]).append(',').append(target);}trace.append('\n');
            }
            var file=Path.of("target/wtara-coupled-diagnostics",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
            System.out.println("WTARA coupled DIAGNOSTIC "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
        }
    }

    private static double[] interpolate(List<double[]> rows,double time) {
        for(int i=0;i<rows.size();i++) {
            var lower=rows.get(i);if(Math.abs(lower[0]-time)<1e-10)return lower;
            if(i+1<rows.size()&&rows.get(i+1)[0]>time){var upper=rows.get(i+1);double f=(time-lower[0])/(upper[0]-lower[0]);var result=new double[lower.length];for(int j=0;j<result.length;j++)result[j]=lower[j]+f*(upper[j]-lower[j]);return result;}
        }
        throw new IllegalArgumentException("Native time outside actual trajectory");
    }
}
