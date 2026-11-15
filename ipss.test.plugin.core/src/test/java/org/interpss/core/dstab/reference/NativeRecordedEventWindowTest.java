package org.interpss.core.dstab.reference;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecordedEventWindowTest {
    @Test void binaryClockEdgesRetainTheDeclaredOneStepWindow() {
        double event=.0500000119209;
        var events=NativeRecordedEventWindow.boundaries(List.of(new double[]{.0495000109076},
                new double[]{event},new double[]{event},new double[]{.0505000129342}),.0005,.05);
        assertTrue(NativeRecordedEventWindow.contains(.0495000109076,events,.0005));
        assertTrue(NativeRecordedEventWindow.contains(.0505000129342,events,.0005));
        assertFalse(NativeRecordedEventWindow.contains(.0510000139475,events,.0005));
    }
    @Test void clearanceAccumulationDoesNotMakeTheWindowAsymmetric() {
        double[] events={.100000113249};
        assertTrue(NativeRecordedEventWindow.contains(.0995001122355,events,.0005));
        assertTrue(NativeRecordedEventWindow.contains(.100500114262,events,.0005));
        assertFalse(NativeRecordedEventWindow.contains(.101000115275,events,.0005));
    }
    @Test void missingEventEvidenceIsRejected() {
        assertThrows(IllegalArgumentException.class,()->NativeRecordedEventWindow.boundaries(
                List.of(new double[]{0},new double[]{.05},new double[]{.1}),.0005,.05));
    }
    @Test void anotherDuplicateDoesNotStandInForTheScheduledEvent() {
        assertThrows(IllegalArgumentException.class,()->NativeRecordedEventWindow.boundaries(
                List.of(new double[]{0},new double[]{.2},new double[]{.2}),.0005,.05));
    }
}
