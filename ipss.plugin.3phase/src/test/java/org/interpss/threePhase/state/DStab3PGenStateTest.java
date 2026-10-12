package org.interpss.threePhase.state;

import com.google.gson.Gson;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.GeneratorType;
import com.interpss.dstab.mach.MachineModelType;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class DStab3PGenStateTest {
    @ParameterizedTest @EnumSource(value=GeneratorType.class,names={"SYNCH_MACHINE","INVERTER_BASED"})
    void restoresCurrentResponseAndIndependentPhaseData(GeneratorType type) throws Exception {
        var net=ThreePhaseObjectFactory.create3PhaseDStabNetwork();
        var bus=ThreePhaseObjectFactory.create3PDStabBus("B1",net);
        var voltage=new Complex3x1(new Complex(.95,0),new Complex(-.475,-.8227),new Complex(-.475,.8227));
        bus.set3PhaseVotlages(voltage);
        var source=ThreePhaseObjectFactory.create3PGenerator("G1");
        bus.getContributeGenList().add(source); source.setParentBus(bus);
        source.setMvaBase(50); source.setZMultiFactor(2); source.setGenType(type);
        source.setPosGenZ(new Complex(.01,.2)); source.setNegGenZ(new Complex(.02,.25)); source.setZeroGenZ(new Complex(.03,.3));
        source.setPower3Phase(new Complex3x1(new Complex(.2,.1),new Complex(.3,.12),new Complex(.4,.14)),UnitType.PU);
        var expected=source.getPowerflowEquivCurrInj(); var expectedZ=source.getZabc(true).ab;
        var gson=new Gson(); var json=gson.toJson(new DStab3PGenState(source));
        source.getZabc(true).ab=new Complex(99,99); source.getPower3Phase(UnitType.PU).a_0=new Complex(99,99);
        var state=gson.fromJson(json,DStab3PGenState.class);
        var copyNet=ThreePhaseObjectFactory.create3PhaseDStabNetwork();
        var copyBus=ThreePhaseObjectFactory.create3PDStabBus("B1",copyNet); copyBus.set3PhaseVotlages(voltage);
        var restored=DStab3PGenState.create(state); copyBus.getContributeGenList().add(restored); restored.setParentBus(copyBus);
        assertNotSame(source,restored); assertSame(copyBus,restored.getParentBus()); assertEquals(type,restored.getGenType());
        assertEquals(expectedZ,restored.getZabc(true).ab); assertEquals(50,restored.getMvaBase());
        var actual=restored.getPowerflowEquivCurrInj();
        assertEquals(0,expected.a_0.subtract(actual.a_0).abs(),1e-12);
        assertEquals(0,expected.b_1.subtract(actual.b_1).abs(),1e-12);
        assertEquals(0,expected.c_2.subtract(actual.c_2).abs(),1e-12);
        restored.getPower3Phase(UnitType.PU).a_0=Complex.ZERO;
        assertEquals(new Complex(.2,.1),DStab3PGenState.create(state).getPower3Phase(UnitType.PU).a_0);
        var oldY=restored.getYabc(true).aa;
        var replacement=new Complex3x3(); replacement.aa=new Complex(.1,.5); replacement.bb=replacement.aa; replacement.cc=replacement.aa;
        restored.setZabc(replacement);
        assertNotEquals(oldY,restored.getYabc(true).aa);
        assertEquals(0,replacement.aa.reciprocal().subtract(restored.getYabc(true).aa).abs(),1e-12);
        restored.setZabc(new Complex(.04,.3),new Complex(.05,.4),new Complex(.06,.5));
        assertEquals(0,restored.getZabc(true).inv().aa.subtract(restored.getYabc(true).aa).abs(),1e-12);
        state.restorePhaseFields(restored); assertSame(copyBus,restored.getParentBus());
    }

    @Test void preservesAttachedMachineAndNullablePhasePower() throws Exception {
        var net=ThreePhaseObjectFactory.create3PhaseDStabNetwork();
        var bus=ThreePhaseObjectFactory.create3PDStabBus("B1",net);
        var source=ThreePhaseObjectFactory.create3PGenerator("G1"); bus.getContributeGenList().add(source);
        var machine=DStabObjectFactory.createMachine("M1","Machine",MachineModelType.ECONSTANT,net,"B1","G1");
        machine.setH(4.5); machine.setD(.1);
        source.setSourceZ(null); source.setPosGenZ(null); source.setNegGenZ(null); source.setZeroGenZ(null);
        var gson=new Gson(); var state=gson.fromJson(gson.toJson(new DStab3PGenState(source)),DStab3PGenState.class);
        machine.setH(99);
        var copyNet=ThreePhaseObjectFactory.create3PhaseDStabNetwork();
        var copyBus=ThreePhaseObjectFactory.create3PDStabBus("B1",copyNet);
        var restored=DStab3PGenState.create(state); copyBus.getContributeGenList().add(restored);
        assertNull(restored.getPosGenZ());
        var copiedMachine=state.machine.create(copyNet,"B1","G1");
        state.updateEObject(restored);
        assertNull(restored.getPower3Phase(UnitType.PU)); assertNull(restored.getPosGenZ());
        assertEquals(source.getNegGenZ(),restored.getNegGenZ()); assertEquals(source.getZeroGenZ(),restored.getZeroGenZ()); assertEquals(4.5,copiedMachine.getH());
        assertNotSame(machine,copiedMachine); assertSame(copiedMachine,restored.getMach()); assertSame(copyBus,copiedMachine.getDStabBus());
    }
}
