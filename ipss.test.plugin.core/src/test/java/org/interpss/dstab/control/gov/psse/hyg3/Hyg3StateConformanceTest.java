package org.interpss.dstab.control.gov.psse.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent gain/time discriminators qualify observation coordinates without changing physical storage. */
public class Hyg3StateConformanceTest {
    @Test void independentCompensationRecurrencesDistinguishScaledStatesAndGainPlacement(@TempDir Path staged) throws Exception {
        var fixture = new Hyg3ReferenceFixture(staged);
        boolean nonunitGain = false; int compared = 0;
        for (var entry : fixture.matrix()) {
            if (!entry.path("repeat").asText().equals("a")) continue;
            Path root = fixture.stage(entry.path("tag").asText());
            var manifest = new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            var constants = manifest.path("initialized_constants");
            double kd = constants.get(6).asDouble(), k2 = constants.get(4).asDouble(), tf = constants.get(7).asDouble();
            double h = manifest.path("settings").path("step").asDouble();
            boolean doubleDerivative = manifest.path("settings").path("mode").asInt() == 0;
            var rows = rows(root.resolve("native.csv"));
            double[] largest = new double[3]; double wrongPlacement = 0.;
            double integralMaximum = 0., missingDirectMaximum = 0., integralRoundoff = 0.;
            double pref = constants.get(0).asDouble()*rows.getFirst().get("VAR0")
                    +constants.get(1).asDouble()*rows.getFirst().get("STATE5");
            for (int j = 2; j < rows.size()-1; j++) {
                var previous = rows.get(j-1); var current = rows.get(j); var next = rows.get(j+1);
                double time = current.get("time_s");
                // Qualify the smooth two-history recurrence, separately from event projection.
                if (!(time > .12 && time < .49) || Math.abs(next.get("time_s")-time-h) > h/20.) continue;
                for (int i = 0; i < (doubleDerivative ? 3 : 1); i++) {
                    String state = "STATE"+new int[]{2,7,8}[i];
                    double predictedChange = h*(1.5*rate(current, i, kd, k2, tf)-.5*rate(previous, i, kd, k2, tf));
                    largest[i] = Math.max(largest[i], Math.abs(next.get(state)-current.get(state)-predictedChange));
                }
                if (doubleDerivative && Math.abs(k2-1.) > .1) {
                    double now = (current.get("STATE0")/tf-current.get("STATE7"))/tf;
                    double old = (previous.get("STATE0")/tf-previous.get("STATE7"))/tf;
                    wrongPlacement = Math.max(wrongPlacement, Math.abs(next.get("STATE7")-current.get("STATE7")-h*(1.5*now-.5*old)));
                }
                if (doubleDerivative) {
                    double change = next.get("STATE1")-current.get("STATE1");
                    double predicted = h*(1.5*integralRate(current, constants, pref, true)-.5*integralRate(previous, constants, pref, true));
                    double missing = h*(1.5*integralRate(current, constants, pref, false)-.5*integralRate(previous, constants, pref, false));
                    double roundoff = 2.*Math.ulp(current.get("STATE1").floatValue());
                    assertTrue(Math.abs(change-predicted) < roundoff, "Integral recurrence including direct filtered speed");
                    integralMaximum = Math.max(integralMaximum, Math.abs(change-predicted));
                    missingDirectMaximum = Math.max(missingDirectMaximum, Math.abs(change-missing));
                    integralRoundoff = Math.max(integralRoundoff,roundoff);
                }
                compared++;
            }
            assertTrue(largest[0] < 2e-10, entry.path("tag").asText()+" derivative compensation "+largest[0]);
            if (doubleDerivative) {
                assertTrue(largest[1] < 8e-10, "First double-derivative compensation "+largest[1]);
                assertTrue(largest[2] < 3e-9, "Second double-derivative compensation "+largest[2]);
                if (Math.abs(k2-1.) > .1) {
                    assertTrue(wrongPlacement > 1e-6, "Nonunit gain must discriminate its placement"); nonunitGain = true;
                }
                assertTrue(missingDirectMaximum > integralRoundoff, "The capture must discriminate omission of direct speed");
            }
            System.out.println("HYG3 independent recurrences "+entry.path("tag").asText()+" compensation="
                    +Arrays.toString(largest)+" integral="+integralMaximum+" omitted_direct="+missingDirectMaximum);
        }
        assertTrue(nonunitGain); assertTrue(compared > 1000);
    }

    private static double rate(Map<String, Double> row, int section, double kd, double k2, double tf) {
        double input = row.get("STATE0");
        return switch (section) {
            case 0 -> (kd/tf*input-row.get("STATE2"))/tf;
            case 1 -> (k2/tf*input-row.get("STATE7"))/tf;
            default -> ((k2/tf*input-row.get("STATE7"))/tf-row.get("STATE8"))/tf;
        };
    }

    private static double integralRate(Map<String, Double> row, JsonNode constants, double pref, boolean direct) {
        double u = row.get("STATE0"), tf = constants.get(7).asDouble();
        double first = constants.get(6).asDouble()/tf*u-row.get("STATE2");
        double second = constants.get(4).asDouble()/(tf*tf)*u-row.get("STATE7")/tf-row.get("STATE8");
        return constants.get(5).asDouble()*(pref-(direct ? u : 0.)-first-second
                -constants.get(0).asDouble()*row.get("VAR0")-constants.get(1).asDouble()*row.get("STATE5"));
    }

    @ParameterizedTest @ValueSource(booleans = {false,true})
    void bothMethodsRetainCompletePublishedStateAndBoundaryComparisons(boolean staggered, @TempDir Path staged) throws Exception {
        org.interpss.IpssCorePlugin.init(); org.interpss.IpssCorePlugin.configureSparseSolver("java-klu");
        var fixture = new Hyg3ReferenceFixture(staged);
        Path output = Files.createTempDirectory(Path.of("target"), staggered ? "hyg3-state-staggered-" : "hyg3-state-euler-");
        compare(fixture, staggered, output, manifest -> {
            double duration = manifest.path("settings").path("duration").asDouble();
            return new ComparisonLimits(stateTolerances(staggered, duration), boundaryTolerances(staggered, duration));
        });
    }

    record ComparisonLimits(double[] states, double[] boundaries) { }

    static void compare(Hyg3ReferenceFixture fixture, boolean staggered, Path output,
            java.util.function.Function<JsonNode, ComparisonLimits> limits) throws Exception {
        compare(fixture, staggered, output, limits, entry -> true);
    }

    static void compare(Hyg3ReferenceFixture fixture, boolean staggered, Path output,
            java.util.function.Function<JsonNode, ComparisonLimits> limits,
            java.util.function.Predicate<JsonNode> include) throws Exception {
        System.out.println("HYG3 state comparison "+output.getFileName());
        var diagnostics = new LinkedHashMap<String, Object>();
        var failures = new ArrayList<String>();
        for (var entry : fixture.matrix()) {
            if (!entry.path("repeat").asText().equals("a")) continue;
            if (!include.test(entry)) continue;
            String tag = entry.path("tag").asText(); Path root = fixture.stage(tag);
            var manifest = new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
            double step = manifest.path("settings").path("step").asDouble(), duration = manifest.path("settings").path("duration").asDouble();
            var reference = rows(root.resolve("native.csv"));
            var actual = Hyg3SharedSmibTest.simulate(root.resolve("network.raw"), root.resolve("dynamic.dyr"), staggered, false, step, duration);
            var maximum = new LinkedHashMap<String, Double>(); var at = new LinkedHashMap<String, Double>();
            var squared = new LinkedHashMap<String, Double>(); var low = new LinkedHashMap<String, Double>(); var high = new LinkedHashMap<String, Double>();
            assertEquals(0., manifest.path("initialized_constants").get(14).asDouble(), 0., "This matrix bypasses backlash");
            for (String key : reference.getFirst().keySet()) if (!key.equals("time_s")) {
                maximum.put(key, 0.); at.put(key, 0.); squared.put(key, 0.); low.put(key, Double.POSITIVE_INFINITY); high.put(key, Double.NEGATIVE_INFINITY);
            }
            int frame = 0, startups = 0;
            long clockTick = 0; float referenceClock = 0.f;
            try (var raw = BenchmarkOutputGuard.newOutputStream(output.resolve(tag+".csv.gz"),
                    BenchmarkOutputGuard.estimateCsvBytes(reference.size(), actual.initial.values().size()+reference.getFirst().size()));
                    var gzip = new GZIPOutputStream(raw); var writer = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8))) {
                writer.write("reference_time_s,actual_time_s,sample_kind,"+String.join(",",actual.initial.values().keySet()));
                for (String key : reference.getFirst().keySet()) writer.write(",REFERENCE."+key); writer.write("\n");
                for (var expected : reference) {
                    double time = expected.get("time_s");
                    assertEquals(reference.getFirst().get("VAR2"),expected.get("VAR2"),0.,
                            "Inactive backlash input history stays initialized in the reference");
                    var row = time < 0. ? actual.initial : actual.frames.get(frame++);
                    if (time < 0.) startups++;
                    else {
                        long tick = Math.round(row.time()/step);
                        assertEquals(tick*step, row.time(), 1e-10, "Actual fixed physical grid");
                        assertTrue(tick >= clockTick && tick-clockTick <= 1, "Retain each grid step and interval-side duplicate");
                        while (clockTick < tick) { referenceClock += (float) step; clockTick++; }
                        assertEquals((double) referenceClock, time, 1e-10, "Explicit accumulated binary32 reference clock");
                    }
                    writer.write(time+","+row.time()+","+(time < 0. ? "STARTUP_HELD_INITIAL" : row.kind()));
                    for (double value : row.values().values()) { assertTrue(Double.isFinite(value)); writer.write(","+value); }
                    for (double value : expected.values()) writer.write(","+value); writer.write("\n");
                    for (String key : maximum.keySet()) {
                        String observed = key.startsWith("STATE") ? "PUBLISHED"+key.substring(5) : key;
                        assertTrue(row.values().containsKey(observed), "Every independent channel has an explicit observation");
                        double value = expected.get(key), error = row.values().get(observed)-value;
                        if (Math.abs(error) > maximum.get(key)) { maximum.put(key, Math.abs(error)); at.put(key,time); }
                        squared.put(key, squared.get(key)+error*error); low.put(key,Math.min(low.get(key),value)); high.put(key,Math.max(high.get(key),value));
                    }
                }
            }
            assertEquals(reference.size(), actual.frames.size()+startups, "Preserve every event-side sample");
            var normalized = new LinkedHashMap<String, Double>();
            for (String key : List.of("V1","V2","P_PU","Q_PU")) {
                double range = high.get(key)-low.get(key), rmse = Math.sqrt(squared.get(key)/reference.size());
                normalized.put(key, range == 0. ? rmse : rmse/range);
            }
            var result = new LinkedHashMap<String,Object>();
            result.put("maximum_absolute_error",maximum); result.put("time_of_maximum",at);
            result.put("voltage_range_nrmse",Map.of("V1",normalized.get("V1"),"V2",normalized.get("V2")));
            result.put("power_range_nrmse",Map.of("P_PU",normalized.get("P_PU"),"Q_PU",normalized.get("Q_PU")));
            result.put("rotor_rmse_deg",Math.sqrt(squared.get("ANGLE_DEG")/reference.size()));
            result.put("step_s",step); result.put("duration_s",duration);
            result.put("coordinate_policy","Published compensation coordinates compared explicitly; stored integration coordinates also retained");
            result.put("inactive_reference_history",Map.of("VAR2",reference.getFirst().get("VAR2")));
            result.put("backlash_input_policy","Compare accepted input history VAR2 and output VAR3 separately from the live BACKLASH_INPUT");
            var bounds = limits.apply(manifest);
            double[] stateTolerance = bounds.states();
            var stateBounds = new LinkedHashMap<String,Double>();
            for (int i = 0; i < stateTolerance.length; i++) {
                String key = "STATE"+i; stateBounds.put(key,stateTolerance[i]);
                if (!(maximum.get(key) < stateTolerance[i])) failures.add(tag+" "+key+" maximum "+maximum.get(key)+" exceeds "+stateTolerance[i]);
            }
            result.put("state_absolute_tolerance",stateBounds);
            String[] boundaryKeys = {"P_PU","Q_PU","PMECH","SPEED","A1","A2","VAR0","VAR1","VAR2","VAR3","VAR4"};
            double[] boundaryTolerance = bounds.boundaries(); var boundaryBounds = new LinkedHashMap<String,Double>();
            for (int i = 0; i < boundaryKeys.length; i++) {
                String key = boundaryKeys[i]; boundaryBounds.put(key,boundaryTolerance[i]);
                if (!(maximum.get(key) < boundaryTolerance[i])) failures.add(tag+" "+key+" maximum "+maximum.get(key)+" exceeds "+boundaryTolerance[i]);
            }
            result.put("boundary_absolute_tolerance",boundaryBounds);
            diagnostics.put(tag,result);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(),diagnostics);
            for (var value : normalized.entrySet()) if (!(value.getValue() < .001)) failures.add(tag+" "+value.getKey()+" NRMSE "+value.getValue());
            if (!((double) result.get("rotor_rmse_deg") < .01)) failures.add(tag+" rotor RMSE "+result.get("rotor_rmse_deg"));
        }
        assertFalse(diagnostics.isEmpty(), "A trajectory gate must exercise at least one complete capture");
        assertEquals(List.of(), failures);
    }

    private static double[] stateTolerances(boolean staggered, double duration) {
        if (duration == .5) return new double[]{3.5e-7,7e-6,4.2e-7,1.3e-5,8e-6,
                staggered ? 1.7e-5 : 1e-4,1e-5,1.8e-7,staggered ? 9e-7 : 1.3e-6};
        return staggered
                ? new double[]{7.5e-7,8e-6,1.1e-6,2.1e-5,6e-6,1.4e-4,6.5e-6,5e-6,3.3e-5}
                : new double[]{4.5e-6,1.6e-5,1.9e-6,6e-5,6.5e-6,9e-4,6.5e-6,9e-6,8.5e-5};
    }

    private static double[] boundaryTolerances(boolean staggered, double duration) {
        if (duration == .5) return staggered
                ? new double[]{5e-5,1.4e-5,4e-5,2.9e-7,.00045,.00015,7.5e-6,2.9e-7,1e-7,8e-6,6e-5}
                : new double[]{.00015,.0021,4e-5,1.7e-6,.0028,.00075,7.5e-6,1.7e-6,1e-7,8e-6,6e-5};
        return staggered
                ? new double[]{.00016,.00095,1e-5,2e-6,.0016,.0005,7.5e-6,2e-6,1e-7,6e-6,8.5e-6}
                : new double[]{.002,.014,1.3e-5,1.6e-5,.028,.008,1.6e-5,1.6e-5,1e-7,6.5e-6,2.3e-5};
    }

    static List<Map<String, Double>> rows(Path path) throws IOException {
        var lines = Files.readAllLines(path); String[] columns = lines.getFirst().split(","); var result = new ArrayList<Map<String,Double>>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i).split(","); assertEquals(columns.length,cells.length);
            var row = new LinkedHashMap<String,Double>();
            for (int j = 0; j < cells.length; j++) { double value = Double.parseDouble(cells[j]); assertTrue(Double.isFinite(value)); row.put(columns[j],value); }
            result.add(row);
        }
        return result;
    }
}
