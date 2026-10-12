package org.interpss.threePhase.state;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.interpss.state.threephase.Static3PGenState;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticGenerator;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.apache.commons.math3.complex.Complex;

class OpenDSSStaticGeneratorStateTest {
    @Test void modelVoltageLimitsAndTerminalCurrentsSurviveJsonCopy() {
        for(int model:new int[]{1,2,3,7}) {
            var source=new OpenDSSStaticGenerator();source.setId("generator");source.setMvaBase(50);
            source.setPower3Phase(new Complex3x1(new Complex(.1,.02),new Complex(.2,.03),new Complex(.3,.04)),UnitType.PU);
            source.setOpenDssModel(model);source.setVminpu(.88);source.setVmaxpu(1.07);
            var gson=new Gson();var copy=(OpenDSSStaticGenerator)Static3PGenState.create(gson.fromJson(gson.toJson(new Static3PGenState(source)),Static3PGenState.class));
            assertNotSame(source,copy);assertEquals(model,copy.getOpenDssModel());assertEquals(.88,copy.getVminpu());assertEquals(1.07,copy.getVmaxpu());
            for(double magnitude:new double[]{.3,.8,.95,1.2}) {
                double[] voltage={magnitude,0,-.5*magnitude,-Math.sqrt(3)*.5*magnitude,-.5*magnitude,Math.sqrt(3)*.5*magnitude};
                double[] expected=new double[6],actual=new double[6];
                source.addEquivCurrInj(voltage,0,expected,0,7);copy.addEquivCurrInj(voltage,0,actual,0,7);
                assertArrayEquals(expected,actual,1e-12);assertTrue(java.util.Arrays.stream(actual).anyMatch(x->Math.abs(x)>0));
            }
            source.setVminpu(.2);source.setPower3Phase(new Complex3x1(),UnitType.PU);
            assertEquals(.88,copy.getVminpu());assertEquals(new Complex(.1,.02),copy.getPower3Phase(UnitType.PU).a_0);
        }
    }
    @Test void subtypeRegistryDoesNotAcceptUnknownSubclass() {
        assertThrows(IllegalArgumentException.class,()->new Static3PGenState(new OpenDSSStaticGenerator(){}));
    }
}
