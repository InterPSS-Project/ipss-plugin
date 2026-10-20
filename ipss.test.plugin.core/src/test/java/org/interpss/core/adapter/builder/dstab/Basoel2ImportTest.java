package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.oel.psse.basoel2.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.PsseGnetIdvProcessor.GeneratorKey;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Strict documented-contract import; not coupled reference-tool acceptance. */
public class Basoel2ImportTest extends CorePluginTestSetup {
    @TempDir Path directory;
    private static final String AC="1 'AC1C' '1' 1 0 0 .2 .05 10 .3 20 -20 .4 .2 .5 .2 .1 1.1 .75 0 1 0 20 -20 99 0 /\n";
    private static final String FIELDS="7 -4 2 .017 .23 .67 3.1 .011 2.2 .022 1.3 .033 .019 1.047 .21";
    private static final Basoel2Data EXPECTED=new Basoel2Data(7,-4,2,.017,.23,.67,3.1,.011,2.2,.022,1.3,.033,.019,1.047,.21);
    private Path file(String records)throws Exception {
        Path path=directory.resolve("input-"+System.nanoTime()+".dyr");Files.writeString(path,records);return path;
    }
    private static String record(String fields){return "1 'BASOEL2' '1' "+fields+" /\n";}
    private static List<Basoel2Model> models(DStabNetworkBuilder b){
        return b.getDStabNetwork().getBus("Bus1").getDynamicBusDeviceList().stream()
            .filter(Basoel2Model.class::isInstance).map(Basoel2Model.class::cast).toList();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void preservesAllDistinctFieldsBeforeOrAfterExciter(boolean before)throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var parser=new PSSEDStabDirectParser(b).setStrictImport(true);
        String bas=record(FIELDS).replace(".67 ",".67\n");
        parser.parseDynFile(file(before?bas+AC:AC+bas).toString());
        assertEquals(1,models(b).size());assertEquals(EXPECTED,models(b).getFirst().getData());
        assertEquals(Basoel2Kernel.TimerSemantics.POWERWORLD_DOCUMENTED,models(b).getFirst().getTimerSemantics());
        var entry=parser.getLastImportReport().entries().stream().filter(e->e.parameterCount()==15).findFirst().orElseThrow();
        assertEquals(DynamicModelImportStatus.ATTACHED,entry.status());
        assertTrue(entry.message().contains("POWERWORLD_DOCUMENTED"));
        assertTrue(entry.message().contains("acceptance remains open"));
    }
    static Stream<String> invalidFields(){
        String[] original=FIELDS.split(" ");
        var fractional=IntStream.range(0,3).mapToObj(i->{var f=original.clone();f[i]="1.0";return String.join(" ",f);});
        var bad=Stream.of(new String[]{"2","3"},new String[]{"3","NaN"},new String[]{"4","Infinity"},
            new String[]{"3","-.01"},new String[]{"7","-.01"},new String[]{"9","-.01"},
            new String[]{"11","-.01"},new String[]{"12","-.01"}).map(p->{var f=original.clone();f[Integer.parseInt(p[0])]=p[1];return String.join(" ",f);});
        return Stream.concat(Stream.concat(fractional,bad),Stream.of(
            String.join(" ",java.util.Arrays.copyOf(original,14)),FIELDS+" 0",FIELDS+" 0 0 0 0 0 0 0 0 0 0 0 0"));
    }
    @ParameterizedTest @MethodSource("invalidFields")
    void rejectsWrongCardinalitySelectorTypeAndInvalidConstants(String fields)throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var parser=new PSSEDStabDirectParser(b).setStrictImport(true);
        Path path=file(AC+record(fields));assertThrows(Exception.class,()->parser.parseDynFile(path.toString()));
        assertTrue(models(b).isEmpty());assertFalse(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(1,parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }
    @ParameterizedTest @ValueSource(ints={0,2})
    void rejectsDisabledAndTakeoverHost(int location)throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var parser=new PSSEDStabDirectParser(b).setStrictImport(true);
        Path path=file(AC.replace("'1' 1 0","'1' "+location+" 0")+record(FIELDS));
        assertThrows(Exception.class,()->parser.parseDynFile(path.toString()));assertTrue(models(b).isEmpty());
    }
    @Test void duplicateDoesNotAcquireASecondPortAndParserReuseDoesNotReplayPendingRecords()throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var parser=new PSSEDStabDirectParser(b).setStrictImport(true);
        Path path=file(AC+record(FIELDS)+record(FIELDS));assertThrows(Exception.class,()->parser.parseDynFile(path.toString()));
        assertEquals(1,models(b).size());assertEquals(1,parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
        parser.setStrictImport(false).parseDynFile(file("").toString());
        assertTrue(parser.getLastImportReport().entries().isEmpty());assertEquals(1,models(b).size());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void preprocessingSkipsRemovedStackBeforeCardinalityValidation(boolean gnet)throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var parser=new PSSEDStabDirectParser(b).setStrictImport(true);
        var keys=List.of(new GeneratorKey("Bus1","1"));
        if(gnet)parser.setGnetRemovedGenerators(keys);else parser.setModelRemovedGenerators(keys);
        parser.parseDynFile(file(record("0")).toString());assertTrue(models(b).isEmpty());
        assertEquals(1,parser.getLastImportReport().count(gnet?DynamicModelImportStatus.SKIPPED_GNET:DynamicModelImportStatus.SKIPPED_MODEL_REMOVE));
    }
    @Test void missingTargetAndMissingHostCannotClaimAttachment()throws Exception {
        var b=DStabBuilderTestFixture.createWithMachine();var parser=new PSSEDStabDirectParser(b).setStrictImport(true);
        Path missing=file(record(FIELDS).replace("1 'BASOEL2'","9 'BASOEL2'"));
        assertThrows(Exception.class,()->parser.parseDynFile(missing.toString()));
        assertEquals(1,parser.getLastImportReport().count(DynamicModelImportStatus.MISSING_TARGET));
        Path noHost=file(record(FIELDS));assertThrows(Exception.class,()->parser.parseDynFile(noHost.toString()));
        assertEquals(1,parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));assertTrue(models(b).isEmpty());
    }
}
