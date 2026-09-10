package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Test void importsThirtyParametersAndInitializesEightNamedStates()throws Exception{
        var context=new PSSEMultiFileLoader().loadDStab(DATA.resolve("ieee9_v33.raw").toString(),
                DATA.resolve("ieee9_perc1.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        var bus=context.getDStabilityNet().getDStabBus("Bus5");
        Perc1Model model=assertInstanceOf(Perc1Model.class,bus.getDynLoadModelList().get(0));
        assertEquals(.8,model.getData().lfm(),1e-12);assertTrue(model.initStates());
        assertEquals(Set.of("PLeadLag","QLeadLag","PWashout","QWashout","wFilt","VFilt","Ip","Iq"),
                model.getNamedStates().keySet());
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

    @Test void ceaseReconnectAndRampFollowPublishedTimers()throws Exception{
        Perc1Data d=new Perc1Data(.8,.66,0,0,0,0,.1,0,.1,0,0,0,0,0,1,2,0,2,-2,
                .6,.9,.02,.01,.95,.02,.04,.5,.01,0,0);
        Perc1Model model=loaded(d);var bus=model.getDStabBus();double dt=.01;
        bus.setVoltage(new Complex(.8,0));
        for(int i=0;i<4;i++)advance(model,dt);
        assertEquals("CEASED",model.getOperatingMode());assertEquals(.4,model.getFractionOn(),1e-12);
        bus.setVoltage(new Complex(1,0));
        for(int i=0;i<3;i++)advance(model,dt);
        assertEquals("RAMP",model.getOperatingMode());
        for(int i=0;i<2;i++)advance(model,dt);
        assertEquals(.55,model.getFractionOn(),1e-12);
        for(int i=0;i<2;i++)advance(model,dt);
        assertEquals("MONITOR",model.getOperatingMode());assertEquals(.7,model.getFractionOn(),1e-12);
    }

    @Test void matchesIndependentPsse36TrajectoryWhenDrivenByTheSameVoltage()throws Exception{
        Path reference=DATA.resolve("../../../reference/psse/ieee9-perc1/psse.csv").normalize();
        Path manifest=reference.resolveSibling("manifest.json");
        String referenceHash=HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(reference)));
        assertTrue(Files.readString(manifest).contains(referenceHash),
                "PSS/E reference CSV hash is absent from its manifest");
        List<String> lines=Files.readAllLines(reference);
        assertEquals(2004,lines.size(),"Expected header plus 2,003 PSS/E samples");
        String[] headings=lines.get(0).split(",");Map<String,Integer> column=new LinkedHashMap<>();
        for(int i=0;i<headings.length;i++)column.put(headings[i],i);
        String[] initial=lines.stream().skip(1).map(line->line.split(","))
                .filter(row->Double.parseDouble(row[0])>=-1e-9).findFirst().orElseThrow();
        double initialVoltage=value(initial,column,"V_BUS5");
        Perc1Model model=loaded();var bus=model.getDStabBus();
        bus.setVoltage(new Complex(initialVoltage,0));assertTrue(model.initStates());

        Map<String,Double> maximumError=new LinkedHashMap<>();
        String[] previous=initial;double previousTime=value(initial,column,"time_s");
        for(String line:lines.subList(lines.indexOf(String.join(",",initial))+1,lines.size())){
            String[] row=line.split(",");double time=value(row,column,"time_s");
            if(time<previousTime-1e-7)continue;
            double dt=time-previousTime;
            if(dt>1e-7){
                bus.setVoltage(new Complex(value(previous,column,"V_BUS5"),0));
                assertTrue(model.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,0));
                bus.setVoltage(new Complex(value(row,column,"V_BUS5"),0));
                assertTrue(model.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,1));
                assertTrue(model.afterStep(dt));
            }
            compare(maximumError,"VFILT",model.getNamedState("VFilt"),value(row,column,"VFILT"));
            compare(maximumError,"PLEADLAG",model.getNamedState("PLeadLag"),value(row,column,"PLEADLAG"));
            compare(maximumError,"QLEADLAG",model.getNamedState("QLeadLag"),value(row,column,"QLEADLAG"));
            compare(maximumError,"IP",model.getNamedState("Ip"),value(row,column,"IP"));
            compare(maximumError,"IQ",model.getNamedState("Iq"),value(row,column,"IQ"));
            compare(maximumError,"FRACON",model.getFractionOn(),value(row,column,"FRACON"));
            previous=row;previousTime=time;
        }
        assertTrue(maximumError.get("VFILT")<.00038,maximumError::toString);
        assertTrue(maximumError.get("IP")<.013,maximumError::toString);
        assertTrue(maximumError.get("IQ")<.0043,maximumError::toString);
        assertTrue(maximumError.get("FRACON")<.00051,maximumError::toString);
        assertTrue(maximumError.get("PLEADLAG")<5e-8,maximumError::toString);
        assertTrue(maximumError.get("QLEADLAG")<8e-9,maximumError::toString);
        System.out.println("PERC1 PSS/E maximum channel errors: "+maximumError);
    }

    @Test void appliesPublishedLimitCorrectionsWithoutMutatingInputData()throws Exception{
        Perc1Data invalid=new Perc1Data(1.2,.4,0,0,0,0,.1,0,.1,0,0,0,0,0,1,1,0,.66,-.66,
                1.5,.9,-1,-1,.8,0,0,-.5,.0001,.02,.02);
        Perc1Model model=loaded(invalid);var bus=model.getDStabBus();
        assertEquals(156.25,model.getMvaBase(),1e-8);
        bus.setVoltage(new Complex(.8,0));for(int i=0;i<30;i++)advance(model,.001);
        assertEquals("CEASED",model.getOperatingMode());assertEquals(0,model.getFractionOn(),1e-12);
        bus.setVoltage(new Complex(.85,0));advance(model,.001);
        assertEquals("CEASED",model.getOperatingMode());
        bus.setVoltage(new Complex(.95,0));for(int i=0;i<50;i++)advance(model,.001);
        assertEquals("MONITOR",model.getOperatingMode());assertEquals(0,model.getFractionOn(),1e-12);
        assertEquals(1.2,invalid.lfm(),0);assertEquals(.8,invalid.vrecon(),0);
    }

    private static Perc1Model loaded()throws Exception{return loaded(Perc1Data.defaults());}
    private static Perc1Model loaded(Perc1Data data)throws Exception{
        var context=new PSSEMultiFileLoader().loadDStab(DATA.resolve("ieee9_v33.raw").toString(),
                DATA.resolve("ieee9_perc1.dyr").toString());
        assertTrue(context.getDynSimuAlgorithm().getAclfAlgorithm().loadflow());
        var bus=context.getDStabilityNet().getDStabBus("Bus5");
        bus.getDynLoadModelList().clear();
        Perc1Model model=new Perc1Model(bus,bus.getContributeLoad("1"),"1",data);assertTrue(model.initStates());return model;
    }

    private static void advance(Perc1Model model,double dt){
        assertTrue(model.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,0));
        assertTrue(model.nextStep(dt,DynamicSimuMethod.MODIFIED_EULER,1));
        assertTrue(model.afterStep(dt));
    }

    private static double value(String[] row,Map<String,Integer> column,String name){
        return Double.parseDouble(row[column.get(name)]);
    }
    private static void compare(Map<String,Double> errors,String name,double actual,double expected){
        errors.merge(name,Math.abs(actual-expected),Math::max);
    }
}
