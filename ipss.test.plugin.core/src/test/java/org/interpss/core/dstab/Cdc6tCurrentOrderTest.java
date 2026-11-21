package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import com.interpss.dstab.hvdc.model.Cdc6tCurrentOrder;
import com.interpss.dstab.hvdc.model.Cdc6tCurrentOrder.Mode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent ordinary-operation command discriminators, not complete converter qualification. */
public class Cdc6tCurrentOrderTest {
    @ParameterizedTest
    @ValueSource(strings={"inverter-power","rectifier-power","current"})
    void completeFaultCapturesSelectTheCorrectMeasurement(String mode) throws Exception {
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-current-order-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent command captures");
        assertEquals("6d2fe324463db46be0fa0f6fd3f69aa928cb687a527b80ff8236ea1ea307a400",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        boolean current=mode.equals("current"),inverter=mode.equals("inverter-power");
        var command=new Cdc6tCurrentOrder(current?Mode.CURRENT:Mode.POWER,current?1000:inverter?-500:505,500,0);
        try(var zip=new ZipFile(archive.toFile())) {
            byte[] raw=zip.getInputStream(zip.getEntry(mode+"/a/native.csv")).readAllBytes();
            assertArrayEquals(raw,zip.getInputStream(zip.getEntry(mode+"/b/native.csv")).readAllBytes());
            var rows=Tiocr1ResetReferenceTest.csv(raw);assertEquals(1005,rows.size());
            double maximum=0,wrongMeasurementError=0;
            for(var row:rows) {
                double vi=row.get("STATE0"),vr=row.get("STATE2"),actual=row.get("VAR11");
                maximum=Math.max(maximum,Math.abs(actual-command.amperes(vi,vr,row.get("VAR0"))));
                if(!current)wrongMeasurementError=Math.max(wrongMeasurementError,
                        Math.abs(actual-(inverter?500:505)*1e6/(inverter?vr:vi)));
            }
            assertTrue(maximum<3.4e-5,"All-sample current error in amperes: "+maximum);
            if(!current)assertTrue(wrongMeasurementError>10,"Profile must distinguish the two measured voltages");
        }
    }
    @Test void auxiliaryUnitConversionAndVoltageCompensationFollowTheEquations() {
        var rectifier=new Cdc6tCurrentOrder(Mode.POWER,505,500,5);
        var inverter=new Cdc6tCurrentOrder(Mode.POWER,-500,500,5);
        var current=new Cdc6tCurrentOrder(Mode.CURRENT,1000,500,5);
        assertEquals(1010,rectifier.recoveryBaseAmperes(),0);
        assertEquals(1000,inverter.recoveryBaseAmperes(),0);
        assertEquals(1000,current.recoveryBaseAmperes(),0);
        assertEquals(515e6/505000,rectifier.amperes(500000,505000,10),1e-12);
        assertEquals(1020,inverter.amperes(500000,505000,10),1e-12);
        assertEquals(1000+10e6/505000,current.amperes(500000,505000,10),1e-12);
        assertEquals(495000,rectifier.inverterVoltage(1000),0);
        assertThrows(IllegalArgumentException.class,()->rectifier.amperes(500000,0,0));
        assertThrows(IllegalArgumentException.class,()->inverter.amperes(Double.NaN,505000,0));
        assertThrows(IllegalArgumentException.class,()->new Cdc6tCurrentOrder(Mode.CURRENT,-1,500,0));
    }
}
