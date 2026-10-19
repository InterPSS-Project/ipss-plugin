package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.interpss.CorePluginTestSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.interpss.dstab.control.pss.psse.ieeest.SecondOrderLagBlock;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.apache.commons.math3.complex.Complex;
import java.nio.file.Files;
import java.util.Map;
import java.util.LinkedHashMap;
import org.interpss.core.dstab.reference.IeeestDerivativeReference;
import org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer;
import org.interpss.dstab.control.pss.psse.ieeest.SecondOrderLeadLagBlock;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.fadapter.psse.PsseDStabSolver;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.common.DStabSimuException;

/** Derivative input requires its own memory, not a voltage-input alias. */
public class IeeestDerivativeSmibTest extends CorePluginTestSetup {
    @Test void derivativeSelectorLoadsLocallyAndRemotely(@TempDir Path directory) throws Exception {
        for (int remote : new int[]{0, 2}) {
            var f = IeeestBusMeasurementSmibTest.load(6, remote, directory.resolve("bus-"+remote));
            assertEquals(6, f.stabilizer().getData().mode());
            assertEquals(remote, f.stabilizer().getData().remoteBus());
        }
    }

    @Test void bothStagesHoldTheAcceptedDerivativeAndIgnoreTrialVoltages(@TempDir Path directory) throws Exception {
        for (int remote : new int[]{0,2}) {
            var f=IeeestBusMeasurementSmibTest.load(6,remote,directory.resolve("bus-"+remote));
            var pss=f.stabilizer();
            var bus=(BaseDStabBus<?,?>)f.algorithm().getNetwork().getBus(remote==0?"Bus1":"Bus2");
            double initial=bus.getVoltageMag();bus.setVoltage(new Complex(initial-.001,0));
            pss.acceptVoltageDerivativeEndpoint(.0005,.0005);
            assertEquals(-.4,pss.getVoltageDerivative(),1e-12);
            var first=assertInstanceOf(SecondOrderLagBlock.class,pss.getNamedStateBlocks().get("secondOrderLag"));
            for(int stage:new int[]{0,1}) {
                bus.setVoltage(new Complex(.7-stage*.1,0));
                assertTrue(pss.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,f.machine(),stage));
                assertEquals(-.4,first.getU0(),1e-12);
                assertEquals(initial-.001,pss.getVoltageDerivativeMemory(),0);
            }
            var storage=pss.getNamedStates();double held=pss.getVoltageDerivative();
            pss.synchronizeVoltageDerivativeMemory();
            assertEquals(held,pss.getVoltageDerivative(),0);assertEquals(storage,pss.getNamedStates());
            for(int i=0;i<10;i++){pss.getOutput(f.machine());pss.busMeasurement.getY();}
            assertEquals(held,pss.getVoltageDerivative(),0);assertEquals(storage,pss.getNamedStates());
        }
    }

    @Test void everyRawRowReplaysVoltageMemoryAndHeldInput(@TempDir Path directory) throws Exception {
        assertEquals(12,IeeestDerivativeReference.profiles().size());
        var smoothErrors=new LinkedHashMap<String,Double>();
        var stateErrors=new LinkedHashMap<String,double[]>();
        for(var p:IeeestDerivativeReference.profiles()) {
            var rows=HplntdSwitchingNativeSmibTest.rows(p.body());
            var f=IeeestPowerInputSmibTest.load(p.network(),p.input(),p.step(),directory.resolve(p.name()));
            var pss=f.stabilizer();boolean local=p.name().startsWith("local-");
            var bus=(BaseDStabBus<?,?>)f.algorithm().getNetwork().getBus(local?"Bus1":"Bus2");
            String key=local?"V_BUS1":"V_BUS2";
            assertEquals(p.samples(),rows.size());assertEquals(39,rows.getFirst().size());
            assertTrue(rows.getFirst().get("time_s")<0);
            bus.setVoltage(new Complex(rows.getFirst().get(key),0));assertTrue(pss.initStates(f.machine().getDStabBus(),f.machine()));
            var first=assertInstanceOf(SecondOrderLagBlock.class,pss.getNamedStateBlocks().get("secondOrderLag"));
            var text=new StringBuilder("raw_row,time_s,native_memory,memory,native_derivative,derivative,native_vstab,vstab");
            String[] names={"PSS_FILTER1","PSS_FILTER2","PSS_FILTER3","PSS_FILTER4","PSS_LL1","PSS_LL2","PSS_LAST"};
            for(String name:names)text.append(",native_").append(name).append(',').append(name);text.append('\n');
            double previousTime=0,outputMaximum=0,derivativeMaximum=0;var previous=rows.getFirst();int duplicates=0;
            double[] stateMaximum=new double[7];
            for(int index=0;index<rows.size();index++) {
                var row=rows.get(index);double time=row.get("time_s"),dt=time>previousTime?p.nativeStep():0;
                for(double value:row.values())assertTrue(Double.isFinite(value));
                double held=pss.getVoltageDerivative();var storage=pss.getNamedStates();
                bus.setVoltage(new Complex(previous.get(key),0));
                assertTrue(pss.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,f.machine(),0));
                assertEquals(held,first.getU0(),1e-12);
                bus.setVoltage(new Complex(row.get(key),0));
                assertTrue(pss.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,f.machine(),1));
                assertEquals(held,first.getU0(),1e-12);
                if(dt>0)pss.acceptVoltageDerivativeEndpoint(time,p.nativeStep());
                else {
                    if(index>0&&time==rows.get(index-1).get("time_s")){duplicates++;pss.synchronizeVoltageDerivativeMemory();}
                    for(var state:storage.entrySet())assertEquals(state.getValue(),pss.getNamedStates().get(state.getKey()),0);
                }
                assertEquals(row.get("PSS_MEMORY"),pss.getVoltageDerivativeMemory(),1e-12,p.name()+" memory row "+index);
                double derivativeError=Math.abs(row.get("PSS_VDOT")-pss.getVoltageDerivative());
                derivativeMaximum=Math.max(derivativeMaximum,derivativeError);
                double derivativeBound=p.name().contains("flat")?1e-12:
                    p.fault()?(local?(p.step()==.0005?2.5e-7:3e-7):1e-7):(local?1.5e-8:8e-9);
                assertTrue(derivativeError<=derivativeBound,p.name()+" derivative row "+index+" error="+derivativeError);
                double output=pss.getOutput(f.machine());
                double outputError=Math.abs(output-row.get("VOTHSG"));
                outputMaximum=Math.max(outputMaximum,outputError);
                // Fault-output parity is explicitly diagnostic: the existing
                // filter-chain numerical discrepancy must not get a broad bound.
                if(!p.fault()) {
                    double bound=p.name().contains("flat")?1e-12:
                        local?(p.step()==.0005?1.46e-3:7.7e-4):(p.step()==.0005?9.1e-4:4.7e-4);
                    assertTrue(outputError<=bound,p.name()+" output row "+index+" error="+outputError);
                }
                text.append(index).append(',').append(time).append(',').append(row.get("PSS_MEMORY")).append(',').append(pss.getVoltageDerivativeMemory())
                    .append(',').append(row.get("PSS_VDOT")).append(',').append(pss.getVoltageDerivative()).append(',').append(row.get("VOTHSG")).append(',').append(output);
                // These fixtures have A2>0, A4=A5=A6=0. Convert coordinates
                // only for observation; never replace the running realization.
                assertTrue(pss.a2>0);assertEquals(0,pss.a4,0);assertEquals(0,pss.a5,0);assertEquals(0,pss.a6,0);
                var second=assertInstanceOf(SecondOrderLeadLagBlock.class,pss.getNamedStateBlocks().get("secondOrderLeadLag"));
                double[] coordinates={pss.a2*first.getRate()+pss.a1*first.getY(),pss.a2*first.getY(),first.getY(),second.getFirstState(),
                    pss.t2*pss.getNamedStateBlocks().get("leadLag1").getStateX(),pss.t4*pss.getNamedStateBlocks().get("leadLag2").getStateX(),
                    pss.t6*pss.getNamedStateBlocks().get("washout").getStateX()};
                double[] bounds=p.fault()
                    ?(local?new double[]{3.3e-4,4e-6,2.35e-3,1.82e-3,3.45e-4,2.51e-3,1.39e-2}
                            :new double[]{1.12e-4,1.37e-6,8.05e-4,6.24e-4,1.18e-4,8.6e-4,4.75e-3})
                    :(local?new double[]{6.3e-6,1.58e-7,9.3e-5,8.3e-5,1.55e-5,7.95e-5,4.5e-4}
                            :new double[]{2.07e-6,5.2e-8,3.05e-5,2.82e-5,5.55e-6,2.65e-5,1.53e-4});
                for(int i=0;i<names.length;i++) {
                    double error=Math.abs(row.get(names[i])-coordinates[i]);stateMaximum[i]=Math.max(stateMaximum[i],error);
                    double bound=p.name().contains("flat")?1e-12:bounds[i]*(p.step()==.0005?1:.55);
                    assertTrue(error<=bound,p.name()+" "+names[i]+" row "+index+" error="+error+" bound="+bound);
                    text.append(',').append(row.get(names[i])).append(',').append(coordinates[i]);
                }
                text.append('\n');
                if(time>=0){previousTime=time;previous=row;}
            }
            assertEquals(2,duplicates);
            Path file=Path.of("target/ieeest-derivative-playback",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,text);
            System.out.println("IEEEST derivative playback "+p.name()+" derivative="+derivativeMaximum+" output="+outputMaximum);
            if(p.name().contains("reference"))smoothErrors.put(p.name(),outputMaximum);
            if(!p.name().contains("flat"))stateErrors.put(p.name(),stateMaximum);
        }
        for(String location:new String[]{"local","remote"})
            assertTrue(smoothErrors.get(location+"-reference-fine")<.55*smoothErrors.get(location+"-reference-coarse"));
        for(String location:new String[]{"local","remote"})for(String event:new String[]{"fault","reference"}) {
            String prefix=location+"-"+event+"-";
            for(int i=0;i<7;i++)assertTrue(stateErrors.get(prefix+"fine")[i]<.55*stateErrors.get(prefix+"coarse")[i],
                prefix+" observed coordinate "+i+" must refine independently");
        }
    }

    private static final class EndpointSolver extends PsseDStabSolver {
        private final IeeestStabilizer stabilizer;
        private final BaseDStabBus<?,?> measured;
        private double memory,held;
        int accepted,events;
        EndpointSolver(DynamicSimuAlgorithm algorithm,IeeestStabilizer stabilizer,BaseDStabBus<?,?> measured) {
            super(algorithm);this.stabilizer=stabilizer;this.measured=measured;
        }
        @Override public boolean networkSolutionStep() throws DStabSimuException {
            boolean solved=super.networkSolutionStep();
            if(solved&&"INITIAL".equals(networkSolutionStage)) {
                memory=stabilizer.getVoltageDerivativeMemory();held=stabilizer.getVoltageDerivative();
                if(eventBoundaryThisStep){events++;assertEquals(measured.getVoltageMag(),memory,0);}
            } else if(solved&&"PREDICTOR".equals(networkSolutionStage)) {
                assertEquals(memory,stabilizer.getVoltageDerivativeMemory(),0);
                assertEquals(held,stabilizer.getVoltageDerivative(),0);
            } else if(solved&&"CORRECTOR".equals(networkSolutionStage)) {
                double expected=.8*held+.2*(measured.getVoltageMag()-memory)/timestep;
                assertEquals(expected,stabilizer.getVoltageDerivative(),1e-12);
                assertEquals(measured.getVoltageMag(),stabilizer.getVoltageDerivativeMemory(),0);accepted++;
            }
            return solved;
        }
    }

    @Test void coupledSolverCommitsOnlySettledEndpointsAndRebasesBothFaultSides(@TempDir Path directory) throws Exception {
        for(var p:IeeestDerivativeReference.profiles()) {
            var f=IeeestPowerInputSmibTest.load(p.network(),p.input(),p.step(),p.fault(),directory.resolve(p.name()));
            var bus=(BaseDStabBus<?,?>)f.algorithm().getNetwork().getBus(p.name().startsWith("local-")?"Bus1":"Bus2");
            var solver=new EndpointSolver(f.algorithm(),f.stabilizer(),bus);
            f.algorithm().setSolver(solver);assertTrue(f.algorithm().initialization());
            var exc=assertInstanceOf(IEEE1981ST1Exciter.class,f.machine().getExciter());double reference=exc.getRefPoint();
            int ticks=(int)Math.ceil(p.end()/p.step());f.algorithm().setTotalSimuTimeSec(ticks*p.step());
            var text=new StringBuilder("time_s,V_BUS1,V_BUS2,SPEED_DEV,memory,derivative,vstab\n");
            for(int tick=0;tick<=ticks;tick++) {
                if(p.name().contains("reference")&&tick==Math.round(.05/p.step()))exc.setRefPoint(reference+.01);
                double[] values={tick*p.step(),f.machine().getDStabBus().getVoltageMag(),f.algorithm().getNetwork().getBus("Bus2").getVoltageMag(),
                    f.machine().getSpeed()-1,f.stabilizer().getVoltageDerivativeMemory(),f.stabilizer().getVoltageDerivative(),f.stabilizer().getOutput(f.machine())};
                for(int i=0;i<values.length;i++){assertTrue(Double.isFinite(values[i]));if(i>0)text.append(',');text.append(values[i]);}text.append('\n');
                if(tick<ticks)assertTrue(f.algorithm().solveDEqnStep(true));
            }
            assertEquals(ticks,solver.accepted);assertEquals(p.fault()?2:0,solver.events);
            double held=f.stabilizer().getVoltageDerivative(),memory=f.stabilizer().getVoltageDerivativeMemory();
            assertTrue(solver.networkSolutionStep());
            assertEquals(held,f.stabilizer().getVoltageDerivative(),0);assertEquals(memory,f.stabilizer().getVoltageDerivativeMemory(),0);
            Path file=Path.of("target/ieeest-derivative-coupled",p.name()+".csv");Files.createDirectories(file.getParent());Files.writeString(file,text);
        }
    }
}
