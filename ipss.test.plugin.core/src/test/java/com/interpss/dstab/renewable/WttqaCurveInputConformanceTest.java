package com.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.WttqaNativeReference;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.renewable.Regca1Model;
import com.interpss.dstab.renewable.Wttqa1Data;
import com.interpss.dstab.renewable.Wttqa1Model;

/** Distinguish the native raw-power curve junction from the published filtered-power cascade. */
public class WttqaCurveInputConformanceTest extends CorePluginTestSetup {
    private static Wttqa1Data data(int selector,double tp,double twref) {
        return new Wttqa1Data(selector,2,1,tp,twref,2,0,.1,.8,.2,.9,.4,1,.8,1.1,100);
    }
    @Test void predictorAndCorrectorUseRawPowerWithoutBypassingThePowerState() {
        var model=Wttqa1Model.fromNativeRecord(data(0,.02,.1));model.initialize(.24,.92);
        model.step(.001,.4,.92,.24,false,0);
        assertEquals(.248,model.getFilteredPower(),1e-14);
        assertEquals(.9208,model.getSpeedReference(),1e-14);
        assertEquals(1,model.getCurveOutput(),0);
        model.step(.001,.4,.92,.24,false,1);
        assertEquals(.2478,model.getFilteredPower(),1e-14);
        assertEquals(.920796,model.getSpeedReference(),1e-14);
        var states=model.getNamedStates();double output=model.getCurveOutput();
        assertEquals(states,model.getNamedStates());assertEquals(output,model.getCurveOutput(),0);
    }
    @Test void typedCascadeAndBothOtherIntegrationPathsRetainTheirDeclaredJunctions() {
        var typed=new Wttqa1Model(data(0,.02,.1));typed.initialize(.24,.92);
        typed.step(.001,.4,.92,.24,false,0);
        assertEquals(.92,typed.getSpeedReference(),1e-14);
        assertFalse(typed.isNativeRecordConvention());
        var complete=Wttqa1Model.fromNativeRecord(data(0,.02,.1));complete.initialize(.24,.92);
        complete.step(.001,.4,.92,.24,false);
        assertEquals(.920796,complete.getSpeedReference(),1e-14);
        var history=Wttqa1Model.fromNativeRecord(data(0,.02,.1));history.initialize(.24,.92);
        history.stepTwoStep(.001,.4,.92,.24,false);
        assertEquals(.9212,history.getSpeedReference(),1e-14);
        assertEquals(1,history.getCurveOutput(),0);
    }
    @Test void algebraicRefreshAndPowerControlStillRespectTheirOwnTimeConstants() {
        var raw=Wttqa1Model.fromNativeRecord(data(0,.02,0));raw.initialize(.24,.92);
        raw.step(0,.4,.92,.24,false,0);
        assertEquals(.24,raw.getFilteredPower(),0);assertEquals(1,raw.getSpeedReference(),0);
        for(int selector:new int[]{1,2}) {
            var model=Wttqa1Model.fromNativeRecord(data(selector,.02,.1));model.initialize(.24,.92);
            model.step(.001,.4,.92,.24,false,0);
            assertEquals(.248,model.getFilteredPower(),1e-14);
            assertEquals(model.getTorqueIntegral()+2*(.24-.248)/.92,model.getTorque(),1e-14);
        }
        var a=Wttqa1Model.fromNativeRecord(data(0,.02,.1));var b=Wttqa1Model.fromNativeRecord(data(0,.04,.1));
        a.initialize(.24,.92);b.initialize(.24,.92);
        for(int flag=0;flag<2;flag++){a.step(.001,.4,.92,.24,false,flag);b.step(.001,.4,.92,.24,false,flag);}
        assertEquals(a.getSpeedReference(),b.getSpeedReference(),0);
        assertNotEquals(a.getFilteredPower(),b.getFilteredPower());
    }
    @Test void importerSelectsNativeConventionWithoutChangingRecordConstants(@TempDir Path root)throws Exception {
        for(var profile:WttqaNativeReference.profiles()) {
            Path directory=root.resolve(profile.name());Files.createDirectory(directory);
            Files.writeString(directory.resolve("network.raw"),WttqaNativeReference.network());
            Files.writeString(directory.resolve("dynamic.dyr"),profile.input());
            var loader=new PSSEMultiFileLoader();var context=loader.loadDStab(directory.resolve("network.raw").toString(),directory.resolve("dynamic.dyr").toString());
            assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
            var converter=(Regca1Model)((com.interpss.dstab.DStabGen)context.getDStabilityNet().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
            var model=converter.getReeca1Controller().getWindControlStack().getTorqueController();
            assertTrue(model.isNativeRecordConvention());assertEquals(profile.data(),model.getData());
        }
    }
    @Test void allNativeReferenceRowsHaveBoundedStateAndCurveErrors()throws Exception {
        Path output=Path.of("target/wttqa-raw-curve-replay");Files.createDirectories(output);
        for(var profile:WttqaNativeReference.profiles()) {
            var rows=org.interpss.core.dstab.reference.RepcaNativeVerification.rows(profile.body());
            var first=rows.getFirst();var previous=first;var model=Wttqa1Model.fromNativeRecord(profile.data());
            model.initialize(first.get("P_PU"),1+first.get("SPEED_DEV"));
            double[] maximum=new double[4];
            var trace=new StringBuilder("time_s,ipss_power,reference_power,ipss_wref,reference_wref,ipss_integral,reference_integral,ipss_curve,reference_curve\n");
            for(var row:rows) {
                if(row.get("time_s")>previous.get("time_s")) {
                    model.step(profile.step(),previous.get("P_PU"),1+previous.get("SPEED_DEV"),previous.get("PITCH_VAR0"),dip(previous),0);
                    model.step(profile.step(),row.get("P_PU"),1+row.get("SPEED_DEV"),row.get("PITCH_VAR0"),dip(row),1);
                } else model.step(0,row.get("P_PU"),1+row.get("SPEED_DEV"),row.get("PITCH_VAR0"),dip(row),0);
                double[] actual={model.getFilteredPower(),model.getSpeedReference(),model.getTorqueIntegral(),model.getCurveOutput()};
                double[] expected={row.get("TRQ_STATE0"),row.get("TRQ_STATE1"),row.get("TRQ_STATE2"),row.get("TRQ_VAR0")};
                trace.append(row.get("time_s"));
                for(int i=0;i<4;i++){maximum[i]=Math.max(maximum[i],Math.abs(actual[i]-expected[i]));trace.append(',').append(actual[i]).append(',').append(expected[i]);}
                trace.append('\n');previous=row;
            }
            double[] bounds=switch(profile.name()) {
                case "flat-coarse","flat-fine" -> new double[]{2e-8,1e-12,2e-8,1e-12};
                case "speed-coarse" -> new double[]{9.5e-5,1e-12,8.5e-7,1e-12};
                case "speed-fine" -> new double[]{5e-5,1e-12,2e-6,1e-12};
                case "power-coarse" -> new double[]{9.5e-5,1e-12,2.6e-7,1e-12};
                case "power-fine" -> new double[]{5e-5,1e-12,1.25e-6,1e-12};
                case "dip-coarse" -> new double[]{5.9e-4,1e-12,2.1e-7,1e-12};
                case "dip-fine" -> new double[]{3.05e-4,1e-12,5.2e-7,1e-12};
                case "curve-coarse" -> new double[]{9e-5,9.5e-6,1.5e-6,6e-8};
                case "curve-fine" -> new double[]{5e-5,5e-6,1e-5,6e-8};
                default -> throw new IllegalArgumentException("Unknown native torque profile");
            };
            for(int i=0;i<4;i++)assertTrue(maximum[i]<=bounds[i],profile.name()+" channel "+i+" error="+maximum[i]);
            Files.writeString(output.resolve(profile.name()+".csv"),trace);
            System.out.println("WTTQA_RAW_CURVE "+profile.name()+" "+Arrays.toString(maximum));
        }
    }
    private static boolean dip(Map<String,Double> row){return row.get("V1")<row.get("TRQ_VAR1")||row.get("V1")>row.get("TRQ_VAR2");}
}
