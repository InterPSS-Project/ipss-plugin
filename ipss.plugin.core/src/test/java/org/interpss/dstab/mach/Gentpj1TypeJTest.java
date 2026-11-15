package org.interpss.dstab.mach;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexUtils;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.core.acsc.AcscFactory;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.mach.MachineModelType;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;

/** Synthetic Type-J equations, independent of retained private case vectors. */
class Gentpj1TypeJTest {
    static final DynamicSimuMethod METHOD = DynamicSimuMethod.MODIFIED_EULER;
    static Gentpj1Data data(double kis, boolean saturated) {
        return new Gentpj1Data(6.8, .042, .65, .083, 4.7, .18,
                2.12, 1.38, .34, .52, .21, .19, .13,
                saturated ? .085 : 0, saturated ? .34 : 0, kis);
    }
    record Fixture(DStabilityNetwork network, BaseDStabBus<?, ?> bus, Gentpj1Machine machine) {}

    static Fixture fixture(Gentpj1Data d, double p, double q, double mva, double vm, double angle) throws Exception {
        IpssCorePlugin.init();
        DStabilityNetwork net = DStabObjectFactory.createDStabilityNetwork();
        net.setBaseKva(100000); net.setFrequency(60);
        var bus = (com.interpss.dstab.DStabBus) DStabObjectFactory.createDStabBus("Gen", net).get();
        bus.setBaseVoltage(1000); bus.setGenCode(AclfGenCode.GEN_PQ);
        bus.setLoadCode(AclfLoadCode.NON_LOAD);
        var gen = DStabObjectFactory.createDStabGen("G1");
        bus.getContributeGenList().add(gen);
        gen.setMvaBase(mva);
        var machine = new Gentpj1Machine(d);
        machine.setId("TypeJ"); machine.setName("GENTPJ1");
        machine.setMachType(MachineModelType.EQ11_ED11_ROUND_ROTOR);
        machine.setMachData(DStabObjectFactory.createMachineData());
        machine.getMachData().setGrounding(AcscFactory.eINSTANCE.createBusScGrounding());
        net.addMachine(machine, "Gen", "G1");
        machine.setRating(mva, UnitType.mVA, net.getBaseKva());
        machine.setRatedVoltage(1, UnitType.kV); machine.calMultiFactors();
        machine.setH(d.h()); machine.setD(d.d() * 100 / net.getFrequency()); machine.setRa(.0037);
        machine.setXd(d.xd()); machine.setXq(d.xq()); machine.setXl(d.xl());
        machine.setXd1(d.xdp()); machine.setXq1(d.xqp());
        machine.setXd11(d.xdpp()); machine.setXq11(d.xqpp());
        machine.setTd01(d.tdop()); machine.setTd011(d.tdopp());
        machine.setTq01(d.tqop()); machine.setTq011(d.tqopp());
        gen.setGen(new Complex(p, q).multiply(machine.getRating()));
        gen.setSourceZ(new Complex(machine.getRa(), d.xdpp()));
        bus.setVoltage(ComplexUtils.polar2Complex(vm, angle));
        assertTrue(machine.initStates(bus));
        return new Fixture(net, bus, machine);
    }

    static double[] state(Gentpj1Machine m) {
        return new double[] {m.getPsid11(), -m.getPsiq11(), m.getEq1(), m.getEd1()};
    }

    /** Direct four-voltage-state equations, not a winding-state transform. */
    static double[] rhs(Gentpj1Machine m) {
        var d = m.getGentpj1Data(); var i = m.getIdq();
        double s = m.getEffectiveSaturationFactor(), sd = 1+s, sq = 1+d.xq()/d.xd()*s;
        double[] x = state(m);
        double field = sd*((d.xd()-d.xdpp())*x[2]-(d.xd()-d.xdp())*x[0])/(d.xdp()-d.xdpp());
        return new double[] {
            (sd*(x[2]-x[0])-(d.xdp()-d.xdpp())*i.d)/d.tdopp(),
            (sq*(x[3]-x[1])+(d.xqp()-d.xqpp())*i.q)/d.tqopp(),
            (m.getEfd()-field)/d.tdop(),
            sq*((d.xq()-d.xqp())*x[1]-(d.xq()-d.xqpp())*x[3])/((d.xqp()-d.xqpp())*d.tqop())};
    }

    @Test void scaledQuadraticHasPublishedCalibrationAndIndependentOffGridValue() {
        var m = new Gentpj1Machine(data(.17, true));
        double s1=.085, s12=.34;
        double a=(1.2-Math.sqrt(1.2*s12/s1))/(1-Math.sqrt(1.2*s12/s1));
        double b=s1/((1-a)*(1-a));
        assertEquals(s1,m.getSatruationFactor(1),1e-14);
        assertEquals(s12,m.getSatruationFactor(1.2),1e-14);
        assertEquals(b*Math.pow(1.09-a,2)/1.09,m.getSatruationFactor(1.09),1e-14);
        assertEquals(0,m.getSatruationFactor(a-.01),0);
        assertEquals(1.09+.17*.9,m.saturationInput(1.09,.9),1e-14);
        assertEquals(1,m.getGenqecData().satFunc());
    }

    @ParameterizedTest @CsvSource({".46,.18,37", ".29,-.23,13.5", "-.63,.09,72"})
    void initializationRetainsPowerCopperLossAndAllFourStationaryStates(double p,double q,double mva) throws Exception {
        var f=fixture(data(.11,true),p,q,mva,1.035,-.21);var m=f.machine();
        assertTrue(m.checkData(null));
        Complex current=m.getIgen().subtract(f.bus().getVoltage().multiply(m.getYgen()));
        Complex power=f.bus().getVoltage().multiply(current.conjugate()).divide(m.getRating());
        assertEquals(p,power.getReal(),2e-11);assertEquals(q,power.getImaginary(),2e-11);
        double loss=m.getRa()*Math.pow(current.abs()/m.getIMultiFactor(),2);
        assertEquals(p+loss,m.getPe(),2e-11);assertEquals(m.getPe(),m.getPm(),2e-11);
        assertEquals(m.getEfd(),m.calculateIfd(MachineIfdBase.EXCITER),2e-11);
        for(double v:rhs(m))assertEquals(0,v,2e-10);
        var x=state(m);m.nextStepElectrical(.001,METHOD,f.network(),0);
        m.nextStepElectrical(.001,METHOD,f.network(),1);assertArrayEquals(x,state(m),2e-12);
        double s=m.getEffectiveSaturationFactor();
        assertEquals(m.getXl()+(m.getXq()-m.getXl())/(1+m.getXq()/m.getXd()*s),m.getXqAdjusted(),2e-12);
        assertEquals(m.getXl()+(m.getXd()-m.getXl())/(1+s),m.getXdAdjusted(),2e-12);
    }

    @ParameterizedTest @CsvSource({"0,false", ".16,true"})
    void predictorAndCorrectorUseTheDirectRhsAndAcceptedOrigin(double kis,boolean sat) throws Exception {
        var f=fixture(data(kis,sat),.61,-.12,43,1.025,-.14);var m=f.machine();double dt=.0013;
        f.bus().setVoltage(ComplexUtils.polar2Complex(.89,-.08));m.setEfd(m.getEfd()+.17);
        m.getIgen();double[] origin=state(m),left=rhs(m),predicted=new double[4];
        for(int i=0;i<4;i++)predicted[i]=origin[i]+dt*left[i];
        assertTrue(m.nextStepElectrical(dt,METHOD,f.network(),0));assertArrayEquals(predicted,state(m),2e-12);
        f.bus().setVoltage(ComplexUtils.polar2Complex(.92,-.10));m.setEfd(m.getEfd()-.06);
        m.getIgen();double[] right=rhs(m),expected=new double[4];
        for(int i=0;i<4;i++)expected[i]=origin[i]+.5*dt*(left[i]+right[i]);
        assertTrue(m.nextStepElectrical(dt,METHOD,f.network(),1));assertArrayEquals(expected,state(m),2e-12);
    }

    @Test void zeroDurationRefreshPreservesPendingDerivativeHistory() throws Exception {
        var a=fixture(data(.12,true),.58,-.16,26,1.03,-.13);
        var b=fixture(data(.12,true),.58,-.16,26,1.03,-.13);
        for(var f:List.of(a,b)) {
            f.bus().setVoltage(ComplexUtils.polar2Complex(.87,-.19));
            f.machine().nextStepElectrical(.001,METHOD,f.network(),0);
        }
        var before=a.machine().getNativeNamedStates();
        a.machine().nextStepElectrical(0,METHOD,a.network(),0);
        a.machine().nextStepElectrical(0,METHOD,a.network(),1);
        assertEquals(before,a.machine().getNativeNamedStates());
        for(var f:List.of(a,b)) {
            f.bus().setVoltage(ComplexUtils.polar2Complex(.93,-.16));
            f.machine().nextStepElectrical(.001,METHOD,f.network(),1);
        }
        assertArrayEquals(state(a.machine()),state(b.machine()),0);
    }

    @Test void invalidOrUnmatchedStageDoesNotAdvanceStates() throws Exception {
        var f=fixture(data(0,true),.4,.1,30,1.02,.1);var m=f.machine();var before=m.getNativeNamedStates();
        assertThrows(IllegalStateException.class,()->m.nextStepElectrical(.001,METHOD,f.network(),1));
        assertThrows(IllegalArgumentException.class,()->m.nextStepElectrical(-.001,METHOD,f.network(),0));
        assertThrows(IllegalArgumentException.class,()->m.nextStepElectrical(Double.NaN,METHOD,f.network(),0));
        assertEquals(before,m.getNativeNamedStates());
        m.nextStepElectrical(.001,METHOD,f.network(),0);before=m.getNativeNamedStates();
        assertThrows(IllegalStateException.class,()->m.nextStepElectrical(.001,METHOD,f.network(),0));
        assertThrows(IllegalStateException.class,()->m.nextStepElectrical(.002,METHOD,f.network(),1));
        assertEquals(before,m.getNativeNamedStates());
    }

    @Test void reinitializationUsesNewSourcePointAndClearsPendingHistory() throws Exception {
        var f=fixture(data(.19,true),.62,.22,47,1.045,-.1);var m=f.machine();
        f.bus().setVoltage(new Complex(.81,-.12));m.nextStepElectrical(.001,METHOD,f.network(),0);
        var fresh=fixture(data(.19,true),.24,-.18,47,1.015,-.22);
        f.bus().setVoltage(fresh.bus().getVoltage());m.getParentGen().setGen(fresh.machine().getParentGen().getGen());
        assertTrue(m.initStates(f.bus()));
        assertEquals(fresh.machine().getNativeNamedStates(),m.getNativeNamedStates());
        assertEquals(fresh.machine().getEfd(),m.getEfd(),0);
        m.nextStepElectrical(.001,METHOD,f.network(),0);m.nextStepElectrical(.001,METHOD,f.network(),1);
        assertArrayEquals(state(fresh.machine()),state(m),2e-12);
    }

    @Test void currentSolveSatisfiesSalientStatorAndSaturationAtDeepVoltageDip() throws Exception {
        var f=fixture(data(.23,true),.68,.19,53,1.04,-.12);var m=f.machine();
        for(double vm:new double[]{.31,.68,1.12,.94}) {
            f.bus().setVoltage(ComplexUtils.polar2Complex(vm,-.25));m.getIgen();
            var v=m.getVdq();var i=m.getIdq();
            assertEquals(v.q,m.getPsid11()-m.getXdppSaturated()*i.d-m.getRa()*i.q,2e-10);
            assertEquals(v.d,-m.getPsiq11()+m.getXqppSaturated()*i.q-m.getRa()*i.d,2e-10);
            double psi=Math.hypot(v.q+m.getRa()*i.q+m.getXl()*i.d,v.d+m.getRa()*i.d-m.getXl()*i.q);
            assertEquals(m.getSatruationFactor(psi+.23*Math.hypot(i.d,i.q)),m.getEffectiveSaturationFactor(),2e-10);
        }
    }

    @Test void ieeeVcAndObservationAliasesRetainTheirBoundaryContract() throws Exception {
        var f=fixture(data(.14,true),.55,-.19,35,1.03,-.15);var m=f.machine();
        for(double xc:new double[]{.07,-.09}) {
            m.setIeeeVcData(new IeeeVcData(.02,xc));
            var before=m.getNativeNamedStates();Complex v=f.bus().getVoltage();
            Complex i=m.getIgen().subtract(v.multiply(m.getYgen()));
            double expected=v.add(new Complex(.02,xc).multiply(m.getZMultiFactor()).multiply(i)).abs()/m.getVMultiFactor();
            assertEquals(expected,m.getCmlMachineVoltage(),2e-12);assertEquals(before,m.getNativeNamedStates());
            assertEquals(v.abs()/m.getVMultiFactor(),m.getCompensatedVoltage(),0);
        }
        var named=m.getNamedStates();assertTrue(named.keySet().containsAll(List.of("E'q","E'd","Psi'd","Psi'q","Angle","Speed deviation")));
        double ad=(m.getXd11()-m.getXl())/(m.getXd1()-m.getXl());
        assertEquals(m.getPsid11(),ad*m.getEq1()+(1-ad)*named.get("Psi'd"),2e-12);
        assertThrows(UnsupportedOperationException.class,()->named.put("E'q",0.));
        m.setIeeeVcData(null);assertEquals(m.getCompensatedVoltage(),m.getCmlMachineVoltage(),0);
    }

    @Test void documentedSalientBranchRemovesOnlyTheQTransientState() throws Exception {
        var d=new Gentpj1Data(6.4,.04,0,.07,4.1,0,2.1,1.4,.35,.5,.22,.20,.12,.08,.32,.1);
        var f=fixture(d,.47,-.15,39,1.025,-.19);var m=f.machine();var before=state(m);
        assertTrue(m.checkData(null));assertEquals(0,m.getEd1(),0);
        for(int i=0;i<5;i++) {m.nextStepElectrical(.0005,METHOD,f.network(),0);m.nextStepElectrical(.0005,METHOD,f.network(),1);}
        assertArrayEquals(before,state(m),1e-11);
    }

    @ParameterizedTest @CsvSource({"0,.07", ".04,0", "1e-12,.07", ".04,1e-12"})
    void unqualifiedZeroSubtransientTimeIsRejectedInsteadOfCollapsed(double td2,double tq2) {
        assertThrows(IllegalArgumentException.class,()->new Gentpj1Data(
            6.4,td2,.5,tq2,4.1,0,2.1,1.4,.35,.5,.22,.20,.12,.08,.32,.1));
    }
    @Test void offNominalStatorCoordinatesAndCopperLossHaveNoExtraSpeedMultiplier() throws Exception {
        var f=fixture(data(.18,true),.53,-.17,41,1.032,-.16);var m=f.machine();
        Complex before=m.getIgen();double pe=m.getPe();
        for(double speed:new double[]{.97,1.04}) {
            m.setSpeed(speed);Complex norton=m.getIgen();assertEquals(before,norton);
            var v=m.getVdq();var i=m.getIdq();
            assertEquals(v.q,m.getPsid11()-m.getXdppSaturated()*i.d-m.getRa()*i.q,2e-11);
            assertEquals(v.d,-m.getPsiq11()+m.getXqppSaturated()*i.q-m.getRa()*i.d,2e-11);
            assertEquals(v.d*i.d+v.q*i.q+m.getRa()*(i.d*i.d+i.q*i.q),m.getPe(),2e-11);
            assertEquals(pe,m.getPe(),0);
        }
    }

    @Test void mechanicalStageUsesPowerOverActualSpeedAndEnteredDamping() throws Exception {
        var f=fixture(data(.1,true),.57,-.12,38,1.024,-.11);var m=f.machine();
        double speed=1.035,dt=.0007;m.setSpeed(speed);m.setPm(m.getPe()+.12);
        double damping=m.getD()*.01*f.network().getFrequency();
        double expected=(m.getPm()-m.getPe()-damping*(speed-1))/(2*m.getH()*speed);
        assertEquals(expected,m.cal_dW_dt(speed,f.network().getFrequency()),2e-14);
        m.nextStepMechanical(dt,METHOD,f.network(),0);
        assertEquals(speed+dt*expected,m.getSpeed(),2e-14);
    }

    @Test void sameTimeDeepFaultCrossesSaturationKneeWithoutAdvancingRotorStates() throws Exception {
        // Zero Kis removes current-driven saturation, allowing a voltage-only
        // dip to exercise the actual below-knee branch without parameter fitting.
        var f=fixture(data(0,true),.48,.14,36,1.04,-.13);var m=f.machine();
        var initial=m.getNativeNamedStates();Complex voltage=f.bus().getVoltage();double pe=m.getPe();
        assertTrue(m.getEffectiveSaturationFactor()>0);
        f.bus().setVoltage(ComplexUtils.polar2Complex(.015,-.13));
        m.nextStepElectrical(0,METHOD,f.network(),0);
        assertEquals(initial,m.getNativeNamedStates());assertEquals(0,m.getEffectiveSaturationFactor(),1e-12);
        assertTrue(Math.abs(pe-m.getPe())>.01);
        f.bus().setVoltage(voltage);m.nextStepElectrical(0,METHOD,f.network(),1);
        assertEquals(initial,m.getNativeNamedStates());assertEquals(pe,m.getPe(),2e-11);
    }

    @ParameterizedTest @CsvSource({"-.11,-1", ".11,1"})
    void disturbedMotoringStaysConnectedAndBalancesGridShaftAndKineticEnergy(
            double shaftPowerChange,double expectedSpeedDirection) throws Exception {
        var f=fixture(data(.13,true),-.58,.07,37,1.028,-.16);var m=f.machine();
        var report=org.interpss.dstab.validation.DynamicParameterValidator.validate(f.network(),.0005,1,ignored->1);
        assertEquals(0,report.count(org.interpss.dstab.validation.DynamicParameterValidator.Severity.ERROR),report.issues().toString());
        assertEquals(1,report.checkedModels().get("Gentpj1Machine"));
        assertSame(m,m.getParentGen().getDynamicGenDevice());
        assertTrue(m.getPm()<0);double enteredP=m.getParentGen().getGen().getReal();
        m.setPm(m.getPm()+shaftPowerChange);assertTrue(m.getPm()<0);
        double speed0=m.getSpeed(),dt=.0005,gridEnergy=0,shaftEnergy=0,copperEnergy=0,dampingEnergy=0;
        Complex v=f.bus().getVoltage();
        Complex current=m.getIgen().subtract(v.multiply(m.getYgen()));
        double input=-v.multiply(current.conjugate()).getReal()/m.getRating();
        double copper=m.getRa()*Math.pow(current.abs()/m.getIMultiFactor(),2);
        double damping=m.getD()*.01*f.network().getFrequency()*(m.getSpeed()-1);
        for(int step=0;step<80;step++) {
            double previousInput=input,previousCopper=copper,previousDamping=damping;
            assertTrue(m.nextStep(dt,METHOD,0));m.getIgen();assertTrue(m.nextStep(dt,METHOD,1));
            current=m.getIgen().subtract(v.multiply(m.getYgen()));
            input=-v.multiply(current.conjugate()).getReal()/m.getRating();
            copper=m.getRa()*Math.pow(current.abs()/m.getIMultiFactor(),2);
            damping=m.getD()*.01*f.network().getFrequency()*(m.getSpeed()-1);
            gridEnergy+=.5*dt*(previousInput+input);
            shaftEnergy+=dt*(-m.getPm());
            copperEnergy+=.5*dt*(previousCopper+copper);
            dampingEnergy+=.5*dt*(previousDamping+damping);
            assertTrue(input>0,"The machine remains an electrical consumer");
            assertTrue(m.getPm()<0&&m.isActive()&&m.getParentGen().isActive());
            assertSame(m,m.getParentGen().getDynamicGenDevice());
        }
        // Observable energy conservation, not a copy of the mechanical RHS:
        // grid input supplies shaft work, stator losses and kinetic change.
        double kinetic=m.getH()*(m.getSpeed()*m.getSpeed()-speed0*speed0);
        assertEquals(gridEnergy-shaftEnergy-copperEnergy-dampingEnergy,kinetic,1e-6);
        assertTrue(gridEnergy>0&&shaftEnergy>0&&copperEnergy>0);
        assertTrue(expectedSpeedDirection*(m.getSpeed()-speed0)>1e-5);
        assertEquals(enteredP,m.getParentGen().getGen().getReal(),0);
    }

    @Test void negativeFieldVoltageSettlesToSignedFieldCurrentWithoutOutputFloor() throws Exception {
        var f=fixture(data(0,true),.46,.18,37,1.035,-.21);var m=f.machine();
        Complex terminalVoltage=f.bus().getVoltage();
        double rotorAngle=m.getAngle(),speed=m.getSpeed();
        // An externally held terminal phasor and rotor coordinate isolate the
        // electrical equilibrium. The entered synthetic machine is unchanged;
        // a negative field-voltage command must admit a negative field current.
        m.setEfd(-.24);
        double dt=.001;double[] tailStart=null;
        for(int step=0;step<40000;step++) {
            if(step==39000)tailStart=state(m);
            assertTrue(m.nextStepElectrical(dt,METHOD,f.network(),0));
            assertTrue(m.nextStepElectrical(dt,METHOD,f.network(),1));
            for(double value:state(m))assertTrue(Double.isFinite(value));
            assertTrue(Double.isFinite(m.calculateIfd(MachineIfdBase.EXCITER)));
        }
        // At a stationary field winding the imposed voltage equals signed
        // field current in exciter units. A zero floor would violate this
        // observable equilibrium despite apparently settled electrical states.
        assertEquals(-.24,m.getEfd(),0);
        assertTrue(m.calculateIfd(MachineIfdBase.EXCITER)<-.1);
        assertEquals(m.getEfd(),m.calculateIfd(MachineIfdBase.EXCITER),1e-8);
        assertArrayEquals(tailStart,state(m),1e-8);
        assertEquals(terminalVoltage,f.bus().getVoltage());
        assertEquals(rotorAngle,m.getAngle(),0);assertEquals(speed,m.getSpeed(),0);
        Complex current=m.getIgen().subtract(terminalVoltage.multiply(m.getYgen()));
        double terminalP=terminalVoltage.multiply(current.conjugate()).getReal()/m.getRating();
        double copperLoss=m.getRa()*Math.pow(current.abs()/m.getIMultiFactor(),2);
        assertEquals(terminalP+copperLoss,m.getPe(),2e-11);
        assertTrue(copperLoss>0);
    }


}
