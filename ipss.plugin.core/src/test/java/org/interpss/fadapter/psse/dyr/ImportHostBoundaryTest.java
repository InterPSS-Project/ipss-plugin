package org.interpss.fadapter.psse.dyr;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.controller.exc.ac8c.Ac8cExciter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Quoted embedded identities and the current five-selector exciter schema. */
class ImportHostBoundaryTest {
    private static final Path DATA=Path.of("..","ipss.test.plugin.core","testData","adpter","psse","v33","SMIB");
    @TempDir Path temp;
    private com.interpss.simu.SimuContext load(String text,PSSEMultiFileLoader loader) throws Exception {
        IpssCorePlugin.init();Path input=temp.resolve("case.dyr");Files.writeString(input,text);
        return loader.loadDStab(DATA.resolve("SMIB_v33.raw").toString(),input.toString());
    }
    @Test void paddedProtectionGeneratorIdsResolveToTheActualGenerator() throws Exception {
        var loader=new PSSEMultiFileLoader();var context=load("""
                1 'GENCLS' '1' 3 0 /
                2 'GENCLS' '1' 99999 0 /
                201 'FRQTPAT' 1 1 '1 ' 57 62 .1 0 /
                202 'VTGTPAT' 1 1 '1 ' .8 1.2 .1 0 /
                """,loader);
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
        var relays=context.getDStabilityNet().getDStabBus("Bus1").getDynamicBusDeviceList();
        assertEquals(2,relays.stream().filter(org.interpss.dstab.relay.AbstractGeneratorTripRelayModel.class::isInstance).count());
    }
    @Test void fiveSelectorFlatExciterUsesExactlyTheWrapperFieldOrder() throws Exception {
        String wrapper=Files.readString(DATA.resolve("SMIB_v33_genrou_ac8cu1.dyr"));
        var result=new StringBuilder();
        for(var record:PsseDyrRecordReader.read(new java.io.StringReader(wrapper),"public")) {
            if(record.canonicalModelName().equals("AC8C"))
                result.append("1 'AC8C' '1' ").append(String.join(" ",record.fields().subList(10,record.fields().size()))).append(" /\n");
            else result.append(String.join(" ",record.fields())).append(" /\n");
        }
        var loader=new PSSEMultiFileLoader();var context=load(result.toString(),loader);
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
        var gen=(com.interpss.dstab.DStabGen)context.getDStabilityNet().getDStabBus("Bus1").getContributeGen("1");
        var data=assertInstanceOf(Ac8cExciter.class,gen.getMach().getExciter()).getData();
        assertEquals(0,data.getSclLocation());assertEquals(1,data.getVosLocation());assertEquals(1,data.getSw1());
        assertEquals(.1,data.getTr(),0);assertEquals(2,data.getKpr(),0);assertEquals(10,data.getVbmax(),0);
    }

    @Test void absentParentsAreSkippedAndReportedButCanBeRequiredExplicitly() throws Exception {
        String source="""
                1 'TGOV1' '1' .05 .1 1 0 .1 .2 0 /
                1 'SEXS' '1' .4 6 20 0 .5 5 /
                201 'FRQTPAT' 1 1 'ZZ ' 57 62 .1 0 /
                3 'GENCLS' '1' 3 0 /
                """;
        var loader=new PSSEMultiFileLoader();load(source,loader);
        assertEquals(4,loader.getLastDynamicImportReport().count(DynamicModelImportStatus.SKIPPED_MISSING_HOST));
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
        assertTrue(loader.getLastDynamicImportReport().entries().stream().allMatch(e->e.message().contains("Ignored absent required host")));
        var require=new PSSEMultiFileLoader().setIgnoreMissingHosts(false);load(source,require);
        assertEquals(0,require.getLastDynamicImportReport().count(DynamicModelImportStatus.SKIPPED_MISSING_HOST));
        assertFalse(require.getLastDynamicImportReport().isStrictlyComplete());
    }
    @Test void zeroDroopStoppedOpeningAndSignedLeadRemainValid() {
        var governor=new com.interpss.dstab.controller.gov.ggov1.PsseGgov1Governor("g","GGOV1","test");
        var data=governor.getData();data.setR(0);data.setRopen(0);data.setTc(-.02);data.setTb(.1);
        assertTrue(governor.validateParameters());assertEquals(0,data.getR(),0);assertEquals(-.02,data.getTc(),0);
        data.setTb(0);assertFalse(governor.validateParameters());
        data.setTb(.1);data.setR(-.01);assertFalse(governor.validateParameters());
    }

    @Test void typeTwoGeneratorAcceptsTheSharedInductionShaft() throws Exception {
        var loader=new PSSEMultiFileLoader();IpssCorePlugin.init();
        Path dyr=temp.resolve("wind.dyr");Files.writeString(dyr,Files.readString(DATA.resolve("SMIB_v33_wt2g1.dyr"))
                +"\n1 'WT12T1' '1' 3 0 .5 2 .2 /\n");
        var context=loader.loadDStab(DATA.resolve("SMIB_v33_wt2g1.raw").toString(),dyr.toString());
        assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
        var host=assertInstanceOf(com.interpss.dstab.mach.model.Wt2g1Machine.class,context.getDStabilityNet().getMachine("Bus1-mach1"));
        assertNotNull(host.getDriveTrain());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        assertTrue(host.initStates(host.getDStabBus()));double speed=host.getSpeed();
        for(int step=0;step<20;step++)for(int stage=0;stage<2;stage++)
            assertTrue(host.nextStepMechanical(.0001,com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER,context.getDStabilityNet(),stage));
        assertEquals(speed,host.getSpeed(),1e-7);
    }

    @Test void sparseHydroCurvesDoNotInterpretUnusedSlotsAsReturningToZero() {
        var governor=new com.interpss.dstab.controller.gov.weccwshygp.WshygpGovernor("g","WSHYGP","test");
        var d=governor.getData();d.set(13,1);d.set(18,1);d.set(19,1);
        assertTrue(governor.validateParameters());assertEquals(.4,governor.gateCurve(.4),1e-12);
        assertEquals(.4,governor.inverseGateCurve(.4),1e-12);
        d.set(20,.5);d.set(21,.6);assertFalse(governor.validateParameters());
    }

    @Test void negativeGovernorLeadPassesStartupScreeningAndDisconnectedStabilizerInputInitializes() throws Exception {
        for(String fixture:new String[]{"SMIB_v33_genrou_ggov1.dyr","SMIB_v33_genrou_esst1a_pss2a.dyr"}) {
            var text=new StringBuilder();
            for(var record:PsseDyrRecordReader.read(DATA.resolve(fixture))) {
                var fields=new java.util.ArrayList<>(record.fields());
                if(record.canonicalModelName().equals("GGOV1")){fields.set(19,"-.02");fields.set(18,".1");}
                if(record.canonicalModelName().equals("PSS2A")){fields.set(5,"0");fields.set(6,"0");}
                text.append(String.join(" ",fields)).append(" /\n");
            }
            var loader=new PSSEMultiFileLoader();var context=load(text.toString(),loader);
            assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete(),loader.getLastDynamicImportReport().failureSummary());
            var report=org.interpss.dstab.validation.DynamicParameterValidator.validate(context.getDStabilityNet(),.001,1,m->1);
            assertEquals(0,report.count(org.interpss.dstab.validation.DynamicParameterValidator.Severity.ERROR),report.toString());
            var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuStepSec(.001);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
            assertTrue(algorithm.initialization());
            for(int step=0;step<10;step++)assertTrue(algorithm.solveDEqnStep(true));
        }
    }

    @Test void zeroLagExciterLeadIsReportedAsBypassedWithoutChangingTheData() throws Exception {
        String source=Files.readString(DATA.resolve("SMIB_v33_genrou_esst1a_pss2a.dyr"));
        var text=new StringBuilder();
        for(var record:PsseDyrRecordReader.read(new java.io.StringReader(source),"public")) {
            var fields=new java.util.ArrayList<>(record.fields());
            if(record.canonicalModelName().equals("ESST1A")){fields.set(10,".7");fields.set(11,"0");}
            text.append(String.join(" ",fields)).append(" /\n");
        }
        var context=load(text.toString(),new PSSEMultiFileLoader());
        var report=org.interpss.dstab.validation.DynamicParameterValidator.validate(context.getDStabilityNet(),.001,1,m->1);
        assertEquals(0,report.count(org.interpss.dstab.validation.DynamicParameterValidator.Severity.ERROR));
        assertTrue(report.issues().stream().anyMatch(x->x.parameter().equals("Tc1/Tb1")&&x.message().contains("bypasses")));
        var exc=(com.interpss.dstab.controller.exc.ieee1981st1.IEEE1981ST1Exciter)context.getDStabilityNet().getMachine("Bus1-mach1").getExciter();
        assertEquals(.7,exc.getData().getTc1(),0);assertEquals(0,exc.getData().getTb1(),0);
    }

    @Test void compoundCircuitOrderingWarningDoesNotHideAnInconsistentMachineMutation() throws Exception {
        var text=new StringBuilder();
        for(var record:PsseDyrRecordReader.read(DATA.resolve("SMIB_v33_gentpj1.dyr"))) {
            var fields=new java.util.ArrayList<>(record.fields());
            if(record.canonicalModelName().equals("GENTPJ1")) {
                fields.set(5,"0");fields.set(6,".05");fields.set(10,".06");fields.set(12,".06");
                fields.set(11,".4");fields.set(13,".2");fields.set(14,".2");fields.set(15,".01");
            }
            text.append(String.join(" ",fields)).append(" /\n");
        }
        var context=load(text.toString(),new PSSEMultiFileLoader());
        var report=org.interpss.dstab.validation.DynamicParameterValidator.validate(context.getDStabilityNet(),.001,1,m->1);
        assertEquals(0,report.count(org.interpss.dstab.validation.DynamicParameterValidator.Severity.ERROR),report.toString());
        assertTrue(report.issues().stream().anyMatch(x->x.parameter().equals("Xq'' <= Xq'")
                &&x.severity()==org.interpss.dstab.validation.DynamicParameterValidator.Severity.WARNING),report.toString());
        var machine=(com.interpss.dstab.mach.model.Gentpj1Machine)context.getDStabilityNet().getMachine("Bus1-mach1");
        machine.setXq11(.5);
        report=org.interpss.dstab.validation.DynamicParameterValidator.validate(context.getDStabilityNet(),.001,1,m->1);
        assertTrue(report.count(org.interpss.dstab.validation.DynamicParameterValidator.Severity.ERROR)>0);
    }

}
