package org.interpss.threePhase.state;

import com.google.gson.Gson;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.LoadConnectionType;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab1PLoadImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class DStabPhaseLoadStateTest {
    @Test void scalarTransmissionPowerSurvivesAnEmptyPhaseVector(){
        var source=new DStab3PLoadImpl();source.setId("positive-sequence-load");
        source.setLoadCP(new Complex(.1,.02));
        var gson=new Gson();var state=gson.fromJson(gson.toJson(new DStabPhaseLoadState(source)),DStabPhaseLoadState.class);
        source.setLoadCP(new Complex(99,99));
        var restored=DStabPhaseLoadState.create(state);
        assertEquals(new Complex(.1,.02),restored.getLoadCP());
        assertEquals(Complex.ZERO,restored.getInit3PhaseLoad().a_0);
        state.updateEObject(restored);assertEquals(new Complex(.1,.02),restored.getLoadCP());
    }

    @ParameterizedTest @CsvSource({"false,false","false,true","true,false","true,true"})
    void jsonRestorationPreservesElectricalResponseAndIsolation(boolean threePhase, boolean delta) {
        DStab1PLoad source=threePhase?new DStab3PLoadImpl():new DStab1PLoadImpl();
        source.setId("L1"); source.setName("Imported load"); source.setStatus(true);
        source.setCode(AclfLoadCode.CONST_P);
        source.setPhaseCode(threePhase?PhaseCode.ABC:delta?PhaseCode.AB:PhaseCode.A);
        source.setLoadConnectionType(threePhase?(delta?LoadConnectionType.THREE_PHASE_DELTA:LoadConnectionType.THREE_PHASE_WYE):(delta?LoadConnectionType.SINGLE_PHASE_DELTA:LoadConnectionType.SINGLE_PHASE_WYE));
        source.setNominalKV(12.47); source.setVminpu(.8); source.setVmaxpu(1.15);
        source.setLoadCP(new Complex(.6,.3)); source.setLoadCI(new Complex(.05,.02)); source.setLoadCZ(new Complex(.03,.01));
        if(threePhase)source.set3PhaseLoad(new Complex3x1(new Complex(.2,.1),new Complex(.3,.12),new Complex(.4,.14)));
        source.setOpenDssLoadModel(8,1.4,2.2,new double[]{.2,.3,.5,.3,.2,.5,.6});
        var originalNet=ThreePhaseObjectFactory.create3PhaseDStabNetwork();
        var originalBus=ThreePhaseObjectFactory.create3PDStabBus("B1",originalNet);
        var voltage=new Complex3x1(new Complex(.91,0),new Complex(-.455,-.788),new Complex(-.455,.788));
        originalBus.set3PhaseVotlages(voltage); source.setParentBus(originalBus);
        var expected=source.getEquivCurrInj(voltage);
        var gson=new Gson();
        var frozen=gson.toJson(new DStabPhaseLoadState(source));
        source.setLoadCP(new Complex(99,99)); source.setOpenDssLoadModel(4,9,9,null);
        var state=gson.fromJson(frozen,DStabPhaseLoadState.class);
        var restored=DStabPhaseLoadState.create(state);
        assertEquals(source.getClass(),restored.getClass()); assertNotSame(source,restored);
        assertNull(restored.getParentBus()); assertEquals("Imported load",restored.getName());
        assertEquals(12.47,restored.getNominalKV()); assertEquals(.8,restored.getVminpu()); assertEquals(1.15,restored.getVmaxpu());
        assertEquals(8,restored.getOpenDssLoadModel()); assertEquals(1.4,restored.getCvrWatts()); assertEquals(2.2,restored.getCvrVars());
        var copyNet=ThreePhaseObjectFactory.create3PhaseDStabNetwork();
        var copyBus=ThreePhaseObjectFactory.create3PDStabBus("B1",copyNet);
        copyBus.set3PhaseVotlages(voltage); restored.setParentBus(copyBus);
        var actual=restored.getEquivCurrInj(voltage);
        assertEquals(0,expected.a_0.subtract(actual.a_0).abs(),1e-12);
        assertEquals(0,expected.b_1.subtract(actual.b_1).abs(),1e-12);
        assertEquals(0,expected.c_2.subtract(actual.c_2).abs(),1e-12);
        restored.getOpenDssZipv()[0]=99;
        var second=DStabPhaseLoadState.create(state);
        assertEquals(.2,second.getOpenDssZipv()[0]);
        second.setParentBus(copyBus); state.updateEObject(second);
        assertSame(copyBus,second.getParentBus());
        assertThrows(IllegalArgumentException.class,()->state.updateEObject(threePhase?new DStab1PLoadImpl():new DStab3PLoadImpl()));
    }
}
