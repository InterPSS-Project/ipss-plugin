package org.interpss.fadapter.psse.dyr;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Map;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.dstab.renewable.Reeca1Model;
import org.interpss.dstab.renewable.Regca1Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Explicit corrections preserve the source and remain subject to model validation. */
class DynamicParameterOverridesTest {
    private static final Path DATA=Path.of("..","ipss.test.plugin.core","testData","adpter","psse","v33");
    @TempDir Path temp;

    private Path input() throws Exception {
        String text=Files.readString(DATA.resolve("renewable/regca_reeca_repca_bus1062.dyr"));
        StringBuilder result=new StringBuilder();
        for(var record:PsseDyrRecordReader.read(new java.io.StringReader(text),"fixture")) {
            if(record.canonicalModelName().equals("REPCA1"))continue;
            if(record.canonicalModelName().equals("GENCLS")){result.append("2 'GENCLS' '1' 99999 0 /\n");continue;}
            var fields=new java.util.ArrayList<>(record.fields());
            if(record.canonicalModelName().equals("REECA1")) {
                fields.set(record.parameterOffset()+31,"0");fields.set(record.parameterOffset()+32,"0.04");
            }
            result.append(String.join(" ",fields)).append(" /\n");
        }
        Path file=temp.resolve("input.dyr");Files.writeString(file,result);return file;
    }

    private com.interpss.simu.SimuContext load(PSSEMultiFileLoader loader,Path dyr) throws Exception {
        IpssCorePlugin.init();return loader.loadDStab(DATA.resolve("SMIB/SMIB_v33_wt3g2.raw").toString(),dyr.toString());
    }

    @Test void defaultsAutomaticallySwapInvertedLimitsWithoutEditingSource() throws Exception {
        Path file=input();byte[] original=Files.readAllBytes(file);
        var corrected=new PSSEMultiFileLoader();
        var context=load(corrected,file);
        assertTrue(corrected.getLastDynamicImportReport().isStrictlyComplete(),corrected.getLastDynamicImportReport().failureSummary());
        var entry=corrected.getLastDynamicImportReport().entries().stream().filter(e->e.canonicalModelName().equals("REECA1")).findFirst().orElseThrow();
        assertTrue(entry.message().contains("parameter[31]: 0 -> 0.04"));
        assertTrue(entry.message().contains("parameter[32]: 0.04 -> 0"));
        assertTrue(entry.message().contains("automatic parameter corrections"));
        assertFalse(entry.message().contains("explicit parameter overrides"));
        assertEquals(2,entry.startLine());assertArrayEquals(original,Files.readAllBytes(file));
        var gen=(com.interpss.dstab.DStabGen)context.getDStabilityNet().getDStabBus("Bus1").getContributeGen("1");
        var controller=(Reeca1Model)((Regca1Model)gen.getDynamicGenDevice()).getActiveElectricalController();
        assertEquals(.04,controller.getData().pmax(),0);assertEquals(0,controller.getData().pmin(),0);
        controller.initialize(.5,.1,1);
        assertEquals(.5,controller.getEffectiveActivePowerMaximum(),0,
                "A corrected range still accommodates the solved initial power through existing runtime limits");
    }

    @Test void overridesCannotBypassValidationOrChangeShape() throws Exception {
        Path file=input();
        for(Map<Integer,String> changes:java.util.List.of(Map.of(8,"-1"),Map.of(51,"1"),Map.of(-1,"1"),Map.of(31,"NaN"))) {
            var loader=new PSSEMultiFileLoader().setDynamicModelParameterOverrides(r->r.canonicalModelName().equals("REECA1")?changes:Map.of());
            load(loader,file);assertEquals(1,loader.getLastDynamicImportReport().count(DynamicModelImportStatus.ERROR));
        }
    }

    @Test void strictDirectImportAlsoAppliesTheAuditedSwap() throws Exception {
        Path file=input();IpssCorePlugin.init();
        var context=new PSSEMultiFileLoader().loadDStab(DATA.resolve("SMIB/SMIB_v33_wt3g2.raw").toString());
        var parser=new org.interpss.fadapter.psse.PSSEDStabDirectParser(
                new org.interpss.fadapter.builder.DStabNetworkBuilder(context.getDStabilityNet())).setStrictImport(true);
        parser.parseDynFile(file.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(1,parser.getLastImportReport().entries().stream()
                .filter(e->e.message().contains("automatic parameter corrections")).count());
    }

    @Test void explicitOverridesPrecedeAutomaticNormalizationAndBothAuditsSurvive() throws Exception {
        Path file=input();
        var loader=new PSSEMultiFileLoader().setDynamicModelParameterOverrides(r->
                r.canonicalModelName().equals("REECA1")?Map.of(31,"-0.1"):Map.of());
        load(loader,file);
        var report=loader.getLastDynamicImportReport();assertTrue(report.isStrictlyComplete());
        var message=report.entries().stream().filter(e->e.canonicalModelName().equals("REECA1")).findFirst().orElseThrow().message();
        assertTrue(message.contains("explicit parameter overrides"));assertTrue(message.contains("parameter[31]: 0 -> -0.1"));
        assertTrue(message.contains("automatic parameter corrections"));assertTrue(message.contains("parameter[31]: -0.1 -> 0.04"));
    }

    @Test void validAndEqualLimitsAreUnchangedAndNonfiniteLimitsAreRejected() throws Exception {
        Path file=input();String original=Files.readString(file);
        for(String pair:new String[]{"0.2 -0.1","0.04 0.04","NaN 0.04","Infinity 0.04","0 -Infinity"}) {
            var fields=PsseDyrRecordReader.read(file).stream().filter(r->r.canonicalModelName().equals("REECA1")).findFirst().orElseThrow();
            var values=new java.util.ArrayList<>(fields.fields());String[] bounds=pair.split(" ");
            values.set(34,bounds[0]);values.set(35,bounds[1]);
            String text=original.lines().map(line->line.contains("REECA1")?String.join(" ",values)+" /":line)
                    .collect(java.util.stream.Collectors.joining("\n"));
            Files.writeString(file,text);var loader=new PSSEMultiFileLoader();load(loader,file);
            boolean invalid=pair.contains("NaN")||pair.contains("Infinity");
            assertEquals(invalid?1:0,loader.getLastDynamicImportReport().count(DynamicModelImportStatus.ERROR));
            assertFalse(loader.getLastDynamicImportReport().entries().stream().anyMatch(e->e.message().contains("automatic parameter corrections")));
        }
    }

    @Test void sourceRecordsSharingALineHaveIndependentCorrectionDiagnostics() throws Exception {
        var rows=PsseDyrRecordReader.read(new java.io.StringReader("1 'GENCLS' '1' 3 0 / 2 'GENCLS' '1' 5 0 /"),"same-line");
        var source=rows.getFirst();var fields=new java.util.ArrayList<>(source.fields());fields.set(3,"4");
        var effective=new PsseDyrRecord(source.source(),source.startLine(),source.endLine(),source.rawText(),source.busNumber(),
                source.sourceModelName(),source.canonicalModelName(),source.deviceId(),fields,source.parameterOffset());
        var builder=DynamicModelImportReport.builder("same-line");builder.recordParameterOverrides(source,effective);
        builder.add(effective,DynamicModelImportStatus.ATTACHED,"attached");builder.add(rows.get(1),DynamicModelImportStatus.ATTACHED,"attached");
        assertTrue(builder.build().entries().getFirst().message().contains("3 -> 4"));
        assertEquals("attached",builder.build().entries().get(1).message());
    }

    @Test void wrapperCorrectionsRetainAllocationAndValidateTheEnabledPath() throws Exception {
        Path file=temp.resolve("plant.dyr");Files.writeString(file,"""
                1 'USRBUS' 'PLNTBU1' 504 0 7 28 7 15
                1 1 2 '1' 0 1 51
                .04 1 .1 0 .02 .7 0 0 .01 1 -1 0 0 1 -1
                0 .5 .00002 -.002 -.003 .2 1.1 1 -1 .02 20 20 100 /
                """);
        var originalLoader=new PSSEMultiFileLoader();load(originalLoader,file);
        assertTrue(originalLoader.getLastDynamicImportReport().isStrictlyComplete());
        assertTrue(originalLoader.getLastDynamicImportReport().entries().getFirst().message().contains("Fflag=51: active-power control disabled"));
        for(var changes:java.util.List.of(Map.of(0,"505"),Map.of(13,"-0.1"),Map.of(12,"1"))) {
            var loader=new PSSEMultiFileLoader().setDynamicModelParameterOverrides(r->changes);load(loader,file);
            assertEquals(1,loader.getLastDynamicImportReport().count(DynamicModelImportStatus.ERROR));
        }
        for(int flag:new int[]{0,1}) {
            var loader=new PSSEMultiFileLoader().setDynamicModelParameterOverrides(r->
                    Map.of(12,Integer.toString(flag),30,"0",32,"0.003",34,"-1.1"));
            load(loader,file);assertTrue(loader.getLastDynamicImportReport().isStrictlyComplete());
            assertTrue(loader.getLastDynamicImportReport().entries().getFirst().message().contains("51 -> "+flag));
        }
    }
}
