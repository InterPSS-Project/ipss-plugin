package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.WttqaNativeReference;
import org.interpss.core.dstab.reference.WtdtaNativeReference;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Independent native mechanical boundaries, separate from older angular-frequency benchmarks. */
public class WtdtaNativeConformanceTest extends CorePluginTestSetup {
    private static Wtdta1Model imported(Path directory)throws Exception {
        return importedConverter(directory).getReeca1Controller().getWindControlStack().getDriveTrain();
    }

    private static Regca1Model importedConverter(Path directory)throws Exception {
        var p=WttqaNativeReference.profiles().stream().filter(x->x.name().equals("curve-coarse")).findFirst().orElseThrow();
        Files.createDirectories(directory);Files.writeString(directory.resolve("network.raw"),WttqaNativeReference.network());Files.writeString(directory.resolve("dynamic.dyr"),p.input());
        var context=new PSSEMultiFileLoader().loadDStab(directory.resolve("network.raw").toString());var network=context.getDStabilityNet();network.setBypassDataCheck(true);network.setAllowGenWithoutMach(true);
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(network)).setStrictImport(true);parser.parseDynFile(directory.resolve("dynamic.dyr").toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuOutputHandler(new StateMonitor());assertTrue(algorithm.initialization());
        return (Regca1Model)((DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
    }

    @Test void nativeReaderKeepsHertzRatherThanTheOlderAngularFrequencyConvention(@TempDir Path input)throws Exception {
        assertEquals(3.6*Math.pow(2*Math.PI,2),imported(input).shaftStiffness(),1e-12);
    }

    @Test void nativeNonunitEquilibriumIncludesSynchronousDampingInTheMechanicalInput(@TempDir Path input)throws Exception {
        var model=imported(input);double speed=model.getInitialSpeed();
        assertEquals(.24+.1*speed*(speed-1),model.getInitialInputPower(),1e-7);
        assertEquals(.24/speed+.1*(speed-1),model.getShaftTorque(),1e-7);
    }

    @Test void nativeBundleRebasePublishesAppliedHistoryRatesAndReinitializesCleanly(@TempDir Path input)throws Exception {
        var converter=importedConverter(input);var model=converter.getReeca1Controller().getWindControlStack().getDriveTrain();double pm=model.getInitialInputPower();
        converter.setIntegrationScheme(Regca1Model.IntegrationScheme.TWO_STEP);
        model.stepTwoStep(.001,pm,.30);var first=model.getStateDerivativeSnapshot();assertEquals(com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.TWO_STEP,first.stage());
        double before=model.getNativeStates()[2];converter.rebaseNextTwoStep();model.stepTwoStep(0,pm,.29);assertEquals(first,model.getStateDerivativeSnapshot());
        model.stepTwoStep(.001,pm,.29);var event=model.getStateDerivativeSnapshot();
        assertEquals(com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.TWO_STEP_EVENT,event.stage());
        assertEquals(before+.001*(1.5*event.rates().get("Generator Speed Deviation")-.5*first.rates().get("Generator Speed Deviation")),model.getNativeStates()[2],1e-14);
        assertFalse(event.rates().containsKey("Electrical Power Measurement Filter"));
        converter.rebaseNextTwoStep();model.initialize(.24,.92);assertEquals(com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.NOT_EVALUATED,model.getStateDerivativeSnapshot().stage());
        model.stepTwoStep(.001,model.getInitialInputPower(),.24);assertEquals(com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage.TWO_STEP,model.getStateDerivativeSnapshot().stage());
    }

    @Test void candidateMechanicalEngineReplaysAllFourNativeCoordinates()throws Exception {
        for(var p:WttqaNativeReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());var old=rows.getFirst();
            var candidate=Wtdtb1Model.forNativePsse367(new Wtdtb1Data(5,.1,.8,1.5,.2,0),60);candidate.setAerodynamicPresent(true);
            candidate.initialize(old.get("P_PU"),1+old.get("SPEED_DEV"));double previous=old.get("time_s");double[] maximum=new double[4],at=new double[4];
            var trace=new StringBuilder("time_s,ipss_angle,native_angle,ipss_wt,native_wt,ipss_wg,native_wg,ipss_rotor_angle,native_rotor_angle\n");
            for(var row:rows) {
                double time=row.get("time_s");
                if(time>previous) {
                    candidate.step(p.step(),old.get("MECH_VAR0"),old.get("P_PU"),0);
                    candidate.step(p.step(),row.get("MECH_VAR0"),row.get("P_PU"),1);
                }
                double[] actual=candidate.getNativeStates();trace.append(time);
                for(int i=0;i<4;i++) {
                    double expected=row.get("MECH_STATE"+i),error=Math.abs(actual[i]-expected);
                    if(error>maximum[i]){maximum[i]=error;at[i]=time;}trace.append(',').append(actual[i]).append(',').append(expected);
                }
                trace.append('\n');old=row;previous=time;
            }
            var file=Path.of("target/wtdta-candidate",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
            System.out.println("WTDTA candidate "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
            assertReplayBounds(p.name(),maximum);
        }
    }

    @Test void freshNativeMatrixReplaysEveryPublishedMechanicalState()throws Exception {
        for(var p:WtdtaNativeReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());var old=rows.getFirst();
            var model=Wtdta1Model.fromNativeRecord(p.data(),p.nominalHz());model.initialize(old.get("P_PU"),1+old.get("SPEED_DEV"));
            assertEquals(old.get("MECH_VAR0"),model.getInitialInputPower(),2e-7);double previous=old.get("time_s");double[] maximum=new double[4],at=new double[4];
            var trace=new StringBuilder("time_s,ipss_angle,native_angle,ipss_wt,native_wt,ipss_wg,native_wg,ipss_rotor_angle,native_rotor_angle\n");
            for(var row:rows) {
                double time=row.get("time_s");
                if(time>previous) {
                    model.step(p.step(),old.get("MECH_VAR0"),old.get("P_PU"),0);model.step(p.step(),row.get("MECH_VAR0"),row.get("P_PU"),1);
                }
                double[] actual=model.getNativeStates();trace.append(time);
                for(int i=0;i<4;i++) {
                    double expected=row.get("MECH_STATE"+i),error=Math.abs(actual[i]-expected);
                    if(error>maximum[i]){maximum[i]=error;at[i]=time;}trace.append(',').append(actual[i]).append(',').append(expected);
                }
                trace.append('\n');old=row;previous=time;
            }
            var file=Path.of("target/wtdta-native-replay",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
            System.out.println("WTDTA native replay "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
            assertReplayBounds(p.name(),maximum);
        }
    }

    private static void assertReplayBounds(String profile,double[] maximum) {
        // Per-coordinate envelopes retain raw history/event rows and float floors.
        boolean fine=profile.endsWith("fine");
        double[] bounds=switch(profile.substring(0,profile.lastIndexOf('-'))) {
            case "flat" -> new double[]{1.2e-6,1.2e-9,4.5e-9,4.2e-7};
            case "curve-flat" -> new double[]{1.3e-6,5e-10,6.2e-10,4.4e-8};
            case "speed", "fault" -> new double[]{1.9e-6,2.6e-9,fine?5.4e-7:1.08e-6,6.8e-7};
            case "power" -> new double[]{1.9e-6,2.4e-9,fine?5.5e-7:1.12e-6,fine?5e-7:9.2e-7};
            case "curve" -> new double[]{fine?1.42e-5:4.5e-6,fine?6.1e-7:1.8e-7,fine?5.8e-7:1.2e-6,7.6e-6};
            case "dip" -> new double[]{fine?2.1e-6:4.8e-6,fine?4.4e-9:7.4e-9,fine?3.4e-6:6.8e-6,fine?1.3e-6:3.5e-6};
            case "single" -> new double[]{1e-14,1e-14,fine?1.1e-7:2.2e-7,fine?3.4e-8:1.4e-7};
            case "single-curve" -> new double[]{1e-14,1e-14,fine?2e-7:3.1e-7,fine?2.9e-6:1.23e-5};
            case "frequency" -> new double[]{fine?7.6e-7:9.8e-7,fine?3.1e-9:7e-9,fine?5.4e-7:1.1e-6,fine?3.2e-7:7.9e-7};
            case "grid50" -> new double[]{1.7e-6,1.9e-9,fine?5.4e-7:1.08e-6,fine?2.2e-7:5.6e-7};
            case "undamped" -> new double[]{fine?1.5e-5:4.5e-6,fine?5.3e-7:2.5e-7,fine?5.7e-7:1.16e-6,fine?2.2e-6:6.7e-6};
            default -> throw new IllegalArgumentException(profile);
        };
        for(int i=0;i<4;i++)assertTrue(maximum[i]<bounds[i],profile+" state "+i+": "+maximum[i]+" >= "+bounds[i]);
    }

    @Test void nativeGeneratorFeedbackRatioHasAnIndependentConditionalDerivativeWitness() {
        for(var p:WttqaNativeReference.profiles())if(p.name().startsWith("speed-")||p.name().startsWith("dip-")) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());double ratioError=0,straightError=0;int samples=0;
            double k=2*5*.8*.2*Math.pow(2*Math.PI*1.5,2)/(2*Math.PI*60);
            // Central differences are meaningful on the smooth interior, not across fault callbacks.
            // Every raw row is still retained and scored in the separate trajectory replay.
            for(int index=1;index+1<rows.size();index++) {
                var before=rows.get(index-1);var row=rows.get(index);var after=rows.get(index+1);double time=row.get("time_s");
                if(time<=.15||time>=.45||after.get("time_s")<=before.get("time_s"))continue;
                double measured=(after.get("MECH_STATE2")-before.get("MECH_STATE2"))/(2*p.step());
                double wt=1+row.get("MECH_STATE1"),wg=1+row.get("MECH_STATE2"),shaft=k*row.get("MECH_STATE0")+.2*(wt-wg);
                double ratio=(shaft*wg/wt-row.get("P_PU")/wg-.1*(wg-1))/2;
                double straight=(shaft-row.get("P_PU")/wg-.1*(wg-1))/2;
                ratioError+=Math.pow(measured-ratio,2);straightError+=Math.pow(measured-straight,2);samples++;
            }
            double ratioRms=Math.sqrt(ratioError/samples),straightRms=Math.sqrt(straightError/samples);
            System.out.println("WTDTA conditional feedback "+p.name()+" ratioRms="+ratioRms+" straightRms="+straightRms);
            assertTrue(samples>500);assertTrue(ratioRms<3e-7);assertTrue(straightRms>30*ratioRms,"The straight-feedback alternative is not equivalent native behavior");
        }
    }

    @Test void strictNativeMatrixKeepsWholeStackCoupledDifferencesExplicit(@TempDir Path input)throws Exception {
        for(var p:WtdtaNativeReference.profiles()) {
            Path directory=input.resolve(p.name());Files.createDirectories(directory);Files.writeString(directory.resolve("network.raw"),p.network());Files.writeString(directory.resolve("dynamic.dyr"),p.input());
            var context=new PSSEMultiFileLoader().loadDStab(directory.resolve("network.raw").toString());var network=context.getDStabilityNet();network.setBypassDataCheck(true);network.setAllowGenWithoutMach(true);
            var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(network)).setStrictImport(true);parser.parseDynFile(directory.resolve("dynamic.dyr").toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());assertEquals(7,parser.getLastImportReport().totalRecordCount());
            assertEquals(p.nominalHz(),network.getFrequency(),0);var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(p.step());algorithm.setSimuOutputHandler(new StateMonitor());
            if(p.hasFault())network.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus2",network,com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new org.apache.commons.math3.complex.Complex(0,1),null,.05,.05),"DriveTrainFault");
            assertTrue(algorithm.initialization());var converter=(Regca1Model)((DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();var stack=converter.getReeca1Controller().getWindControlStack();var model=stack.getDriveTrain();assertTrue(model.isNativeRecordConvention());assertEquals(p.data(),model.getData());
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());var first=rows.getFirst();assertEquals(first.get("MECH_VAR0"),model.getInitialInputPower(),2e-7);
            assertArrayEquals(new double[]{first.get("MECH_STATE0"),first.get("MECH_STATE1"),first.get("MECH_STATE2"),first.get("MECH_STATE3")},model.getNativeStates(),3e-6);
            int count=(int)Math.ceil(Math.max(.5,rows.getLast().get("time_s"))/p.step());var actual=new ArrayList<double[]>();
            for(int tick=0;tick<=count;tick++) {
                var states=converter.getStates(null);var x=model.getNativeStates();double[] row={tick*p.step(),network.getBus("Bus1").getVoltageMag(),network.getBus("Bus2").getVoltageMag(),network.getBus("Bus3").getVoltageMag(),
                    ((Number)states.get("REGCA1_P")).doubleValue(),((Number)states.get("REGCA1_Q")).doubleValue(),model.getGeneratorSpeed()-1,x[0],x[1],x[2],x[3],stack.getAerodynamics().getMechanicalPower()};
                for(double value:row)assertTrue(Double.isFinite(value));actual.add(row);if(tick<count)assertTrue(algorithm.solveDEqnStep(true));
            }
            String[] labels={"V1","V2","V3","P_PU","Q_PU","SPEED_DEV","MECH_STATE0","MECH_STATE1","MECH_STATE2","MECH_STATE3","MECH_VAR0"};double[] maximum=new double[11],at=new double[11];var trace=new StringBuilder("time_s");
            for(String label:labels)trace.append(",ipss_").append(label).append(",reference_").append(label);trace.append('\n');
            for(var expected:rows) {
                double time=expected.get("time_s");double[] local=interpolate(actual,Math.max(0,time));trace.append(time);
                for(int i=0;i<11;i++){double target=expected.get(labels[i]),error=Math.abs(local[i+1]-target);if(error>maximum[i]){maximum[i]=error;at[i]=time;}trace.append(',').append(local[i+1]).append(',').append(target);}trace.append('\n');
            }
            var file=Path.of("target/wtdta-coupled-diagnostics",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,trace);
            System.out.println("WTDTA coupled DIAGNOSTIC "+p.name()+" maxima="+Arrays.toString(maximum)+" at="+Arrays.toString(at));
        }
    }

    private static double[] interpolate(List<double[]> rows,double time) {
        for(int i=0;i<rows.size();i++) {
            var lower=rows.get(i);if(Math.abs(lower[0]-time)<1e-10)return lower;
            if(i+1<rows.size()&&rows.get(i+1)[0]>time){var upper=rows.get(i+1);double f=(time-lower[0])/(upper[0]-lower[0]);var result=new double[lower.length];for(int j=0;j<result.length;j++)result[j]=lower[j]+f*(upper[j]-lower[j]);return result;}
        }
        throw new IllegalArgumentException("Native comparison time outside actual trajectory");
    }
}
