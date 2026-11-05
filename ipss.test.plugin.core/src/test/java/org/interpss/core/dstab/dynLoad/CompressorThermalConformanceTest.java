package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.google.gson.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.interpss.IpssCorePlugin;
import org.interpss.test.TestConfig;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent thermal threshold, reset and restart-pending coupled controls. */
public class CompressorThermalConformanceTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "CompressorThermalConformanceTest");
    private static final Path ARCHIVE = Path.of(CONFIG.string("reference.archive",
            "testData/private/verification-artifacts/acmtblu1-protection-20261101/accepted.zip"));
    private static final String ARCHIVE_SHA = "f2464f2c2cd7c99cbc4f78ba5be6d298ae3904f75744963b853c8bd3f012c794";
    private static final Map<String, Acmtblu1SharedSmibTest.Reference> REFERENCES = new TreeMap<>();
    private static final Map<String, Map<String, Double>> INITIAL_ROWS = new TreeMap<>();
    private static final Map<String, double[]> THERMAL_CONSTANTS = new TreeMap<>();

    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE), "Retain the independent thermal controls");
        assertEquals(ARCHIVE_SHA, sha(Files.readAllBytes(ARCHIVE)));
        try (var z = new ZipFile(ARCHIVE.toFile())) {
            var manifest = JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : manifest.getAsJsonObject("files").entrySet())
                assertEquals(e.getValue().getAsString(), sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for (var e : manifest.getAsJsonObject("profiles").entrySet()) {
                if (!e.getKey().startsWith("thermal-")) continue;
                var p = e.getValue().getAsJsonObject(); String entry = p.get("entry").getAsString();
                byte[] raw = z.getInputStream(z.getEntry(entry)).readAllBytes();
                assertArrayEquals(raw, z.getInputStream(z.getEntry(entry.replace("-a-part", "-b-part"))).readAllBytes());
                String[] lines = new String(raw, StandardCharsets.UTF_8).strip().split("\\R"), keys = lines[0].split(",");
                assertEquals(51, keys.length); var rows = new ArrayList<Map<String, Double>>();
                int duplicates = 0; double previous = Double.NEGATIVE_INFINITY;
                for (int i = 1; i < lines.length; i++) {
                    String[] fields = lines[i].split(","); assertEquals(keys.length, fields.length);
                    var row = new LinkedHashMap<String, Double>();
                    for (int j = 0; j < keys.length; j++) {
                        double v = Double.parseDouble(fields[j]); assertTrue(Double.isFinite(v)); row.put(keys[j], v);
                    }
                    double time = row.get("time_s"); assertTrue(time >= previous);
                    if (time == previous) duplicates++; previous = time;
                    assertEquals(row.get("V2"), row.get("VAR0"), 0); rows.add(row);
                }
                assertEquals(p.get("rows").getAsInt(), rows.size()); assertEquals(4, duplicates);
                assertTrue(rows.getFirst().get("time_s") < 0);
                INITIAL_ROWS.put(e.getKey(), rows.getFirst());
                String dir = entry.substring(0, entry.lastIndexOf('/') + 1);
                var metadata = JsonParser.parseString(new String(z.getInputStream(z.getEntry(dir + "manifest.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                var constants=metadata.getAsJsonArray("initialized_constants");
                THERMAL_CONSTANTS.put(e.getKey(),new double[]{constants.get(28).getAsDouble(),constants.get(29).getAsDouble(),constants.get(27).getAsDouble(),constants.get(7).getAsDouble()});
                String dyr = new String(z.getInputStream(z.getEntry(dir + "dynamic.dyr")).readAllBytes(), StandardCharsets.UTF_8);
                REFERENCES.put(e.getKey(), new Acmtblu1SharedSmibTest.Reference(p.get("step_sec").getAsDouble(),
                        metadata.getAsJsonObject("settings").get("fault_x_pu").getAsDouble(),
                        metadata.getAsJsonArray("initialized_constants").get(4).getAsDouble(), dyr,
                        rows));
            }
        }
        assertEquals(16, REFERENCES.size());
    }
    @AfterAll static void release() { REFERENCES.clear(); INITIAL_ROWS.clear(); THERMAL_CONSTANTS.clear(); }
    static Stream<Arguments> methods() {
        return REFERENCES.keySet().stream().flatMap(n -> Stream.of(false, true).map(s -> Arguments.of(n, s)));
    }
    @ParameterizedTest @ValueSource(strings={"thermal-zero-thresholds-h0.0005", "thermal-zero-lower-h0.0005", "thermal-ramp-h0.0005"})
    void initialThermalSheddingRetainsSourceLoadOwnership(String name) throws Exception {
        var m = CompressorProtectionConformanceTest.motor(REFERENCES.get(name));
        var bus = m.getDStabBus();
        System.out.println("Thermal ownership " + name + ": init=" + m.getInitLoadPQ() + ", current=" + m.getLoadPQ()
                + ", static=" + bus.getInitConstPStaticLoad() + "/" + bus.getInitConstIStaticLoad() + "/" + bus.getInitConstZStaticLoad()
                + ", compensation=" + m.getCompensateShuntY());
        assertEquals(0, bus.getInitConstPStaticLoad().abs(), 1e-12);
        assertEquals(0, bus.getInitConstIStaticLoad().abs(), 1e-12);
        assertEquals(0, bus.getInitConstZStaticLoad().abs(), 1e-12);
        var fixed = ((com.interpss.dstab.BaseDStabNetwork<?, ?>) bus.getNetwork()).getCustomBusCurrInjHashtable().get(bus.getId());
        var control = CompressorProtectionConformanceTest.motor(REFERENCES.get("thermal-ramp-h0.0005"));
        var original = ((com.interpss.dstab.BaseDStabNetwork<?, ?>) control.getDStabBus().getNetwork())
                .getCustomBusCurrInjHashtable().get(bus.getId());
        assertEquals(original, fixed, "The operating-point residual must exclude physical initial shedding");
        var x = m.getIntegrationStates(); var h = m.getIntegrationHistories();
        assertFalse(m.acceptInitialEndpoint());
        m.acceptInitialNetworkEndpoint();
        assertEquals(x, m.getIntegrationStates()); assertEquals(h, m.getIntegrationHistories());
    }
    private static LD1PACImpl example() throws Exception {
        return CompressorProtectionConformanceTest.motor(REFERENCES.get("thermal-instant-zero-h0.0005"));
    }
    private static void acceptTemperatures(LD1PACImpl m, double a, double b) {
        double[] x = new double[m.staggeredStateCount()]; m.readStaggeredState(x, 0);
        x[2] = a; x[3] = b; m.applyStaggeredState(x, 0, 0);
        m.completeStaggeredStep(.0005); assertTrue(m.afterStep(.0005));
    }
    @ParameterizedTest @ValueSource(doubles={0, .1, .12})
    void instantThresholdEqualityResetsStatesAndCannotResurrectHeating(double upper) throws Exception {
        var m = example(); m.setTh1t(.12); m.setTh2t(upper);
        double[] x = new double[9], rates = new double[9]; m.readStaggeredState(x, 0);
        x[2] = x[3] = .12; m.applyStaggeredState(x, 0, 0);
        var states = m.getIntegrationStates(); var histories = m.getIntegrationHistories();
        for (int i = 0; i < 8; i++) m.evaluateStaggered(.0005, rates, 0);
        assertTrue(m.afterStep(0)); assertEquals(states, m.getIntegrationStates());
        assertEquals(histories, m.getIntegrationHistories());
        m.completeStaggeredStep(.0005); assertTrue(m.afterStep(.0005));
        for (String key : List.of("ThermalFractionA", "ThermalFractionB", "ElectricalHeatingSquaredA", "ElectricalHeatingSquaredB"))
            assertEquals(0., m.getIntegrationHistories().get(key), 0);
        // Even the unused negative derivative history of a two-step method
        // cannot move a projected, completely tripped temperature off zero.
        m.readStaggeredState(x, 0); x[2] = -.01; x[3] = .01;
        m.applyStaggeredState(x, 0, .0005);
        assertEquals(0., m.getIntegrationStates().get("TemperatureA"));
        assertEquals(0., m.getIntegrationStates().get("TemperatureB"));
        assertTrue(m.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(m.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, 1));
        m.evaluateStaggered(.0005, rates, 0); assertEquals(0, rates[2]); assertEquals(0, rates[3]);
        for (double magnitude : new double[]{.8, 1.05}) {
            var v = new Complex(magnitude, 0); m.getDStabBus().setVoltage(v);
            var current = v.multiply(m.getPosSeqEquivY()).subtract(m.getNortonCurInj());
            var power = v.multiply(current.conjugate());
            assertEquals(0, power.getReal(), 1e-14);
            assertEquals(-m.getCompensateShuntY().getImaginary() * magnitude * magnitude, power.getImaginary(), 1e-14);
        }
    }
    @Test void partialSheddingWhileRunningDoesNotRecoverDuringCooling() throws Exception {
        var m = example(); m.setTh1t(.12); m.setTh2t(.2);
        acceptTemperatures(m, .16, .16);
        assertEquals(1., m.getIntegrationHistories().get("RunA"));
        assertEquals(.5, m.getIntegrationHistories().get("ThermalFractionA"), 2e-15);
        assertEquals(.5, m.getIntegrationHistories().get("ThermalFractionB"), 2e-15);
        acceptTemperatures(m, .13, .13);
        assertEquals(.5, m.getIntegrationHistories().get("ThermalFractionA"), 2e-15);
        assertEquals(.5, m.getIntegrationHistories().get("ThermalFractionB"), 2e-15);
        assertTrue(m.initStates()); assertFalse(m.acceptInitialEndpoint());
        assertEquals(1., m.getIntegrationHistories().get("ThermalFractionA"));
    }
    @Test void groupBFreezesItsFractionWhilePendingRestartThenAcceptsTheCurrentCurve() throws Exception {
        var m = CompressorProtectionConformanceTest.motor(REFERENCES.get("thermal-partial-restart-h0.0005"));
        var bus = m.getDStabBus(); bus.setVoltage(new Complex(.1, 0)); m.acceptNetworkEventInput(.05);
        double[] x = new double[9]; m.readStaggeredState(x, 0);
        x[2] = x[3] = .13; x[6] = x[7] = m.getTstall() + .001;
        m.applyStaggeredState(x, 0, 0); m.completeStaggeredStep(.0005); assertTrue(m.afterStep(.0005));
        assertEquals(0., m.getIntegrationHistories().get("RunB"));
        double original = m.getIntegrationHistories().get("ThermalFractionB");
        bus.setVoltage(Complex.ONE); m.acceptNetworkEventInput(.1);
        m.readStaggeredState(x, 0); x[2] = x[3] = .2; x[8] = .005;
        m.applyStaggeredState(x, 0, 0); m.completeStaggeredStep(.0005); assertTrue(m.afterStep(.0005));
        assertEquals(original, m.getIntegrationHistories().get("ThermalFractionB"), 0);
        assertTrue(m.getIntegrationHistories().get("ThermalFractionA") < original);
        m.readStaggeredState(x, 0); x[2] = x[3] = .25; x[8] = m.getTrst() + .001;
        m.applyStaggeredState(x, 0, 0); m.completeStaggeredStep(.0005); assertTrue(m.afterStep(.0005));
        assertEquals(1., m.getIntegrationHistories().get("RunB"));
        assertEquals(0., m.getIntegrationHistories().get("RestartTimer"));
        assertEquals((m.getTh2t() - .25) / (m.getTh2t() - m.getTh1t()),
                m.getIntegrationHistories().get("ThermalFractionB"), 2e-15);
    }
    @Test void zeroLowerThresholdMatchesTheIndependentPreIntegrationThermalState() throws Exception {
        String name = "thermal-zero-lower-h0.0005";
        var m = CompressorProtectionConformanceTest.motor(REFERENCES.get(name)); var source = INITIAL_ROWS.get(name);
        assertEquals(source.get("STATE2"), m.getIntegrationStates().get("TemperatureA"), 1e-7);
        assertEquals(source.get("VAR14"), m.getIntegrationHistories().get("ThermalFractionA"), 2e-7);
        assertEquals(source.get("VAR18"), m.getIntegrationHistories().get("ThermalFractionB"), 2e-7);
    }
    @Test void fullThermalTripBlocksRestartButKeepsTheAllocatedTimerActive() throws Exception {
        var m=example();m.setVrst(.85);var bus=m.getDStabBus();bus.setVoltage(new Complex(.1,0));m.acceptNetworkEventInput(.05);
        double[] x=new double[9];m.readStaggeredState(x,0);x[2]=x[3]=.12;x[6]=x[7]=m.getTstall()+.001;
        m.applyStaggeredState(x,0,0);m.completeStaggeredStep(.0005);assertTrue(m.afterStep(.0005));
        assertEquals(0.,m.getIntegrationHistories().get("ThermalFractionB"));
        bus.setVoltage(Complex.ONE);m.acceptNetworkEventInput(.1);m.readStaggeredState(x,0);x[8]=.025;
        m.applyStaggeredState(x,0,0);m.completeStaggeredStep(.0005);assertTrue(m.afterStep(.0005));
        assertEquals(0.,m.getIntegrationHistories().get("RunB"));
        assertEquals((double)(float).025,m.getIntegrationStates().get("RestartTimer"),0);
        m.evaluateStaggered(.0005,x,0);assertEquals(1,x[8]);
    }
    private static final class Error {
        double maximum, sum, time; int count;
        void add(double actual, double expected, double at) {
            double d=actual-expected; assertTrue(Double.isFinite(d));sum+=d*d;count++;
            if(Math.abs(d)>maximum){maximum=Math.abs(d);time=at;}
        }
        Map<String,Object> report(){return Map.of("maximum_absolute",maximum,"rmse",Math.sqrt(sum/count),"source_time_of_maximum",time,"samples",count);}
    }
    private static Complex voltage(Map<String,Double> row) {
        double phase=Math.toRadians(row.get("A2")),v=row.get("V2");return new Complex(v*Math.cos(phase),v*Math.sin(phase));
    }
    private static double thermalMethodBound(String name) {
        var ref=REFERENCES.get(name);var c=THERMAL_CONSTANTS.get(name);double maximum=0;
        // A stable one-pole input response bounds first-order method/staging
        // differences by h/tau times input total variation. Include the initial
        // imbalance; this also covers the native two-step startup recurrence.
        for(int g=0;g<2;g++) {
            double prior=Math.pow(ref.rows().getFirst().get("VAR"+(19+g)),2)*c[3];
            double variation=Math.abs(prior-ref.rows().getFirst().get("STATE"+(2+g)));
            for(var r:ref.rows()){double u=Math.pow(r.get("VAR"+(19+g)),2)*c[3];variation+=Math.abs(u-prior);prior=u;}
            maximum=Math.max(maximum,ref.h()/c[2]*variation);
        }
        return maximum+2.5e-6;
    }
    private static List<double[]> transitions(List<Map<String,Double>> rows,String timeKey,String key,double h) {
        var result=new ArrayList<double[]>();Double previous=null;
        for(var r:rows){double value=r.get(key);if(previous==null||value!=previous){result.add(new double[]{Math.round((r.get(timeKey)+2*h)/h),value});previous=value;}}
        return result;
    }
    private static void runActions(Acmtblu1SharedSmibTest.Reference ref,List<Map<String,Double>> rows,int gridAllowance) {
        for(int g=0;g<2;g++) {
            var expected=transitions(ref.rows(),"time_s","VAR"+(16+g),ref.h());
            var actual=transitions(rows,rows.getFirst().containsKey("replay_time_s")?"replay_time_s":"time_s","Run"+"AB".substring(g,g+1),ref.h());
            assertEquals(expected.size(),actual.size(),"Every run/stall action must remain represented");
            for(int i=0;i<expected.size();i++) {
                assertEquals(expected.get(i)[1],actual.get(i)[1],0);
                assertTrue(Math.abs(expected.get(i)[0]-actual.get(i)[0])<=gridAllowance,"Run/stall action exceeds the method's physical-grid allowance");
            }
        }
    }
    @ParameterizedTest @MethodSource("methods")
    void recordedVoltageAndFrequencyQualifyIndependentThermalIntegration(String name, boolean shared) throws Exception {
        var ref=REFERENCES.get(name);var m=CompressorProtectionConformanceTest.motor(ref);var bus=m.getDStabBus();
        double[] x=new double[9],rates=new double[9],history=new double[9],left=new double[9];m.readStaggeredState(x,0);
        var errors=new LinkedHashMap<String,Error>();
        String[] states={"TemperatureA","TemperatureB","UvTimer1","UvTimer2","StallTimer","StallTimerB","RestartTimer"};
        for(String key:states)errors.put(key,new Error());
        for(String key:List.of("ThermalFractionA","ThermalFractionB","MotorCurrentA","MotorCurrentB","P","Q"))errors.put(key,new Error());
        var rows=new ArrayList<Map<String,Double>>();int grid=0;boolean event=false;double[] priorFraction={1,1};
        double[] sourceTrip={Double.NaN,Double.NaN},actualTrip={Double.NaN,Double.NaN};
        double maximumHeunError=0;
        for(var source:ref.rows()) {
            double time=source.get("time_s");int next=(int)Math.round((time+2*ref.h())/ref.h());
            if(!rows.isEmpty()&&next>grid) {
                assertEquals(grid+1,next);var before=m.getIntegrationStates();var input=m.getIntegrationHistories();
                if(shared) {
                    m.evaluateStaggered(ref.h(),rates,0);
                    for(int i=2;i<9;i++) {
                        double rate=event?.5*(rates[i]+left[i]):rates[i];
                        x[i]+=ref.h()*(1.5*rate-.5*history[i]);history[i]=rate;
                    }
                    m.applyStaggeredState(x,0,ref.h());
                } else {
                    assertTrue(m.nextStep(ref.h(),DynamicSimuMethod.MODIFIED_EULER,0));
                    assertTrue(m.nextStep(ref.h(),DynamicSimuMethod.MODIFIED_EULER,1));m.readStaggeredState(x,0);
                    for(int g=0;g<2;g++) {
                        String group="AB".substring(g,g+1);double a=ref.h()/m.getTth(),old=before.get("Temperature"+group);
                        double forcing=input.get("ElectricalHeatingSquared"+group)*m.getRstall();
                        double expected=old+(a-.5*a*a)*(forcing-old);
                        maximumHeunError=Math.max(maximumHeunError,Math.abs(x[2+g]-expected));
                        assertEquals(expected,x[2+g],2e-15,"Held-input Heun equation before accepted thermal projection");
                    }
                }
                bus.setVoltage(voltage(source));bus.setFreq(source.get("VAR1"));x[0]=source.get("STATE0");x[1]=source.get("VAR1");m.applyStaggeredState(x,0,0);
                if(shared)m.completeStaggeredStep(ref.h());assertTrue(m.afterStep(ref.h()));m.readStaggeredState(x,0);
                grid=next;event=false;
            } else {
                if(!rows.isEmpty())m.evaluateStaggered(ref.h(),left,0);
                bus.setVoltage(voltage(source));bus.setFreq(source.get("VAR1"));x[0]=source.get("STATE0");x[1]=source.get("VAR1");m.applyStaggeredState(x,0,0);
                if(!rows.isEmpty()){m.acceptNetworkEventInput(time);assertTrue(m.readNetworkEventLeftRates(left,0));m.readStaggeredState(x,0);event=true;}
            }
            var actual=m.getIntegrationStates();var h=m.getIntegrationHistories();
            var v=bus.getVoltage();var current=v.multiply(m.getPosSeqEquivY()).subtract(m.getNortonCurInj());
            var power=v.multiply(current.conjugate()).multiply(bus.getNetwork().getBaseMva()/m.getMvaBase());
            var row=new LinkedHashMap<String,Double>();row.put("source_time_s",time);row.put("replay_time_s",-2*ref.h()+grid*ref.h());
            for(int i=0;i<states.length;i++) {
                String key=states[i];double value=actual.get(key),expected=source.get("STATE"+(i+2));
                errors.get(key).add(value,expected,time);row.put(key,value);row.put("source_"+key,expected);
            }
            for(int g=0;g<2;g++) {
                String group="AB".substring(g,g+1),key="ThermalFraction"+group;double fraction=h.get(key),expected=source.get(g==0?"VAR14":"VAR18");
                assertTrue(fraction>=0&&fraction<=priorFraction[g]);priorFraction[g]=fraction;
                if(expected==0&&Double.isNaN(sourceTrip[g]))sourceTrip[g]=grid;
                if(fraction==0&&Double.isNaN(actualTrip[g]))actualTrip[g]=grid;
                if(fraction==0){assertEquals(0.,actual.get("Temperature"+group),0);assertEquals(0.,h.get("ElectricalHeatingSquared"+group),0);}
                errors.get(key).add(fraction,expected,time);row.put(key,fraction);row.put("source_"+key,expected);
                key="MotorCurrent"+group;double rms=Math.sqrt(h.get("ElectricalHeatingSquared"+group));expected=source.get("VAR"+(19+g));
                errors.get(key).add(rms,expected,time);row.put(key,rms);row.put("source_"+key,expected);
                row.put("Run"+group,h.get("Run"+group));row.put("source_Run"+group,source.get("VAR"+(16+g)));
            }
            errors.get("P").add(power.getReal(),source.get("VAR2")/source.get("VAR26"),time);
            errors.get("Q").add(power.getImaginary(),source.get("VAR3")/source.get("VAR26"),time);
            row.put("P",power.getReal());row.put("Q",power.getImaginary());
            row.put("source_P",source.get("VAR2")/source.get("VAR26"));row.put("source_Q",source.get("VAR3")/source.get("VAR26"));
            var saved=m.getIntegrationStates();var savedHistory=m.getIntegrationHistories();m.evaluateStaggered(ref.h(),rates,0);assertTrue(m.afterStep(0));
            assertEquals(saved,m.getIntegrationStates());assertEquals(savedHistory,m.getIntegrationHistories());rows.add(row);
        }
        var report=new LinkedHashMap<String,Object>();report.put("status","independent-thermal-integration-voltage-and-frequency-fed-not-coupled-acceptance");report.put("profile",name);report.put("method",shared?"staggered":"modified-euler");report.put("maximum_heun_equation_error",maximumHeunError);
        for(var e:errors.entrySet())report.put(e.getKey(),e.getValue().report());
        Path out=Files.createTempDirectory(Path.of("target"),"compressor-thermal-replay-");
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("comparison.json"),100000)){new GsonBuilder().setPrettyPrinting().create().toJson(report,w);}
        var keys=rows.getFirst().keySet();
        try(var w=BenchmarkOutputGuard.newWriter(out.resolve("actual.csv"),BenchmarkOutputGuard.estimateCsvBytes(rows.size(),keys.size()))) {
            w.write(String.join(",",keys)+"\n");for(var row:rows){boolean first=true;for(String key:keys){if(!first)w.write(",");first=false;w.write(row.get(key).toString());}w.write("\n");}
        }
        for(int g=0;g<2;g++){assertEquals(Double.isNaN(sourceTrip[g]),Double.isNaN(actualTrip[g]));if(!Double.isNaN(sourceTrip[g]))assertTrue(Math.abs(sourceTrip[g]-actualTrip[g])<=(shared?0:1),"Complete thermal trip on the declared integration grid");}
        runActions(ref,rows,shared?0:1);
        if(shared) {
            for(String key:List.of("TemperatureA","TemperatureB","MotorCurrentA","MotorCurrentB"))assertTrue(errors.get(key).maximum<=2.5e-6,key+": "+errors.get(key).report());
            for(String key:List.of("ThermalFractionA","ThermalFractionB","P","Q"))assertTrue(errors.get(key).maximum<=5e-5,key+": "+errors.get(key).report());
            for(String key:List.of("UvTimer1","UvTimer2","StallTimer","StallTimerB","RestartTimer"))assertTrue(errors.get(key).maximum<=2e-8,key+": "+errors.get(key).report());
        } else {
            double temperatureBound=thermalMethodBound(name);
            for(String key:List.of("TemperatureA","TemperatureB"))assertTrue(errors.get(key).maximum<=temperatureBound,key+": "+errors.get(key).report());
            var c=THERMAL_CONSTANTS.get(name);
            if(c[1]>c[0])for(String key:List.of("ThermalFractionA","ThermalFractionB"))assertTrue(errors.get(key).maximum<=temperatureBound/(c[1]-c[0])+2e-7,key+": "+errors.get(key).report());
        }
    }
    private static Map<String,Object> coupledControl(String name, boolean shared) throws Exception {
        var ref=REFERENCES.get(name);Path out=Files.createTempDirectory(Path.of("target"), "compressor-thermal-coupled-");
        var report = Acmtblu1SharedSmibTest.runReference(name, ref, shared,out, shared, 2, shared);
        var errors = (Map<?, ?>) report.get("state_errors");
        if(shared)for (String key : List.of("ThermalFractionA", "ThermalFractionB"))
            assertTrue(((Number) ((Map<?, ?>) errors.get(key)).get("maximum_absolute")).doubleValue() <= 5e-5,
                    key + ": " + errors.get(key));
        if(!shared) {
            double scale=ref.h()/.0005;var boundary=(Map<?,?>)report.get("boundary_errors");var limits=new LinkedHashMap<String,Double>();
            for(String key:List.of("V1","V2","V3","P_PU","Q_PU","ANGLE_DEG","SPEED")) {
                double bound=scale*(key.equals("ANGLE_DEG")?.01:key.equals("SPEED")?5e-6:key.startsWith("V")?1e-4:1e-3);
                String norm=key.equals("ANGLE_DEG")||key.equals("SPEED")?"rmse":"range_normalized_rmse";
                double actual=((Number)((Map<?,?>)boundary.get(key)).get(norm)).doubleValue();
                limits.put(key+"_"+norm,bound);assertTrue(actual<=bound,key+": "+boundary.get(key));
            }
            double temperatureBound=thermalMethodBound(name);var c=THERMAL_CONSTANTS.get(name);
            for(String key:List.of("TemperatureA","TemperatureB"))assertTrue(((Number)((Map<?,?>)errors.get(key)).get("maximum_absolute")).doubleValue()<=temperatureBound,key+": "+errors.get(key));
            if(c[1]>c[0])for(String key:List.of("ThermalFractionA","ThermalFractionB"))assertTrue(((Number)((Map<?,?>)errors.get(key)).get("maximum_absolute")).doubleValue()<=temperatureBound/(c[1]-c[0])+2e-7,key+": "+errors.get(key));
            var actualRows=new ArrayList<Map<String,Double>>();String[] lines=Files.readString(out.resolve("actual.csv")).strip().split("\\R"),keys=lines[0].split(",");
            for(int i=1;i<lines.length;i++){String[] fields=lines[i].split(",");var row=new LinkedHashMap<String,Double>();for(int j=0;j<keys.length;j++)row.put(keys[j],Double.parseDouble(fields[j]));actualRows.add(row);}
            runActions(ref,actualRows,1);
            var gate=Map.of("status","modified-euler-finite-step-thermal-and-coupled-bounds","step_sec",ref.h(),"trajectory_limits",limits,"temperature_maximum_bound",temperatureBound,"thermal_fraction_maximum_bound",c[1]>c[0]?temperatureBound/(c[1]-c[0])+2e-7:1.,"heun_equation_checked_separately",true);
            try(var w=BenchmarkOutputGuard.newWriter(out.resolve("method-gate.json"),100000)){new GsonBuilder().setPrettyPrinting().create().toJson(gate,w);}
        }
        return report;
    }
    static Stream<Arguments> coupledMethods() {
        return REFERENCES.keySet().stream().map(n->n.substring(0,n.lastIndexOf("-h"))).distinct()
                .flatMap(n->Stream.of(false,true).map(s->Arguments.of(n,s)));
    }
    @ParameterizedTest @MethodSource("coupledMethods")
    void importedThermalThresholdsAndHistoriesFollowTheIndependentCoupledControl(String name, boolean shared) throws Exception {
        var coarse=coupledControl(name+"-h0.0005",shared);var fine=coupledControl(name+"-h0.00025",shared);
        if(!shared) {
            for(String key:List.of("V1","V2","V3","P_PU","Q_PU","ANGLE_DEG","SPEED")) {
                double a=((Number)((Map<?,?>)((Map<?,?>)coarse.get("boundary_errors")).get(key)).get("rmse")).doubleValue();
                double b=((Number)((Map<?,?>)((Map<?,?>)fine.get("boundary_errors")).get(key)).get("rmse")).doubleValue();
                double noise=key.equals("ANGLE_DEG")?2e-5:key.equals("SPEED")?2e-8:2e-7;
                assertTrue(b<=.75*a+noise,"Step refinement must reduce "+key+" error: coarse="+a+", fine="+b);
            }
            for(String key:List.of("TemperatureA","TemperatureB","ThermalFractionA","ThermalFractionB")) {
                double a=((Number)((Map<?,?>)((Map<?,?>)coarse.get("state_errors")).get(key)).get("rmse")).doubleValue();
                double b=((Number)((Map<?,?>)((Map<?,?>)fine.get("state_errors")).get(key)).get("rmse")).doubleValue();
                assertTrue(b<=.75*a+2.5e-6,"Step refinement must reduce "+key+" error: coarse="+a+", fine="+b);
            }
        }
    }
    /** Explicit complete-recording diagnostic, including generator states; no acceptance claim. */
    public static void main(String[] args) throws Exception {
        boolean hold=args.length>1&&Boolean.parseBoolean(args[1]);double tolerance=args.length>2?Double.parseDouble(args[2]):1e-4;
        setup();
        Path archive=Path.of("testData/private/verification-artifacts/acmtblu1-thermal-20261101/generator-captures.zip");
        assertEquals("87825634a489b5b07f04e31f172c95a2ccabe22f31a8a64879c8a482f6bda024",sha(Files.readAllBytes(archive)));
        try(var z=new ZipFile(archive.toFile())) {
            var manifest=JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var profile:manifest.getAsJsonObject("profiles").entrySet()) {
                double h=Double.parseDouble(profile.getKey());String entry=profile.getValue().getAsJsonObject().get("entry").getAsString();
                String[] lines=new String(z.getInputStream(z.getEntry(entry)).readAllBytes(),StandardCharsets.UTF_8).strip().split("\\R"),keys=lines[0].split(",");
                var rows=new ArrayList<Map<String,Double>>();
                for(int i=1;i<lines.length;i++){String[] fields=lines[i].split(",");assertEquals(keys.length,fields.length);var row=new LinkedHashMap<String,Double>();for(int j=0;j<keys.length;j++)row.put(keys[j],Double.parseDouble(fields[j]));rows.add(row);}
                var original=REFERENCES.get("thermal-instant-zero-h"+java.math.BigDecimal.valueOf(h).stripTrailingZeros().toPlainString());var reference=new Acmtblu1SharedSmibTest.Reference(h,original.fault(),original.loading(),original.dyr(),rows);
                for(boolean shared:new boolean[]{false,true})Acmtblu1SharedSmibTest.runReference("thermal-instant-zero-generator-h"+h,reference,shared,Path.of(args[0]).resolve("h"+h+(shared?"-staggered":"-modified-euler")),false,new Acmtblu1SharedSmibTest.RunSettings(2,hold,tolerance,true));
            }
        }
        release();
    }
}
