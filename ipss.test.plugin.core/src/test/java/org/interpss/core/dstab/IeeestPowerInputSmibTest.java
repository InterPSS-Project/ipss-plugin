package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.GenrouNativeReference;
import org.interpss.core.dstab.reference.IeeestInputReference;
import org.interpss.core.dstab.reference.EventSideTrajectory;
import org.interpss.core.dstab.reference.EventSideTrajectory.Side;
import org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer;
import org.interpss.dstab.control.pss.psse.ieeest.SecondOrderLagBlock;
import org.interpss.dstab.mach.IeeeVcRoundRotorMachine;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.algo.*;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.Machine;

/** Native electrical-power and accelerating-power selectors are distinct physical inputs. */
public class IeeestPowerInputSmibTest extends CorePluginTestSetup {
    record Fixture(DynamicSimuAlgorithm algorithm,Machine machine,IeeestStabilizer stabilizer) {}
    static Fixture load(String rawText,String dynamic,double step,Path directory)throws Exception {
        return load(rawText,dynamic,step,false,directory);
    }
    static Fixture load(String rawText,String dynamic,double step,boolean fault,Path directory)throws Exception {
        Files.createDirectories(directory);Path raw=directory.resolve("network.raw"),dyr=directory.resolve("dynamic.dyr");
        Files.writeString(raw,rawText);Files.writeString(dyr,dynamic);
        var context=new PSSEMultiFileLoader().loadDStab(raw.toString());var net=context.getDStabilityNet();
        net.setStaticLoadIncludedInYMatrix(false);net.setStaticLoadModel(StaticLoadModel.CONST_P);net.setReactiveStaticLoadModel(StaticLoadModel.CONST_P);
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(net)).setStrictImport(true);
        parser.parseDynFile(dyr.toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
        var algorithm=context.getDynSimuAlgorithm();algorithm.getAclfAlgorithm().setTolerance(1e-10);assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(step);algorithm.setTotalSimuTimeSec(.5);algorithm.setSimuOutputHandler(new StateMonitor());
        if(fault)net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"input-three-cycle");
        assertTrue(algorithm.initialization());var machine=net.getMachine("Bus1-mach1");
        return new Fixture(algorithm,machine,assertInstanceOf(IeeestStabilizer.class,machine.getStabilizer()));
    }
    private static Fixture load(int mode, Path directory, boolean playback) throws Exception {
        var p=GenrouNativeReference.profiles().stream().filter(x->x.name().equals("flat-coarse")).findFirst().orElseThrow();
        String input=p.input()+"1 'ESST1A' '1' 1 1 .02 .8 -.8 .2 .8 .1 .4 80 .04 99 -99 7.3 -7.3 0 .1 1 0 1 /\n"
                +"1 'IEEEST' '1' "+mode+" 0 .061 .0017 .02 0 0 0 .3 .03 .3 .03 5 5 5 .05 -.05 0 0 /\n";
        return playback ? loadForPowerPlayback(p.network(), input, p.step(), directory)
                : load(p.network(), input, p.step(), directory);
    }
    private static Fixture loadForPowerPlayback(String raw, String dynamic, double step,
            Path directory) throws Exception {
        // The playback supplies electrical and mechanical power directly. A
        // classical host lets it inject those signals without solving a stator.
        String entered = dynamic.replaceFirst("(?m)^\\s*1\\s+'GENROU'\\s+'?1'?\\s+[^/]+/",
                "1 'GENCLS' '1' 5 0 /");
        assertNotEquals(dynamic, entered, "Expected the Bus1 round-rotor source in the fixture");
        return load(raw, entered, step, directory);
    }

    @Test
    void roundRotorElectricalModeReadsTerminalPowerAtBothStages(@TempDir Path directory) throws Exception {
        for (boolean direct : List.of(false, true)) {
            var fixture = load(3, directory.resolve("stator-" + direct), false);
            var machine = assertInstanceOf(IeeeVcRoundRotorMachine.class, fixture.machine());
            var stabilizer = fixture.stabilizer();
            var bus = machine.getDStabBus();
            machine.setRa(.025);
            assertTrue(machine.initStates(bus));
            machine.getIgen();
            double initialTerminalPower = machine.getParentGen().getGen().getReal() / machine.getRating();
            assertTrue(machine.getPe() - initialTerminalPower > .001);
            assertTrue(stabilizer.initStates(bus, machine));
            assertEquals(initialTerminalPower, stabilizer.peRef, 1e-12);
            if (direct) {
                stabilizer.setPreparedDirectStage(true);
                stabilizer.compileRuntimePlan();
                assertTrue(stabilizer.hasCompiledStageEvaluator());
            }
            var input = assertInstanceOf(SecondOrderLagBlock.class,
                    stabilizer.getNamedStateBlocks().get("secondOrderLag"));
            for (int stage = 0; stage < 2; stage++) {
                bus.setVoltage(bus.getVoltageMag(), bus.getVoltageAng() + .015);
                var voltage = bus.getVoltage();
                var current = machine.getIgen().subtract(voltage.multiply(machine.getYgen()));
                double terminalPower = voltage.multiply(current.conjugate()).getReal() / machine.getRating();
                double swingPower = machine.getPe();
                assertTrue(Math.abs(terminalPower - initialTerminalPower) > .01);
                assertTrue(stabilizer.nextStep(.0005, DynamicSimuMethod.MODIFIED_EULER, machine, stage));
                assertEquals(terminalPower - initialTerminalPower, input.getU0(), 1e-12);
                assertEquals(swingPower, machine.getPe(), 0.0);
            }
        }
    }

    @Test void electricalAndAcceleratingPowerUseTheirDocumentedSignsInBothStages(@TempDir Path directory)throws Exception {
        for(int mode:List.of(3,4)) {
            var f=load(mode, directory.resolve("mode-" + mode), true);var m=f.machine();var pss=f.stabilizer();
            assertTrue(pss.initStates(m.getDStabBus(), m));
            double pe=m.getPe(),pm=m.getPm();m.setPe(pe+.2);m.setPm(pm+.05);
            double expected=mode==3?.2:-.15;
            var first=assertInstanceOf(SecondOrderLagBlock.class,pss.getNamedStateBlocks().get("secondOrderLag"));
            for(int stage:List.of(0,1)) {
                assertTrue(pss.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,stage));
                assertEquals(expected,first.getU0(),1e-12,"Mode "+mode+" must sample the declared power combination at stage "+stage);
            }
        }
    }
    @Test void equalElectricalAndMechanicalChangesCancelOnlyForAcceleratingPower(@TempDir Path directory)throws Exception {
        for(int mode:List.of(3,4)) {
            var f=load(mode, directory.resolve("mode-" + mode), true);var m=f.machine();var pss=f.stabilizer();
            assertTrue(pss.initStates(m.getDStabBus(), m));
            m.setPe(m.getPe()+.2);m.setPm(m.getPm()+.2);
            assertTrue(pss.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,m,0));
            var first=assertInstanceOf(SecondOrderLagBlock.class,pss.getNamedStateBlocks().get("secondOrderLag"));
            assertEquals(mode==3?.2:0,first.getU0(),1e-12,"Pm-Pe cancels equal perturbations without changing the power-input selector");
        }
    }
    @Test void nativeHistoriesRetainEveryStateAndIntervalSide() {
        assertEquals(8,IeeestInputReference.profiles().size());
        for(var p:IeeestInputReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());assertEquals(p.samples(),rows.size());assertEquals(35,rows.getFirst().size());
            assertTrue(rows.getFirst().get("time_s")<0);assertEquals((double)(float)p.step(),p.nativeStep(),0);
            int duplicates=0,upper=0,lower=0;double previous=Double.NEGATIVE_INFINITY,maximum=0;
            for(var row:rows) {
                assertTrue(row.get("time_s")>=previous);if(row.get("time_s")==previous)duplicates++;previous=row.get("time_s");
                for(double value:row.values())assertTrue(Double.isFinite(value));
                for(String key:List.of("PSS_FILTER1","PSS_FILTER2","PSS_FILTER3","PSS_FILTER4","PSS_LL1","PSS_LL2","PSS_LAST"))assertTrue(row.containsKey(key));
                assertFalse(row.containsKey("PSS_MEMORY"));assertFalse(row.containsKey("PSS_VDOT"));
                maximum=Math.max(maximum,Math.abs(row.get("VOTHSG")));assertTrue(Math.abs(row.get("VOTHSG"))<=(double)(float).05+1e-12);
                if(row.get("VOTHSG")>=(double)(float).05-1e-10)upper++;
                if(row.get("VOTHSG")<=(double)(float)-.05+1e-10)lower++;
            }
            assertEquals(2,duplicates);assertTrue(Math.abs(previous-p.end())<p.step());
            if(p.fault()){assertTrue(maximum>.01,"The declared native fault must excite the stabilizer");assertTrue(upper>0 && lower>0,"Both output-limit directions must be independently active");}
        }
    }
    @Test void everyRecordedPowerSampleReplaysTheExactInputCombination(@TempDir Path directory)throws Exception {
        var faultErrors=new LinkedHashMap<String,Double>();
        for(var p:IeeestInputReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());var f=loadForPowerPlayback(p.network(),p.input(),p.step(),directory.resolve(p.name()));
            var m=f.machine();var pss=f.stabilizer();var initial=rows.getFirst();
            double pe0=initial.get("P_PU"),pm0=initial.get("PMECH");m.setPe(pe0);m.setPm(pm0);assertTrue(pss.initStates(m.getDStabBus(),m));
            var first=assertInstanceOf(SecondOrderLagBlock.class,pss.getNamedStateBlocks().get("secondOrderLag"));
            var text=new StringBuilder("raw_row,time_s,expected_input,sampled_input,native_vstab,replayed_vstab\n");
            double previousTime=0;var previous=initial;double maximum=0;
            for(int index=0;index<rows.size();index++) {
                var row=rows.get(index);double time=row.get("time_s"),dt=time>previousTime?p.nativeStep():0;
                var storage=pss.getNamedStates();m.setPe(previous.get("P_PU"));m.setPm(previous.get("PMECH"));
                assertTrue(pss.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,0));
                m.setPe(row.get("P_PU"));m.setPm(row.get("PMECH"));assertTrue(pss.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,m,1));
                if(dt==0)for(var state:storage.entrySet())assertEquals(state.getValue(),pss.getNamedStates().get(state.getKey()),0,
                        "Zero-duration rows preserve numerical storage; signed zero is not an advance");
                double expected=p.mode()==3?row.get("P_PU")-pe0:(row.get("PMECH")-pm0)-(row.get("P_PU")-pe0);
                assertEquals(expected,first.getU0(),1e-12,p.name()+" raw row "+index);
                double output=pss.getOutput(m);assertTrue(Double.isFinite(output));assertTrue(Math.abs(output)<=.05+1e-12);
                double error=Math.abs(output-row.get("VOTHSG"));maximum=Math.max(maximum,error);
                double bound=p.fault()?(p.mode()==3?(p.step()==.0005?1.3e-3:3.7e-4):(p.step()==.0005?1.25e-3:3.2e-4)):(p.mode()==3?3e-5:1e-12);
                assertTrue(error<=bound,p.name()+" Vstab @ raw row "+index+" error="+error+" bound="+bound);
                text.append(index).append(',').append(time).append(',').append(expected).append(',').append(first.getU0()).append(',').append(row.get("VOTHSG")).append(',').append(output).append('\n');
                if(time>=0){previousTime=time;previous=row;}
            }
            if(p.fault())faultErrors.put(p.name(),maximum);
            write("playback",p.name(),text);System.out.println("IEEEST power-input playback "+p.name()+" output_maximum="+maximum);
        }
        for(String mode:List.of("electrical-fault","accelerating-fault"))assertTrue(faultErrors.get(mode+"-fine")<.4*faultErrors.get(mode+"-coarse"),
                "The active-profile output residual must shrink under step halving, not merely fit a broad output limit");
    }
    @Test void coupledCoreMachineProfilesRetainCompleteDiagnosticTraces(@TempDir Path directory)throws Exception {
        for(var p:IeeestInputReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());var f=load(p.network(),p.input(),p.step(),p.fault(),directory.resolve(p.name()));
            assertInstanceOf(IeeeVcRoundRotorMachine.class, f.machine());
            int ticks=(int)Math.ceil(Math.max(p.end(),rows.getLast().get("time_s"))/p.step());f.algorithm().setTotalSimuTimeSec(ticks*p.step());
            var points=new ArrayList<Map<String,Double>>();var text=new StringBuilder("time_s,V_BUS1,V_BUS2,SPEED_DEV,ANGLE,EFD,PMECH,VOTHSG\n");
            for(int tick=0;tick<=ticks;tick++) {
                var m=f.machine();var row=new LinkedHashMap<String,Double>();row.put("time_s",tick*p.step());row.put("V_BUS1",m.getDStabBus().getVoltageMag());
                row.put("V_BUS2",f.algorithm().getNetwork().getBus("Bus2").getVoltageMag());row.put("SPEED_DEV",m.getSpeed()-1);row.put("ANGLE",m.getAngle());
                row.put("EFD",m.getEfd());row.put("PMECH",m.getPm());row.put("VOTHSG",f.stabilizer().getOutput(m));points.add(row);
                boolean first=true;for(double value:row.values()){assertTrue(Double.isFinite(value));if(!first)text.append(',');text.append(value);first=false;}text.append('\n');
                if(tick<ticks)assertTrue(f.algorithm().solveDEqnStep(true));
            }
            var history=new EventSideTrajectory(points);var diagnostic=new StringBuilder("raw_row,time_s,native_vstab,coupled_vstab\n");
            double maximum=0;
            for(int index=0;index<rows.size();index++) {
                var row=rows.get(index);double time=row.get("time_s"),actual=history.sample(time<0?0:time,Side.AFTER).get("VOTHSG");
                maximum=Math.max(maximum,Math.abs(actual-row.get("VOTHSG")));
                diagnostic.append(index).append(',').append(time).append(',').append(row.get("VOTHSG")).append(',').append(actual).append('\n');
            }
            write("coupled",p.name(),text);write("diagnostic",p.name(),diagnostic);
            System.out.println("IEEEST power-input coupled diagnostic "+p.name()+" output_maximum="+maximum);
        }
    }
    private static void write(String kind,String name,StringBuilder text)throws Exception {
        Path path=Path.of("target/ieeest-input-"+kind,name+".csv");Files.createDirectories(path.getParent());Files.writeString(path,text);
    }
}
