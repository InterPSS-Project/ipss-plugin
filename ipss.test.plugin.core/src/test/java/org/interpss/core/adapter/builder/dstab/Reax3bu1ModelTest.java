package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Gewtgcu1Model;
import org.interpss.dstab.mach.Reaxbu1Data;
import org.interpss.dstab.mach.Reaxbu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;

/** Synthetic equation, limit, and exact-wrapper checks for REAX3BU1. */
class Reax3bu1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void modifiedEulerMatchesIndependentLagCalculation() {
        Reaxbu1Model model = new Reaxbu1Model("REAX3BU1", data(.2));
        model.initialize(0,0); model.setReferences(.1,.2);
        model.step(.01,0); model.step(.01,1);
        assertEquals(.008775,model.getReactiveOutput(),1e-12);
        assertEquals(.008775,model.getActiveOutput(),1e-12);
        assertEquals(2,model.getNamedStates().size());
    }

    @Test
    void algebraicPathAndLimitsAreApplied() {
        Reaxbu1Model model=new Reaxbu1Model("REAX3BU1",data(0));
        model.initialize(0,0);model.setReferences(2,-3);
        model.step(.01,0);model.step(.01,1);
        assertEquals(.7,model.getReactiveOutput(),0);
        assertEquals(-.6,model.getActiveOutput(),0);
        assertThrows(IllegalArgumentException.class,()->new Reaxbu1Data(0,.1,0,1,1,-1,1,-1));
    }

    @Test
    void exactUserWrapperAttachesToConverter(@TempDir Path tempDir) throws Exception {
        IpssCorePlugin.init();
        Path dyr=tempDir.resolve("synthetic-type3-auxiliary.dyr");
        Files.writeString(dyr,"""
                1 'USRMDL' '1' 'GEWTGCU1' 101 1 2 18 3 3
                  40 0 1.5 .33403 .5 .9 2.775 1.2 1 .4 .9 10 .02 .4 0 .7 .55 .9 1 .1 /
                1 'USRMDL' '1' 'REAX3BU1' 107 0 1 7 2 4 99
                  .17 1.83 .94 .72 -.68 .81 -.63 /
                2 'GENCLS' '1' 99999 0 /
                """);
        var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(),dyr.toString());
        DStabGen gen=(DStabGen)context.getDStabilityNet().getBus("Bus1").getContributeGen("1");
        Gewtgcu1Model host=assertInstanceOf(Gewtgcu1Model.class,gen.getDynamicGenDevice());
        assertNotNull(host.getAuxiliaryController());
        assertEquals("REAX3BU1",host.getAuxiliaryController().getModelName());
        assertEquals(14,DynamicModelCatalog.find("REAX3BU1").orElseThrow().parameterCount());
    }

    private static Reaxbu1Data data(double time) {
        return new Reaxbu1Data(99,time,1.8,.9,.7,-.5,.8,-.6);
    }
}
