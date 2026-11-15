package org.interpss.dstab.mach;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Vector_dq;

import com.interpss.core.net.Network;
import com.interpss.core.net.DataCheckConfiguration;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.impl.DynamicSimuAdapterImpl;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.funcImpl.DStabFunction;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.mach.impl.RoundRotorMachineImpl;

/**
 * WECC GENQEC synchronous generator.
 *
 * <p>GENQEC saturates all mutual inductances and permits unequal saturated
 * d/q subtransient reactances. The latter cannot be represented by a single
 * complex Norton impedance, so this implementation solves the published d/q
 * boundary equations and adds a compensation current around InterPSS's fixed
 * network admittance.</p>
 */
public class GenqecMachine extends RoundRotorMachineImpl
        implements ICMLMachineVoltageProvider, IeeeVoltageCompensatedMachine, ICMLStateProvider {
    private static final double EPS = 1.0e-9;

    private final GenqecData data;
    private final GenqecEqnSolver genqecSolver;
    private IeeeVcData ieeeVcData;
    private boolean networkFrequencyDependenceEnabled;

    /** Apply the native GENQEC/GENQECU NETFRQ policy before initialization. */
    public void setNetworkFrequencyDependenceEnabled(boolean enabled) {
        networkFrequencyDependenceEnabled = enabled;
    }

    private double statorReactanceFrequencyRatio() {
        if (!networkFrequencyDependenceEnabled) return 1.;
        double ratio = getDStabBus().getFreq();
        if (!Double.isFinite(ratio) || ratio <= 0.) {
            throw new ArithmeticException("GENQEC stator frequency ratio must be finite and positive");
        }
        return ratio;
    }

    @Override
    protected double cal_dW_dt(double speed, double baseFrequency) {
        double rate = super.cal_dW_dt(speed, baseFrequency);
        // Pe remains air-gap power. NETFRQ converts accelerating power to
        // torque using shaft speed while retaining the same output power base.
        return networkFrequencyDependenceEnabled ? rate / speed : rate;
    }

    public GenqecMachine(GenqecData data) {
        super();
        this.data = data;
        this.genqecSolver = new GenqecEqnSolver();
        this._dEqnSolver = genqecSolver;
    }

    public GenqecData getGenqecData() {
        return data;
    }

    @Override
    public IeeeVcData getIeeeVcData() {
        return ieeeVcData;
    }

    @Override
    public void setIeeeVcData(IeeeVcData data) {
        ieeeVcData = data;
    }

    @Override
    protected boolean advanceElectromechanicalStage(double dt, DynamicSimuMethod method,
            Network<?, ?> net, int flag) {
        // The stator RHS uses the solved terminal voltage, rotor angle and
        // speed at the same predictor/corrector endpoint. Advancing the
        // angle first rotates the stator coordinates against an older
        // network voltage and biases the power even at constant slip.
        double stageAirGapPower = getPe();
        try {
            if (!nextStepElectrical(dt, method, net, flag)) {
                return false;
            }
        } finally {
            // Electrical integration publishes provisional power. The swing
            // derivative still needs the power of the solved stage.
            setPe(stageAirGapPower);
        }
        return nextStepMechanical(dt, method, net, flag);
    }

    /** GENQEC permits Xd'' != Xq'' and zero q-axis time constants. */
    @Override
    public boolean checkData(DataCheckConfiguration config) {
        return getH() > EPS
                && getRating() > EPS
                && getRatedVoltage() > EPS
                && getRa() >= 0.0
                && getXd() > getXd1()
                && getXq() >= getXq1()
                && getXd1() > getXl()
                && getXq1() > getXl()
                && getXd11() >= getXl()
                && getXq11() >= getXl()
                && getTd01() > EPS
                && getTd011() >= 0.0
                && getTq01() >= 0.0
                && getTq011() >= 0.0;
    }

    @Override
    public double getSatruationFactor(double flux) {
        if (data.satFunc() == -1 || data.s1() <= EPS || data.s12() <= EPS || flux <= 0.0) {
            return 0.0;
        }
        double s1 = data.s1();
        double s12 = data.s12();
        return switch (data.satFunc()) {
            case 1 -> scaledQuadraticSaturation(flux, s1, s12);
            case 2 -> quadraticSaturation(flux, s1, s12);
            default -> exponentialSaturation(flux, s1, s12);
        };
    }

    /**
     * Native standard/wrapped inputs retain the sign of Kw, but the electrical
     * equation uses its magnitude. Id keeps its physical sign, and the signed
     * product is limited afterward. Positive-only validation did not expose
     * the distinction; matched-P/Q/V native runs with both Kw signs, both Id
     * directions and both limiter boundaries do. Apply this same function to
     * initialization/output refresh and the electrical RHS so the computed
     * initial field voltage is also the stationary field input.
     */
    private double limitedFieldCompensationProduct(double id) {
        return Math.max(-0.4, Math.min(0.4, Math.abs(data.kw()) * id));
    }

    private static double exponentialSaturation(double flux, double s1, double s12) {
        double exponent = Math.log(s12 / s1) / Math.log(1.2);
        return s1 * Math.pow(flux, exponent);
    }

    private static double scaledQuadraticSaturation(double flux, double s1, double s12) {
        double alpha = Math.sqrt(1.2 * s12 / s1);
        if (Math.abs(alpha - 1.0) < EPS) {
            return exponentialSaturation(flux, s1, s12);
        }
        double threshold = (alpha - 1.2) / (alpha - 1.0);
        if (flux <= threshold) {
            return 0.0;
        }
        double coefficient = s1 / Math.pow(1.0 - threshold, 2.0);
        return coefficient * Math.pow(flux - threshold, 2.0) / flux;
    }

    private static double quadraticSaturation(double flux, double s1, double s12) {
        double alpha = Math.sqrt(s12 / s1);
        if (Math.abs(alpha - 1.0) < EPS) {
            return exponentialSaturation(flux, s1, s12);
        }
        double threshold = (alpha - 1.2) / (alpha - 1.0);
        if (flux <= threshold) {
            return 0.0;
        }
        double coefficient = s1 / Math.pow(1.0 - threshold, 2.0);
        return coefficient * Math.pow(flux - threshold, 2.0);
    }

    @Override
    public double getXdAdjusted() {
        return saturatedReactance(getXd(), currentAirGapFlux());
    }

    @Override
    public double getXqAdjusted() {
        return saturatedReactance(getXq(), currentAirGapFlux());
    }

    public double getXdppSaturated() {
        return saturatedReactance(getXd11(), currentAirGapFlux());
    }

    public double getXqppSaturated() {
        return saturatedReactance(getXq11(), currentAirGapFlux());
    }

    private double saturatedReactance(double reactance, double flux) {
        return (reactance - getXl()) / (1.0 + effectiveSaturation(flux)) + getXl();
    }

    /** Hook used by GENQEJ to add KIS times terminal-current magnitude. */
    protected double saturationInput(double airGapFlux, double terminalCurrentMagnitude) {
        return airGapFlux;
    }

    private double effectiveSaturation(double airGapFlux) {
        double currentMagnitude;
        if (genqecSolver != null && genqecSolver.initialized) {
            currentMagnitude = Math.hypot(genqecSolver.id, genqecSolver.iq);
        } else if (getParentGen() != null && getDStabBus() != null
                && getDStabBus().getVoltage().abs() > EPS) {
            // Rotor angle is initialized before the electrical state solver.
            // Current magnitude is reference-frame invariant, so derive it
            // directly from the solved load-flow P/Q and convert to machine base.
            currentMagnitude = getParentGen().getGen().divide(getDStabBus().getVoltage())
                    .conjugate().abs() / getIMultiFactor();
        } else {
            currentMagnitude = 0.0;
        }
        return getSatruationFactor(saturationInput(airGapFlux, currentMagnitude));
    }

    /** Current operating-point saturation factor, exposed for model verification. */
    public double getEffectiveSaturationFactor() {
        return effectiveSaturation(currentAirGapFlux());
    }

    private double currentAirGapFlux() {
        if (genqecSolver != null && genqecSolver.initialized) {
            return genqecSolver.airGapFlux;
        }
        return getVoltBehindXl();
    }

    /** Machine-base terminal MW sampled during the most recent stator evaluation. */
    @Override
    public double getTerminalActivePower() {
        return genqecSolver.terminalActivePower;
    }

    /** Compensated terminal voltage supplied to a compatible exciter. */
    public double getCompensatedVoltage() {
        Complex terminalCurrent = getIgen().subtract(getDStabBus().getVoltage().multiply(getYgen()));
        Complex zcomp = new Complex(data.rcomp(), data.xcomp()).multiply(getZMultiFactor());
        return getDStabBus().getVoltage().subtract(zcomp.multiply(terminalCurrent)).abs() / getVMultiFactor();
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("E'q", getEq1());
        states.put("E'd", getEd1());
        states.put("Psi'd", getPsikd());
        states.put("Psi'q", getPsikq());
        states.put("Speed deviation", getSpeed() - 1.0);
        states.put("Angle", getAngle());
        return Map.copyOf(states);
    }

    /** Supplies the GENQEC compensated voltage to every CML exciter using {@code mach.vt}. */
    @Override
    public double getCmlMachineVoltage() {
        return ieeeVcData == null ? getCompensatedVoltage() : getIeeeVcVoltage();
    }

    @Override
    public double calculateIfd(MachineIfdBase base) {
        double ladIfd = genqecSolver == null ? getEfd() : genqecSolver.ladIfd;
        double xadSat = getXdAdjusted() - getXl();
        return base == MachineIfdBase.MACHINE ? ladIfd / xadSat : ladIfd;
    }

    private final class GenqecEqnSolver extends DynamicSimuAdapterImpl {
        private double eq1State;
        private double psikdState;
        private double psikqState;
        private double ed1State;
        private double psidpp;
        private double psiqpp;
        private double id;
        private double iq;
        private double airGapFlux;
        private double terminalActivePower;
        private double ladIfd;
        private boolean initialized;

        private double oldEq1;
        private double oldPsikd;
        private double oldPsikq;
        private double oldEd1;
        private double dEq1;
        private double dPsikd;
        private double dPsikq;
        private double dEd1;

        @Override
        public boolean initStates(BaseDStabBus<?, ?> bus) {
            super.initStates(bus);
            Vector_dq initialCurrent = getInitIdq();
            id = initialCurrent.d;
            iq = initialCurrent.q;

            Vector_dq terminalVoltage = getVdq();
            terminalActivePower = terminalVoltage.d * id + terminalVoltage.q * iq;
            double vqag = terminalVoltage.q + getRa() * iq + getXl() * id;
            double vdag = terminalVoltage.d + getRa() * id - getXl() * iq;
            airGapFlux = Math.hypot(vqag, vdag) / Math.max(getSpeed(), EPS);
            double sat = 1.0 + effectiveSaturation(airGapFlux);
            double xdppSat = saturatedReactance(getXd11(), airGapFlux);
            double xqppSat = saturatedReactance(getXq11(), airGapFlux);

            psidpp = (terminalVoltage.q + xdppSat * id + getRa() * iq) / getSpeed();
            psiqpp = (-terminalVoltage.d - getRa() * id + xqppSat * iq) / getSpeed();

            psikdState = psidpp - (getXd11() - getXl()) * id / sat;
            eq1State = psikdState + (getXd1() - getXl()) * id / sat;
            ed1State = -psiqpp - (getXq1() - getXq11()) * iq / sat;
            psikqState = ed1State + (getXq1() - getXl()) * iq / sat;

            enforceAlgebraicStates(sat);
            updateFluxes();
            updateElectricalOutputs();
            setPsikd(psikdState);
            setPsikq(psikqState);
            setEq1(eq1State);
            setEd1(ed1State);
            setPsid11(psidpp);
            setPsiq11(psiqpp);
            setEfd(ladIfd);
            initialized = true;
            return true;
        }

        @Override
        public boolean nextStepElectricalModifiedEuler(
                double dt, DynamicSimuMethod method, Network<?, ?> net, int flag) {
            if (method != DynamicSimuMethod.MODIFIED_EULER) {
                throw new UnsupportedOperationException("GENQEC currently supports MODIFIED_EULER only");
            }

            updateCurrent();
            double sat = 1.0 + effectiveSaturation(airGapFlux);
            enforceAlgebraicStates(sat);
            Derivatives derivatives = derivatives(sat);

            if (flag == 0) {
                oldEq1 = eq1State;
                oldPsikd = psikdState;
                oldPsikq = psikqState;
                oldEd1 = ed1State;
                dEq1 = derivatives.eq1;
                dPsikd = derivatives.psikd;
                dPsikq = derivatives.psikq;
                dEd1 = derivatives.ed1;
                eq1State = oldEq1 + dEq1 * dt;
                psikdState = oldPsikd + dPsikd * dt;
                psikqState = oldPsikq + dPsikq * dt;
                ed1State = oldEd1 + dEd1 * dt;
            } else {
                eq1State = oldEq1 + 0.5 * (dEq1 + derivatives.eq1) * dt;
                psikdState = oldPsikd + 0.5 * (dPsikd + derivatives.psikd) * dt;
                psikqState = oldPsikq + 0.5 * (dPsikq + derivatives.psikq) * dt;
                ed1State = oldEd1 + 0.5 * (dEd1 + derivatives.ed1) * dt;
            }

            sat = 1.0 + effectiveSaturation(airGapFlux);
            enforceAlgebraicStates(sat);
            updateFluxes();
            updateElectricalOutputs();
            setPsikd(psikdState);
            setPsikq(psikqState);
            setEq1(eq1State);
            setEd1(ed1State);
            setPsid11(psidpp);
            setPsiq11(psiqpp);
            return true;
        }

        private Derivatives derivatives(double sat) {
            double dAxisError = -psikdState - (getXd1() - getXl()) * id / sat + eq1State;
            double qAxisError = -psikqState + (getXq1() - getXl()) * iq / sat + ed1State;
            double tempD = (getXd1() - getXd11()) * sat
                    / Math.pow(getXd1() - getXl(), 2.0) * dAxisError;
            double tempQ = (getXq1() - getXq11()) * sat
                    / Math.pow(getXq1() - getXl(), 2.0) * qAxisError;

            double kwId = limitedFieldCompensationProduct(id);
            ladIfd = sat * (eq1State + (getXd() - getXd1()) / sat * (id + tempD))
                    / (1.0 - kwId);

            double eq1Dot = (getEfd() - ladIfd) / getTd01();
            double psikdDot = getTd011() > EPS ? sat * dAxisError / getTd011() : 0.0;
            double psikqDot = getTq011() > EPS ? sat * qAxisError / getTq011() : 0.0;
            double ed1Dot = getTq01() > EPS
                    ? sat * (-ed1State + (getXq() - getXq1()) / sat * (iq - tempQ)) / getTq01()
                    : 0.0;
            return new Derivatives(eq1Dot, psikdDot, psikqDot, ed1Dot);
        }

        private void enforceAlgebraicStates(double sat) {
            if (getTd011() <= EPS) {
                psikdState = eq1State - (getXd1() - getXl()) * id / sat;
            }
            if (getTq01() <= EPS) {
                ed1State = 0.0;
            }
            if (getTq011() <= EPS) {
                psikqState = ed1State + (getXq1() - getXl()) * iq / sat;
            }
        }

        private void updateFluxes() {
            psidpp = eq1State * (getXd11() - getXl()) / (getXd1() - getXl())
                    + psikdState * (getXd1() - getXd11()) / (getXd1() - getXl());
            psiqpp = -ed1State * (getXq11() - getXl()) / (getXq1() - getXl())
                    - psikqState * (getXq1() - getXq11()) / (getXq1() - getXl());
        }

        private void updateCurrent() {
            Vector_dq terminalVoltage = getVdq();
            // GENQEC/GENQECU uses shaft speed in its internal EMF and
            // saturation flux normalization. With NETFRQ, local electrical
            // frequency scales the stator and leakage reactances independently.
            double speed = getSpeed();
            double reactanceRatio = statorReactanceFrequencyRatio();
            double leakage = reactanceRatio * getXl();
            double dvD = -psiqpp * speed - terminalVoltage.d;
            double dvQ = psidpp * speed - terminalVoltage.q;
            // Current and saturation are weakly coupled through air-gap flux.
            // A short fixed-point iteration makes the boundary solution
            // self-consistent without adding states to the network solver.
            for (int iteration = 0; iteration < 4; iteration++) {
                double xdppSat = reactanceRatio * saturatedReactance(getXd11(), airGapFlux);
                double xqppSat = reactanceRatio * saturatedReactance(getXq11(), airGapFlux);
                double determinant = getRa() * getRa() + xdppSat * xqppSat;
                id = (getRa() * dvD + xqppSat * dvQ) / determinant;
                iq = (-xdppSat * dvD + getRa() * dvQ) / determinant;

                double vqag = terminalVoltage.q + getRa() * iq + leakage * id;
                double vdag = terminalVoltage.d + getRa() * id - leakage * iq;
                airGapFlux = Math.hypot(vqag, vdag) / Math.max(speed, EPS);
            }
            // The load controller reads the terminal measurement without
            // recalculating current or changing the swing-equation input.
            terminalActivePower = terminalVoltage.d * id + terminalVoltage.q * iq;
        }

        private void updateElectricalOutputs() {
            double sat = 1.0 + effectiveSaturation(airGapFlux);
            double dAxisError = -psikdState - (getXd1() - getXl()) * id / sat + eq1State;
            double tempD = (getXd1() - getXd11()) * sat
                    / Math.pow(getXd1() - getXl(), 2.0) * dAxisError;
            double kwId = limitedFieldCompensationProduct(id);
            ladIfd = sat * (eq1State + (getXd() - getXd1()) / sat * (id + tempD))
                    / (1.0 - kwId);

            double psiD = psidpp - saturatedReactance(getXd11(), airGapFlux) * id;
            double psiQ = psiqpp - saturatedReactance(getXq11(), airGapFlux) * iq;
            setPe((psiD * iq - psiQ * id) * getSpeed());
        }

        private Complex actualTerminalCurrent() {
            updateCurrent();
            Vector_dq currentDq = new Vector_dq(id, iq);
            return ComplexFunc.createComplex(DStabFunction.transfer(currentDq, getAngle()))
                    .multiply(getIMultiFactor());
        }

        @Override
        public Object getOutputObject() {
            Complex actualCurrent = actualTerminalCurrent();
            Complex norton = actualCurrent.add(getDStabBus().getVoltage().multiply(getYgen()));
            Complex terminalPower = getDStabBus().getVoltage().multiply(actualCurrent.conjugate());
            setQGen(terminalPower.getImaginary() / getRating());
            updateElectricalOutputs();
            return norton;
        }

        @Override
        public Hashtable<String, Object> getStates(Object ref) {
            Hashtable<String, Object> states = new Hashtable<>();
            states.put(DStabOutSymbol.OUT_SYMBOL_MACH_ED1, getEd1());
            states.put(DStabOutSymbol.OUT_SYMBOL_MACH_EQ1, getEq1());
            states.put(DStabOutSymbol.OUT_SYMBOL_MACH_ED11, getPsid11());
            states.put(DStabOutSymbol.OUT_SYMBOL_MACH_EQ11, getPsiq11());
            states.put("mach.genqec.psiag", airGapFlux);
            states.put("mach.genqec.vcomp", getCompensatedVoltage());
            return states;
        }
    }

    private record Derivatives(double eq1, double psikd, double psikq, double ed1) {}
}
