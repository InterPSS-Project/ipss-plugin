package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.Stream;
import com.interpss.dstab.algo.DynamicSimuMethod;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.impl.LD1PACImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Loading-factor writes must reach the physical rating and both shared integration paths. */
public class LD1PACParameterBindingTest {
    @BeforeAll static void setup() { IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu"); }

    @Test void defaultLoadingFactorIsVisibleThroughThePublicContract() {
        assertEquals(.85,new LD1PACImpl().getLoadFactor(),0);
    }

    @Test void initializedCharacteristicCannotChangeBeforeTheFirstNortonRead() throws Exception {
        var composite=Cmldblu1SharedStateTest.model("cmldblu1-mixed");
        var motor=new LD1PACImpl(composite.getLoadBus(),"policy-contract");
        motor.setLoadPercent(15);motor.setLoadFactor(.82);
        assertTrue(motor.initStates());
        var states=motor.getIntegrationStates();
        assertThrows(IllegalStateException.class,
                ()->motor.setCharacteristicPolicy(LD1PACImpl.CharacteristicPolicy.LEGACY_COMPOSITE));
        motor.setCharacteristicPolicy(LD1PACImpl.CharacteristicPolicy.STANDALONE);
        assertEquals(states,motor.getIntegrationStates());
    }

    static Stream<Arguments> methods() {
        return Stream.of(.65,.82,1.).flatMap(loading->Stream.of(false,true).map(shared->Arguments.of(loading,shared)));
    }

    @ParameterizedTest @MethodSource("methods")
    void configuredLoadingReachesRatingTemperatureAndAcceptedUpdates(double loading,boolean shared) throws Exception {
        var composite=Cmldblu1SharedStateTest.model("cmldblu1-mixed");
        var bus=composite.getLoadBus();
        var motor=new LD1PACImpl(bus,"loading-contract");
        motor.setLoadPercent(15);motor.setLoadFactor(loading);motor.setMvaBase(0);
        motor.setPowerFactor(.95);motor.setRstall(.11);motor.setXstall(.13);
        motor.setTv(.025);motor.setTf(.07);motor.setTth(13);
        motor.setVstall(.58);motor.setVbrk(.84);motor.setLFadj(.04);
        assertTrue(motor.initStates());
        assertEquals(loading,motor.getLoadFactor(),0);
        double systemMva=bus.getNetwork().getBaseMva();
        assertEquals(motor.getInitLoadPQ().getReal()*systemMva/loading,motor.getMvaBase(),1e-12,
                "The configured loading, rather than a hidden default, determines the standalone rating");
        double voltage=bus.getVoltageMag();
        double temperature=.11*Math.pow(loading/(.95*voltage),2);
        assertEquals(temperature,motor.getNamedStates().get("TemperatureA"),1e-14);
        assertEquals(temperature,motor.getNamedStates().get("TemperatureB"),1e-14);
        assertEquals(.58*(1+.04*(loading-1)),motor.getVstall(),1e-14);
        assertEquals(.84*(1+.04*(loading-1)),motor.getVbrk(),1e-14);
        var initial=motor.getIntegrationStates();double h=.0005;
        if(shared) {
            double[] state=new double[4],rates=new double[4];motor.readStaggeredState(state,0);
            motor.evaluateStaggered(h,rates,0);
            for(double rate:rates)assertEquals(0,rate,1e-13,"Initialized standalone motor must be in equilibrium");
            motor.applyStaggeredState(state,0,h);motor.completeStaggeredStep(h);
        } else {
            assertTrue(motor.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,0));
            assertTrue(motor.nextStep(h,DynamicSimuMethod.MODIFIED_EULER,1));
        }
        assertTrue(motor.afterStep(h));
        for(var entry:initial.entrySet())assertEquals(entry.getValue(),motor.getIntegrationStates().get(entry.getKey()),1e-14);
        double adjustedStall=motor.getVstall(),adjustedBreak=motor.getVbrk();
        assertTrue(motor.initStates());
        assertEquals(adjustedStall,motor.getVstall(),0,"Fresh initialization cannot compound the loading adjustment");
        assertEquals(adjustedBreak,motor.getVbrk(),0);
    }
}
