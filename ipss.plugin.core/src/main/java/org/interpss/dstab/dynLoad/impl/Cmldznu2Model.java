package org.interpss.dstab.dynLoad.impl;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.Cmldznu2Data;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.LD1PAC;
import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStepAware;

import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Zone-scoped composite load assembled from the native distribution and motor components. */
public class Cmldznu2Model extends DynLoadCMPLDWImpl implements ICMLStateProvider, IntegrationStepAware {
    private static final double EPS = 1.0e-10;

    private final Cmldznu2Data data;
    private final Set<String> targetLoadIds;
    private final Complex targetPower;
    private double staticFraction;
    private Cmldznu2AlgebraicLoadModel staticLoadComponent;
    private Cmldznu2AlgebraicLoadModel electronicLoadComponent;
    private Complex collapsedParentRemainder;
    private Complex collapsedParentOriginalShunt;
    private Complex ownedLowSideShunt = Complex.ZERO;
    private CompositeTapControlPort tapControl;
    private Complex nearCompensation = Complex.ZERO, farCompensation = Complex.ZERO;
    private com.interpss.dstab.dynLoad.DynLoadModel[] capacityLeaves;
    private double remainingCapacity = 1.0;
    private double disconnectedLowVoltage, disconnectedLoadVoltage;
    protected LegacyDistributionShuntPort ownedDistributionShuntPort;
    private BaseDStabBus<?,?> capacityParent, capacityLowBus, capacityLoadBus;
    private DStabilityNetwork capacityNetwork;

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

    @Override public void configureIntegrationStep(double timeStepSec) {
        if (get1PhaseACMotor() instanceof IntegrationStepAware motor)
            motor.configureIntegrationStep(timeStepSec);
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
		if (motor instanceof LD1PACImpl implementation) {
			implementation.setFrequencySourceBus(getDStabBus());
            implementation.setCharacteristicPolicy(LD1PACImpl.CharacteristicPolicy.ZONE_COMPOSITE);
		}
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

    @Override protected boolean initializeOmittedDistribution(double mvaFactor) {
        boolean omitTransformer = data.value(5) == 0;
        boolean omitFeeder = data.value(3) == 0;
        if (!omitTransformer && !omitFeeder) return false;
        var parent = getDStabBus();
        lowBus = omitTransformer ? parent : internalBus("_lowBus");
        loadBus = omitFeeder ? lowBus : internalBus("_loadBus");
        if (loadBus == parent) {
            collapsedParentRemainder = new Complex(parent.getLoadP(), parent.getLoadQ());
            collapsedParentOriginalShunt = parent.getShuntY() == null ? Complex.ZERO : parent.getShuntY();
        }
        loadBus.setLoadCode(AclfLoadCode.CONST_P);
        var network = (DStabilityNetwork) parent.getNetwork();
        try {
            distXfr = omitTransformer ? null : DStabObjectFactory.createDStabBranch(parent.getId(), lowBus.getId(), network);
            if (distXfr != null) { distXfr.setBranchCode(AclfBranchCode.XFORMER); distXfr.setZ(new Complex(0, data.value(5) * mvaFactor)); }
            distFdr = omitFeeder ? null : DStabObjectFactory.createDStabBranch(lowBus.getId(), loadBus.getId(), network);
            if (distFdr != null) distFdr.setZ(new Complex(data.value(2), data.value(3)).multiply(mvaFactor));
        } catch (com.interpss.common.exp.InterpssException e) { throw new IllegalArgumentException("Cannot attach composite distribution elements", e); }
        initializeDistributionEquivalent(parent.getVoltage(), totalLoad.divide(parent.getVoltage()).conjugate(), mvaFactor);
        return true;
    }

    private BaseDStabBus<?, ?> internalBus(String suffix) {
        var parent = getDStabBus(); var network = (DStabilityNetwork) parent.getNetwork();
        var bus = DStabObjectFactory.createDStabBus(parent.getId() + suffix, network).orElseThrow();
        bus.setBaseVoltage(1); bus.setSortNumber(network.getNoBus() - 1); bus.setArea(parent.getArea()); bus.setZone(parent.getZone());
        return bus;
    }

    @Override protected boolean initializeSinglePhaseMotor(LD1PAC motor) {
        if (getLoadBus() == getDStabBus() && motor instanceof LD1PACImpl compressor) {
            double active = getLoadBus().getInitLoad().getReal() * getFmD();
            compressor.setInitialLoadAllocation(new Complex(active, active * reactiveRatio(data.value(102))));
        }
        return super.initializeSinglePhaseMotor(motor);
    }

    @Override protected void initializeDistributionEquivalent(Complex voltage, Complex incomingCurrent, double mvaFactor) {
        var data = getData();
        boolean legacyAllocation = distributionIncludesReactiveCompensation();
        if (!legacyAllocation && distXfr != null) distXfr.setZ(distXfr.getZ().multiply(data.value(6) * data.value(6)));
        double fixedRatio = distXfr == null ? 1 : data.value(7) / data.value(6);
        if (!Double.isFinite(fixedRatio) || fixedRatio <= 0)
            throw new IllegalArgumentException("Composite fixed transformer taps must be positive");
        Complex highSide = distXfr == null ? voltage : voltage.subtract(distXfr.getZ().multiply(incomingCurrent));
        double tap = fixedRatio, step = data.value(11), target = .5 * (data.value(12) + data.value(13));
        if (distXfr != null && step > 0) {
            Complex compensationDrop = new Complex(data.value(16), data.value(17)).multiply(mvaFactor).multiply(incomingCurrent);
            double a = highSide.abs() * highSide.abs();
            double b = target * target + 2 * highSide.multiply(compensationDrop.conjugate()).getReal();
            double c = compensationDrop.abs() * compensationDrop.abs();
            double discriminant = b * b - 4 * a * c;
            if (discriminant < 0 || !(a > 0)) throw new IllegalArgumentException("No valid compensated initial transformer ratio");
            double continuous = Math.sqrt((b + Math.sqrt(discriminant)) / (2 * a));
            long moves = Math.round((continuous - fixedRatio) / step);
            if (moves > 0) moves = Math.min(moves, Math.max(0, Math.round((data.value(10) - fixedRatio) / step)));
            else if (moves < 0) moves = Math.max(moves, Math.min(0, Math.round((data.value(9) - fixedRatio) / step)));
            tap = fixedRatio + moves * step;
        }
        if (distXfr != null) { distXfr.setFromTurnRatio(1); distXfr.setToTurnRatio(tap); }
        Complex lowVoltage = highSide.multiply(tap);
        getLowVoltBus().setVoltage(lowVoltage);
        double initialReactiveDemandRatio = legacyAllocation ? (getFmA() > 1e-10 ? getFmA() * motorReactiveRatio(data.value(38)) : 0)
                + (getFmB() > 1e-10 ? getFmB() * motorReactiveRatio(data.value(58)) : 0)
                + (getFmC() > 1e-10 ? getFmC() * motorReactiveRatio(data.value(78)) : 0)
                + getFmD() * reactiveRatio(data.value(102)) + getFel() * reactiveRatio(data.value(23))
                + getStaticFraction() * staticReactiveDemandRatio() : 0;
        double fromCompensation = (legacyAllocation ? Math.max(0, Math.min(1, data.value(4))) : 0)
                * (totalLoad.getReal() * initialReactiveDemandRatio - totalLoad.getImaginary())
                / (lowVoltage.abs() * lowVoltage.abs());
        double lowSusceptance = data.value(1) / mvaFactor + fromCompensation;
        Complex componentLowShunt = new Complex(0, lowSusceptance);
        ownedLowSideShunt = componentLowShunt;
        nearCompensation = new Complex(0, fromCompensation);
        if (getLowVoltBus() == getDStabBus()) addShunt(getLowVoltBus(), lowSusceptance);
        else getLowVoltBus().setShuntY(componentLowShunt);
        Complex feederCurrent = incomingCurrent.divide(tap).subtract(lowVoltage.multiply(componentLowShunt));
        Complex loadVoltage = distFdr == null ? lowVoltage : lowVoltage.subtract(distFdr.getZ().multiply(feederCurrent));
        if (loadVoltage.abs() < .95) {
            if (lowVoltage.abs() < .95) throw new IllegalArgumentException("Composite low-side voltage cannot support its minimum load voltage");
            if (distFdr == null) throw new IllegalArgumentException("An omitted feeder cannot be adjusted to raise voltage");
            Complex drop = distFdr.getZ().multiply(feederCurrent);
            double a = drop.abs() * drop.abs(), projection = lowVoltage.multiply(drop.conjugate()).getReal();
            double c = lowVoltage.abs() * lowVoltage.abs() - .95 * .95;
            double scale = (projection - Math.sqrt(Math.max(0, projection * projection - a * c))) / a;
            distFdr.setZ(distFdr.getZ().multiply(Math.max(0, Math.min(1, scale))));
            loadVoltage = lowVoltage.subtract(distFdr.getZ().multiply(feederCurrent));
        }
        Complex loadPower = loadVoltage.multiply(feederCurrent.conjugate());
        double grossReactiveDemand = legacyAllocation ? loadPower.getReal() * initialReactiveDemandRatio : loadPower.getImaginary();
        getLoadBus().setVoltage(loadVoltage); getLoadBus().setLoadP(loadPower.getReal()); getLoadBus().setLoadQ(grossReactiveDemand);
        // The engine subtracts owned component demand from this gross load.
        // The explicitly stamped far-end capacitor already supplies the Q difference.
        getLoadBus().setInitLoad(new Complex(loadPower.getReal(), grossReactiveDemand));
        double farCompensation = legacyAllocation ? (loadPower.getReal() * initialReactiveDemandRatio - loadPower.getImaginary())
                / (loadVoltage.abs() * loadVoltage.abs()) : 0;
        this.farCompensation = new Complex(0, farCompensation);
        if (getLoadBus() == getLowVoltBus()) addShunt(getLoadBus(), farCompensation);
        else getLoadBus().setShuntY(new Complex(0, farCompensation));
    }

    private static double reactiveRatio(double powerFactor) { return Math.tan(Math.acos(powerFactor)); }
    protected double staticReactiveDemandRatio() { return reactiveRatio(data.value(26)); }
    protected static double motorReactiveRatio(double loading) {
        if (!(loading > 0 && loading <= 1)) throw new IllegalArgumentException("Composite motor loading factor must be in (0,1]");
        return Math.sqrt(1 / (loading * loading) - 1);
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
        double assignedActive = getLoadBus() == getDStabBus() ? getLoadBus().getInitLoad().getReal() : Double.NaN;
        if (staticFraction > EPS) {
            staticLoadComponent = createStaticComponent(assignedActive * staticFraction);
            if (!staticLoadComponent.initStates()) return false;
        }
        if (getFel() > EPS) {
            electronicLoadComponent = new Cmldznu2AlgebraicLoadModel(
                    Cmldznu2AlgebraicLoadModel.Kind.ELECTRONIC, getLoadBus(), getId() + "_E",
                    getFel(), data, assignedActive * getFel());
            if (!electronicLoadComponent.initStates()) return false;
        }
        prepareAlgebraicNetworkInterface(staticLoadComponent, electronicLoadComponent);
        if (data.value(8) == 1 && distXfr != null && data.value(11) > 0) {
            tapControl = new CompositeTapControlPort(this);
            if (!tapControl.initStates()) return false;
        }
        if (!distributionIncludesReactiveCompensation()) applyReactiveCompensation();
        if (getLoadBus() == getDStabBus()) {
            var parent = getDStabBus(); Complex combined = collapsedParentRemainder.add(parent.getInitLoad());
            parent.setLoadP(combined.getReal()); parent.setLoadQ(combined.getImaginary()); parent.setInitLoad(combined);
            parent.setNetLoadResults(combined);
            for (var motor : new InductionMotor[]{getInductionMotorA(), getInductionMotorB(), getInductionMotorC()})
                if (motor != null) motor.setLoadPercent(100 * motor.getInitLoadPQ().getReal() / combined.getReal());
            if (get1PhaseACMotor() != null) get1PhaseACMotor().setLoadPercent(100 * get1PhaseACMotor().getInitLoadPQ().getReal() / combined.getReal());
            if (staticLoadComponent != null) staticLoadComponent.setLoadPercent(100 * staticLoadComponent.getInitLoadPQ().getReal() / combined.getReal());
            if (electronicLoadComponent != null) electronicLoadComponent.setLoadPercent(100 * electronicLoadComponent.getInitLoadPQ().getReal() / combined.getReal());
        }
        capacityLeaves = java.util.stream.Stream.of(getInductionMotorA(), getInductionMotorB(),
                getInductionMotorC(), get1PhaseACMotor(), staticLoadComponent, electronicLoadComponent)
                .filter(java.util.Objects::nonNull).toArray(com.interpss.dstab.dynLoad.DynLoadModel[]::new);
        remainingCapacity = 1.0;
        capacityParent=getDStabBus(); capacityLowBus=getLowVoltBus(); capacityLoadBus=getLoadBus();
        capacityNetwork=(DStabilityNetwork)capacityParent.getNetwork();
        return true;
    }

    /** Prepared whole-owner capacity action, including verified owned-subtree disconnection. */
    protected final boolean canChangeCompositeCapacity(double factor) {
        return canPrepareCompositeCapacity(factor) && capacityNetwork.getYMatrix()!=null;
    }

    protected final boolean canPrepareCompositeCapacity(double factor) {
        if (capacityLeaves == null || !Double.isFinite(factor) || factor > 0 || remainingCapacity <= 0 || remainingCapacity + factor < 0
                || getDStabBus()!=capacityParent || getLowVoltBus()!=capacityLowBus || getLoadBus()!=capacityLoadBus
                || capacityParent.getNetwork()!=capacityNetwork || capacityLowBus.getNetwork()!=capacityNetwork
                || capacityLoadBus.getNetwork()!=capacityNetwork) return false;
        if (distFdr!=null && (distFdr.getFromBus()!=capacityLowBus || distFdr.getToBus()!=capacityLoadBus)) return false;
        if (distXfr!=null && (distXfr.getFromBus()!=capacityParent || distXfr.getToBus()!=capacityLowBus)) return false;
        for (var leaf:capacityLeaves) if (leaf.getDStabBus()!=capacityLoadBus) return false;
        if (remainingCapacity+factor==0 && !canDisconnectOwnedDistribution()) return false;
        return true;
    }

    private boolean canDisconnectOwnedDistribution() {
        // With both elements omitted, remove only this owner; the common bus stays live.
        if (distXfr==null && distFdr==null && capacityLowBus==capacityParent && capacityLoadBus==capacityParent)
            return true;
        // Internal buses may coincide, or the low-side bus may be the energized parent.
        if (capacityLoadBus==capacityParent) return false;
        for (var bus:new BaseDStabBus<?,?>[]{capacityLowBus,capacityLoadBus}) {
            if (bus==capacityParent) continue;
            if (!bus.getContributeGenList().isEmpty()) return false;
            for (var device:bus.getDynamicBusDeviceList()) if(!isOwnedCapacityDevice(device)) return false;
            for (var branch:capacityNetwork.getBranchList())
                if (branch.isActive() && (branch.getFromBus()==bus || branch.getToBus()==bus)
                        && branch!=distFdr && branch!=distXfr) return false;
            if(bus.getDynLoadModelList()!=null) for (var leaf:bus.getDynLoadModelList()) {
                if (!isOwnedCapacityDevice(leaf)) return false;
            }
        }
        return true;
    }

    private boolean isOwnedCapacityDevice(Object device) {
        if(device==tapControl)return true;
        for(var leaf:capacityLeaves)if(leaf==device)return true;
        return device==ownedDistributionShuntPort;
    }

    private void disconnectOwnedDistribution(double factor) {
        disconnectedLowVoltage=capacityLowBus.getVoltageMag();
        disconnectedLoadVoltage=capacityLoadBus.getVoltageMag();
        if (capacityLowBus==capacityParent) {
            // Remove physical shunts and each active owned leaf's numerical stamp separately.
            // Their cancellation is model-specific; neither includes unrelated parent equipment.
            Complex parentOwned=ownedLowSideShunt.add(capacityLoadBus==capacityParent?farCompensation:Complex.ZERO);
            changePhysicalShunt(capacityParent,parentOwned.negate(),capacityNetwork);
            int row=capacityParent.getSortNumber();
            for (var leaf:capacityLeaves) if (leaf.isActive() && leaf.getDStabBus()==capacityParent)
                capacityNetwork.getYMatrix().addToA(leaf.getPosSeqEquivY().negate(),row,row);
            if (ownedDistributionShuntPort!=null && ownedDistributionShuntPort.isActive()) {
                capacityNetwork.getYMatrix().addToA(ownedDistributionShuntPort.getPosSeqEquivY().negate(),row,row);
                ownedDistributionShuntPort.setStatus(false);
            }
        }
        // Remove live stamps in place, preserving an unrelated active fault's diagonal.
        removeDistributionBranch(distFdr);removeDistributionBranch(distXfr);
        for (var leaf:capacityLeaves) {
            leaf.changeLoad(factor);
            leaf.setLoadPQ(Complex.ZERO);leaf.setStatus(false);
        }
        if (tapControl!=null)tapControl.setStatus(false);
        for (var bus:new BaseDStabBus<?,?>[]{capacityLowBus,capacityLoadBus}) if (bus!=capacityParent && bus.isActive()) {
            if(bus.getDynLoadModelList()!=null)for (var leaf:bus.getDynLoadModelList())leaf.setStatus(false);
            bus.setStatus(false);
            int row=bus.getSortNumber();capacityNetwork.getYMatrix().setA(Complex.ONE,row,row);
            capacityNetwork.getYMatrix().setBi(Complex.ZERO,row);
        }
        ownedLowSideShunt=Complex.ZERO;nearCompensation=Complex.ZERO;farCompensation=Complex.ZERO;
        remainingCapacity=0;
        capacityNetwork.setYMatrixDirty(true);
    }

    private void removeDistributionBranch(com.interpss.dstab.DStabBranch branch) {
        if(branch==null || !branch.isActive())return;
        int from=branch.getFromBus().getSortNumber(),to=branch.getToBus().getSortNumber();
        var matrix=capacityNetwork.getYMatrix();
        matrix.addToA(branch.yff().negate(),from,from);matrix.addToA(branch.yft().negate(),from,to);
        matrix.addToA(branch.ytf().negate(),to,from);matrix.addToA(branch.ytt().negate(),to,to);
        branch.setStatus(false);
    }

    protected final boolean changeCompositeCapacity(double factor) {
        if (!canChangeCompositeCapacity(factor)) return false;
        if (factor == 0) return true;
        if (remainingCapacity+factor==0) { disconnectOwnedDistribution(factor);return true; }
        double next = remainingCapacity + factor, ratio = next / remainingCapacity;
        var network = (DStabilityNetwork)getDStabBus().getNetwork();
        if (distFdr != null) {
            var ff=distFdr.yff(); var ft=distFdr.yft(); var tf=distFdr.ytf(); var tt=distFdr.ytt();
            distFdr.setZ(distFdr.getZ().divide(ratio));
            int from=getLowVoltBus().getSortNumber(), to=getLoadBus().getSortNumber();
            network.getYMatrix().addToA(distFdr.yff().subtract(ff),from,from);
            network.getYMatrix().addToA(distFdr.yft().subtract(ft),from,to);
            network.getYMatrix().addToA(distFdr.ytf().subtract(tf),to,from);
            network.getYMatrix().addToA(distFdr.ytt().subtract(tt),to,to);
        }
        Complex nearDelta=nearCompensation.multiply(ratio-1), farDelta=farCompensation.multiply(ratio-1);
        changePhysicalShunt(getLowVoltBus(),nearDelta,network);
        changePhysicalShunt(getLoadBus(),farDelta,network);
        ownedLowSideShunt=ownedLowSideShunt.add(nearDelta);
        nearCompensation=nearCompensation.multiply(ratio); farCompensation=farCompensation.multiply(ratio);
        for (var leaf:capacityLeaves) if (!leaf.changeLoad(factor))
            throw new IllegalStateException("Prepared composite leaf rejected a valid capacity decrement");
        remainingCapacity=next;
        network.setYMatrixDirty(true);
        return true;
    }

    private static void changePhysicalShunt(BaseDStabBus<?,?> bus,Complex delta,DStabilityNetwork network) {
        bus.setShuntY(bus.getShuntY().add(delta));
        bus.applyPositiveSequenceShuntAdmittanceChange(delta);
        int row=bus.getSortNumber(); network.getYMatrix().addToA(delta,row,row);
    }

    /** Identity membership for callers that also traverse the source bus's dynamic loads. */
    public boolean ownsCapacityLoad(com.interpss.dstab.dynLoad.DynLoadModel load) {
        if (load==null || capacityLeaves==null) return false;
        for (var leaf:capacityLeaves) if (leaf==load) return true;
        return false;
    }

    public double getRemainingCapacity() { return remainingCapacity; }

    /** Prepare only this composite's owned leaves before the engine builds its dynamic matrix. */
    protected void prepareAlgebraicNetworkInterface(Cmldznu2AlgebraicLoadModel staticComponent,
            Cmldznu2AlgebraicLoadModel electronicComponent) { }

    protected Cmldznu2AlgebraicLoadModel createStaticComponent(double assignedActivePower) {
        return new Cmldznu2AlgebraicLoadModel(Cmldznu2AlgebraicLoadModel.Kind.STATIC,
                getLoadBus(), getId() + "_S", staticFraction, data, assignedActivePower);
    }

    /** Prepared unrelated parent demand and shunt, before owned leaves are allocated. */
    public record CollapsedParentAllocation(Complex staticPower, Complex shunt) { }

    public CollapsedParentAllocation getCollapsedParentAllocation() {
        if (getLoadBus() != getDStabBus() || collapsedParentRemainder == null
                || collapsedParentOriginalShunt == null || capacityLeaves == null)
            throw new IllegalStateException("Collapsed parent ownership has not been prepared");
        return new CollapsedParentAllocation(collapsedParentRemainder, collapsedParentOriginalShunt);
    }

    protected Complex getCollapsedParentOwnedShunt() {
        if (getLoadBus() != getDStabBus() || collapsedParentOriginalShunt == null)
            throw new IllegalStateException("An owned parent shunt requires omitted distribution elements");
        return getDStabBus().getShuntY().subtract(collapsedParentOriginalShunt);
    }

    /** The low-side capacitor owned by this composite, excluding unrelated parent shunts. */
    protected Complex getOwnedLowSideShunt() { return ownedLowSideShunt; }

    /** Whether the distribution hook already included all reactive compensation. */
    protected boolean distributionIncludesReactiveCompensation() { return false; }

    private void applyReactiveCompensation() {
        double componentQ = initialQ(getInductionMotorA()) + initialQ(getInductionMotorB())
                + initialQ(getInductionMotorC()) + initialQ(get1PhaseACMotor())
                + initialQ(staticLoadComponent) + initialQ(electronicLoadComponent);
        double voltageSquared = Math.max(EPS, getLoadBus().getVoltageMag() * getLoadBus().getVoltageMag());
        double compensation = (componentQ - getLoadBus().getInitLoad().getImaginary()) / voltageSquared;
        // This dialect allocates the compensation at the far end. Keep gross
        // owned demand on the internal bus so the engine creates no second
        // residual static compensation while assembling the dynamic network.
        getLoadBus().setLoadQ(componentQ);
        getLoadBus().setInitLoad(new Complex(getLoadBus().getInitLoad().getReal(), componentQ));
        addShunt(getLoadBus(), compensation);
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
        if (getLowVoltBus()!=null && getLoadBus()!=null) {
            states.put("Distribution.LowVoltage",remainingCapacity==0?disconnectedLowVoltage:getLowVoltBus().getVoltageMag());
            states.put("Distribution.LoadVoltage",remainingCapacity==0?disconnectedLoadVoltage:getLoadBus().getVoltageMag());
        }
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
        if (tapControl != null) states.putAll(tapControl.getNamedStates());
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
        motor.getNamedStates().forEach((name, value) -> states.put(prefix + "." + name, value));
    }

    public Cmldznu2Data getData() { return data; }
    public Set<String> getTargetLoadIds() { return targetLoadIds; }
    public double getStaticFraction() { return staticFraction; }
    /** Owned distribution transformer, or null when the input omits it. */
    public com.interpss.dstab.DStabBranch getDistributionTransformer() { return distXfr; }

    /** Change the active tap-control target between completed integration intervals. */
    public void setTapVoltageBand(double minimum, double maximum) {
        if (tapControl == null) throw new IllegalStateException("No active transformer tap controller");
        tapControl.setVoltageBand(minimum,maximum);
    }

    /** Leaf-owned coordinates and diagnostics; the composite container owns no duplicate state vector. */
    public Map<String, IntegrationStateAccess> getIntegrationStateOwners() {
        var owners = new LinkedHashMap<String, IntegrationStateAccess>();
        if (getInductionMotorA() instanceof IntegrationStateAccess state) owners.put("MotorA", state);
        if (getInductionMotorB() instanceof IntegrationStateAccess state) owners.put("MotorB", state);
        if (getInductionMotorC() instanceof IntegrationStateAccess state) owners.put("MotorC", state);
        if (get1PhaseACMotor() instanceof IntegrationStateAccess state) owners.put("MotorD", state);
        if (tapControl != null) owners.put("TapControl",tapControl);
        return Map.copyOf(owners);
    }

    /** Exact algebraic leaf type; the shared solver must not accept arbitrary custom loads. */
    public static boolean isAlgebraicComponent(Object model) {
        return model != null && (model.getClass() == Cmldznu2AlgebraicLoadModel.class
                || model.getClass() == LegacyStaticLoadModel.class || model.getClass() == LegacyStaticLoadPort.class
                || model.getClass() == LegacyDistributedStaticLoadPort.class
                || model.getClass() == LegacyDistributionShuntPort.class);
    }

    /** Exact supported discrete-history leaf; it is not an algebraic load. */
    public static boolean isTapControl(Object model) {
        return model != null && model.getClass() == CompositeTapControlPort.class;
    }

    /** Refreshes the algebraic components after a network-voltage or frequency change. */
    public void refreshAlgebraicComponents() {
        if (staticLoadComponent != null) staticLoadComponent.updateAttributes(false);
        if (electronicLoadComponent != null) electronicLoadComponent.updateAttributes(false);
    }
}
