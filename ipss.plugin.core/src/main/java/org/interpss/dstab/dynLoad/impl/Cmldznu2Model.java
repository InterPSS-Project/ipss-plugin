package org.interpss.dstab.dynLoad.impl;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.Cmldznu2Data;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.LD1PAC;

import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Zone-scoped composite load assembled from the native distribution and motor components. */
public final class Cmldznu2Model extends DynLoadCMPLDWImpl implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;

    private final Cmldznu2Data data;
    private final Set<String> targetLoadIds;
    private final Complex targetPower;
    private double staticFraction;
    private Cmldznu2AlgebraicLoadModel staticLoadComponent;
    private Cmldznu2AlgebraicLoadModel electronicLoadComponent;

    public Cmldznu2Model(String id, BaseDStabBus<?, ?> bus, Set<String> targetLoadIds,
            Complex targetPower, Cmldznu2Data data) {
        super(id, bus);
        if (targetLoadIds == null || targetLoadIds.isEmpty()) {
            throw new IllegalArgumentException("CMLDZNU2 requires at least one target load");
        }
        this.data = data;
        this.targetLoadIds = Set.copyOf(new LinkedHashSet<>(targetLoadIds));
        this.targetPower = targetPower;
        setId(id);
        setGroupId("zone:" + (bus.getZone() == null ? "" : bus.getZone().getNumber()));
        configure();
    }

    private void configure() {
        setMvaBase(data.value(0));
        getDistEquivalent().setBSubStation(data.value(1));
        getDistEquivalent().setRFdr(data.value(2));
        getDistEquivalent().setXFdr(data.value(3));
        getDistEquivalent().setFB(data.value(4));
        getDistEquivalent().setXXf(data.value(5));
        getDistEquivalent().setTFixHS(data.value(6));
        getDistEquivalent().setTFixLS(data.value(7));
        getDistEquivalent().setLTC((int) Math.rint(data.value(8)));
        getDistEquivalent().setTMin(data.value(9));
        getDistEquivalent().setTMax(data.value(10));
        getDistEquivalent().setStep(data.value(11));
        getDistEquivalent().setVMin(data.value(12));
        getDistEquivalent().setVMax(data.value(13));
        getDistEquivalent().setTDelay(data.value(14));
        getDistEquivalent().setTTap(data.value(15));
        getDistEquivalent().setRComp(data.value(16));
        getDistEquivalent().setXComp(data.value(17));

        double[] fractions = {data.value(18), data.value(19), data.value(20),
                data.value(21), data.value(22)};
        double sum = java.util.Arrays.stream(fractions).sum();
        if (sum > 1.0) for (int i = 0; i < fractions.length; i++) fractions[i] /= sum;
        setFmA(fractions[0]); setFmB(fractions[1]); setFmC(fractions[2]);
        setFmD(fractions[3]); setFel(fractions[4]);
        staticFraction = Math.max(0.0, 1.0 - java.util.Arrays.stream(fractions).sum());

        setMotorTypeA((int) Math.rint(data.value(37)));
        setMotorTypeB((int) Math.rint(data.value(57)));
        setMotorTypeC((int) Math.rint(data.value(77)));
        setMotorTypeD(1);
        configureMotor(getInductionMotorA(), 38);
        configureMotor(getInductionMotorB(), 58);
        configureMotor(getInductionMotorC(), 78);
        configureMotorD(get1PhaseACMotor());
    }

    private void configureMotor(InductionMotor motor, int base) {
        motor.setLoadFactor(data.value(base));
        motor.setRa(data.value(base + 1));
        motor.setXs(data.value(base + 2));
        motor.setXp(data.value(base + 3));
        motor.setXpp(data.value(base + 4));
        motor.setTp0(data.value(base + 5));
        motor.setTpp0(data.value(base + 6));
        motor.setH(data.value(base + 7));
        motor.setTorqueExponent(data.value(base + 8));
        motor.setVtr1(data.value(base + 9));
        motor.setTtr1(data.value(base + 10));
        motor.setFtr1(data.value(base + 11));
        motor.setVrc1(data.value(base + 12));
        motor.setTrc1(data.value(base + 13));
        motor.setVtr2(data.value(base + 14));
        motor.setTtr2(data.value(base + 15));
        motor.setFtr2(data.value(base + 16));
        motor.setVrc2(data.value(base + 17));
        motor.setTrc2(data.value(base + 18));
    }

    private void configureMotorD(LD1PAC motor) {
        motor.setTstall(data.value(97)); motor.setTrst(data.value(98));
        motor.setTv(data.value(99)); motor.setTf(data.value(100));
        motor.setLoadFactor(data.value(101));
        motor.setPowerFactor(data.value(102)); motor.setVstall(data.value(103));
        motor.setRstall(data.value(104)); motor.setXstall(data.value(105));
        motor.setLFadj(data.value(106)); motor.setKp1(data.value(107));
        motor.setNp1(data.value(108)); motor.setKq1(data.value(109));
        motor.setNq1(data.value(110)); motor.setKp2(data.value(111));
        motor.setNp2(data.value(112)); motor.setKq2(data.value(113));
        motor.setNq2(data.value(114)); motor.setVbrk(data.value(115));
        motor.setFrst(data.value(116)); motor.setVrst(data.value(117));
        motor.setCmpKpf(data.value(118)); motor.setCmpKqf(data.value(119));
        motor.setVc1off(data.value(120)); motor.setVc2off(data.value(121));
        motor.setVc1on(data.value(122)); motor.setVc2on(data.value(123));
        motor.setTth(data.value(124)); motor.setTh1t(data.value(125));
        motor.setTh2t(data.value(126)); motor.setFuvr(data.value(127));
        motor.setUVtr1(data.value(128)); motor.setTtr1(data.value(129));
        motor.setUVtr2(data.value(130)); motor.setTtr2(data.value(131));
    }

    @Override
    protected Complex resolveCompositeLoad() { return targetPower; }

    @Override
    protected void detachCompositeLoadFromParentBus() {
        BaseDStabBus<?, ?> bus = getDStabBus();
        Complex remaining = new Complex(bus.getLoadP(), bus.getLoadQ()).subtract(targetPower);
        bus.getContributeLoadList().removeIf(load -> targetLoadIds.contains(load.getId()));
        bus.setLoadP(Math.abs(remaining.getReal()) <= EPS ? 0.0 : remaining.getReal());
        bus.setLoadQ(Math.abs(remaining.getImaginary()) <= EPS ? 0.0 : remaining.getImaginary());
        bus.setLoadCode(remaining.abs() <= EPS ? AclfLoadCode.NON_LOAD : AclfLoadCode.CONST_P);
    }

    @Override
    public boolean initStates() {
        if (!super.initStates()) return false;
        if (staticFraction > EPS) {
            staticLoadComponent = new Cmldznu2AlgebraicLoadModel(
                    Cmldznu2AlgebraicLoadModel.Kind.STATIC, getLoadBus(), getId() + "_S",
                    staticFraction, data);
            if (!staticLoadComponent.initStates()) return false;
        }
        if (getFel() > EPS) {
            electronicLoadComponent = new Cmldznu2AlgebraicLoadModel(
                    Cmldznu2AlgebraicLoadModel.Kind.ELECTRONIC, getLoadBus(), getId() + "_E",
                    getFel(), data);
            if (!electronicLoadComponent.initStates()) return false;
        }
        applyReactiveCompensation();
        return true;
    }

    private void applyReactiveCompensation() {
        double componentQ = initialQ(getInductionMotorA()) + initialQ(getInductionMotorB())
                + initialQ(getInductionMotorC()) + initialQ(get1PhaseACMotor())
                + initialQ(staticLoadComponent) + initialQ(electronicLoadComponent);
        double voltageSquared = Math.max(EPS, getLoadBus().getVoltageMag() * getLoadBus().getVoltageMag());
        double compensation = (componentQ - getLoadBus().getInitLoad().getImaginary()) / voltageSquared;
        double fb = Math.max(0.0, Math.min(1.0, getDistEquivalent().getFB()));
        addShunt(getLowVoltBus(), fb * compensation);
        addShunt(getLoadBus(), (1.0 - fb) * compensation);
    }

    private static double initialQ(com.interpss.dstab.dynLoad.DynLoadModel model) {
        return model == null || model.getInitLoadPQ() == null ? 0.0
                : model.getInitLoadPQ().getImaginary();
    }

    private static void addShunt(BaseDStabBus<?, ?> bus, double susceptance) {
        Complex existing = bus.getShuntY() == null ? Complex.ZERO : bus.getShuntY();
        bus.setShuntY(existing.add(new Complex(0.0, susceptance)));
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        addMotorStates(states, "MotorA", getInductionMotorA());
        addMotorStates(states, "MotorB", getInductionMotorB());
        addMotorStates(states, "MotorC", getInductionMotorC());
        LD1PAC motorD = get1PhaseACMotor();
        if (motorD != null) {
            states.put("MotorD.Voltage", getLoadBus() == null ? getDStabBus().getVoltageMag()
                    : getLoadBus().getVoltageMag());
            states.put("MotorD.StallTimer", motorD.getAcStallTimer());
            states.put("MotorD.RestartTimer", motorD.getAcRestartTimer());
            states.put("MotorD.UvTimer1", motorD.getUVRelayTimer1());
            states.put("MotorD.UvTimer2", motorD.getUVRelayTimer2());
            states.put("MotorD.RunState", motorD.getStage() == 1 ? 1.0 : 0.0);
            motorD.getNamedStates().forEach((name, value) -> states.put("MotorD." + name, value));
        }
        if (staticLoadComponent != null) states.putAll(staticLoadComponent.getNamedStates());
        if (electronicLoadComponent != null) states.putAll(electronicLoadComponent.getNamedStates());
        return Map.copyOf(states);
    }

    private static void addMotorStates(Map<String, Double> states, String prefix,
            InductionMotor motor) {
        if (motor == null) return;
        states.put(prefix + ".EpReal", motor.getEp() == null ? 0.0 : motor.getEp().getReal());
        states.put(prefix + ".EpImag", motor.getEp() == null ? 0.0 : motor.getEp().getImaginary());
        states.put(prefix + ".EppReal", motor.getEpp() == null ? 0.0 : motor.getEpp().getReal());
        states.put(prefix + ".EppImag", motor.getEpp() == null ? 0.0 : motor.getEpp().getImaginary());
        states.put(prefix + ".SpeedDeviation", motor.getW() - 1.0);
        states.put(prefix + ".OnlineFraction", motor.getFonline());
    }

    public Cmldznu2Data getData() { return data; }
    public Set<String> getTargetLoadIds() { return targetLoadIds; }
    public double getStaticFraction() { return staticFraction; }

    /** Refreshes the algebraic components after a network-voltage or frequency change. */
    public void refreshAlgebraicComponents() {
        if (staticLoadComponent != null) staticLoadComponent.updateAttributes(false);
        if (electronicLoadComponent != null) electronicLoadComponent.updateAttributes(false);
    }
}
