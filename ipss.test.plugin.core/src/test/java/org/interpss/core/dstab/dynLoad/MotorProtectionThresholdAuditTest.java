package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/** Reports the observed-voltage bracket without assuming a hidden comparator coordinate. */
public class MotorProtectionThresholdAuditTest {
    @Test void retainedObservedVoltageBracket()throws Exception {
        var archive=Path.of("testData/private/verification-artifacts/cmldblu1-motor-threshold-audit-20261104/captures.zip");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(archive));
        assertEquals("a229651baeaedb205ccca472769e9db93da39149325e6d70c0c937cd07e1e23e",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        try(var zip=new ZipFile(archive.toFile())) {
            byte[] lower=null;
            for(String boundary:List.of("down","equal","up")) {
                String prefix="target/cmld-motor-threshold-"+boundary+"-20261104/cmldblu1-mixed-flat-h0.0005-";
                byte[] raw=CompositeMotorRecloseTest.read(zip,prefix+"a-part1/native.csv");
                assertArrayEquals(raw,CompositeMotorRecloseTest.read(zip,prefix+"b-part1/native.csv"));
                var rows=CompositeMotorRecloseTest.csv(new String(raw,java.nio.charset.StandardCharsets.UTF_8));assertEquals(1007,rows.size());
                var events=com.google.gson.JsonParser.parseString(new String(CompositeMotorRecloseTest.read(zip,prefix+"a-part1/threshold-events.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
                var change=events.get(1).getAsJsonObject();int delta=boundary.equals("down")?-1:boundary.equals("up")?1:0;
                assertEquals(delta,change.get("threshold_bits").getAsInt()-change.get("voltage_bits").getAsInt());
                var first=rows.stream().filter(r->r.get("VAR83")<1).findFirst().orElseThrow();
                assertEquals(boundary.equals("up")?.004:.025,first.get("time_s"),5e-6);
                assertEquals(.75,first.get("VAR83"),0);
                if(boundary.equals("down"))lower=raw;
                if(boundary.equals("equal"))assertArrayEquals(lower,raw,"Observed-equality and lower-threshold traces agree completely");
                if(!boundary.equals("up"))for(var row:rows)if(row.get("time_s")>.0021&&row.get("time_s")<.0199) {
                    assertEquals(9999.,row.get("VAR78"),0,"Pending deadline cleared");
                    assertEquals(change.get("voltage").getAsDouble(),row.get("V_OBS"),1e-12,"Published voltage remains stationary");
                }
            }
        }
    }
    @Test void analyticAdjacentDoubleAudit()throws Exception {
        var outcomes=new LinkedHashMap<String,List<Double>>();
        for(String boundary:List.of("down","equal","up")) {
            var net=com.interpss.dstab.DStabObjectFactory.createDStabilityNetwork();net.setBaseKva(100000);net.setFrequency(60);
            var bus=(com.interpss.dstab.DStabBus)com.interpss.dstab.DStabObjectFactory.createDStabBus("B",net).get();
            bus.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);bus.setBaseVoltage(1000);bus.setVoltage(org.apache.commons.math3.complex.Complex.ONE);bus.setInitLoad(new org.apache.commons.math3.complex.Complex(.6,.2));
            var motor=new org.interpss.dstab.dynLoad.impl.InductionMotorImpl(bus,"M");
            motor.setXm(3);motor.setXl(.07);motor.setRa(.032);motor.setXr1(.2);motor.setRr1(.01);motor.setMvaBase(50);motor.setH(1);motor.setLoadPercent(50);motor.setRatedVoltage(1000);motor.setInitLoadPQ(new org.apache.commons.math3.complex.Complex(.3,.12));
            motor.setVtr1(.9);motor.setTtr1(.01);motor.setFtr1(.25);motor.setVrc1(2);motor.setTrc1(999);
            motor.setVtr2(.9);motor.setTtr2(.01);motor.setFtr2(.25);motor.setVrc2(2);motor.setTrc2(999);motor.setLegacyCompositeProtectionScheduling(true);assertTrue(motor.initStates());
            bus.setVoltage(new org.apache.commons.math3.complex.Complex(.8,0));for(int i=0;i<4;i++)motor.afterStep(.0005);
            double voltage=boundary.equals("down")?Math.nextDown(.9):boundary.equals("up")?Math.nextUp(.9):.9;
            bus.setVoltage(new org.apache.commons.math3.complex.Complex(voltage,0));assertEquals(voltage,bus.getVoltageMag(),0);motor.afterStep(.0005);
            var history=motor.getIntegrationHistories();var timers=List.of(history.get("TripTimer1"),history.get("TripTimer2"));outcomes.put(boundary,timers);
            if(!boundary.equals("equal"))for(double timer:timers)assertEquals(boundary.equals("down")?.0025:0,timer,1e-15);
            else for(double timer:timers)assertTrue(timer>=0&&timer<=.002,"Equality cannot accumulate undervoltage exposure");
            assertEquals(1,motor.getFuv(),0);
        }
        System.out.println("Motor threshold audit (stage1,stage2): "+outcomes+"; exact equality reset is not source-qualified");
    }
}
