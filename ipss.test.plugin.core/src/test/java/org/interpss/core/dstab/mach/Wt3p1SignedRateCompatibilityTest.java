package org.interpss.core.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.mach.Wt3g2Model;
import org.interpss.dstab.mach.Wt3p1Data;
import org.interpss.dstab.mach.Wt3p1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Explicit converted-interface rate policy; native validation is unchanged. */
public class Wt3p1SignedRateCompatibilityTest {
    private Wt3p1Data data(double rate, Wt3p1Data.RateLimitPolicy policy) {
        return new Wt3p1Data(.2, 100, 20, 2, 15, -4, 25, rate, 1, policy);
    }
    @Test void signedPolicyPreservesInputAndClampsBothDirections() {
        var negative = data(-8, Wt3p1Data.RateLimitPolicy.SIGNED_MAGNITUDE);
        assertEquals(-8, negative.thetaRateMax());
        assertEquals(8, negative.preparedRateMagnitude());
        assertEquals(8, Wt3p1Model.publishedPitchRate(negative,0,20,0,.5,0,1,.001));
        assertEquals(-8, Wt3p1Model.publishedPitchRate(negative,0,-20,0,-.5,0,1,.001));
        assertThrows(IllegalArgumentException.class, () -> data(-8, Wt3p1Data.RateLimitPolicy.NONNEGATIVE));
        assertThrows(IllegalArgumentException.class, () -> data(Double.NaN, Wt3p1Data.RateLimitPolicy.SIGNED_MAGNITUDE));
    }
    @Test void positiveAndNegativeRatesHaveIdenticalAcceptedStateHistories() {
        var positive = new Wt3p1Model(data(8,Wt3p1Data.RateLimitPolicy.NONNEGATIVE));
        var negative = new Wt3p1Model(data(-8,Wt3p1Data.RateLimitPolicy.SIGNED_MAGNITUDE));
        positive.initialize(0,0,1); negative.initialize(0,0,1);
        for(int step=0;step<1000;step++) {
            double speed=step<500?.2:-.2;
            for(int flag=0;flag<2;flag++) {
                positive.step(.001,speed,0,1,flag); negative.step(.001,speed,0,1,flag);
                assertEquals(positive.getNamedStates(),negative.getNamedStates());
            }
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void convertedSmibExportsCompleteBoundary(boolean staggered) throws Exception {
        String folder=System.getProperty("legacy.ge.reference");
        Assumptions.assumeTrue(folder!=null,"Private converted-interface diagnostic must be explicitly selected");
        Path input=Path.of(folder), output=Files.createTempDirectory(Path.of("target"),"converted-wt3-"+(staggered?"staggered-":"euler-"));
        String dyr=Files.readString(input.resolve("dyr_loaded-converted.dyr"));
        String pitch=dyr.lines().filter(s->s.contains("'WT3P1'")).findFirst().orElseThrow();
        List<String> tokens=PsseDyrRecordReader.tokenize(pitch.replace("/",""));
        double[] c=new double[9]; for(int i=0;i<c.length;i++)c[i]=Double.parseDouble(tokens.get(i+3));
        var data=new Wt3p1Data(c[0],c[1],c[2],c[3],c[4],c[5],c[6],c[7],c[8],Wt3p1Data.RateLimitPolicy.SIGNED_MAGNITUDE);
        Path staged=output.resolve("converted-with-typed-pitch.dyr"); Files.writeString(staged,dyr.replace(pitch,"").replaceAll("(?m)^.*GENCLS.*$", ""));
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        var context=new PSSEMultiFileLoader().loadDStab(input.resolve("inputs/network.raw").toString(),staged.toString());
        var net=context.getDStabilityNet();var algo=context.getDynSimuAlgorithm();
        DStabObjectFactory.createInfiniteMachine("Bus2-mach1", "InfiniteBus", net, "Bus2", "1");
        var gen=(DStabGen)net.getBus("Bus1").getContributeGen("1");
        var converter=assertInstanceOf(Wt3g2Model.class,gen.getDynamicGenDevice());
        converter.getDriveTrain().setPitchController(new Wt3p1Model(data),converter.getElectricalController());
        algo.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algo.getAclfAlgorithm().loadflow());
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(.0005);algo.setTotalSimuTimeSec(.15);
        algo.setSimuOutputHandler(new StateMonitor());
        List<double[]> rows=new ArrayList<>();
        Runnable capture=()-> {
            Complex voltage=net.getBus("Bus1").getVoltage();
            Complex source=(Complex)converter.getOutputObject();
            Complex current=source.subtract(voltage.divide(gen.getPosGenZ().multiply(gen.getZMultiFactor())));
            Complex power=voltage.multiply(current.conjugate()).multiply(net.getBaseMva());
            rows.add(new double[]{algo.getSimuTime(),voltage.abs(),net.getBus("Bus2").getVoltageMag(),
                    power.getReal(),power.getImaginary()});
        };
        if(staggered)algo.setSolver(new StaggeredEulerSolver(algo) {
            double lastEvent=-1;
            @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
                boolean solved=super.networkSolutionStep(); double time=algo.getSimuTime();
                if(solved && "INITIAL".equals(networkSolutionStage) && time!=lastEvent
                        && (Math.abs(time-.05)<1e-9 || Math.abs(time-.1)<1e-9)) {
                    capture.run();lastEvent=time;
                }
                return solved;
            }
        });
        else algo.setSolver(new DefaultDynamicSimulationSolver(algo) {
            double lastEvent=-1;
            @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
                boolean solved=super.networkSolutionStep(); double time=algo.getSimuTime();
                if(solved && "INITIAL".equals(networkSolutionStage) && time!=lastEvent
                        && (Math.abs(time-.05)<1e-9 || Math.abs(time-.1)<1e-9)) {
                    capture.run();lastEvent=time;
                }
                return solved;
            }
        });
        net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.05,.05),"Fault");
        assertTrue(algo.initialization());
        capture.run();
        while(algo.getSimuTime()<.15-.00025) { assertTrue(algo.solveDEqnStep(true));capture.run(); }
        assertEquals(303,rows.size(),"Both event-side duplicates must be retained");
        try(var writer=Files.newBufferedWriter(output.resolve("boundary.csv"))) {
            writer.write("time_s,V1,V2,P_MW,Q_MVAR\n");
            for(double[] row:rows)writer.write(row[0]+","+row[1]+","+row[2]+","+row[3]+","+row[4]+"\n");
        }
        System.out.println("Converted WT3 diagnostic: "+output.toAbsolutePath());
    }
}
