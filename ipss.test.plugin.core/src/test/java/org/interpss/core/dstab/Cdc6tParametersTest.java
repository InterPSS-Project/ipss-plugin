package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.Arrays;
import org.interpss.dstab.hvdc.*;
import org.interpss.dstab.hvdc.Cdc6tNormalRuntime.OperatingMode;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Test;

public class Cdc6tParametersTest {
    private static double[] constants(){return new double[]{5,20,.05,.05,.9,1.05,.02,.2,.3,.02,
            250,100,3,4,200,250,450,500,2000,425,2000,.02,-999,999,-999,0,0,999,999,0,999,.05};}
    private static PsseDyrRecord record(String header,double[] constants)throws Exception{
        String values=String.join(" ",Arrays.stream(constants).mapToObj(Double::toString).toList());
        return PsseDyrRecordReader.read(new StringReader(header+values+" /\n"),"synthetic.dyr").get(0);
    }
    private static Cdc6tParameters.Prepared prepare(Cdc6tParameters parameters){
        return parameters.prepare(new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8),
                new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8),5,
                new Cdc6tParameters.OperatingPoint(Cdc6tCurrentOrder.Mode.POWER,-500,500,0,0,500000,1000,505000));
    }
    @Test void namedRecordPreservesEveryConstantAndOwnsAnImmutableCopy()throws Exception{
        var raw=constants();var record=record("'Line, O''Brien' 'CDC6T' ",raw);
        var p=Cdc6tRecordMapper.prepare(record);assertEquals("Line, O'Brien",p.linkName());
        assertArrayEquals(raw,p.copySourceConstants());assertEquals(-999,p.copySourceConstants()[24],0);
        var direct=new Cdc6tParameters("direct",raw);raw[2]=999;
        double[] copy=direct.copySourceConstants();copy[3]=999;
        assertEquals(.05,direct.copySourceConstants()[2],0);assertEquals(.05,direct.copySourceConstants()[3],0);
        assertEquals("-999.0",record.parameter(24),"Preparation must not replace the source sentinel");
    }
    @Test void mapperRejectsWrongIdentityCardinalityAndInvalidFieldsAtSource()throws Exception{
        assertThrows(IllegalArgumentException.class,()->Cdc6tRecordMapper.prepare(record("1 'CDC6T' '1' ",constants())));
        assertThrows(IllegalArgumentException.class,()->Cdc6tRecordMapper.prepare(record("'link' 'SVSMO3T2' ",constants())));
        for(int count:new int[]{31,33}){
            var failure=assertThrows(IllegalArgumentException.class,()->Cdc6tRecordMapper.prepare(record("'link' 'CDC6T' ",Arrays.copyOf(constants(),count))));
            assertTrue(failure.getMessage().contains("synthetic.dyr:1"));
        }
        for(int index=0;index<32;index++){
            var invalid=constants();invalid[index]=Double.NaN;
            var failure=assertThrows(IllegalArgumentException.class,()->Cdc6tRecordMapper.prepare(record("'link' 'CDC6T' ",invalid)));
            assertTrue(failure.getMessage().contains("synthetic.dyr:1"));
        }
        var invalid=constants();invalid[14]=invalid[16];assertThrows(IllegalArgumentException.class,()->new Cdc6tParameters("link",invalid));
        invalid[14]=200;invalid[24]=-1;assertThrows(IllegalArgumentException.class,()->new Cdc6tParameters("link",invalid));
    }
    @Test void unequalTransducerConstantsFeedBothIntegrationInterfaces(){
        var source=constants();source[2]=.02;source[3]=.04;source[31]=.08;
        var p=new Cdc6tParameters("link",source);var runtime=prepare(p).runtime();double[] output=new double[4],rates=new double[3];
        runtime.evaluateBoundary(0,0,0,0,0,OperatingMode.BLOCKED,1,1,output,0);
        runtime.evaluateStaggered(.001,rates,0);assertArrayEquals(new double[]{-25000000,-25000,-6312500},rates,1e-8);
        var local=prepare(p).runtime();local.evaluateBoundary(0,0,0,0,0,OperatingMode.BLOCKED,1,1,output,0);local.step(.001,0);
        local.evaluateBoundary(0,0,0,0,0,OperatingMode.BLOCKED,1,1,output,0);local.step(.001,1);
        double[] state=new double[3];local.readStaggeredState(state,0);
        double[] initial={500000,1000,505000},times={.02,.04,.08};
        for(int i=0;i<3;i++){double ratio=.001/times[i];assertEquals(initial[i]*(1-ratio+.5*ratio*ratio),state[i],1e-8);}
    }
    @Test void independentBundlesMapRecoveryUnitsFloorAndHistory(){
        var p=new Cdc6tParameters("link",constants());var first=prepare(p);var second=prepare(p);
        var c=first.protection();c.beginTrial(.05);c.observe(.8,1,500000);c.acceptTrial();
        assertEquals(OperatingMode.BLOCKED,c.operatingMode());assertEquals(OperatingMode.NORMAL,second.protection().operatingMode());
        c.beginTrial(.1);c.observe(1.2,1,0);c.acceptTrial();assertEquals(OperatingMode.RECOVERING,c.operatingMode());
        assertEquals(.5,c.voltageFactor(),0);assertEquals(.1,c.currentFactor(),0);
        double[] output=new double[4],boundary=new double[8];
        first.runtime().evaluateBoundary(1,0,1,0,0,c.operatingMode(),c.voltageFactor(),c.currentFactor(),output,0);
        first.runtime().readBoundary(boundary,0);assertEquals(250000,boundary[7],0);assertEquals(200,boundary[0]*1e8/boundary[6],1e-10);
        c.beginTrial(.15);c.observe(1.2,1,250000);c.acceptTrial();
        assertEquals(.65,c.voltageFactor(),1e-15);assertEquals(.3,c.currentFactor(),1e-15);
        first.runtime().applyStaggeredState(new double[]{1,2,3},0,0);
        double[] state=new double[3];second.runtime().readStaggeredState(state,0);assertArrayEquals(new double[]{500000,1000,505000},state);
    }
    @Test void dynamicMinimumAnglesAreConvertedFromDegrees(){
        var source=constants();source[0]=40;source[1]=55;var runtime=prepare(new Cdc6tParameters("link",source)).runtime();
        double[] output=new double[4],boundary=new double[8];runtime.evaluateBoundary(1,0,1,0,0,output,0);runtime.readBoundary(boundary,0);
        assertEquals(55,Math.toDegrees(boundary[5]),1e-10);
        runtime.evaluateBoundary(.7,0,1,0,0,output,0);runtime.readBoundary(boundary,0);
        assertEquals(40,Math.toDegrees(boundary[2]),1e-10);
    }
    @Test void staticGammaOverridesDynamicGammaWithoutChangingSourceConstants(){
        var rectifier=new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,13.5939,1e8);
        var inverter=new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,13.3174,1e8);
        var point=new Cdc6tParameters.OperatingPoint(Cdc6tCurrentOrder.Mode.CURRENT,1000,500,0,0,500000,1000,505000);
        double[] expected=null;
        for(double dynamicGamma:new double[]{5,35,55}){
            var source=constants();source[1]=dynamicGamma;var p=new Cdc6tParameters("link",source);
            var runtime=p.prepareConstantGamma(rectifier,inverter,5,point,15).runtime();
            double[] output=new double[4],boundary=new double[8];
            runtime.evaluateBoundary(1,0,1,0,0,output,0);runtime.readBoundary(boundary,0);
            assertEquals(15,Math.toDegrees(boundary[5]),1e-10);
            if(expected==null)expected=boundary;else assertArrayEquals(expected,boundary);
            assertArrayEquals(source,p.copySourceConstants());
            for(double invalid:new double[]{-1,90,Double.NaN,Double.POSITIVE_INFINITY})
                assertThrows(IllegalArgumentException.class,()->p.prepareConstantGamma(rectifier,inverter,5,point,invalid));
        }
    }
}
