package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.interpss.dstab.hvdc.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class Cdc6tModeSwitchTest {
    @Test void acceptedMinimumDurationAndStrictThresholds() {
        var model=new Cdc6tModeSwitch(true,490000,.02);double[] state=new double[2];
        model.preview(0,480000,false,false,state,0);assertEquals(1,state[0],0);assertFalse(model.isSwitched());
        model.accept(0,490000,false,false);assertFalse(model.isSwitched());
        model.accept(.01,480000,false,false);assertTrue(model.isSwitched());
        model.accept(.02,500000,false,false);assertTrue(model.isSwitched());
        model.accept(.031,490000,false,false);assertTrue(model.isSwitched());
        model.accept(.032,500000,false,false);assertFalse(model.isSwitched());
        assertThrows(IllegalArgumentException.class,()->model.accept(.04,Double.NaN,false,false));
        assertThrows(IllegalArgumentException.class,()->model.accept(.01,500000,false,false));
        assertFalse(model.isSwitched());
    }
    @Test void currentControlAndProtectionInhibitSwitching() {
        var current=new Cdc6tModeSwitch(false,490000,0);
        current.accept(0,100000,false,false);assertFalse(current.isSwitched());
        var power=new Cdc6tModeSwitch(true,490000,0);
        power.accept(0,100000,true,false);assertFalse(power.isSwitched());
        power.accept(.01,100000,false,true);assertFalse(power.isSwitched());
        power.accept(.02,100000,false,false);assertTrue(power.isSwitched());
    }
    @ParameterizedTest @ValueSource(strings={"long","short"})
    void independentModeAndCurrentAcrossEveryRecordedSample(String profile) throws Exception {
        var path=Path.of("testData/private/verification-artifacts/cdc6t-mode-switch-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent mode-switch captures");
        assertEquals("28b4d06ee2aa18d3d052a7240032a6ec3f804dbbb4849a39d32e2d84c87b7ad8",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        var model=new Cdc6tModeSwitch(true,490000,.02);
        var normal=new Cdc6tCurrentOrder(Cdc6tCurrentOrder.Mode.POWER,-500,500,0);
        double restore=profile.equals("short")?.07:.1,firstSwitch=Double.NaN,firstRestore=Double.NaN;
        boolean previous=false;int switched=0,edgeDifferences=0;
        try(var zip=new ZipFile(path.toFile())) {
            var rows=Tiocr1ResetReferenceTest.csv(zip.getInputStream(zip.getEntry(profile+"/a/native.csv")).readAllBytes());
            assertEquals(profile.equals("short")?1006:1005,rows.size());
            for(var row:rows) {
                double time=row.get("time_s");
                model.accept(time,row.get("VAR9"),false,false);
                if(model.isSwitched()&&Double.isNaN(firstSwitch))firstSwitch=time;
                if(previous&&!model.isSwitched()&&Double.isNaN(firstRestore))firstRestore=time;
                previous=model.isSwitched();
                boolean reference=row.get("VAR14")<90000;
                double expected=model.isSwitched()?1000:normal.amperes(row.get("STATE0"),row.get("STATE2"),0);
                if(model.isSwitched()!=reference||Math.abs(expected-row.get("VAR11"))>1e-4) {
                    assertTrue(Math.min(Math.abs(time-.05),Math.abs(time-restore))<.0011,"Unexpected mode/current discrepancy at "+time);
                    edgeDifferences++;
                }
                if(reference)switched++;
            }
        }
        assertTrue(switched>=40);assertTrue(edgeDifferences<=6);
        assertEquals(.05,firstSwitch,.000501);assertEquals(restore,firstRestore,.000501);
    }
}
