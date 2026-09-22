package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Gewtgcu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;

/** Exact synthetic-wrapper check for the Type-4 REAXB attachment. */
public class Reax4bu1ModelTest {
    private static final Path CASE=Path.of("testData","adpter","psse","v33","SMIB");
    @Test void exactUserWrapperUsesSharedTwoStateKernel(@TempDir Path tempDir)throws Exception{
        IpssCorePlugin.init();Path dyr=tempDir.resolve("synthetic-type4-auxiliary.dyr");
        Files.writeString(dyr,"""
                1 'USRMDL' '1' 'GEWTGCU1' 101 1 2 18 3 3
                  40 0 1.5 .33403 .5 .9 2.775 1.2 1 .4 .9 10 .02 .4 0 .7 .55 .9 1 .1 /
                1 'USRMDL' '1' 'REAX4BU1' 107 0 1 7 2 4 88
                  .19 1.71 .91 .69 -.66 .79 -.61 /
                2 'GENCLS' '1' 99999 0 /
                """);
        var context=new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1.raw").toString(),dyr.toString());
        DStabGen gen=(DStabGen)context.getDStabilityNet().getBus("Bus1").getContributeGen("1");
        Gewtgcu1Model host=assertInstanceOf(Gewtgcu1Model.class,gen.getDynamicGenDevice());
        assertNotNull(host.getAuxiliaryController());
        assertEquals("REAX4BU1",host.getAuxiliaryController().getModelName());
        host.getAuxiliaryController().initialize(0,0);
        assertEquals(2,host.getAuxiliaryController().getNamedStates().size());
        assertEquals(14,DynamicModelCatalog.find("REAX4BU1").orElseThrow().parameterCount());
    }
}
