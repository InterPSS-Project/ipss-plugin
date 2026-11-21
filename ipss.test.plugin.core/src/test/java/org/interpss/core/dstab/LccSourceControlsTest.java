package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.hvdc.model.*;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.interpss.fadapter.psse.export.PSSEJsonExporter;
import org.interpss.numeric.datatype.LimitType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.LccSourceControls;

public class LccSourceControlsTest extends CorePluginTestSetup {
    @TempDir Path folder;
    @ParameterizedTest @ValueSource(ints={34,35,36})
    void sourceVersionLayoutsRetainThresholdAndBothCapacitorImpedances(int version)throws Exception{
        var net=new PSSEDirectParser().parse("testData/psse/v"+version+"/sample_v"+version+".raw");
        var line=(HvdcLine2TLCC<?>)net.getSpecialBranchList().stream().filter(HvdcLine2TLCC.class::isInstance).findFirst().orElseThrow();
        var controls=assertInstanceOf(LccSourceControls.class,line.getExtensionObject());
        assertEquals(new LccSourceControls(400,2.0034,2),controls);
        assertEquals(0,line.getRectifier().getCommutingCapacitor(),0);
        assertEquals(0,line.getInverter().getCommutingCapacitor(),0);
    }
    @Test void rawToJsonToNetworkRoundTripKeepsSourceControls()throws Exception{
        var net=new PSSEDirectParser().parse("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC_PsetOnInv.raw");
        var line=(HvdcLine2TLCC<?>)net.getSpecialBranchList().get(0);
        assertEquals(new LccSourceControls(0,0,0),line.getExtensionObject());
        var expected=new LccSourceControls(487.25,12.5,24.75);line.setExtensionObject(expected);
        com.interpss.core.aclf.AclfNetwork copied=net.jsonCopy();
        assertEquals(expected,((HvdcLine2TLCC<?>)copied.getSpecialBranchList().get(0)).getExtensionObject());
        Path output=folder.resolve("roundtrip.rawx");new PSSEJsonExporter(net).export(output);
        var restored=new PSSEJsonDirectParser().parse(output.toString());
        var actual=(HvdcLine2TLCC<?>)restored.getSpecialBranchList().stream().filter(HvdcLine2TLCC.class::isInstance).findFirst().orElseThrow();
        assertEquals(expected,actual.getExtensionObject());
        assertEquals(0,actual.getRectifier().getCommutingCapacitor(),0);
        assertEquals(0,actual.getInverter().getCommutingCapacitor(),0);
    }
    @Test void preparationUsesImportedThresholdAndDoesNotDefaultMissingMetadata()throws Exception{
        var fixture=Cdc6tCoupledNetworkTest.load(folder);var net=fixture.context().getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)net.getSpecialBranchList().get(0);
        var p=new Cdc6tParameters(owner.getName(),new double[]{5,20,.05,.05,.2,.3,.02,.2,.3,.02,
                250,500,99,99,400,250,450,500,2000,425,2000,.02,-999,999,-999,0,0,999,999,0,999,.05});
        owner.setExtensionObject(new LccSourceControls(490,0,0));
        var prepared=Cdc6tNetworkPreparation.prepare(net,p);
        assertEquals(490,prepared.operatingPoint().modeThresholdKilovolts(),0);
        var protection=prepared.protection();protection.beginTrial(.05);protection.observe(1,1,480000);protection.acceptTrial();
        assertEquals(Cdc6tNormalRuntime.OperatingMode.POWER_TO_CURRENT,protection.operatingMode());
        owner.setExtensionObject(null);
        assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(net,p));
        assertEquals(0,Cdc6tNetworkPreparation.prepare(net,p,0).operatingPoint().modeThresholdKilovolts(),0);
        owner.setExtensionObject("unrelated metadata");
        assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(net,p));
        for(var controls:java.util.List.of(new LccSourceControls(0,1,0),new LccSourceControls(0,0,1),new LccSourceControls(-1,0,0))){
            owner.setExtensionObject(controls);
            assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(net,p));
            assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(net,p,0));
        }
        owner.setExtensionObject(new LccSourceControls(0,0,0));
        owner.getInverter().setFiringAngLimit(new LimitType(15,15));
        assertThrows(IllegalArgumentException.class,()->Cdc6tNetworkPreparation.prepare(net,p),"Constant gamma must not silently use the ordinary dynamic angle");
    }
    @Test void nonfiniteSourceFieldsCannotEnterPreparedMetadata(){
        assertThrows(IllegalArgumentException.class,()->new LccSourceControls(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->new LccSourceControls(0,Double.POSITIVE_INFINITY,0));
        assertThrows(IllegalArgumentException.class,()->new LccSourceControls(0,0,Double.NaN));
    }
}
