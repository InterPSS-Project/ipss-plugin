package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.interpss.core.dstab.reference.PowerWorldCsvRepeatability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PowerWorldCsvRepeatabilityTest {
    @TempDir Path directory;

    private static final String HEADER = "ObjectFields\n"
            + "ObjectType, PrimaryKey, SecondaryKey, Label, VariableName, ColHeader\n"
            + "Bus,1,,,TSVpu,V pu\nGenerator,1 1,,,TSSpeed,Speed\nEND\nResults,\"Fault\"\n";
    private static final String DATA = "0,1,1\n0.05,1,1\n0.05,0.2,1.001\n0.1,0.9,1.002\n";

    @Test
    void acceptsOnlyTheDocumentedTrailingEmptyFieldAndNewlineDifference() throws Exception {
        String old = HEADER + DATA.replace("\n", ",\n") + "END\n";
        var result = compare(old.replace("\n", "\r\n"), HEADER + DATA + "END\n");
        assertEquals(4, result.samples());
        assertEquals(2, result.channels());
        assertEquals(1, result.repeatedTimeSamples());
    }

    @Test
    void comparesNumericalValuesRatherThanDecimalFormatting() throws Exception {
        assertEquals(4, compare(HEADER + DATA + "END\n",
                HEADER + DATA.replace("0.2,", "2e-1,") + "END\n").samples());
    }

    @Test
    void rejectsAnyNumericalDifferenceWithoutATolerance() {
        reject(HEADER + DATA.replace("1.001", "1.001000000001") + "END\n");
    }

    @Test
    void rejectsMissingOrReorderedEventSideRows() {
        reject(HEADER + DATA.replace("0.05,1,1\n", "") + "END\n");
        reject(HEADER + DATA.replace("0.05,1,1\n0.05,0.2,1.001",
                "0.05,0.2,1.001\n0.05,1,1") + "END\n");
    }

    @Test
    void rejectsChangedMetadataAndContingency() {
        reject(HEADER.replace("TSVpu", "TSAngle") + DATA + "END\n");
        reject(HEADER.replace("\"Fault\"", "\"Other\"") + DATA + "END\n");
    }

    @Test
    void rejectsMissingChannelsAndExtraEmptyFields() {
        reject(HEADER + DATA.replace("0.05,0.2,1.001", "0.05,0.2") + "END\n");
        reject(HEADER + DATA.replace("0.05,0.2,1.001", "0.05,0.2,1.001,,") + "END\n");
    }

    @Test
    void rejectsNonfiniteAndIncompleteExportsEvenWhenBothFilesAgree() throws Exception {
        for (String invalid : new String[] { HEADER + DATA.replace("1.001", "NaN") + "END\n",
                HEADER + DATA.replace("1.001", "Infinity") + "END\n",
                HEADER + DATA, HEADER + "END\n", "ObjectFields\nEND\n" }) {
            assertThrows(IllegalArgumentException.class, () -> compare(invalid, invalid));
        }
    }

    @Test
    void rejectsDecreasingTimeEvenWhenBothFilesAgree() {
        String invalid = HEADER + DATA.replace("0.1,", "0.01,") + "END\n";
        assertThrows(IllegalArgumentException.class, () -> compare(invalid, invalid));
    }

    private void reject(String actual) {
        assertThrows(IllegalArgumentException.class, () -> compare(HEADER + DATA + "END\n", actual));
    }

    @Test void reportsEveryChannelWithoutWeakeningExactComparison()throws Exception {
        Path left=directory.resolve("diagnostic-old.csv"),right=directory.resolve("diagnostic-new.csv");
        Files.writeString(left,HEADER+DATA+"END\n");Files.writeString(right,HEADER+DATA.replace("0.2,1.001","0.3,1.004")+"END\n");
        var result=PowerWorldCsvRepeatability.differences(left,right);assertEquals(4,result.summary().samples());assertEquals(1,result.summary().repeatedTimeSamples());assertEquals(2,result.channels().size());
        assertEquals(1,result.channels().get(0).changedSamples());assertEquals(.1,result.channels().get(0).maximumAbsoluteError(),1e-15);assertEquals(.05,result.channels().get(0).timeOfMaximum());
        assertEquals(.003,result.channels().get(1).maximumAbsoluteError(),1e-15);
        assertThrows(UnsupportedOperationException.class,()->result.channels().clear());assertThrows(IllegalArgumentException.class,()->PowerWorldCsvRepeatability.compare(left,right));
    }

    @Test void diagnosticRefusesTimeRealignmentAndMalformedData()throws Exception {
        Path left=directory.resolve("left.csv"),right=directory.resolve("right.csv");Files.writeString(left,HEADER+DATA+"END\n");
        for(String data:new String[]{HEADER+DATA.replace("0.1,","0.11,")+"END\n",HEADER+DATA.replace("1.001","NaN")+"END\n",HEADER+DATA}) {
            Files.writeString(right,data);assertThrows(IllegalArgumentException.class,()->PowerWorldCsvRepeatability.differences(left,right));
        }
    }

    private static String hash(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}

    @Test void importsPinnedFullRowsWithDuplicateEventOccurrences()throws Exception {
        String text=HEADER+DATA+"END\n";Path path=directory.resolve("full.csv");Files.writeString(path,text.replace("\n","\r\n"));
        var trajectory=PowerWorldCsvRepeatability.trajectory(path,hash(text));assertEquals(4,trajectory.samples().size());assertEquals(0,trajectory.samples().get(1).occurrence());assertEquals(1,trajectory.samples().get(2).occurrence());assertEquals(.2,trajectory.samples().get(2).value(0));
        assertEquals(0,trajectory.fieldIndex("Bus","1","TSVpu"));assertEquals(1,trajectory.fieldIndex("Generator","1 1","TSSpeed"));
        double[] values=trajectory.samples().get(2).values();values[0]=99;assertEquals(.2,trajectory.samples().get(2).value(0));
        assertThrows(UnsupportedOperationException.class,()->trajectory.samples().clear());
    }

    @Test void pinnedImportRejectsHashAndMalformedNumericData()throws Exception {
        Path path=directory.resolve("bad.csv");String text=HEADER+DATA+"END\n";Files.writeString(path,text);
        assertThrows(IllegalArgumentException.class,()->PowerWorldCsvRepeatability.trajectory(path,"0".repeat(64)));
        for(String bad:new String[]{HEADER+DATA.replace("1.001","NaN")+"END\n",HEADER+DATA,HEADER+DATA.replace("0.05,0.2,1.001","0.05,0.2")+"END\n"}) {
            Files.writeString(path,bad);assertThrows(IllegalArgumentException.class,()->PowerWorldCsvRepeatability.trajectory(path,hash(bad)));
        }
    }

    @Test void quotedMetadataRetainsCommasAndEscapedQuotes()throws Exception {
        String text=HEADER.replace("Bus,1,,,TSVpu,V pu","Bus,1,\"name, with \"\"quotes\"\"\",,TSVpu,\"V, pu\"")+DATA+"END\n";Path path=directory.resolve("quoted.csv");Files.writeString(path,text);
        var trajectory=PowerWorldCsvRepeatability.trajectory(path,hash(text));assertEquals("name, with \"quotes\"",trajectory.fields().get(0).secondaryKey());assertEquals("V, pu",trajectory.fields().get(0).columnHeader());
    }

    @Test void importRejectsDuplicateAndInvalidFieldIdentities()throws Exception {
        Path path=directory.resolve("metadata.csv");
        for(String bad:new String[]{HEADER.replace("Generator,1 1,,,TSSpeed,Speed","Bus,1,,,TSVpu,V pu")+DATA+"END\n",HEADER.replace("Bus,1,,,TSVpu,V pu","Bus,,,,TSVpu,V pu")+DATA+"END\n",HEADER.replace("Bus,1,,,TSVpu,V pu","Bus,1,,,TSVpu,\"unterminated")+DATA+"END\n"}) {
            Files.writeString(path,bad);assertThrows(IllegalArgumentException.class,()->PowerWorldCsvRepeatability.trajectory(path,hash(bad)));
        }
    }

    @Test void tenFreshExciterFixturesRetainCompleteFiniteRowsAndEventOccurrences()throws Exception {
        for(String model:new String[]{"ac7c","ac8c","ac9c","dc1c","dc2c","ac8b","exac1a","st2c","st3c","st5c"}) {
            var reference=org.interpss.core.dstab.reference.FreshExciterPowerWorldReference.reference(model);
            assertEquals(2003,reference.samples().size(),model);assertEquals(2001,reference.postEventSamples().size(),model);
            assertEquals(2,reference.samples().stream().filter(row->row.occurrence()==1).count(),model);
            assertEquals(0,reference.samples().get(0).time());assertEquals(1,reference.samples().get(2002).time());
            for(var row:reference.samples())for(double value:row.values())org.junit.jupiter.api.Assertions.assertTrue(Double.isFinite(value),model);
            assertThrows(UnsupportedOperationException.class,()->reference.fields().clear());
        }
    }

    @Test void freshFixtureLookupNeverFallsBackToAnotherModel()throws Exception {
        for(String model:new String[]{"missing","AC7C","../ac7c","ac11c"})
            assertThrows(IllegalArgumentException.class,()->org.interpss.core.dstab.reference.FreshExciterPowerWorldReference.reference(model));
    }

    @Test void freshInputStagingRejectsMutationAndCollisionBeforeWriting()throws Exception {
        Path original=Path.of("testData/adpter/psse/v33/SMIB");Path staged=directory.resolve("staged");Files.createDirectories(staged);
        Path sentinel=staged.resolve("SMIB_v33_genrou_ac7c.dyr");Files.writeString(sentinel,"do not overwrite");
        assertThrows(IllegalArgumentException.class,()->org.interpss.core.dstab.reference.FreshExciterPowerWorldReference.stageInputs("ac7c",original,staged));
        assertEquals("do not overwrite",Files.readString(sentinel));org.junit.jupiter.api.Assertions.assertFalse(Files.exists(staged.resolve("SMIB_v33.raw")));
        Path altered=directory.resolve("altered");Files.createDirectories(altered);Files.writeString(altered.resolve("SMIB_v33.raw"),"changed raw");
        Path empty=directory.resolve("unwritten");
        assertThrows(IllegalArgumentException.class,()->org.interpss.core.dstab.reference.FreshExciterPowerWorldReference.stageInputs("ac7c",altered,empty));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(empty));
    }

    private PowerWorldCsvRepeatability.Summary compare(String expected, String actual) throws Exception {
        Path left = directory.resolve("expected.csv");
        Path right = directory.resolve("actual.csv");
        Files.writeString(left, expected);
        Files.writeString(right, actual);
        return PowerWorldCsvRepeatability.compare(left, right);
    }
}
