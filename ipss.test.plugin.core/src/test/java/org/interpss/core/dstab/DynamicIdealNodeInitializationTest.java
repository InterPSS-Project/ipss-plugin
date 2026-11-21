package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.core.algo.impl.solver.ZbrNrSolver;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.defaultImpl.DynamicIdealNodeMatrix;
import com.interpss.dstab.algo.defaultImpl.DynamicEventProcessor;

/** Synthetic public fixtures exercise the actual import/initialization owner.
 * Ideal currents must replace only solver-created internal residuals, never a
 * physical caller source even when that source has zero component aggregate. */
class DynamicIdealNodeInitializationTest {
    @TempDir Path temp;
    private record Fixture(BaseDStabNetwork<?,?> net,DynamicSimuAlgorithm algo) {}
    private Fixture fixture(boolean physicalSource)throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Path raw=temp.resolve(UUID.randomUUID()+".raw"),dyr=temp.resolve(UUID.randomUUID()+".dyr");
        Files.writeString(raw,"""
                0,100,33,0,1,60 / synthetic ideal-node lifecycle
                SYNTHETIC FOUR BUS
                0
                1,'BUS 1',230,3,1,1,1,1.03,0,1.1,.9,1.1,.9
                2,'BUS 2',230,2,1,1,1,1.01,0,1.1,.9,1.1,.9
                3,'BUS 3',230,2,1,1,1,1.01,0,1.1,.9,1.1,.9
                4,'BUS 4',230,1,1,1,1,1.0,0,1.1,.9,1.1,.9
                0 / END OF BUS DATA, BEGIN LOAD DATA
                4,'1',1,1,1,180,60,0,0,0,0,1,1,0
                0 / END OF LOAD DATA, BEGIN FIXED SHUNT DATA
                0 / END OF FIXED SHUNT DATA, BEGIN GENERATOR DATA
                1,'1',40,0,100,-100,1.03,0,100,0,.2,0,0,1,1,100,200,-100,1,1
                2,'1',70,10,100,-100,1.01,0,100,0,.2,0,0,1,1,100,200,-100,1,1
                3,'1',70,30,100,-100,1.01,0,100,0,.2,0,0,1,1,100,200,-100,1,1
                0 / END OF GENERATOR DATA, BEGIN BRANCH DATA
                1,4,'1',.01,.1,0,0,0,0,0,0,0,0,1,1,0,1,1
                2,4,'1',.02,.2,0,0,0,0,0,0,0,0,1,1,0,1,1
                3,4,'1',.03,.3,0,0,0,0,0,0,0,0,1,1,0,1,1
                2,3,'BS',0,.00005,0,0,0,0,0,0,0,0,1,1,0,1,1
                0 / END OF BRANCH DATA, BEGIN TRANSFORMER DATA
                0 / END OF TRANSFORMER DATA, BEGIN AREA DATA
                0 / END OF AREA DATA, BEGIN TWO-TERMINAL DC DATA
                0 / END OF TWO-TERMINAL DC DATA, BEGIN VSC DC LINE DATA
                0 / END OF VSC DC LINE DATA, BEGIN IMPEDANCE CORRECTION DATA
                0 / END OF IMPEDANCE CORRECTION DATA, BEGIN MULTI-TERMINAL DC DATA
                0 / END OF MULTI-TERMINAL DC DATA, BEGIN MULTI-SECTION LINE DATA
                0 / END OF MULTI-SECTION LINE DATA, BEGIN ZONE DATA
                0 / END OF ZONE DATA, BEGIN INTER-AREA TRANSFER DATA
                0 / END OF INTER-AREA TRANSFER DATA, BEGIN OWNER DATA
                0 / END OF OWNER DATA, BEGIN FACTS DEVICE DATA
                0 / END OF FACTS DEVICE DATA, BEGIN SWITCHED SHUNT DATA
                0 / END OF SWITCHED SHUNT DATA, BEGIN GNE DATA
                0 / END OF GNE DATA, BEGIN INDUCTION MACHINE DATA
                0 / END OF INDUCTION MACHINE DATA
                Q
                """);
        Files.writeString(dyr,"1 'GENCLS' '1' 99999 0 /\n2 'GENCLS' '1' 4 1 /\n3 'GENCLS' '1' 4 1 /\n");
        var context=new PSSEMultiFileLoader().setAutoDiscoverCasePreparationFiles(false)
                .setDynamicRunConfigJson("{\"activeStaticLoadModel\":\"CONST_I\",\"reactiveStaticLoadModel\":\"CONST_Z\"}")
                .loadDStab(raw.toString(),dyr.toString());
        var net=context.getDStabilityNet();var algo=context.getDynSimuAlgorithm();var pf=algo.getAclfAlgorithm();
        net.setZeroZBranchThreshold(.0001);pf.getLfCalculator().setNrSolver(new ZbrNrSolver(net,pf.getNrMethodConfig()));
        pf.setNonDivergent(false);pf.setTolerance(1e-9);pf.setMaxIterations(40);assertTrue(pf.loadflow());
        algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuStepSec(.0033333);algo.setTotalSimuTimeSec(.02);
        algo.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(physicalSource) {
            var table=new Hashtable<String,Complex>();table.put("Bus2",new Complex(.02,.01));table.put("Bus3",new Complex(-.02,-.01));
            net.setCustomBusCurrInjHashtable(table);
        }
        return new Fixture(net,algo);
    }
    private static void near(Complex a,Complex b,double tol){assertEquals(0,a.subtract(b).abs(),tol);}

    @Test void actualInitializationUsesSparseConstraintsWithoutArtificialInternalSource()throws Exception {
        var f=fixture(false);assertTrue(f.algo.initialization());
        var matrix=assertInstanceOf(DynamicIdealNodeMatrix.class,f.net.getYMatrix());
        assertEquals(4,matrix.getBusRowCount());assertEquals(1,matrix.getIdealCurrentRowCount());
        assertTrue(f.net.getCustomBusCurrInjHashtable().values().stream().allMatch(i->i.abs()<1e-7));
        assertTrue(f.net.solveNetEqn());assertTrue(matrix.getConstrainedSolveCount()>0);
        near(f.net.getBus("Bus2").getVoltage(),f.net.getBus("Bus3").getVoltage(),1e-12);
        double before=f.net.getMachine("Bus2-mach1").getSpeed();
        for(int i=0;i<6;i++)assertTrue(f.algo.solveDEqnStep(true));
        assertEquals(before,f.net.getMachine("Bus2-mach1").getSpeed(),1e-8);
    }

    @Test void callerOwnedZeroSumSourceKeepsIdentityValuesAndPhysicalTransfer()throws Exception {
        var base=fixture(false);assertTrue(base.algo.initialization());assertTrue(base.net.solveNetEqn());
        var baseEdge=base.net.getBranch("Bus2","Bus3","BS");
        var i0=((DynamicIdealNodeMatrix)base.net.getYMatrix()).terminalCurrent(baseEdge,true);
        var f=fixture(true);var owner=f.net.getCustomBusCurrInjHashtable();var before=Map.copyOf(owner);
        assertTrue(f.algo.initialization());assertSame(owner,f.net.getCustomBusCurrInjHashtable());assertEquals(before,owner);
        assertTrue(f.net.solveNetEqn());
        var edge=f.net.getBranch("Bus2","Bus3","BS");
        var i1=((DynamicIdealNodeMatrix)f.net.getYMatrix()).terminalCurrent(edge,true);
        near(new Complex(.02,.01),i1.subtract(i0),2e-7);
        for(var bus:f.net.getBusList())near(base.net.getBus(bus.getId()).getVoltage(),bus.getVoltage(),2e-8);
        assertSame(owner,f.net.getCustomBusCurrInjHashtable());assertEquals(before,owner);
    }

    // solveNetEqn() deliberately performs one algebraic iteration. Settle the
    // fixed-current/voltage load boundary before comparing open/reclosed states;
    // no differential state or simulation clock is advanced in this helper.
    private static void settle(BaseDStabNetwork<?,?> net)throws Exception {
        for(int i=0;i<60;i++) {
            var before=net.getBusList().stream().map(b->b.getVoltage()).toList();
            assertTrue(net.solveNetEqn());double maximum=0;
            for(int k=0;k<before.size();k++)maximum=Math.max(maximum,before.get(k).subtract(net.getBusList().get(k).getVoltage()).abs());
            if(maximum<1e-12)return;
        }
        fail("Algebraic fixed point did not settle");
    }

    @Test void outageRecloseAndExternalRebuildUseCurrentTopology()throws Exception {
        var f=fixture(false);assertTrue(f.algo.initialization());assertTrue(f.net.solveNetEqn());
        var edge=f.net.getBranch("Bus2","Bus3","BS");var processor=new DynamicEventProcessor();
        settle(f.net);var before=edge.powerFrom2To();assertTrue(before.abs()>.01);
        edge.setStatus(false);processor.rebuildCurrentTopology(f.net);
        assertFalse(f.net.getYMatrix() instanceof DynamicIdealNodeMatrix);settle(f.net);
        near(Complex.ZERO,edge.powerFrom2To(),0);near(Complex.ZERO,edge.powerTo2From(),0);
        edge.setStatus(true);processor.rebuildCurrentTopology(f.net);
        var matrix=assertInstanceOf(DynamicIdealNodeMatrix.class,f.net.getYMatrix());
        assertEquals(1,matrix.getIdealCurrentRowCount());settle(f.net);
        near(before,edge.powerFrom2To(),1e-7);
        near(f.net.getBus("Bus2").getVoltage(),f.net.getBus("Bus3").getVoltage(),1e-12);
    }

    @Test void activeBranchFaultRebuildExcludesOriginalIdealConstraintAndStampsOnce()throws Exception {
        var f=fixture(false);assertTrue(f.algo.initialization());
        var edge=f.net.getBranch("Bus2","Bus3","BS");
        edge.setStatus(false);f.net.formYMatrix4DStab();
        int n=f.net.getNoBus(),i=edge.getFromBus().getSortNumber(),j=edge.getToBus().getSortNumber();
        var ordinary=new Complex[n][n];
        for(int r=0;r<n;r++)for(int c=0;c<n;c++)ordinary[r][c]=f.net.getYMatrix().getA(r,c);
        edge.setStatus(true);
        var event=DStabObjectFactory.createDEvent("fault","fault",com.interpss.dstab.devent.DynamicSimuEventType.BRANCH_FAULT,f.net);
        var fault=DStabObjectFactory.createDStabBranchFault("fault");fault.setFaultBranch(edge);
        fault.setFaultCode(com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P);
        fault.setDistance(.4);fault.setZLGFault(new Complex(0,.1));fault.setZLLFault(new Complex(0,.1));
        event.setBranchFault(fault);event.setStatus(true);
        var yi=edge.getZ().multiply(.4).reciprocal();var yj=edge.getZ().multiply(.6).reciprocal();
        var denominator=yi.add(yj).add(new Complex(0,.1).reciprocal());
        ordinary[i][i]=ordinary[i][i].add(yi).subtract(yi.multiply(yi).divide(denominator));
        ordinary[j][j]=ordinary[j][j].add(yj).subtract(yj.multiply(yj).divide(denominator));
        var cross=yi.multiply(yj).divide(denominator).negate();
        ordinary[i][j]=ordinary[i][j].add(cross);ordinary[j][i]=ordinary[j][i].add(cross);
        var processor=new DynamicEventProcessor();
        for(int repeat=0;repeat<2;repeat++) {
            processor.rebuildCurrentTopology(f.net);
            assertTrue(edge.isActive(),"Keep public branch status after stamping the fault equivalent");
            assertEquals(n,f.net.getYMatrix().getDimension(),"No ideal constraint across the faulted branch");
            for(int r=0;r<n;r++)for(int c=0;c<n;c++)near(ordinary[r][c],f.net.getYMatrix().getA(r,c),1e-9);
        }
    }
}