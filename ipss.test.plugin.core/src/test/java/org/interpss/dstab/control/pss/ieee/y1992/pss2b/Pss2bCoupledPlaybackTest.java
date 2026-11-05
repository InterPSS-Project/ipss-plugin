package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.controller.deqn.AbstractExciter;
import com.interpss.dstab.controller.deqn.AbstractStabilizer;
import com.interpss.dstab.mach.Machine;

/** Diagnostic signal cuts localize downstream errors; they do not qualify the replaced controllers. */
public class Pss2bCoupledPlaybackTest {
    @TempDir Path staged;

    @ParameterizedTest(name="{0}, prescribed field={1}, held stabilizer={2}")
    @CsvSource({"quarter,false,false", "quarter,true,false", "half-quarter,false,false", "half-quarter,true,false",
            "quarter,false,true", "half-quarter,false,true"})
    void completeReferenceOutputPlaybackSeparatesControllerAndDownstreamErrors(String grid, boolean field, boolean held) throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        var root = Pss2bReferenceFixture.longRun(staged).stage("pss2b-native-long-"+grid+"-derivative-a-20261102");
        var manifest = new ObjectMapper().readTree(root.resolve("manifest.json").toFile());
        var expected = Pss2bReferenceReplayTest.rows(root.resolve("native.csv"));
        double step = manifest.get("step_s").asDouble();
        var clock = new PlaybackClock(expected, step, held);
        var context = new PSSEMultiFileLoader().loadDStab(root.resolve("network.raw").toString(), root.resolve("dynamic.dyr").toString());
        var algorithm = context.getDynSimuAlgorithm(); var net = context.getDStabilityNet();
        net.setStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_P);
        net.setReactiveStaticLoadModel(com.interpss.dstab.StaticLoadModel.CONST_P);
        net.setStaticLoadIncludedInYMatrix(false); net.setStaticLoadZPartInYMatrix(true);
        for (var bus : net.getBusList())
            ((com.interpss.dstab.controller.deqn.measure.BusFreqMeasurementImpl)bus.getBusFreqMeasureBlock()).setTf(.04);
        var machine = net.getMachine("Bus1-mach1");
        machine.getStabilizer().setMachine(null);
        new PrescribedStabilizer(clock).setMachine(machine);
        if (field) { machine.getExciter().setMachine(null); new PrescribedExciter(clock).setMachine(machine); }
        algorithm.getAclfAlgorithm().setTolerance(1e-10); assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER); algorithm.setSimuStepSec(step);
        algorithm.setTotalSimuTimeSec((clock.output.length-1)*step);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", net,
                com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P, new Complex(0,.2), null, .05,.05), "fault");
        var recorder = new Recorder(algorithm,clock); algorithm.setSolver(recorder);
        assertTrue(algorithm.initialization()); var initial = recorder.capture("INITIAL"); recorder.enabled = true;
        while (algorithm.getSimuTime()<algorithm.getTotalSimuTimeSec()-step/2) assertTrue(algorithm.solveDEqnStep(true));
        recorder.frames.add(recorder.capture("FINAL"));
        assertEquals(clock.output.length-1, clock.committed);
        assertEquals(clock.committed, clock.predictors);
        long startups = expected.stream().filter(row->row.get("time_s")<0).count();
        assertEquals(expected.size(), recorder.frames.size()+startups);
        var output = Files.createTempDirectory(Files.createDirectories(Path.of("target/pss2b-playback")), grid+(field?"-field-":held?"-held-pss-":"-pss-"));
        var maximum = new LinkedHashMap<String,Double>(); var sums = new LinkedHashMap<String,Double>();
        for (String name : initial.values.keySet()) { maximum.put(name,0.); sums.put(name,0.); }
        try (var raw = BenchmarkOutputGuard.newOutputStream(output.resolve("boundary.csv.gz"),
                BenchmarkOutputGuard.estimateCsvBytes(expected.size(),initial.values.size()));
                var gzip = new java.util.zip.GZIPOutputStream(raw);
                var writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(gzip, java.nio.charset.StandardCharsets.UTF_8))) {
            writer.write("reference_time_s,actual_time_s,sample_kind,"+String.join(",",initial.values.keySet())+"\n");
            int index=0;
            for (var row : expected) {
                double time=row.get("time_s"); var actual=time<0?initial:recorder.frames.get(index++);
                if(time>=0)assertEquals(time,actual.time,step/3);
                assertEquals(row.get("VOTHSG"),actual.values.get("VOTHSG"),0.,"Unmodified source output, including event duplicates");
                if(field && time>0)assertEquals(row.get("EFD"),actual.values.get("EFD"),0.,"Unmodified field output");
                writer.write(time+","+actual.time+","+(time<0?"STARTUP_HELD_INITIAL":actual.kind));
                for(var value:actual.values.entrySet()) {
                    assertTrue(Double.isFinite(value.getValue())); double error=value.getValue()-row.get(value.getKey());
                    sums.merge(value.getKey(),error*error,Double::sum); maximum.merge(value.getKey(),Math.abs(error),Math::max);
                    writer.write(","+value.getValue());
                }
                writer.write('\n');
            }
            assertEquals(recorder.frames.size(),index);
        }
        var rmse=new LinkedHashMap<String,Double>(); sums.forEach((key,value)->rmse.put(key,Math.sqrt(value/expected.size())));
        var result=new LinkedHashMap<String,Object>();
        result.put("status","diagnostic-prescribed-boundary-not-controller-acceptance");
        result.put("prescribed_channels",field?List.of("VOTHSG","EFD"):List.of("VOTHSG"));
        result.put("stabilizer_coupling",held?"held through both stages, published at accepted endpoint":"old endpoint in predictor, new endpoint in corrector");
        result.put("rmse",rmse); result.put("maximum_error",maximum);result.put("samples",expected.size());
        result.put("network_interval_s",step);result.put("network_tolerance",1e-6);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("diagnostic.json").toFile(),result);
        if(field)assertTrue(rmse.get("MACH_ANGLE")<.001,
                "Downstream machine/network gate with prescribed field; does not qualify either controller");
        System.out.println("PSS2B prescribed "+grid+" field="+field+" held="+held+" RMSE="+rmse);
    }

    private static final class PlaybackClock {
        final double step; final boolean held; final double[] output, field; int committed, predictors, observed;
        PlaybackClock(List<Map<String,Double>> rows,double step,boolean held) {
            this.step=step; this.held=held; int last=(int)Math.round(rows.getLast().get("time_s")/step);
            output=new double[last+1];field=new double[last+1];java.util.Arrays.fill(output,Double.NaN);
            for(var row:rows) {
                if(row.get("time_s")<0)continue;int tick=(int)Math.round(row.get("time_s")/step);
                if(!Double.isNaN(output[tick])) {
                    assertEquals(output[tick],row.get("VOTHSG"),0.,"Distinct event-side output requires explicit replay");
                    assertEquals(field[tick],row.get("EFD"),0.,"Distinct event-side field requires explicit replay");
                }
                output[tick]=row.get("VOTHSG");field[tick]=row.get("EFD");
            }
            for(double value:output)assertTrue(Double.isFinite(value),"No missing integration ordinals");
        }
        void advance(double dt,int flag) {
            if(dt==0)return; assertEquals(step,dt,0.);
            assertTrue(flag==0||flag==1);
            observed=held?committed:committed+1;
            if(flag==0)predictors++;else {committed++;assertEquals(predictors,committed);}
        }
    }

    private static final class PrescribedStabilizer extends AbstractStabilizer {
        final PlaybackClock clock;
        PrescribedStabilizer(PlaybackClock clock) {super("prescribed-pss","Prescribed stabilizer boundary","test");this.clock=clock;_data=new Object();}
        @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine) {clock.committed=clock.predictors=clock.observed=0;return true;}
        @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag) {clock.advance(dt,flag);return true;}
        @Override public double getOutput(Machine machine) {return clock.output[clock.observed];}
    }
    private static final class PrescribedExciter extends AbstractExciter {
        final PlaybackClock clock;
        PrescribedExciter(PlaybackClock clock) {super("prescribed-efd","Prescribed field boundary","test");this.clock=clock;_data=new Object();}
        @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine) {return true;}
        @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag) {return true;}
        @Override public double getOutput(Machine machine) {return clock.field[clock.observed];}
    }
    private record Frame(double time,String kind,Map<String,Double> values) { }
    private static final class Recorder extends DefaultDynamicSimulationSolver {
        final List<Frame> frames=new ArrayList<>(); final PlaybackClock clock; boolean enabled;
        Recorder(DynamicSimuAlgorithm algorithm,PlaybackClock clock) {super(algorithm);converge_tol=1e-6;this.clock=clock;}
        @Override public void beforeStep(double time)throws DStabSimuException {
            if(enabled && (Math.abs(time-.05)<1e-9||Math.abs(time-.1)<1e-9))frames.add(capture("INTERVAL_PRE"));
            super.beforeStep(time);
        }
        @Override public boolean networkSolutionStep()throws DStabSimuException {
            boolean solved=super.networkSolutionStep();
            if(solved&&"CORRECTOR".equals(networkSolutionStage))clock.observed=clock.committed;
            if(solved&&enabled&&"INITIAL".equals(networkSolutionStage))frames.add(capture("GRID"));
            return solved;
        }
        Frame capture(String kind) {
            var net=dstabAlgo.getNetwork();var machine=net.getMachine("Bus1-mach1");var values=new LinkedHashMap<String,Double>();
            var round=(org.interpss.dstab.mach.IeeeVcRoundRotorMachine)machine;
            values.put("EP_Q",round.getEq1());values.put("EP_D",round.getEd1());
            values.put("PSIK_D",round.getPsikd());values.put("PSIK_Q",round.getPsikq());
            for(int id:new int[]{1,2}) {
                var bus=net.getDStabBus("Bus"+id);values.put("V_BUS"+id,bus.getVoltageMag());
                values.put("ANGLE_BUS"+id,Math.toDegrees(bus.getVoltageAng()));
            }
            var voltage=machine.getDStabBus().getVoltage();var power=voltage.multiply(machine.getIgen().subtract(voltage.multiply(machine.getYgen())).conjugate());
            values.put("P_PU",power.getReal());values.put("Q_PU",power.getImaginary());
            values.put("MACH_ANGLE",Math.toDegrees(machine.getAngle()));values.put("MACH_SPEED",machine.getSpeed()-1);
            values.put("EFD",machine.getEfd());values.put("VOTHSG",machine.getStabilizer().getOutput(machine));
            return new Frame(dstabAlgo.getSimuTime(),kind,values);
        }
    }
}
