package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.algo.PsseStaggeredEulerSolver;
import com.interpss.dstab.controller.runtime.StaggeredEulerParticipant;
import org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer;
import org.interpss.dstab.control.pss.psse.ieeest.IeeestData;
import com.interpss.dstab.mach.ieeevc.IeeeVcRoundRotorMachine;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.common.DStabSimuException;
import org.interpss.core.dstab.reference.IeeestDerivativeReference;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.BaseDStabBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Matched-method comparisons use the running model, not a replacement filter. */
public class IeeestStaggeredSmibTest extends CorePluginTestSetup {
    @Test void optInSolverBindsTheExistingMachineAndIeeestStates(@TempDir Path directory) throws Exception {
        var f=IeeestBusMeasurementSmibTest.load(6,0,directory);
        var solver=new PsseStaggeredEulerSolver(f.algorithm());
        f.algorithm().setSolver(solver);
        assertTrue(f.algorithm().initialization());
        assertEquals(2,solver.getBoundMachineCount());
        assertEquals(20,solver.getBoundDifferentialStateCount());
    }

    @Test void statePortIsPureAndMatchesTheFullSecondOrderEquations(@TempDir Path directory) throws Exception {
        var f=IeeestBusMeasurementSmibTest.load(1,0,directory);
        f.stabilizer().setMachine(null);
        var model=new IeeestStabilizer("port",new IeeestData(1,0,.1,.02,.3,.04,.05,.006,
                .07,.08,.09,.1,4,5,3,.2,-.2,0,0),f.machine());
        assertSame(model,f.machine().getStabilizer());assertTrue(model.initStates(f.machine().getDStabBus(),f.machine()));
        f.machine().setSpeed(f.machine().getSpeed()+.1);
        double[] state={17,.01,-.02,.03,-.04,.005,-.006,.007,19},rates=new double[9],observed=new double[9];
        model.applyStaggeredState(state,1,.0005);var named=model.getNamedStates();
        model.evaluateStaggered(.0005,rates,1);model.readStaggeredState(observed,1);
        for(int i=1;i<8;i++)assertEquals(state[i],observed[i],0);
        assertEquals(named,model.getNamedStates());assertEquals(5,named.size());
        double firstOutput=.01,firstRate=-.02,zRate=.03,z=-.04;
        double zAcceleration=(firstOutput-z-.3*zRate)/.04;
        double secondOutput=z+.05*zRate+.006*zAcceleration;
        double lead1=.005+.07/.08*secondOutput,lead2=-.006+.09/.1*lead1;
        double[] expected={firstRate,(.1-firstOutput-.1*firstRate)/.02,zAcceleration,zRate,
            ((1-.07/.08)*secondOutput-.005)/.08,((1-.09/.1)*lead1+.006)/.1,
            ((4./5)*3*lead2-.007)/5};
        for(int i=0;i<7;i++)assertEquals(expected[i],rates[i+1],1e-12);
        for(boolean optimized:new boolean[]{false,true}) {
            model.setRuntimeExpressionBindingsEnabled(optimized);model.evaluateStaggered(.0005,rates,1);
            for(int i=0;i<7;i++)assertEquals(expected[i],rates[i+1],1e-12);
        }
        for(int i=0;i<10;i++)model.evaluateStaggered(0,rates,1);
        model.readStaggeredState(observed,1);for(int i=1;i<8;i++)assertEquals(state[i],observed[i],0);
    }

    @Test void eventPolicyAndInvalidStateAccessAreExplicit(@TempDir Path directory) throws Exception {
        for(int mode:new int[]{1,2,3,4,5,6}) {
            var model=IeeestBusMeasurementSmibTest.load(mode,0,directory.resolve("mode-"+mode)).stabilizer();
            assertEquals(mode!=6,model.averageStaggeredRatesAtDiscontinuity());
            double[] before=new double[7];model.readStaggeredState(before,0);
            assertThrows(IndexOutOfBoundsException.class,()->model.readStaggeredState(new double[6],0));
            double[] invalid=before.clone();invalid[6]=Double.NaN;
            assertThrows(IllegalArgumentException.class,()->model.applyStaggeredState(invalid,0,.0005));
            assertThrows(IllegalArgumentException.class,()->model.evaluateStaggered(-1,new double[7],0));
            double[] after=new double[7];model.readStaggeredState(after,0);assertArrayEquals(before,after,0);
            var bus=model.getMachine().getDStabBus();bus.setStatus(false);
            assertFalse(model.initStates(bus,model.getMachine()));
            assertThrows(IllegalStateException.class,()->model.readStaggeredState(new double[7],0),"Failed reinitialization cannot expose stale state bindings");
        }
    }

    @Test void inactiveControllerFreezesAndReactivationStartsFromItsCurrentRate(@TempDir Path directory) throws Exception {
        var f=IeeestBusMeasurementSmibTest.load(6,0,directory);var model=f.stabilizer();
        var solver=new PsseStaggeredEulerSolver(f.algorithm());f.algorithm().setSolver(solver);assertTrue(f.algorithm().initialization());
        double[] state={.0001,.001,.0002,0,.00003,.00004,.00001};model.applyStaggeredState(state,0,.0005);
        model.setStatus(false);solver.notifyDiscontinuity();
        for(int i=0;i<4;i++)assertTrue(f.algorithm().solveDEqnStep(true));
        double[] actual=new double[7];model.readStaggeredState(actual,0);assertArrayEquals(state,actual,0);
        model.setStatus(true);double[] rates=new double[7];model.evaluateStaggered(.0005,rates,0);solver.notifyDiscontinuity();
        assertTrue(f.algorithm().solveDEqnStep(true));model.readStaggeredState(actual,0);
        for(int i=0;i<7;i++)assertEquals(state[i]+.0005*rates[i],actual[i],1e-12,"Reactivation must not use stale slope history");
        assertEquals(f.machine().getDStabBus().getVoltageMag(),model.getVoltageDerivativeMemory(),1e-12);
        model.setMachine(null);
        assertThrows(IllegalStateException.class,()->solver.nextStep(f.algorithm().getSimuTime(),.0005,
                com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER));
    }

    private static double[] coordinates(IeeestStabilizer model) {
        double[] state=new double[7];model.readStaggeredState(state,0);
        return new double[]{model.a2*state[1]+model.a1*state[0],model.a2*state[0],state[0],state[2],
                model.t2*state[4],model.t4*state[5],model.t6*state[6]};
    }

    @Test void actualControllerGraphReplaysEveryNativeRowWithTheMatchedRule(@TempDir Path directory) throws Exception {
        String[] keys={"PSS_FILTER1","PSS_FILTER2","PSS_FILTER3","PSS_FILTER4","PSS_LL1","PSS_LL2","PSS_LAST"};
        for(var p:IeeestDerivativeReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());
            assertEquals(39,rows.getFirst().size());assertTrue(rows.getFirst().get("time_s")<0);
            var f=IeeestPowerInputSmibTest.load(p.network(),p.input(),p.step(),directory.resolve(p.name()));
            var model=f.stabilizer();assertInstanceOf(StaggeredEulerParticipant.class,model);
            assertFalse(model.averageStaggeredRatesAtDiscontinuity());
            var bus=(BaseDStabBus<?,?>)f.algorithm().getNetwork().getBus(p.name().startsWith("local")?"Bus1":"Bus2");
            String voltage=p.name().startsWith("local")?"V_BUS1":"V_BUS2";
            bus.setVoltage(new Complex(rows.getFirst().get(voltage),0));assertTrue(model.initStates(f.machine().getDStabBus(),f.machine()));
            double[] history=new double[7],rates=new double[7],start=new double[7],trial=new double[7],scratch=new double[7],maximum=new double[8];
            double previousTime=0;int duplicates=0;var previous=rows.getFirst();
            var text=new StringBuilder("raw_row,time_s,native_vstab,vstab,native_memory,memory,native_derivative,derivative");
            for(String key:keys)text.append(",native_").append(key).append(',').append(key);text.append('\n');
            for(int index=0;index<rows.size();index++) {
                var row=rows.get(index);double time=row.get("time_s"),dt=time>previousTime?p.nativeStep():0;
                model.readStaggeredState(start,0);for(double value:row.values())assertTrue(Double.isFinite(value));
                if(dt>0) {
                    bus.setVoltage(new Complex(previous.get(voltage),0));
                    model.readStaggeredState(start,0);model.evaluateStaggered(dt,rates,0);
                    for(int i=0;i<7;i++)trial[i]=start[i]+dt*(1.5*rates[i]-.5*history[i]);
                    model.applyStaggeredState(trial,0,dt);System.arraycopy(rates,0,history,0,7);
                    model.evaluateStaggered(0,scratch,0);
                }
                bus.setVoltage(new Complex(row.get(voltage),0));
                if(dt>0)model.acceptVoltageDerivativeEndpoint(time,dt);
                else if(index>0&&time==rows.get(index-1).get("time_s")){duplicates++;model.synchronizeVoltageDerivativeMemory();}
                model.evaluateStaggered(0,scratch,0);
                if(dt==0){model.readStaggeredState(trial,0);assertArrayEquals(start,trial,0,"Duplicate/history rows cannot advance continuous storage");}
                double output=model.getOutput(f.machine());
                double outputError=Math.abs(output-row.get("VOTHSG"));
                maximum[0]=Math.max(maximum[0],outputError);
                assertTrue(output>=model.lsmin&&output<=model.lsmax,"Physical output limits are unchanged");
                double[] observed=coordinates(model);
                text.append(index).append(',').append(time).append(',').append(row.get("VOTHSG")).append(',').append(output)
                    .append(',').append(row.get("PSS_MEMORY")).append(',').append(model.getVoltageDerivativeMemory())
                    .append(',').append(row.get("PSS_VDOT")).append(',').append(model.getVoltageDerivative());
                for(int i=0;i<7;i++) {
                    double error=Math.abs(observed[i]-row.get(keys[i]));maximum[i+1]=Math.max(maximum[i+1],error);
                    text.append(',').append(row.get(keys[i])).append(',').append(observed[i]);
                }
                text.append('\n');
                if(time>=0){previousTime=time;previous=row;}
            }
            assertEquals(2,duplicates);assertEquals(p.samples(),rows.size());
            write("playback",p.name(),text);
            System.out.println("IEEEST matched-rule playback "+p.name()+" maxima="+Arrays.toString(maximum));
        }
    }

    @Test void coupledMatchedMethodProfilesRetainSolvedEndpointDiagnostics(@TempDir Path directory) throws Exception {
        for(var p:IeeestDerivativeReference.profiles()) {
            var f=IeeestPowerInputSmibTest.load(p.network(),p.input(),p.step(),p.fault(),directory.resolve(p.name()));
            var afterEvent=new HashMap<Integer,Map<String,Double>>();
            var solver=new PsseStaggeredEulerSolver(f.algorithm()) {
                @Override public boolean networkSolutionStep() throws DStabSimuException {
                    boolean solved=super.networkSolutionStep();
                    if(solved&&eventBoundaryThisStep&&"INITIAL".equals(networkSolutionStage)) {
                        int tick=(int)Math.round(f.algorithm().getSimuTime()/p.step());
                        assertNull(afterEvent.put(tick,snapshot(f,tick*p.step())));
                    }
                    return solved;
                }
            };solver.setNetworkSolutionTolerance(1e-10);
            f.algorithm().setSolver(solver);assertTrue(f.algorithm().initialization());
            var model=f.stabilizer();var bus=(BaseDStabBus<?,?>)f.algorithm().getNetwork().getBus(p.name().startsWith("local")?"Bus1":"Bus2");
            var exc=assertInstanceOf(IEEE1981ST1Exciter.class,f.machine().getExciter());double reference=exc.getRefPoint();
            int ticks=(int)Math.ceil(p.end()/p.step());f.algorithm().setTotalSimuTimeSec(ticks*p.step());
            var points=new ArrayList<Map<String,Double>>();var text=new StringBuilder(String.join(",",snapshot(f,0).keySet())+"\n");
            for(int tick=0;tick<=ticks;tick++) {
                var point=snapshot(f,tick*p.step());points.add(point);append(text,point);
                if(p.name().contains("reference")&&tick==Math.round(.05/p.step())) {
                    exc.setRefPoint(reference+.01);solver.notifyDiscontinuity();afterEvent.put(tick,snapshot(f,tick*p.step()));
                }
                assertEquals(bus.getVoltageMag(),model.getVoltageDerivativeMemory(),1e-12,"Accepted memory follows the settled endpoint");
                if(tick<ticks)assertTrue(f.algorithm().solveDEqnStep(true));
            }
            write("coupled",p.name(),text);
            assertEquals(p.fault()?2:p.name().contains("reference")?1:0,afterEvent.size());
            var raw=HplntdSwitchingNativeSmibTest.rows(p.body());
            var comparison=new StringBuilder("raw_row,raw_time_s,step_index,event_side,native_integration_time_s,canonical_time_s");
            for(String key:points.getFirst().keySet())if(!key.equals("time_s"))comparison.append(",native_").append(key).append(',').append(key);
            comparison.append('\n');double previousTime=0;int tick=0;
            var maxima=new LinkedHashMap<String,Double>();
            for(int index=0;index<raw.size();index++) {
                var row=raw.get(index);double time=row.get("time_s");if(time>previousTime){
                    assertEquals((double)((float)previousTime+(float)p.nativeStep()),time,5e-13,"Recorded binary32 clock must advance by one declared step");
                    tick++;previousTime=time;
                }
                boolean right=index>0&&time==raw.get(index-1).get("time_s");
                var point=right?afterEvent.getOrDefault(tick,points.get(tick)):points.get(tick);
                comparison.append(index).append(',').append(time).append(',').append(tick).append(',').append(right?"AFTER":"BEFORE")
                    .append(',').append(tick*p.nativeStep()).append(',').append(point.get("time_s"));
                for(var value:point.entrySet())if(!value.getKey().equals("time_s")) {
                    double expected=row.get(value.getKey());maxima.merge(value.getKey(),Math.abs(value.getValue()-expected),Math::max);
                    comparison.append(',').append(expected).append(',').append(value.getValue());
                }
                comparison.append('\n');
            }
            assertEquals(ticks,tick);write("comparison",p.name(),comparison);
            System.out.println("IEEEST coupled matched-rule "+p.name()+" maxima="+maxima);
        }
    }

    private static Map<String,Double> snapshot(IeeestPowerInputSmibTest.Fixture f,double time) {
        var m=assertInstanceOf(IeeeVcRoundRotorMachine.class,f.machine());
        var result=new LinkedHashMap<String,Double>();result.put("time_s",time);
        result.put("EP_Q",m.getEq1());result.put("EP_D",m.getEd1());result.put("PSIK_D",m.getPsikd());result.put("PSIK_Q",m.getPsikq());
        result.put("SPEED_DEV",m.getSpeed()-1);result.put("ANGLE",m.getAngle());
        var exc=assertInstanceOf(IEEE1981ST1Exciter.class,m.getExciter());var states=exc.getNativeStateSnapshot();
        String[] excKeys={"Measured voltage","First lead-lag","Second lead-lag","Regulator output","Feedback"};
        String[] excNames={"EXC_VMEAS","EXC_LL1","EXC_LL2","EXC_VA","EXC_FEEDBACK"};
        for(int i=0;i<5;i++)result.put(excNames[i],states.get(excKeys[i]));
        double[] pss=coordinates(f.stabilizer());String[] names={"PSS_FILTER1","PSS_FILTER2","PSS_FILTER3","PSS_FILTER4","PSS_LL1","PSS_LL2","PSS_LAST"};
        for(int i=0;i<7;i++)result.put(names[i],pss[i]);
        result.put("PSS_MEMORY",f.stabilizer().getVoltageDerivativeMemory());result.put("PSS_VDOT",f.stabilizer().getVoltageDerivative());
        var v=m.getDStabBus().getVoltage();var ref=f.algorithm().getNetwork().getBus("Bus2").getVoltage();
        result.put("V_BUS1",v.abs());result.put("ANGLE_BUS1",Math.toDegrees(v.getArgument()));result.put("V_BUS2",ref.abs());result.put("ANGLE_BUS2",Math.toDegrees(ref.getArgument()));
        result.put("FREQ1",m.getDStabBus().getFreq()-1);result.put("FREQ2",((BaseDStabBus<?,?>)f.algorithm().getNetwork().getBus("Bus2")).getFreq()-1);
        var current=m.getIgen().subtract(v.multiply(m.getYgen()));var power=v.multiply(current.conjugate());
        result.put("MACH_ANGLE",Math.toDegrees(m.getAngle()));result.put("P_PU",power.getReal());result.put("Q_PU",power.getImaginary());
        result.put("EFD",m.getEfd());result.put("PMECH",m.getPm());result.put("MACH_SPEED",m.getSpeed()-1);
        result.put("XAD_IFD",m.calculateIfd(MachineIfdBase.EXCITER));result.put("ECOMP",m.getCmlMachineVoltage());
        result.put("VOTHSG",f.stabilizer().getOutput(m));result.put("VREF",exc.getRefPoint());
        var machine=f.algorithm().getNetwork().getMachine("Bus2-mach1");result.put("REF_ANGLE",Math.toDegrees(machine.getAngle()));result.put("REF_SPEED",machine.getSpeed()-1);
        return result;
    }

    private static void append(StringBuilder text,Map<String,Double> row) {
        boolean first=true;for(double value:row.values()){assertTrue(Double.isFinite(value));if(!first)text.append(',');text.append(value);first=false;}text.append('\n');
    }

    private static void write(String kind,String name,StringBuilder text) throws Exception {
        Path file=Path.of("target/ieeest-staggered-"+kind,name+".csv");Files.createDirectories(file.getParent());Files.writeString(file,text);
    }
}
