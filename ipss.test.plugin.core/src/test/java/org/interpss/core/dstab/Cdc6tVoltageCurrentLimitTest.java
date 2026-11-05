package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.interpss.dstab.hvdc.Cdc6tCurrentOrder;
import org.interpss.dstab.hvdc.Cdc6tVoltageCurrentLimit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent measured-input current-limiter equations; coupled network qualification remains separate. */
public class Cdc6tVoltageCurrentLimitTest {
    @Test void increasedCurrentOrderRemainsNonbindingInConvergedControl() throws Exception {
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-current-order-bracket-20261105/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain current-order reference captures");
        assertEquals("f72b27f6db37309cce87fe18b700f056fb6873de8c7fbbcccf109d54b854f951",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        String prefix="cdc6t-margin1300-pass2-nov5-current-fault-";
        try(var zip=new ZipFile(archive.toFile())) {
            byte[] first=zip.getInputStream(zip.getEntry(prefix+"a/native.csv")).readAllBytes();
            assertArrayEquals(first,zip.getInputStream(zip.getEntry(prefix+"b/native.csv")).readAllBytes());
            for(String repeat:new String[]{"a","b"}) {
                String manifest=new String(zip.getInputStream(zip.getEntry(prefix+repeat+"/manifest.json"))
                        .readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(manifest.contains("\"status\": \"complete\""));
                assertTrue(manifest.contains("\"flow_passes\": 2"));
                assertTrue(manifest.contains("\"current_order\": 1300.0"));
            }
            var rows=Tiocr1ResetReferenceTest.csv(first);
            assertEquals(1005,rows.size());
            for(var row:rows) {
                assertEquals(1300.,row.get("STATE1"),1e-9);
                assertEquals(1300.,row.get("VAR11"),1e-9);
            }
        }
    }
    @ParameterizedTest
    @ValueSource(strings={"flat","equal-slope-fault","two-slope-fault"})
    void completeReferenceSamplesMatchNormalCurrentCeiling(String profile) throws Exception {
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-vdcl-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain current-limit reference captures");
        assertEquals("0afeb5bb1968ce9048b86daa4d3061a9a54ef2505424a8824acc89c7b916416d",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        var limiter=new Cdc6tVoltageCurrentLimit(480,800,490,profile.equals("two-slope-fault")?980:1000,500,1200);
        var command=new Cdc6tCurrentOrder(Cdc6tCurrentOrder.Mode.POWER,-500,500,0);
        try(var zip=new ZipFile(archive.toFile())) {
            byte[] raw=zip.getInputStream(zip.getEntry(profile+"/a/native.csv")).readAllBytes();
            assertArrayEquals(raw,zip.getInputStream(zip.getEntry(profile+"/b/native.csv")).readAllBytes());
            var rows=Tiocr1ResetReferenceTest.csv(raw);assertEquals(1005,rows.size());
            int lower=0,upper=0;double maxError=0;
            for(var row:rows) {
                double volts=row.get("STATE0"),desired=command.amperes(volts,row.get("STATE2"),row.get("VAR0"));
                double limited=limiter.limit(desired,volts);
                maxError=Math.max(maxError,Math.abs(limited-row.get("VAR11")));
                if(limited<desired-1) {if(volts<490000)lower++;else upper++;}
                if(profile.equals("flat"))assertEquals(1000,limited,1e-12);
            }
            assertTrue(maxError<3.9e-5,"All-sample current error in amperes: "+maxError);
            if(!profile.equals("flat")){assertTrue(lower>0);assertTrue(upper>0);}
        }
    }
    @Test void preparedCurvePreservesKneesPlateausAndSmallerOrders() {
        var limiter=new Cdc6tVoltageCurrentLimit(480,800,490,980,500,1200);
        assertEquals(800,limiter.ceilingAmperes(0),0);
        assertEquals(800,limiter.ceilingAmperes(480000),0);
        assertEquals(890,limiter.ceilingAmperes(485000),1e-12);
        assertEquals(980,limiter.ceilingAmperes(490000),0);
        assertEquals(1090,limiter.ceilingAmperes(495000),1e-12);
        assertEquals(1200,limiter.ceilingAmperes(500000),0);
        assertEquals(1200,limiter.ceilingAmperes(600000),0);
        assertEquals(500,limiter.limit(500,485000),0);
        assertEquals(890,limiter.limit(1400,485000),1e-12);
        assertThrows(IllegalArgumentException.class,()->limiter.ceilingAmperes(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->new Cdc6tVoltageCurrentLimit(480,800,480,980,500,1200));
        assertThrows(IllegalArgumentException.class,()->new Cdc6tVoltageCurrentLimit(480,800,490,700,500,1200));
    }

    @Test void independentVoltageReversalSamplesUseInverterMeasurement() {
        var limiter=new Cdc6tVoltageCurrentLimit(450,400,490,1000,550,1200);
        // These selected fault samples have a lower rectifier measurement. Both
        // voltage choices yield different limits, so the recorded order distinguishes them.
        double[][] samples={
                {495899.78125,489067.875,1008.26824951},
                {492301.46875,487583.03125,1007.67156982},
                {490577.5625,488241.8125,1001.92523193}
        };
        for(double[] sample:samples) {
            double inverterVoltage=sample[0],rectifierVoltage=sample[1],recordedOrder=sample[2];
            double requested=500e6/inverterVoltage;
            double inverterOrder=limiter.limit(requested,inverterVoltage);
            double lowerVoltageOrder=limiter.limit(requested,Math.min(inverterVoltage,rectifierVoltage));
            assertEquals(inverterOrder,recordedOrder,1e-4);
            assertTrue(inverterOrder-lowerVoltageOrder>20,
                    "The recorded order must distinguish the two voltage selectors");
        }
    }

    @Test void completeVoltageReversalReferenceUsesInverterMeasurement() throws Exception {
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-vdcol-selector-20261105/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain voltage-selector reference captures");
        assertEquals("bfe0fe3eac2a06dd6da492f4ef87904c589930350c089bc8b92773eac15b2922",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        var limiter=new Cdc6tVoltageCurrentLimit(450,400,490,1000,550,1200);
        try(var zip=new ZipFile(archive.toFile())) {
            for(String repeat:new String[]{"a","b"}) {
                for(String input:new String[]{"manifest.json","network.raw","dynamic.dyr","cdc6t_reference_probe.py"})
                    assertNotNull(zip.getEntry("selector/"+repeat+"/"+input),"Missing reproducibility input "+input);
            }
            byte[] raw=zip.getInputStream(zip.getEntry("selector/a/native.csv")).readAllBytes();
            assertArrayEquals(raw,zip.getInputStream(zip.getEntry("selector/b/native.csv")).readAllBytes());
            var rows=Tiocr1ResetReferenceTest.csv(raw);
            assertEquals(1005,rows.size(),"Keep every sample and both event sides");
            int reversed=0,distinguished=0;double maxInverterError=0,minLowerVoltageError=Double.POSITIVE_INFINITY;
            for(var row:rows) {
                double time=row.get("time_s"),inverterVoltage=row.get("STATE0"),rectifierVoltage=row.get("STATE2");
                if(time<=.05||time>=.1||rectifierVoltage>=inverterVoltage)continue;
                reversed++;
                double requested=500e6/inverterVoltage;
                double inverterOrder=limiter.limit(requested,inverterVoltage);
                double lowerVoltageOrder=limiter.limit(requested,rectifierVoltage);
                if(inverterOrder-lowerVoltageOrder<=1)continue;
                distinguished++;
                double observed=row.get("VAR11");
                maxInverterError=Math.max(maxInverterError,Math.abs(observed-inverterOrder));
                minLowerVoltageError=Math.min(minLowerVoltageError,Math.abs(observed-lowerVoltageOrder));
            }
            assertEquals(95,reversed);
            assertEquals(82,distinguished);
            assertTrue(maxInverterError<5e-5,"All distinguishing current-order samples in amperes");
            assertTrue(minLowerVoltageError>1,"The alternative selector must remain distinguishable");
        }
    }
}
