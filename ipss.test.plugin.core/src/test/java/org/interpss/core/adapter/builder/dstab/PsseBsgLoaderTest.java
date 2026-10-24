package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.renewable.*;
import org.interpss.fadapter.psse.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;

public class PsseBsgLoaderTest {
    @TempDir Path directory;
    private static final Path SOURCE=Path.of("testData/adpter/psse/v33/renewable");
    @BeforeAll static void initialize() { IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu"); }
    private void stage() throws Exception {
        Files.copy(SOURCE.resolve("regca_reeca_repca_bus1062.raw"),directory.resolve("case.raw"));
        Files.copy(SOURCE.resolve("regca_reeca_repca_bus1062.dyr"),directory.resolve("case.dyr"));
    }
    private static Regca1Model converter(com.interpss.simu.SimuContext context) {
        return (Regca1Model)((DStabGen)context.getDStabilityNet().getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
    }
    @Test void explicitAndDiscoveredFilesApplyBeforeInitializationAndReportsReset() throws Exception {
        stage();var loader=new PSSEMultiFileLoader();
        for(boolean sibling:new boolean[]{false,true}) {
            var file=directory.resolve(sibling?"case_basegen.bsg":"explicit.bsg");
            Files.writeString(file,"1 \"Plant long name\" 230 \"1\"\n");
            var paths=sibling?new String[]{directory.resolve("case.raw").toString(),directory.resolve("case.dyr").toString()}
                    :new String[]{directory.resolve("case.raw").toString(),directory.resolve("case.dyr").toString(),file.toString()};
            var context=loader.loadDStab(paths);var controller=converter(context).getReeca1Controller();
            assertEquals(1,loader.getLastResponseEntries().size());
            assertEquals(ActivePowerResponseLimits.FIXED,controller.getActivePowerResponseLimits());
            controller.initialize(.24,.04,1.01);
            assertEquals(.24,controller.getEffectiveActivePowerMaximum(),0);
            assertEquals(.24,controller.getEffectiveActivePowerMinimum(),0);
            assertEquals(0,controller.getPlantController().getEffectiveActivePowerMaximum(),0);
            assertEquals(0,controller.getPlantController().getEffectiveActivePowerMinimum(),0);
            assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
        }
        // Explicit input takes precedence over the discovered sibling.
        Files.writeString(directory.resolve("explicit.bsg"),"1 \"Plant\" 230 \"1\" 1\n");
        var explicit=loader.loadDStab(directory.resolve("case.raw").toString(),directory.resolve("case.dyr").toString(),directory.resolve("explicit.bsg").toString());
        assertEquals(ActivePowerResponseLimits.DOWN_ONLY,converter(explicit).getReeca1Controller().getActivePowerResponseLimits());
        loader.loadDStab(directory.resolve("case.raw").toString());
        assertTrue(loader.getLastResponseEntries().isEmpty());
        assertEquals(0,loader.getLastDynamicImportReport().totalRecordCount());
    }
    @Test void missingOrUnsupportedTargetsFailBeforeAnyPolicyChanges() throws Exception {
        stage();var context=new PSSEMultiFileLoader().loadDStab(directory.resolve("case.raw").toString(),directory.resolve("case.dyr").toString());
        var controller=converter(context).getReeca1Controller();
        for(String bad:new String[]{"999 \"Unknown\" 230 \"1\"","3 \"Synchronous\" 230 \"1\""}) {
            var file=directory.resolve("invalid.bsg");Files.writeString(file,"1 \"Plant\" 230 \"1\"\n"+bad+"\n");
            assertThrows(com.interpss.common.exp.InterpssException.class,()->PsseBsgProcessor.apply(context.getDStabilityNet(),file));
            assertEquals(ActivePowerResponseLimits.NORMAL,controller.getActivePowerResponseLimits());
        }
    }
    @Test void initializedTargetRejectsPolicyChangeWithoutLeavingPartialUpdates() throws Exception {
        stage();var context=new PSSEMultiFileLoader().loadDStab(directory.resolve("case.raw").toString(),directory.resolve("case.dyr").toString());
        var first=converter(context).getReeca1Controller();var bus=context.getDStabilityNet().getBus("Bus3");
        var secondConverter=new Regca1Model((DStabGen)bus.getContributeGen("1"),bus,"1",converter(context).getData());
        var second=Reeca1Model.fromNativeRecord(first.getData(),secondConverter);secondConverter.setReeca1Controller(second);
        second.initialize(.1,.04,1);
        var file=directory.resolve("state.bsg");Files.writeString(file,"1 \"First\" 230 \"1\"\n3 \"Second\" 230 \"1\"\n");
        assertThrows(com.interpss.common.exp.InterpssException.class,()->PsseBsgProcessor.apply(context.getDStabilityNet(),file));
        assertEquals(ActivePowerResponseLimits.NORMAL,first.getActivePowerResponseLimits());
        assertEquals(ActivePowerResponseLimits.NORMAL,second.getActivePowerResponseLimits());
    }

    @Test void absentElectricalRecordUsesConstantCommandsButDeclaredInvalidControlDoesNot() throws Exception {
        stage();var original=Files.readString(directory.resolve("case.dyr"));
        var bare=original.lines().filter(line->!line.contains("REECA1")&&!line.contains("REPCA1")).collect(java.util.stream.Collectors.joining("\n"));
        Files.writeString(directory.resolve("case.dyr"),bare);
        var context=new PSSEMultiFileLoader().loadDStab(directory.resolve("case.raw").toString(),directory.resolve("case.dyr").toString());
        var commands=assertInstanceOf(ConverterCurrentCommands.class,converter(context).getActiveElectricalController());
        commands.initialize(.24,.04,1.01);
        commands.step(.001,.1,.1,.6,.98,0);commands.step(.001,.1,.1,.6,.98,1);
        assertEquals(.24/1.01,commands.getIpcmd(),0);assertEquals(-.04/1.01,commands.getIqcmd(),0);
        Files.writeString(directory.resolve("case.dyr"),bare+"\n1 'REECA1' '1' 0 /\n");
        context=new PSSEMultiFileLoader().loadDStab(directory.resolve("case.raw").toString(),directory.resolve("case.dyr").toString());
        assertNull(converter(context).getActiveElectricalController(),"A declared invalid control must not be replaced silently");
    }
    @Test void initializationAccommodationUsesTheSameFloatingPointOrderAsItsState() throws Exception {
        var builder=DStabBuilderTestFixture.createWithMachine();var machine=builder.getDStabNetwork().getMachine("Bus1-mach1");
        var gov=builder.addGovIeeeg1("Bus1","1",20,.2,0,.1,.2903,0,.3633,.3,0,0,.4,0,-.1,.1,.5779,0);
        for(double power:new double[]{.4,.401,.5,.5779,.7}) {
            machine.setSpeed(1);machine.setPm(power);machine.setPe(power);
            assertTrue(gov.initStates(machine.getDStabBus(),machine),"power="+power);
            assertEquals(power,gov.getOutput(machine),1e-12);
        }
    }
}
