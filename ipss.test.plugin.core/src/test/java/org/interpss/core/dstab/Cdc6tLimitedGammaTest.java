package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.*;
import org.interpss.fadapter.psse.dyr.*;

/** Independent fixed-tap gamma control with a binding static rectifier limit. */
public class Cdc6tLimitedGammaTest extends CorePluginTestSetup {
    @TempDir Path folder;

    @Test
    void limitedStaticOperatingPointMatchesIndependentEquipment() throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-constant-gamma-fixed-20261101/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain independent limited-gamma captures");
        assertEquals("447984396d6920f266a9b0640a2424b4c7d39d9a81d8df6d352eb4e9530c0d1f",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        try(var zip=new ZipFile(archive.toFile())) {
            String prefix="inverter/a/";
            var raw=new ArrayList<>(new String(zip.getInputStream(zip.getEntry(prefix+"network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var expected=new com.fasterxml.jackson.databind.ObjectMapper().readTree(zip.getInputStream(zip.getEntry(prefix+"solved-equipment.json")));
            // This test inspects the static owner before running any dynamic steps.
            var fixture=Cdc6tCoupledNetworkTest.load(folder,raw,Cdc6tCurrentOrder.Mode.CURRENT,1000,
                    Cdc6tVoltageCurrentLimit.fromSourcePoints(250,450,500,2000,425,2000));
            var owner=(com.interpss.core.aclf.hvdc.HvdcLine2TLCC<?>)fixture.context().getDStabilityNet().getSpecialBranchList().get(0);
            System.out.println("Limited gamma modes "+owner.getRectifierControlMode()+" / "+owner.getInverterControlMode());
            for(var entry:Map.of("7",owner.getRectifier(),"9",owner.getInverter()).entrySet()) {
                var e=expected.get(entry.getKey());var c=entry.getValue();
                System.out.println("Limited gamma "+entry.getKey()+" tap/V/I/angle="+c.getXformerTapSetting()+"/"+c.getDcVoltage()+"/"+c.getIdc()+"/"+c.getFiringAng());
                System.out.println("Limited gamma static bus "+entry.getKey()+" "+c.getBus().getVoltage());
                assertEquals(e.get("TAP").doubleValue(),c.getXformerTapSetting(),1e-7);
                assertEquals(e.get("KVDC").doubleValue()*1000,c.getDcVoltage(),.5);
                assertEquals(e.get("DCCUR").doubleValue(),c.getIdc(),.001);
                // The independent static solve retains a small AC-voltage residual;
                // the converted-network capture provides a separate tighter control.
                assertEquals(e.get("ANGLE").doubleValue(),c.getFiringAng(),.003);
            }
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(zip.getInputStream(zip.getEntry(prefix+"dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            var parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
            var network=fixture.context().getDStabilityNet();
            owner.setPowerCurrentMargin(.1);
            assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(network,parameters));
            owner.setPowerCurrentMargin(0);owner.getRectifier().setFiringAng(Double.NaN);
            assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(network,parameters));
            owner.getRectifier().setFiringAng(15);
            var binding=Cdc6tNetworkPreparation.prepare(network,parameters);
            var r=binding.ports().rectifierPort();var i=binding.ports().inverterPort();
            var rb=network.getBus("Bus7");var ib=network.getBus("Bus9");
            rb.getDynamicBusDeviceList().clear();ib.getDynamicBusDeviceList().clear();binding.ports().attach();
            assertTrue(i.initStates(ib));assertTrue(r.initStates(rb));
            var initial=owner.getRectifier().powerIntoConverter().divide(rb.getVoltage()).conjugate().negate();
            assertEquals(initial,r.getOutputObject());assertEquals(initial,r.getOutputObject());
            assertThrows(IllegalStateException.class,()->r.nextStep(.0005,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,0));
            assertThrows(IllegalStateException.class,()->r.evaluateStaggered(.0005,new double[3],0));
            assertFalse(i.acceptInitialEndpoint());assertTrue(r.acceptInitialEndpoint());assertFalse(r.acceptInitialEndpoint());
            double[] before=new double[3];binding.runtime().readStaggeredState(before,0);
            assertNotEquals(initial,r.getOutputObject());
            double[] after=new double[3];binding.runtime().readStaggeredState(after,0);
            assertArrayEquals(before,after,0,"Current getters cannot initialize measurement states");
            r.acceptInitialNetworkEndpoint();binding.runtime().readStaggeredState(after,0);
            assertNotEquals(before[0],after[0]);
            double[] settled=after.clone();r.acceptInitialNetworkEndpoint();binding.runtime().readStaggeredState(after,0);
            assertArrayEquals(settled,after,0,"Accepted initial measurements are idempotent");
            assertTrue(r.initStates(rb));assertTrue(i.initStates(ib));assertEquals(initial,r.getOutputObject());
        }
    }
}
