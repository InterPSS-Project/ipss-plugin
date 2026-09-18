package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.interpss.dstab.renewable.Wtpta1Data;
import org.interpss.dstab.renewable.Wtpta1Model;
import org.interpss.dstab.renewable.Wttqa1Data;
import org.interpss.dstab.renewable.Wttqa1Model;
import org.interpss.fadapter.psse.dyr.PsseDyrRecord;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;

/** Generated behavioral coverage over every distinct Texas2k wind-control profile. */
public class Texas2kWindProfileCoverageTest {
    private static final Path ROOT = Path.of(System.getProperty("texas2k.dynamic.case.dir",
            Path.of("testData", "private", "texas2k").toString()));
    private static final List<String> CASES = List.of(
            "Texas2k_series24_case1_2016summerpeak/dynamic_models_case1.dyr",
            "Texas2k_series24_case2_2016lowload/dynamic_models_case2.dyr",
            "Texas2k_series24_case3_2024summerpeak/dynamic_models_case3.dyr",
            "Texas2k_series24_case4_2024lowload/dynamic_models_case4.dyr",
            "Texas2k_series24_case5_2024highrenewables/dynamic_models_case5.dyr",
            "Texas2k_series24_case6_2024lowloadwithgfm/dynamic_models_case6.dyr");

    @Test
    void everyDistinctWtpta1ProfileInitializesStepsAndRespectsLimits() throws Exception {
        Set<Wtpta1Data> profiles = new HashSet<>();
        int records = 0;
        for (PsseDyrRecord record : records()) {
            if (!record.canonicalModelName().equals("WTPTA1")) continue;
            records++;
            profiles.add(wtpta1(record));
        }
        assertEquals(524, records);
        assertEquals(94, profiles.size());

        for (Wtpta1Data data : profiles) {
            Wtpta1Model model = new Wtpta1Model(data);
            model.initialize(data.thetaMin(), 1.0);
            for (int i = 0; i < 20; i++) {
                double direction = i < 10 ? 1.0 : -1.0;
                model.step(.001, .6 + .1 * direction, .6, 1.0 + .02 * direction);
                assertTrue(Double.isFinite(model.getPitch()), data.toString());
                assertTrue(model.getPitch() >= data.thetaMin() - 1.0e-12, data.toString());
                assertTrue(model.getPitch() <= data.thetaMax() + 1.0e-12, data.toString());
            }
        }
    }

    @Test
    void everyDistinctWttqa1ProfileCoversItsFlagCurveAndLimits() throws Exception {
        Set<Wttqa1Data> profiles = new HashSet<>();
        int records = 0;
        for (PsseDyrRecord record : records()) {
            if (!record.canonicalModelName().equals("WTTQA1")) continue;
            records++;
            profiles.add(wttqa1(record));
        }
        assertEquals(524, records);
        assertEquals(81, profiles.size());
        assertEquals(Set.of(0, 1), profiles.stream()
                .map(Wttqa1Data::tFlag).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(0.0), profiles.stream()
                .map(Wttqa1Data::turbineMva).collect(java.util.stream.Collectors.toSet()),
                "all supplied Texas2k WTTQA1 records select the generator MVA base");

        for (Wttqa1Data data : profiles) {
            Wttqa1Model model = new Wttqa1Model(data);
            assertEquals(data.sp1(), model.speedForPower(data.p1()), 1.0e-12, data.toString());
            assertEquals(data.sp2(), model.speedForPower(data.p2()), 1.0e-12, data.toString());
            assertEquals(data.sp3(), model.speedForPower(data.p3()), 1.0e-12, data.toString());
            assertEquals(data.sp4(), model.speedForPower(data.p4()), 1.0e-12, data.toString());

            double power = (data.p2() + data.p3()) / 2.0;
            model.initialize(power);
            double speed = model.getSpeedReference();
            for (int i = 0; i < 20; i++) {
                double direction = i < 10 ? 1.0 : -1.0;
                model.step(.001, power + .01 * direction, speed - .01 * direction);
                assertTrue(Double.isFinite(model.getPref()), data.toString());
                assertTrue(model.getTorque() >= data.teMin() - 1.0e-12, data.toString());
                assertTrue(model.getTorque() <= data.teMax() + 1.0e-12, data.toString());
            }
        }
    }

    private static List<PsseDyrRecord> records() throws Exception {
        assumeTrue(Files.isDirectory(ROOT), "Missing private Texas2k case root: " + ROOT);
        java.util.ArrayList<PsseDyrRecord> records = new java.util.ArrayList<>();
        for (String relative : CASES) {
            Path path = ROOT.resolve(relative);
            assumeTrue(Files.isRegularFile(path), "Missing Texas2k DYR: " + path);
            records.addAll(PsseDyrRecordReader.read(path));
        }
        return records;
    }

    private static Wtpta1Data wtpta1(PsseDyrRecord record) {
        List<String> p = record.parameters();
        assertEquals(10, p.size(), record.source() + ":" + record.startLine());
        return new Wtpta1Data(d(p, 0), d(p, 1), d(p, 2), d(p, 3), d(p, 4),
                d(p, 5), d(p, 6), d(p, 7), d(p, 8), d(p, 9));
    }

    private static Wttqa1Data wttqa1(PsseDyrRecord record) {
        List<String> p = record.parameters();
        assertEquals(16, p.size(), record.source() + ":" + record.startLine());
        return new Wttqa1Data((int) d(p, 0), d(p, 1), d(p, 2), d(p, 3), d(p, 4),
                d(p, 5), d(p, 6), d(p, 7), d(p, 8), d(p, 9), d(p, 10),
                d(p, 11), d(p, 12), d(p, 13), d(p, 14), d(p, 15));
    }

    private static double d(List<String> parameters, int index) {
        return Double.parseDouble(parameters.get(index).replace('d', 'E').replace('D', 'E'));
    }
}
