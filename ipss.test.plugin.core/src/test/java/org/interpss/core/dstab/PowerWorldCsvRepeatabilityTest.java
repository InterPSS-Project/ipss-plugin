package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

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

    private PowerWorldCsvRepeatability.Summary compare(String expected, String actual) throws Exception {
        Path left = directory.resolve("expected.csv");
        Path right = directory.resolve("actual.csv");
        Files.writeString(left, expected);
        Files.writeString(right, actual);
        return PowerWorldCsvRepeatability.compare(left, right);
    }
}
