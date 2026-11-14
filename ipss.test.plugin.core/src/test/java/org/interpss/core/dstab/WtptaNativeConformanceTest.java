package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.core.dstab.reference.WtptaNativeReference;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.cache.StateMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Native pitch inputs and three-state reuse witnesses, not whole-stack acceptance. */
public class WtptaNativeConformanceTest extends CorePluginTestSetup {
    @Test void revisionBOutputClippingIsNotAnEquivalentRevisionAImplementation()throws Exception {
        int witnesses=0;
        for(var p:WtptaNativeReference.profiles()) {
            double[] maximum=replay(p,true);
            if(p.name().startsWith("fault-")){assertTrue(maximum[0]>1e-4);witnesses++;}
            if(p.name().startsWith("order-upper-")){assertTrue(maximum[2]>.005);witnesses++;}
        }
        assertEquals(4,witnesses);
    }

    @Test void nativePitchPathReplaysAllThreeStates()throws Exception {
        var errors=new ArrayList<String>();
        for(var p:WtptaNativeReference.profiles()) {
            var maximum=replay(p,false);
            if(p.hasOrderDriver()) {
                assertTrue(maximum[0]>2e-4,"Explicit modified Euler retains this input-history counterexample");
                continue;
            }
            double[] bounds=replayBounds(p);
            for(int i=0;i<3;i++)if(maximum[i]>bounds[i])errors.add(p.name()+" state "+i+": "+maximum[i]+" > "+bounds[i]);
        }
        assertTrue(errors.isEmpty(),String.join("; ",errors));
    }

    @Test void explicitTwoStepInputHistoryReplaysTheFourOrderDriverProfiles(@TempDir Path input)throws Exception {
        for(var p:WtptaNativeReference.profiles())if(p.hasOrderDriver()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());var previous=rows.getFirst();
            var context=imported(p,input.resolve(p.name()));var converter=converter(context);
            converter.setIntegrationScheme(Regca1Model.IntegrationScheme.TWO_STEP);converter.setSinglePrecisionStates(true);
            var model=converter.getReeca1Controller().getWindControlStack().getPitchController();model.initialize(p.initialPitch(),previous.get("WREF"));
            double[] maximum=new double[3],at=new double[3];int events=0;
            var trace=new StringBuilder("time_s,ipss_pitch,native_pitch,ipss_speed_integral,native_speed_integral,ipss_compensation_integral,native_compensation_integral\n");
            for(var row:rows) {
                if(row.get("time_s")>previous.get("time_s"))model.stepTwoStepWithReference(p.step(),previous.get("PORD"),previous.get("PITCH_VAR0"),1+previous.get("TURBINE_SPEED_DEV"),previous.get("WREF"));
                else if(row!=rows.getFirst()&&row.get("time_s")>=0) {
                    model.rebaseNextTwoStep();model.stepTwoStepWithReference(0,row.get("PORD"),row.get("PITCH_VAR0"),1+row.get("TURBINE_SPEED_DEV"),row.get("WREF"));events++;
                }
                double[] actual=model.getNativeStates();trace.append(row.get("time_s"));
                for(int i=0;i<3;i++){double target=row.get("PITCH_STATE"+i),error=Math.abs(actual[i]-target);if(error>maximum[i]){maximum[i]=error;at[i]=row.get("time_s");}trace.append(',').append(actual[i]).append(',').append(target);}trace.append('\n');previous=row;
            }
            assertEquals(2,events);var file=Path.of("target/wtpta-history-replay",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
            System.out.println("WTPTA history replay "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
            boolean fine=p.name().endsWith("fine");double[] bounds=p.name().startsWith("order-upper")
                    ?new double[]{fine?5.5e-6:7.1e-6,1e-10,3e-10}:fine?new double[]{3.6e-5,1e-10,2.2e-10}:new double[]{7.4e-5,7.2e-7,1.7e-9};
            for(int i=0;i<3;i++)assertTrue(maximum[i]<bounds[i],p.name()+" state "+i+": "+maximum[i]);
        }
    }

    private static com.interpss.simu.SimuContext imported(WtptaNativeReference.Profile p,Path directory)throws Exception {
        Files.createDirectories(directory);Files.writeString(directory.resolve("network.raw"),p.network());Files.writeString(directory.resolve("dynamic.dyr"),p.input());
        var context=new PSSEMultiFileLoader().loadDStab(directory.resolve("network.raw").toString());var network=context.getDStabilityNet();network.setBypassDataCheck(true);network.setAllowGenWithoutMach(true);
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(network)).setStrictImport(true);parser.parseDynFile(directory.resolve("dynamic.dyr").toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());assertEquals(7,parser.getLastImportReport().totalRecordCount());
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuStepSec(p.step());algorithm.setSimuOutputHandler(new StateMonitor());
        algorithm.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);
        if(p.hasFault())network.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus2",network,com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new org.apache.commons.math3.complex.Complex(0,1),null,.05,.05),"NativePitchFault");
        assertTrue(algorithm.initialization());assertTrue(converter(context).getReeca1Controller().getWindControlStack().getPitchController().isNativeRecordConvention());return context;
    }

    private static Regca1Model converter(com.interpss.simu.SimuContext context) {
        return (Regca1Model)((DStabGen)context.getDStabilityNet().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
    }

    @Test void nativeOrderStepHasAnIndependentThreeQuarterFirstIncrementWitness() {
        int witnesses=0;
        for(var p:WtptaNativeReference.profiles())if(p.name().startsWith("order-upper-")) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());
            for(int i=1;i+1<rows.size();i++) {
                var pre=rows.get(i-1);var post=rows.get(i);var next=rows.get(i+1);
                if(pre.get("time_s").equals(post.get("time_s"))&&post.get("PORD")>pre.get("PORD")+.09) {
                    for(int state=0;state<3;state++)assertEquals(pre.get("PITCH_STATE"+state),post.get("PITCH_STATE"+state),0);
                    double expected=.75*p.step()*p.data().kic()*(post.get("PORD")-post.get("PITCH_VAR0"));
                    assertEquals(expected,next.get("PITCH_STATE2")-post.get("PITCH_STATE2"),1e-9);
                    witnesses++;
                }
            }
        }
        assertEquals(2,witnesses);
    }

    @Test void firstCrossFreezeRemovesThePrecedingHalfStepFromNativeIntegralStorage() {
        int witnesses=0;
        for(var p:WtptaNativeReference.profiles())if(p.name().startsWith("order-upper-")) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());
            for(int i=1;i+1<rows.size();i++) {
                var before=rows.get(i-1);var now=rows.get(i);var next=rows.get(i+1);
                if(now.get("PITCH_STATE0")>=p.data().thetaMax()-2e-7&&next.get("PITCH_STATE0")>=p.data().thetaMax()-2e-7
                        &&now.get("PITCH_STATE2")-next.get("PITCH_STATE2")>1e-7) {
                    assertEquals(.5*p.step()*p.data().kic()*(before.get("PORD")-before.get("PITCH_VAR0")),now.get("PITCH_STATE2")-next.get("PITCH_STATE2"),2e-9);
                    witnesses++;break;
                }
            }
        }
        assertEquals(2,witnesses);
    }

    @Test void nativeProfilesExerciseRateLimitsLowerBoundaryAndZeroTimeRateBypass() {
        for(var p:WtptaNativeReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());double minimum=Double.POSITIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY;
            for(int i=1;i<rows.size();i++) {
                var a=rows.get(i-1);var b=rows.get(i);if(b.get("time_s")<=a.get("time_s"))continue;
                double slope=(b.get("PITCH_STATE0")-a.get("PITCH_STATE0"))/(b.get("time_s")-a.get("time_s"));minimum=Math.min(minimum,slope);maximum=Math.max(maximum,slope);
            }
            if(p.name().startsWith("rate-")){assertTrue(minimum<-.009);assertTrue(maximum>.009);}
            if(p.name().startsWith("lower-"))assertEquals(0,rows.getFirst().get("PITCH_STATE0"),0);
            if(p.name().startsWith("zero-rate-")) {
                assertTrue(maximum>40*p.data().dThetaMax());
                var baseline=WtptaNativeReference.profiles().stream().filter(q->q.name().equals(p.name().replace("zero-rate-","zero-"))).findFirst().orElseThrow();
                assertEquals(baseline.body(),p.body(),"Native zero-time outputs ignore a 1000-fold change of the declared rate bound");
            }
        }
    }

    @Test void conditionalNativePitchDerivativeDiscriminatesRawFromClippedPiOutputs() {
        int witnesses=0;
        for(var p:WtptaNativeReference.profiles())if(p.name().startsWith("fault-")||p.name().startsWith("moving-")) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());var d=p.data();double rawSquared=0,clippedSquared=0;int count=0;
            for(int i=1;i+1<rows.size();i++) {
                var a=rows.get(i-1);var row=rows.get(i);var b=rows.get(i+1);double time=row.get("time_s");
                if(!(time>.15&&time<.45&&a.get("time_s")<time&&b.get("time_s")>time))continue;
                double ep=row.get("PORD")-row.get("PITCH_VAR0"),ew=1+row.get("TURBINE_SPEED_DEV")-row.get("WREF")+d.kcc()*ep;
                double speed=row.get("PITCH_STATE1")+d.kpw()*ew,comp=row.get("PITCH_STATE2")+d.kpc()*ep;
                double measured=(b.get("PITCH_STATE0")-a.get("PITCH_STATE0"))/(b.get("time_s")-a.get("time_s"));
                double raw=(speed+comp-row.get("PITCH_STATE0"))/d.tp(),clipped=(clip(speed,d.thetaMin(),d.thetaMax())+clip(comp,d.thetaMin(),d.thetaMax())-row.get("PITCH_STATE0"))/d.tp();
                rawSquared+=Math.pow(raw-measured,2);clippedSquared+=Math.pow(clipped-measured,2);count++;
            }
            assertTrue(count>500);assertTrue(Math.sqrt(rawSquared/count)<.35*Math.sqrt(clippedSquared/count));witnesses++;
        }
        assertEquals(4,witnesses);
    }

    @Test void everyProfileImportsExactlyAndUnforcedProfilesRunWithCoupledDiagnostics(@TempDir Path input)throws Exception {
        int simulated=0,importedOnly=0;
        for(var p:WtptaNativeReference.profiles()) {
            var context=imported(p,input.resolve(p.name()));var converter=converter(context);var stack=converter.getReeca1Controller().getWindControlStack();var model=stack.getPitchController();assertEquals(p.data(),model.getData());
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertArrayEquals(new double[]{p.initialPitch(),p.initialPitch(),0},model.getNativeStates(),1e-12);
            if(p.hasOrderDriver()){importedOnly++;continue;}
            var algorithm=context.getDynSimuAlgorithm();var network=context.getDStabilityNet();int ticks=(int)Math.ceil(Math.max(.5,rows.getLast().get("time_s"))/p.step());var actual=new ArrayList<double[]>();
            for(int tick=0;tick<=ticks;tick++) {
                var values=converter.getStates(null);double[] x=model.getNativeStates();double[] point={tick*p.step(),network.getBus("Bus1").getVoltageMag(),network.getBus("Bus2").getVoltageMag(),network.getBus("Bus3").getVoltageMag(),
                        ((Number)values.get("REGCA1_P")).doubleValue(),((Number)values.get("REGCA1_Q")).doubleValue(),stack.getGeneratorSpeed()-1,x[0],x[1],x[2],converter.getReeca1Controller().getActivePowerOrder(),stack.getTorqueController().getSpeedReference()};
                for(double value:point)assertTrue(Double.isFinite(value));actual.add(point);if(tick<ticks)assertTrue(algorithm.solveDEqnStep(true));
            }
            String[] labels={"V1","V2","V3","P_PU","Q_PU","SPEED_DEV","PITCH_STATE0","PITCH_STATE1","PITCH_STATE2","PORD","WREF"};double[] maximum=new double[11],at=new double[11];var trace=new StringBuilder("time_s");
            for(String label:labels)trace.append(",ipss_").append(label).append(",reference_").append(label);trace.append('\n');
            for(var row:rows) {
                double time=row.get("time_s");double[] local=interpolate(actual,Math.max(0,time));trace.append(time);
                for(int i=0;i<11;i++){double target=row.get(labels[i]),error=Math.abs(local[i+1]-target);if(error>maximum[i]){maximum[i]=error;at[i]=time;}trace.append(',').append(local[i+1]).append(',').append(target);}trace.append('\n');
            }
            var file=Path.of("target/wtpta-coupled-diagnostics",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);simulated++;
            System.out.println("WTPTA coupled DIAGNOSTIC "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
        }
        assertEquals(16,simulated);assertEquals(4,importedOnly);
    }

    private static double clip(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    private static double[] replayBounds(WtptaNativeReference.Profile p) {
        boolean fine=p.name().endsWith("fine");return switch(p.name().substring(0,p.name().lastIndexOf('-'))) {
            case "flat" -> new double[]{9.4e-7,7.4e-8,fine?5.5e-11:1.1e-10};
            case "fault","upper" -> fine?new double[]{7.2e-6,2.5e-5,8e-9}:new double[]{3.2e-6,6.1e-6,3.2e-8};
            case "lower" -> fine?new double[]{2.9e-6,1.5e-7,8e-9}:new double[]{4.2e-7,2.7e-7,1.15e-6};
            case "rate" -> fine?new double[]{5.8e-5,2.4e-5,8e-9}:new double[]{3.2e-6,7.5e-6,8e-7};
            case "moving" -> fine?new double[]{4e-6,2.9e-6,1.3e-6}:new double[]{7.8e-6,3.3e-6,3e-7};
            case "zero","zero-rate" -> fine?new double[]{1.9e-5,1.9e-5,8e-9}:new double[]{5.9e-6,3.3e-6,3.2e-8};
            default -> throw new IllegalArgumentException(p.name());
        };
    }
    private static double[] interpolate(List<double[]> rows,double time) {
        for(int i=0;i<rows.size();i++) {
            var lower=rows.get(i);if(Math.abs(lower[0]-time)<1e-10)return lower;
            if(i+1<rows.size()&&rows.get(i+1)[0]>time){var upper=rows.get(i+1);double f=(time-lower[0])/(upper[0]-lower[0]);var result=new double[lower.length];for(int j=0;j<result.length;j++)result[j]=lower[j]+f*(upper[j]-lower[j]);return result;}
        }
        throw new IllegalArgumentException("Native time outside actual trajectory");
    }

    private static double[] replay(WtptaNativeReference.Profile p,boolean candidate)throws Exception {
        var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());var first=rows.getFirst();var d=p.data();
        var shared=new Wtptb1Model(new Wtptb1Data(d.kiw(),d.kpw(),d.kic(),d.kpc(),d.kcc(),d.tp(),d.thetaMax(),d.thetaMin(),d.dThetaMax(),d.dThetaMin(),d.thetaMax(),d.thetaMin(),d.thetaMax(),d.thetaMin()));
        shared.initialize(p.initialPitch());var model=Wtpta1Model.fromNativeRecord(d);model.initialize(p.initialPitch(),first.get("WREF"));
        var previous=first;double[] maximum=new double[3],at=new double[3];var trace=new StringBuilder("time_s,ipss_pitch,native_pitch,ipss_speed_integral,native_speed_integral,ipss_compensation_integral,native_compensation_integral\n");
        for(var row:rows) {
            if(row.get("time_s")>previous.get("time_s")) {
                if(candidate) {
                    shared.step(p.step(),previous.get("PORD"),previous.get("PITCH_VAR0"),1+previous.get("TURBINE_SPEED_DEV"),previous.get("WREF"),0);
                    shared.step(p.step(),row.get("PORD"),row.get("PITCH_VAR0"),1+row.get("TURBINE_SPEED_DEV"),row.get("WREF"),1);
                } else {
                    model.stepWithReference(p.step(),previous.get("PORD"),previous.get("PITCH_VAR0"),1+previous.get("TURBINE_SPEED_DEV"),previous.get("WREF"),0);
                    model.stepWithReference(p.step(),row.get("PORD"),row.get("PITCH_VAR0"),1+row.get("TURBINE_SPEED_DEV"),row.get("WREF"),1);
                }
            } else if(row!=first&&d.tp()==0) {
                if(candidate)shared.step(0,row.get("PORD"),row.get("PITCH_VAR0"),1+row.get("TURBINE_SPEED_DEV"),row.get("WREF"),0);
                else model.stepWithReference(0,row.get("PORD"),row.get("PITCH_VAR0"),1+row.get("TURBINE_SPEED_DEV"),row.get("WREF"),0);
            }
            double[] actual=candidate?shared.getNativeStates():new double[]{model.getPitch(),model.getSpeedIntegral(),model.getCompensationIntegral()};trace.append(row.get("time_s"));
            for(int i=0;i<3;i++){double target=row.get("PITCH_STATE"+i),error=Math.abs(actual[i]-target);if(error>maximum[i]){maximum[i]=error;at[i]=row.get("time_s");}trace.append(',').append(actual[i]).append(',').append(target);}trace.append('\n');previous=row;
        }
        var file=Path.of("target/"+(candidate?"wtpta-reuse-replay":"wtpta-native-replay"),p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
        System.out.println("WTPTA "+(candidate?"candidate":"native")+" replay "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
        return maximum;
    }
}
