package org.interpss.dstab.renewable;

import java.util.List;
import java.util.Objects;

/** Exact standard-native REPCC data contract. No runtime or legacy alias is inferred.
 * All power and impedance quantities use machine MVA base. */
public final class RepccData {
    public static final int ICON_COUNT = 35, CON_COUNT = 57, STATE_COUNT = 13, VAR_COUNT = 36;

    public enum Parameter {
        TFLTR, KP, KI, TFT, TFV, VFRZ, RC, XC, KC, EMAX, EMIN, DBD1, DBD2,
        QVMAX, QVMIN, KPG, KIG, TP, FDBD1, FDBD2, PFREMAX, PFREMIN,
        PMAX, PMIN, TLAG, DDN, DUP, VREFMAX, VREFMIN, QREFMAX, QREFMIN,
        DQREFMAX, DQREFMIN, QVRMAX, QVRMIN, DPRMAX, DPRMIN, PFMAX, PFMIN,
        DPRMAX2, DPRMIN2, PIMAX, PIMIN, TC, QDN1, QDN2, QUP1, QUP2,
        TDELAY1, TDELAY2, TMSSBRK, TOUT, TFRZ, TFRQ, VFRQ, DFMAX, DFMIN
    }

    public record Branch(int from, int to, String circuit) {
        public Branch {
            if (from == Integer.MIN_VALUE || to < 0)
                throw new IllegalArgumentException("Invalid REPCC branch bus number");
            circuit = identifier(circuit);
        }
        public int measurementBus() { return Math.abs(from); }
        public boolean arrivingFlow() { return from < 0; }
    }

    public record Shunt(int bus, String id) {
        public Shunt {
            if (bus < 0) throw new IllegalArgumentException("Invalid REPCC shunt bus number");
            id = identifier(id);
        }
    }

    private final int remoteBus, frequencyBus, compensationFlag, referenceFlag,
            frequencyFlag, feedbackFlag, feedforwardFlag, switchingFlag, reactiveFlag;
    private final Branch measurementBranch, switchingBranch;
    private final List<Shunt> shunts;
    private final double[] constants;

    public RepccData(int remoteBus, Branch measurementBranch, int compensationFlag,
                     int referenceFlag, int frequencyFlag, int feedbackFlag,
                     int feedforwardFlag, int switchingFlag, Branch switchingBranch,
                     List<Shunt> shunts, int frequencyBus, int reactiveFlag, double[] constants) {
        if (remoteBus < 0 || frequencyBus < 0)
            throw new IllegalArgumentException("Invalid REPCC voltage/frequency bus number");
        if (referenceFlag < 0 || referenceFlag > 2)
            throw new IllegalArgumentException("REPCC RefFlag must be zero, one or two");
        for (int flag : new int[]{compensationFlag, frequencyFlag, feedbackFlag,
                feedforwardFlag, switchingFlag, reactiveFlag})
            if (flag != 0 && flag != 1)
                throw new IllegalArgumentException("REPCC binary flags must be zero or one");
        this.remoteBus = remoteBus;
        this.frequencyBus = frequencyBus;
        this.measurementBranch = Objects.requireNonNull(measurementBranch);
        this.switchingBranch = Objects.requireNonNull(switchingBranch);
        this.shunts = List.copyOf(shunts);
        if (this.shunts.size() != 10)
            throw new IllegalArgumentException("REPCC requires ten ordered shunt slots, including unused slots");
        this.compensationFlag = compensationFlag;
        this.referenceFlag = referenceFlag;
        this.frequencyFlag = frequencyFlag;
        this.feedbackFlag = feedbackFlag;
        this.feedforwardFlag = feedforwardFlag;
        this.switchingFlag = switchingFlag;
        this.reactiveFlag = reactiveFlag;
        this.constants = Objects.requireNonNull(constants).clone();
        if (this.constants.length != CON_COUNT)
            throw new IllegalArgumentException("REPCC requires exactly 57 CONs");
        for (double value : this.constants)
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Finite REPCC CONs required");
        for (Parameter p : new Parameter[]{Parameter.TFLTR, Parameter.TFV, Parameter.TFT,
                Parameter.TP, Parameter.TLAG, Parameter.TC, Parameter.TDELAY1,
                Parameter.TDELAY2, Parameter.TMSSBRK, Parameter.TOUT, Parameter.TFRZ, Parameter.TFRQ})
            if (value(p) < 0) throw new IllegalArgumentException("Nonnegative REPCC time required: " + p);
        for (Parameter[] pair : new Parameter[][]{
                {Parameter.EMIN, Parameter.EMAX}, {Parameter.QVMIN, Parameter.QVMAX},
                {Parameter.PFREMIN, Parameter.PFREMAX}, {Parameter.PMIN, Parameter.PMAX},
                {Parameter.VREFMIN, Parameter.VREFMAX}, {Parameter.QREFMIN, Parameter.QREFMAX},
                {Parameter.DQREFMIN, Parameter.DQREFMAX}, {Parameter.QVRMIN, Parameter.QVRMAX},
                {Parameter.DPRMIN, Parameter.DPRMAX}, {Parameter.PFMIN, Parameter.PFMAX},
                {Parameter.DPRMIN2, Parameter.DPRMAX2}, {Parameter.PIMIN, Parameter.PIMAX},
                {Parameter.DFMIN, Parameter.DFMAX}})
            if (value(pair[0]) > value(pair[1]))
                throw new IllegalArgumentException("Ordered REPCC bounds required: " + pair[0]);
        if (value(Parameter.DBD1) > 0 || value(Parameter.DBD2) < 0
                || value(Parameter.FDBD1) > 0 || value(Parameter.FDBD2) < 0
                || value(Parameter.DFMIN) > 0 || value(Parameter.DFMAX) < 0
                || value(Parameter.DDN) < 0 || value(Parameter.DUP) < 0)
            throw new IllegalArgumentException("Invalid REPCC deadband/frequency polarity");
    }

    private static String identifier(String text) {
        String result = Objects.requireNonNull(text).trim();
        if (result.isEmpty() || result.length() > 2 || result.contains("'"))
            throw new IllegalArgumentException("REPCC identifiers require one or two characters");
        return result;
    }
    public double value(Parameter p) { return constants[p.ordinal()]; }
    public double[] constants() { return constants.clone(); }
    public int remoteBus() { return remoteBus; }
    public int frequencyBus() { return frequencyBus; }
    public Branch measurementBranch() { return measurementBranch; }
    public Branch switchingBranch() { return switchingBranch; }
    public List<Shunt> shunts() { return shunts; }
    public int compensationFlag() { return compensationFlag; }
    public int referenceFlag() { return referenceFlag; }
    public int frequencyFlag() { return frequencyFlag; }
    public int feedbackFlag() { return feedbackFlag; }
    public int feedforwardFlag() { return feedforwardFlag; }
    public int switchingFlag() { return switchingFlag; }
    public int reactiveFlag() { return reactiveFlag; }
    /** Invalid threshold ordering disables switching; it must not reorder the data. */
    public boolean switchingThresholdsValid() {
        return value(Parameter.QDN2) < value(Parameter.QDN1) && value(Parameter.QDN1) < 0
                && value(Parameter.QUP2) > value(Parameter.QUP1) && value(Parameter.QUP1) > 0;
    }
    public double activePiMinimum() { return value(feedforwardFlag == 0 ? Parameter.PMIN : Parameter.PIMIN); }
    public double activePiMaximum() { return value(feedforwardFlag == 0 ? Parameter.PMAX : Parameter.PIMAX); }
    public int effectiveReferenceFlag(double measuredActivePower) {
        if (!Double.isFinite(measuredActivePower)) throw new IllegalArgumentException("Finite REPCC power required");
        return referenceFlag == 2 && Math.abs(measuredActivePower) < .01 ? 0 : referenceFlag;
    }
    /** Reference slew filters use twice the actual integration step, not a stored CON. */
    public double referenceSlewTime(double step) {
        if (!Double.isFinite(step) || step <= 0) throw new IllegalArgumentException("Positive finite REPCC timestep required");
        return 2 * step;
    }
}
