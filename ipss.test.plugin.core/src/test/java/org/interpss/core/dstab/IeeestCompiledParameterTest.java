package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import org.interpss.CorePluginTestSetup;
import org.interpss.core.dstab.reference.GenrouNativeReference;
import com.interpss.dstab.controller.pss.ieeest.IeeestData;
import com.interpss.dstab.controller.pss.ieeest.IeeestStabilizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Compiled field bindings retain live parameters and the ordinary stage history. */
class IeeestCompiledParameterTest extends CorePluginTestSetup {
    private static IeeestData data() {
        return new IeeestData(1,0,.061,.0017,.02,0,0,0,.3,.03,.3,.03,5,5,5,.05,-.05,0,0);
    }

    private static final class CustomProvider extends IeeestStabilizer {
        CustomProvider() { super("custom",data(),null); }
        @Override public double getDoubleField(String name) { return 37.25; }
    }

    private static void same(double expected,double actual,String label) {
        assertEquals(Double.doubleToRawLongBits(expected),Double.doubleToRawLongBits(actual),label);
    }

    @Test void compiledPublicParametersObserveWritesWithoutRecompiling() throws Exception {
        var model=new IeeestStabilizer("fields",data(),null);
        int checked=0;
        for(var field:IeeestStabilizer.class.getFields()) {
            if(field.getType()!=double.class || Modifier.isStatic(field.getModifiers()))continue;
            var binding=model.bindCompiledParameter(field.getName());
            for(double value:new double[]{0.,-0.,.123456789,-19.25,Double.MIN_VALUE,Double.MAX_VALUE,Double.NaN}) {
                field.setDouble(model,value);
                same(model.getDoubleField(field.getName()),binding.getAsDouble(),field.getName());
                checked++;
            }
        }
        assertEquals(203,checked);
    }

    @Test void customParameterProviderKeepsItsOverride() throws Exception {
        same(37.25,new CustomProvider().bindCompiledParameter("speedGain").getAsDouble(),"custom provider");
    }

    @Test void compiledStagesMatchOrdinaryStagesForEveryInputSelector(@TempDir Path directory) throws Exception {
        var profile=GenrouNativeReference.profiles().stream().filter(p->p.name().equals("flat-coarse")).findFirst().orElseThrow();
        for(int mode=1;mode<=6;mode++) {
            String dynamic=profile.input()+"1 'ESST1A' '1' 1 1 .02 .8 -.8 .2 .8 .1 .4 80 .04 99 -99 7.3 -7.3 0 .1 1 0 1 /\n"
                    +"1 'IEEEST' '1' "+mode+" 0 .061 .0017 .02 0 0 0 .3 .03 .3 .03 5 5 5 .05 -.05 0 0 /\n";
            var ordinary=IeeestPowerInputSmibTest.load(profile.network(),dynamic,.0005,directory.resolve("ordinary-"+mode));
            var compiled=IeeestPowerInputSmibTest.load(profile.network(),dynamic,.0005,directory.resolve("compiled-"+mode));
            compiled.stabilizer().compileRuntimePlan();
            for(int tick=0;tick<1000;tick++)for(int stage=0;stage<2;stage++) {
                double time=(tick+.5*stage)*.0005;
                for(var fixture:List.of(ordinary,compiled)) {
                    var machine=fixture.machine();
                    machine.setSpeed(1+.002*Math.sin(17*time));machine.setPe(.5+.02*Math.sin(11*time));machine.setPm(.5+.01*Math.cos(13*time));
                    machine.getDStabBus().setFreq(1+.001*Math.sin(9*time));machine.getDStabBus().setVoltageMag(1+.03*Math.sin(7*time));
                    if(tick%50==0) { fixture.stabilizer().ks=4.5+.001*tick;fixture.stabilizer().speedRef=1+.00001*tick; }
                    assertTrue(fixture.stabilizer().nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,machine,stage));
                }
                same(ordinary.stabilizer().getOutput(ordinary.machine()),compiled.stabilizer().getOutput(compiled.machine()),"selector "+mode);
                var expected=ordinary.stabilizer().getNamedStates();var actual=compiled.stabilizer().getNamedStates();
                assertEquals(expected.keySet(),actual.keySet());
                for(var state:expected.entrySet())same(state.getValue(),actual.get(state.getKey()),state.getKey());
            }
        }
    }
}
