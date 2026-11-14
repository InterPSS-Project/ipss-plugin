package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import com.interpss.dstab.hvdc.model.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Measured-state qualification of conducting algebra, not coupled trajectory acceptance. */
public class Cdc6tConductingLinkTest {
    private static Cdc6tConductingLink link() {
        return new Cdc6tConductingLink(
            new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8),
            new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8),
            5,Math.toRadians(5),Math.toRadians(20));
    }
    @ParameterizedTest
    @CsvSource({"current-order,inverter-power","current-order,rectifier-power","current-order,current",
        "vdcl,flat","vdcl,equal-slope-fault","vdcl,two-slope-fault","inverter-fault,inverter-power"})
    void commandsAndMinimumAnglesReproduceConductingBoundary(String group,String profile) throws Exception {
        var archive=Path.of("testData/private/verification-artifacts/cdc6t-"+group+"-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent conducting-link captures");
        String hash=switch(group) {
            case "vdcl" -> "0afeb5bb1968ce9048b86daa4d3061a9a54ef2505424a8824acc89c7b916416d";
            case "inverter-fault" -> "7e18c3851254b3c1b705cc8eafc1a589cd9828cd85a26f34a4c484d421cb2628";
            default -> "6d2fe324463db46be0fa0f6fd3f69aa928cb687a527b80ff8236ea1ea307a400";
        };
        assertEquals(hash,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        var command=new Cdc6tCurrentOrder(profile.equals("current")?Cdc6tCurrentOrder.Mode.CURRENT:Cdc6tCurrentOrder.Mode.POWER,
            profile.equals("current")?1000:profile.equals("rectifier-power")?505:-500,500,0);
        var limit=new Cdc6tVoltageCurrentLimit(480,800,490,profile.equals("two-slope-fault")?980:1000,500,1200);
        var model=link(); double[] output=new double[8],max=new double[8];
        int rectifierBound=0,inverterBound=0;
        try(var zip=new ZipFile(archive.toFile())) {
            String entry=group.equals("inverter-fault")?"a/native.csv":profile+"/a/native.csv";
            var rows=Tiocr1ResetReferenceTest.csv(zip.getInputStream(zip.getEntry(entry)).readAllBytes());
            assertEquals(1005,rows.size());
            for(var row:rows) {
                double current=command.amperes(row.get("STATE0"),row.get("STATE2"),0);
                if(group.equals("vdcl"))current=limit.limit(current,row.get("STATE0"));
                model.evaluate(row.get("V7"),row.get("V9"),current,command.inverterVoltage(current),output,0);
                String[] keys={"VAR5","VAR6","VAR12","VAR7","VAR8","VAR13","VAR10","VAR9"};
                for(int i=0;i<8;i++)max[i]=Math.max(max[i],Math.abs((i==2||i==5?Math.toDegrees(output[i]):output[i])-row.get(keys[i])));
                assertEquals(5*current*current/1e8,output[0]+output[3],1e-12);
                if(Math.abs(Math.toDegrees(output[2])-5)<1e-7)rectifierBound++;
                if(Math.abs(Math.toDegrees(output[5])-20)<1e-7)inverterBound++;
            }
        }
        System.out.println("CDC6T conducting "+group+" "+profile+" "+java.util.Arrays.toString(max));
        for(int i=0;i<8;i++)assertTrue(max[i]<(i>=6?.3:i==2||i==5?.0003:i%3==1?2e-5:3e-6),"Coordinate "+i+" error "+max[i]);
        if(group.equals("current-order"))assertTrue(rectifierBound>0);
        if(group.equals("inverter-fault"))assertTrue(inverterBound>0);
    }
    @Test void failedTrialDoesNotPublishPartialResults() {
        var model=link();double[] output={1,2,3,4,5,6,7,8};var before=output.clone();
        assertThrows(IllegalArgumentException.class,()->model.evaluate(1,.01,1000,500000,output,0));
        assertArrayEquals(before,output);
        assertThrows(IllegalArgumentException.class,()->model.evaluate(1,1,-1,500000,output,0));
        assertArrayEquals(before,output);
        model.evaluate(1,1,1000,500000,output,0);
        assertEquals(500000,output[7],1e-9);assertEquals(505000,output[6],1e-9);
    }
    @Test void constantGammaIgnoresScheduledVoltageButRetainsRectifierCapability(){
        var rectifier=new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8);
        var inverter=new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8);
        var gamma=new Cdc6tConductingLink(rectifier,inverter,5,Math.toRadians(5),Math.toRadians(15),
                Cdc6tConductingLink.InverterControl.CONSTANT_GAMMA);
        double[] first=new double[8],second=new double[8];
        gamma.evaluate(1,1,1000,100000,first,0);
        gamma.evaluate(1,1,1000,900000,second,0);assertArrayEquals(first,second);
        assertEquals(15,Math.toDegrees(first[5]),1e-10);
        assertEquals(inverter.maximumDcVoltage(1,1000,Math.toRadians(15)),first[7],1e-9);
        gamma.evaluate(.8,1,1000,100000,first,0);
        assertEquals(5,Math.toDegrees(first[2]),1e-10);assertTrue(Math.toDegrees(first[5])>15);
        assertEquals(5000,first[6]-first[7],1e-9);
        assertThrows(NullPointerException.class,()->new Cdc6tConductingLink(rectifier,inverter,5,0,0,null));
    }
    @Test void currentMarginIsAlgebraicAndOnlyChangesAnActiveRectifierLimit(){
        var rectifier=new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8);
        var inverter=new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8);
        var gamma=new Cdc6tConductingLink(rectifier,inverter,5,Math.toRadians(5),Math.toRadians(15),
                Cdc6tConductingLink.InverterControl.CONSTANT_GAMMA,.1);
        var zero=new Cdc6tConductingLink(rectifier,inverter,5,Math.toRadians(5),Math.toRadians(15),
                Cdc6tConductingLink.InverterControl.CONSTANT_GAMMA);
        double[] first=new double[8],second=new double[8];
        assertEquals(1000,gamma.evaluateCurrent(1,1,1000,500000,first,0),0);
        zero.evaluate(1,1,1000,500000,second,0);assertArrayEquals(first,second,0);
        for(int repetition=0;repetition<3;repetition++){
            assertEquals(900,gamma.evaluateCurrent(.8,1,1000,500000,first,0),0);
            assertEquals(5,Math.toDegrees(first[2]),1e-10);
            assertEquals(4500,first[6]-first[7],1e-9);
            assertEquals(5*900*900/1e8,first[0]+first[3],1e-12);
        }
        assertEquals(1000,gamma.evaluateCurrent(1,1,1000,500000,first,0),0);
        assertArrayEquals(first,second,0,"No retained reduction when the voltage recovers");
        assertThrows(IllegalArgumentException.class,()->new Cdc6tConductingLink(rectifier,inverter,5,0,0,
                Cdc6tConductingLink.InverterControl.CONSTANT_GAMMA,1));
    }
    @Test void scheduledVoltageControlTransfersMarginAtTheRectifierLimit(){
        var rectifier=new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8);
        var inverter=new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8);
        var margin=new Cdc6tConductingLink(rectifier,inverter,5,Math.toRadians(5),Math.toRadians(20),
                Cdc6tConductingLink.InverterControl.SCHEDULED_VOLTAGE,.1);
        var zero=new Cdc6tConductingLink(rectifier,inverter,5,Math.toRadians(5),Math.toRadians(20),
                Cdc6tConductingLink.InverterControl.SCHEDULED_VOLTAGE,0);
        double[] actual=new double[8],control=new double[8];
        assertEquals(1000,margin.evaluateCurrent(1,1,1000,500000,actual,0),0);
        assertEquals(1000,zero.evaluateCurrent(1,1,1000,500000,control,0),0);
        assertArrayEquals(control,actual,0);
        assertEquals(900,margin.evaluateCurrent(.8,1,1000,500000,actual,0),0);
        assertEquals(1000,zero.evaluateCurrent(.8,1,1000,500000,control,0),0);
        assertEquals(4500,actual[6]-actual[7],1e-9);
        assertEquals(5*900*900/1e8,actual[0]+actual[3],1e-12);
        assertEquals(1000,margin.evaluateCurrent(1,1,1000,500000,actual,0),0);
    }
}
