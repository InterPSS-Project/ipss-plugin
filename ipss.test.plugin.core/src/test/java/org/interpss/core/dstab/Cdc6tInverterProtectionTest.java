package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import com.interpss.dstab.hvdc.model.Cdc6tInverterProtection;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class Cdc6tInverterProtectionTest {
    @Test void requestSurvivesVoltageRecoveryButPreviewCannotLatchIt() {
        var model=new Cdc6tInverterProtection(.9,.02,.92,.01,.02);
        double[] trial=new double[5],history=new double[5];
        model.accept(0,1);model.preview(.01,.8,trial,0);model.readHistory(history,0);
        assertTrue(Double.isNaN(history[1]));assertEquals(.03,trial[1],1e-15);
        model.accept(.02,.8);model.accept(.025,1);
        model.readHistory(history,0);assertEquals(.04,history[1],1e-15);
        model.accept(.041,1);assertTrue(model.isBlocked());
        model.accept(.042,1);model.accept(.053,1);assertTrue(model.isBlocked());
        model.accept(.062,1);assertFalse(model.isBlocked());assertEquals(.062,model.lastRestartTime(),0);
        model.readHistory(history,0);assertTrue(Double.isNaN(history[1]));
    }
    @Test void interruptedRecoveryInvalidInputAndThresholdEquality() {
        var model=new Cdc6tInverterProtection(.9,0,.92,.01,0);
        model.accept(0,.9);assertFalse(model.isBlocked());
        model.accept(.01,.8);assertTrue(model.isBlocked());
        model.accept(.02,1);model.accept(.029,.92);model.accept(.03,1);
        model.accept(.039,1);assertTrue(model.isBlocked());
        double[] before=new double[5],after=new double[5];model.readHistory(before,0);
        assertThrows(IllegalArgumentException.class,()->model.accept(.04,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->model.accept(.01,1));
        model.readHistory(after,0);assertArrayEquals(before,after);
        model.accept(.041,1);assertFalse(model.isBlocked());
    }
    @ParameterizedTest @ValueSource(strings={"short","long"})
    void firstBlockAndRecoveryMatchIndependentRecordedBoundary(String profile) throws Exception {
        var path=Path.of("testData/private/verification-artifacts/cdc6t-inverter-blocking-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent inverter blocking captures");
        assertEquals("0626f2f1f0a173678d0836a9b3411a3f0f059fbfc6ad9b1258f6c3f3cd66f787",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        var model=new Cdc6tInverterProtection(.9,.02,.92,profile.equals("short")?.01:.04,.02);
        double firstBlock=Double.NaN,firstRelease=Double.NaN,nativeBlock=Double.NaN,nativeRelease=Double.NaN;
        double priorDeadline=99999;boolean priorBlocked=false;
        try(var zip=new ZipFile(path.toFile())) {
            var rows=Tiocr1ResetReferenceTest.csv(zip.getInputStream(zip.getEntry(profile+"/a/native.csv")).readAllBytes());
            assertEquals(profile.equals("short")?1009:1007,rows.size());
            for(var row:rows) {
                double time=row.get("time_s"),deadline=row.get("VAR1");
                if(deadline<90000&&Double.isNaN(nativeBlock))nativeBlock=time;
                if(priorDeadline<90000&&deadline>90000&&Double.isNaN(nativeRelease))nativeRelease=time;
                model.accept(time,row.get("V9"));
                if(model.isBlocked()&&Double.isNaN(firstBlock))firstBlock=time;
                if(priorBlocked&&!model.isBlocked()&&Double.isNaN(firstRelease))firstRelease=time;
                priorBlocked=model.isBlocked();priorDeadline=deadline;
            }
        }
        assertEquals(.07,nativeBlock,1e-6);
        assertEquals(profile.equals("short")?.09:.1405,nativeRelease,1e-6);
        assertEquals(nativeBlock,firstBlock,.000501);
        assertEquals(nativeRelease,firstRelease,.000501,
            "Endpoint replay excludes internal half-step observations; full coupled events remain unqualified");
    }
}
