package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.*;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Complete network initialization and no-event test; independent fault acceptance remains separate. */
public class Cdc6tCoupledNetworkTest extends CorePluginTestSetup {
    @TempDir Path folder;
    @ParameterizedTest @ValueSource(booleans={false,true})
    void ordinaryConductionMaintainsTheSolvedNetwork(boolean staggered) throws Exception {
        var fixture=load(folder);
        var context=fixture.context();var runtime=fixture.runtime();
        var net=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        double v7=net.getBus("Bus7").getVoltageMag(),v9=net.getBus("Bus9").getVoltageMag();
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(.0005);algorithm.setTotalSimuTimeSec(.1);
        algorithm.setSimuOutputHandler(new StateMonitor());
        algorithm.setSolver(staggered?new StaggeredEulerSolver(algorithm):new DefaultDynamicSimulationSolver(algorithm));
        assertTrue(algorithm.initialization());
        double worst=0;
        while(algorithm.getSimuTime()<.1-.00025) {
            assertTrue(algorithm.solveDEqnStep(true));
            worst=Math.max(worst,Math.max(Math.abs(net.getBus("Bus7").getVoltageMag()-v7),Math.abs(net.getBus("Bus9").getVoltageMag()-v9)));
        }
        double[] states=new double[3];runtime.readStaggeredState(states,0);
        System.out.println("CDC6T coupled flat staggered="+staggered+" maxVoltage="+worst+" states="+java.util.Arrays.toString(states));
        assertTrue(worst<1e-5,"Initial network mismatch or drift "+worst);
        assertArrayEquals(new double[]{500000,1000,505000},states,.01);
    }
    record Fixture(com.interpss.simu.SimuContext context,Cdc6tNormalRuntime runtime,Cdc6tNormalTerminalPorts ports) {}
    static Fixture load(Path folder) throws Exception {
        var lines=Files.readAllLines(Path.of("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC_PsetOnInv.raw"));
        return load(folder,lines,Cdc6tCurrentOrder.Mode.POWER,-500,Cdc6tVoltageCurrentLimit.fromSourcePoints(250,450,500,2000,425,2000));
    }
    static Fixture load(Path folder,java.util.List<String> lines,Cdc6tCurrentOrder.Mode mode,double setpoint,
            Cdc6tVoltageCurrentLimit limit) throws Exception {
        return load(folder,lines,mode,setpoint,limit,null);
    }
    static Fixture load(Path folder,java.util.List<String> lines,Cdc6tCurrentOrder.Mode mode,double setpoint,
            Cdc6tVoltageCurrentLimit limit,Cdc6tProtectionCoordinator protection) throws Exception {
        return load(folder,lines,mode,setpoint,limit,protection,400);
    }
    static Fixture load(Path folder,java.util.List<String> lines,Cdc6tCurrentOrder.Mode mode,double setpoint,
            Cdc6tVoltageCurrentLimit limit,Cdc6tProtectionCoordinator protection,double minimumCurrent) throws Exception {
        for(int n=3;n<lines.size();n++) {
            if(lines.get(n).strip().startsWith("0 /"))break;
            String[] fields=lines.get(n).split(",",-1);
            if(Integer.parseInt(fields[0].strip())==8){fields[3]="4";lines.set(n,String.join(",",fields));}
        }
        Files.write(folder.resolve("network.raw"),lines);
        Files.writeString(folder.resolve("dynamic.dyr"),"1 'GENCLS' '1' 99999 0 /\n2 'GENCLS' '1' 99999 0 /\n3 'GENCLS' '1' 99999 0 /\n4 'GENCLS' '1' 99999 0 /\n");
        var context=new PSSEMultiFileLoader().loadDStab(folder.resolve("network.raw").toString(),folder.resolve("dynamic.dyr").toString());
        var net=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        var staticLink=(com.interpss.core.aclf.hvdc.HvdcLine2TLCC<?>)net.getSpecialBranchList().get(0);
        assertSame(net.getBus("Bus7"),staticLink.getRectifier().getBus());
        assertSame(net.getBus("Bus9"),staticLink.getInverter().getBus());
        assertEquals("Bus7",staticLink.getRectifier().getId());
        assertEquals("Bus9",staticLink.getInverter().getId());
        net.setStaticLoadIncludedInYMatrix(true);net.setStaticLoadModel(StaticLoadModel.CONST_Z);net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        algorithm.getAclfAlgorithm().setTolerance(1e-10);algorithm.getAclfAlgorithm().setMaxIterations(80);
        algorithm.getAclfAlgorithm().getLfAdjAlgo().setApplyAdjustAlgo(false);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        var runtime=new Cdc6tNormalRuntime(new Cdc6tMeasurements(.05,.05,.05),
            new Cdc6tCurrentOrder(mode,setpoint,500,0),limit,
            new Cdc6tConductingLink(new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8),
                new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8),5,Math.toRadians(5),Math.toRadians(20)),minimumCurrent);
        var pair=new Cdc6tNormalTerminalPorts("1",net.getBus("Bus7"),net.getBus("Bus9"),runtime,500000,1000,505000,protection);
        pair.attach();
        return new Fixture(context,runtime,pair);
    }
}
