package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.interpss.dstab.hvdc.model.Chvdc2TerminalPorts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Finite winding resistance with independently solved initial points and complete terminal trajectories. */
public class Chvdc2ResistanceReferenceTest {
    static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-resistance-20261104/captures.zip");
    @BeforeAll static void integrity()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent resistance controls");
        assertEquals("227a0258624eb44a7ace7cc1b328f6457f38676715d045aa958abcb55f5d855f",
            HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ARCHIVE))));
    }
    static Stream<Arguments> profiles(){return Stream.of("zero","rectifier","inverter")
        .flatMap(end->Stream.of("flat","fault").flatMap(profile->Stream.of(false,true).map(staggered->Arguments.of(end,profile,staggered))));}
    @ParameterizedTest @MethodSource("profiles")
    void completeResistanceTrajectory(String end,String profile,boolean staggered,@TempDir Path folder)throws Exception {
        var comparison=new Chvdc2TerminalReferenceTest();comparison.folder=folder;
        comparison.compare(ARCHIVE,end+"/"+profile+"/a",staggered,false,
            Chvdc2TerminalPorts.ProtectionClock.BINARY32,Chvdc2TerminalPorts.DEFAULT_LOCAL_STEPS);
    }
    @Test void zeroResistanceCaptureIsUnchangedFromRetainedControl()throws Exception {
        Path previous=Path.of("testData/private/verification-artifacts/chvdc2-controller-20261102/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(previous),"Retain original zero-resistance control");
        try(var old=new ZipFile(previous.toFile());var current=new ZipFile(ARCHIVE.toFile())) {
            assertArrayEquals(old.getInputStream(old.getEntry("stiff-legacy-inverter-fault-flags001-h0p0005-a/native.csv")).readAllBytes(),
                current.getInputStream(current.getEntry("zero/fault/a/native.csv")).readAllBytes());
        }
    }
    @ParameterizedTest @ValueSource(strings={"zero","rectifier","inverter"})
    void independentInitialLossAndColdRepeat(String end)throws Exception {
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            for(String profile:List.of("flat","fault")) {
                byte[] bytes=z.getInputStream(z.getEntry(end+"/"+profile+"/a/native.csv")).readAllBytes();
                assertArrayEquals(bytes,z.getInputStream(z.getEntry(end+"/"+profile+"/b/native.csv")).readAllBytes());
                var rows=Tiocr1ResetReferenceTest.csv(bytes);assertEquals(1005,rows.size());
                var row=rows.getFirst();double ir=row.get("VAR5"),ii=row.get("VAR7");
                assertEquals(ir,ii,.001);
                double windingLoss=end.equals("zero")?0:2*2*.5*(end.equals("rectifier")?ir*ir:ii*ii);
                assertEquals((5*ir*ir+windingLoss)/1e6,row.get("VAR9")+row.get("VAR11"),.0001,
                    "Independent initialized line plus winding loss in MW");
            }
        }
    }
}
