package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.Perc1Data;
import org.interpss.dstab.dynLoad.impl.Perc1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.algo.DynamicSimuMethod;

public class Perc1ModelTest {
    private static final Path DATA=Path.of("testData","adpter","psse","v33");
    @BeforeAll static void setup(){IpssCorePlugin.init();}

    @Test void importsThirtyParametersAndInitializesNineNamedStates()throws Exception{
        var context=new PSSEMultiFileLoader().loadDStab(DATA.resolve("ieee9_v33.raw").toString(),
                DATA.resolve("ieee9_perc1.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        var bus=context.getDStabilityNet().getDStabBus("Bus5");
        Perc1Model model=assertInstanceOf(Perc1Model.class,bus.getDynLoadModelList().get(0));
        assertEquals(.8,model.getData().lfm(),1e-12);assertTrue(model.initStates());
        assertEquals(Set.of("PLeadLag","QLeadLag","PWashout","QWashout","wFilt","VFilt","Ip","Iq","FracOn"),
                model.getNamedStates().keySet());
        assertEquals(1.0,model.getNamedState("FracOn"),0.0);
        assertEquals(1.5625,model.getMvaBase()/100,1e-8);
        assertEquals(1.25,model.getInitLoadPQ().getReal(),1e-8);
        assertEquals(.5,model.getInitLoadPQ().getImaginary(),1e-8);
    }

    @Test void modifiedEulerVoltageFilterMatchesIndependentEquation()throws Exception{
        Perc1Model model=loaded();var bus=model.getDStabBus();
        double v0=model.getNamedState("VFilt"),dt=.005,v1=.8;
        bus.setVoltage(new Complex(v1,0));
        model.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,0);
        model.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,1);
        double predictor=v0+dt*(v1-v0)/model.getData().tv();
        double expected=v0+.5*dt*((v1-v0)/model.getData().tv()+(v1-predictor)/model.getData().tv());
        assertEquals(expected,model.getNamedState("VFilt"),1e-12);
        assertTrue(Double.isFinite(model.getNortonCurInj().abs()));
    }

    @Test void voltageTrajectoryMatchesExpectedOperatingValues()throws Exception{
        Perc1Data data=new Perc1Data(.8,.66,0,0,0,0,.1,0,.1,0,0,0,0,0,1,2,0,2,-2,
                .6,.9,.02,.01,.95,.02,.04,.5,.01,0,0);
        Perc1Model model=loaded(data);var bus=model.getDStabBus();double dt=.01;
        bus.setVoltage(new Complex(.8,0));
        for(int i=0;i<4;i++)advance(model,dt);
        assertEquals("CEASED",model.getOperatingMode());
        assertEquals(.4,model.getFractionOn(),1e-12);
        assertEquals(.4,model.getNamedState("FracOn"),1e-12);
        assertEquals(.4,((Number)model.getStates(null).get("PERC1_FracOn")).doubleValue(),1e-12);
        bus.setVoltage(new Complex(1,0));
        for(int i=0;i<3;i++)advance(model,dt);
        assertEquals("RAMP",model.getOperatingMode());
        for(int i=0;i<2;i++)advance(model,dt);
        assertEquals(.55,model.getFractionOn(),1e-12);
        for(int i=0;i<2;i++)advance(model,dt);
        assertEquals("MONITOR",model.getOperatingMode());
        assertEquals(.7,model.getFractionOn(),1e-12);
    }

    @Test void appliesPublishedParameterCorrectionsWithoutMutatingInputData()throws Exception{
        Perc1Data invalid=new Perc1Data(1.2,.4,0,0,0,0,.1,0,.1,0,0,0,0,0,1,1,0,.66,-.66,
                1.5,.9,-1,-1,.8,0,0,-.5,.0001,.02,.02);
        Perc1Model model=loaded(invalid);var bus=model.getDStabBus();
        assertEquals(125.0/1.2,model.getMvaBase(),1e-8);
        bus.setVoltage(new Complex(.8,0));for(int i=0;i<30;i++)advance(model,.001);
        assertEquals("CEASED",model.getOperatingMode());assertEquals(0,model.getFractionOn(),1e-12);
        bus.setVoltage(new Complex(.85,0));advance(model,.001);
        assertEquals("CEASED",model.getOperatingMode());
        bus.setVoltage(new Complex(.95,0));for(int i=0;i<50;i++)advance(model,.001);
        assertEquals("MONITOR",model.getOperatingMode());assertEquals(0,model.getFractionOn(),1e-12);
        assertEquals(1.2,invalid.lfm(),0);assertEquals(.8,invalid.vrecon(),0);
    }

    @Test void loadChangeScalesNetworkCurrentAndReportedPower()throws Exception{
        Perc1Model model=loaded();model.getNortonCurInj();Complex baseline=model.getLoadPQ();
        assertTrue(model.changeLoad(-.25));model.getNortonCurInj();
        assertEquals(.75*baseline.getReal(),model.getLoadPQ().getReal(),1e-12);
        assertEquals(.75*baseline.getImaginary(),model.getLoadPQ().getImaginary(),1e-12);
        assertTrue(model.changeLoad(-.75));model.getNortonCurInj();
        assertEquals(0.0,model.getLoadPQ().abs(),1e-12);
        assertFalse(model.changeLoad(-1.01));
    }

    private static Perc1Model loaded()throws Exception{return loaded(Perc1Data.defaults());}
    private static Perc1Model loaded(Perc1Data data)throws Exception{
        var context=new PSSEMultiFileLoader().loadDStab(DATA.resolve("ieee9_v33.raw").toString(),
                DATA.resolve("ieee9_perc1.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        var bus=context.getDStabilityNet().getDStabBus("Bus5");bus.getDynLoadModelList().clear();
        Perc1Model model=new Perc1Model(bus,bus.getContributeLoad("1"),"1",data);
        assertTrue(model.initStates());return model;
    }

    private static void advance(Perc1Model model,double dt){
        assertTrue(model.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,0));
        assertTrue(model.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,1));
        assertTrue(model.afterStep(dt));
    }
}
