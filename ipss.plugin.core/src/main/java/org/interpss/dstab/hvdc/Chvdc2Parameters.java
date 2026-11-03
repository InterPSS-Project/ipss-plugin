package org.interpss.dstab.hvdc;

import java.util.Objects;

/** Immutable named-link source data. Decoding does not imply runtime attachment or qualification. */
public final class Chvdc2Parameters {
    public static final int ICON_COUNT = 3, CONSTANT_COUNT = 48, STATE_COUNT = 18, VARIABLE_COUNT = 24;
    /** Preserve the dialect: matching allocations do not prove identical equations or auxiliary routing. */
    public enum Dialect { CHVDC2, CHVDC2U1 }
    /** Source order and units. Per-unit current and voltage use the entered DC ratings. */
    public enum Constant {
        KP_R, KI_R, ALPHA_MAX_R_DEG, ALPHA_MIN_R_DEG, T_ALPHA_R_SEC,
        KP_I, KI_I, ALPHA_MIN_I_DEG, T_ALPHA_I_SEC,
        K_COS, K_REF, T_REF_SEC, K_MAX, T_MAX_SEC, MAX_C_I, MIN_C_I, COS_MIN_I,
        T_MEAS_SEC, MAX_RATE_DEG, MIN_RATE_DEG,
        I_DC_1_PU, I_DC_2_PU, V_DC_1_PU, V_DC_2_PU,
        T_UP_R_SEC, T_DOWN_R_SEC, T_UP_I_SEC, T_DOWN_I_SEC,
        I_DC_RATED_A, V_DC_RATED_KV, GAMMA_CF_DEG, V_AC_UCF_PU, T_CF_SEC,
        MAX_ERROR_PU, MIN_ERROR_PU, I_MAX_LIMIT_PU, I_MIN_LIMIT_PU, T_VD_SEC,
        ALPHA_MAX_RAM_DEG, T_RAM_SEC, V_RAM_PU, T_TIMER_RAM_SEC, V_AC_REF_PU,
        I_MARGIN_PU, L_LINE_MH, L_SMOOTH_R_MH, L_SMOOTH_I_MH, C_LINE_MICRO_F
    }
    private final String linkName;
    private final Dialect dialect;
    private final int[] flags;
    private final double[] constants;

    public Chvdc2Parameters(String linkName, Dialect dialect, int[] flags, double[] constants) {
        if (linkName == null || linkName.isBlank()) throw new IllegalArgumentException("Named DC link required");
        this.linkName = linkName.trim();
        this.dialect = Objects.requireNonNull(dialect);
        if (flags.length != ICON_COUNT || constants.length != CONSTANT_COUNT)
            throw new IllegalArgumentException("CHVDC2 data requires three flags and 48 constants");
        this.flags = flags.clone(); this.constants = constants.clone();
        for (int flag : this.flags) if (flag != 0 && flag != 1)
            throw new IllegalArgumentException("CHVDC2 flags must be integer zero or one");
        for (Constant c : Constant.values()) if (!Double.isFinite(value(c)))
            throw new IllegalArgumentException("Nonfinite CHVDC2 constant " + c);
        for (Constant c : new Constant[] {Constant.KI_R, Constant.T_ALPHA_R_SEC, Constant.KI_I,
                Constant.T_ALPHA_I_SEC, Constant.K_REF, Constant.I_DC_1_PU, Constant.I_DC_2_PU,
                Constant.V_DC_1_PU, Constant.V_DC_2_PU, Constant.T_UP_R_SEC, Constant.T_UP_I_SEC,
                Constant.I_DC_RATED_A, Constant.V_DC_RATED_KV, Constant.T_VD_SEC, Constant.T_RAM_SEC})
            if (value(c) <= 0) throw new IllegalArgumentException("Positive CHVDC2 constant required: " + c);
        for (Constant c : new Constant[] {Constant.T_REF_SEC, Constant.T_MAX_SEC, Constant.T_MEAS_SEC,
                Constant.T_DOWN_R_SEC, Constant.T_DOWN_I_SEC, Constant.T_CF_SEC, Constant.T_TIMER_RAM_SEC,
                Constant.L_LINE_MH, Constant.L_SMOOTH_R_MH, Constant.L_SMOOTH_I_MH, Constant.C_LINE_MICRO_F})
            if (value(c) < 0) throw new IllegalArgumentException("Nonnegative CHVDC2 constant required: " + c);
        ordered(Constant.ALPHA_MIN_R_DEG, Constant.ALPHA_MAX_R_DEG);
        ordered(Constant.MIN_C_I, Constant.MAX_C_I);
        ordered(Constant.MIN_RATE_DEG, Constant.MAX_RATE_DEG);
        ordered(Constant.MIN_ERROR_PU, Constant.MAX_ERROR_PU);
        ordered(Constant.I_MIN_LIMIT_PU, Constant.I_MAX_LIMIT_PU);
        ordered(Constant.I_DC_1_PU, Constant.I_DC_2_PU);
        if (value(Constant.V_DC_1_PU) >= value(Constant.V_DC_2_PU))
            throw new IllegalArgumentException("CHVDC2 voltage breakpoints must strictly increase");
        if (value(Constant.COS_MIN_I) < 0 || value(Constant.COS_MIN_I) > 1)
            throw new IllegalArgumentException("CHVDC2 cosine limit must be in [0,1]");
    }
    private void ordered(Constant lower, Constant upper) {
        if (value(lower) > value(upper)) throw new IllegalArgumentException("Reversed CHVDC2 limits: " + lower + "/" + upper);
    }
    public String linkName() { return linkName; }
    public Dialect dialect() { return dialect; }
    public boolean acVoltageCurrentLimitEnabled() { return flags[0] != 0; }
    public boolean rectifierMinimumAngleLogicEnabled() { return flags[1] != 0; }
    public boolean lineDynamicsEnabled() { return flags[2] != 0; }
    public double value(Constant constant) { return constants[constant.ordinal()]; }
    public int[] copySourceFlags() { return flags.clone(); }
    public double[] copySourceConstants() { return constants.clone(); }
    /** Independently owned PI/angle storage; outer measurements, angle-bound logic and attachment remain separate. */
    public Chvdc2CurrentControls prepareCurrentControls() { return new Chvdc2CurrentControls(this); }
    /** Shared washout state and explicitly committed RAML/commutation histories. */
    public Chvdc2Protection prepareProtection() { return new Chvdc2Protection(this); }
    /** Joint controller coordinates with explicit protection-stage and transaction ownership. */
    public Chvdc2ControlAssembly prepareControls(double inverterTransformerRatio,double inverterTap) {
        return new Chvdc2ControlAssembly(this,inverterTransformerRatio,inverterTap);
    }
    /** Independently owned transducers, current-order controls and calculated inverter angle bound. */
    public Chvdc2OrderControls prepareOrderControls(double inverterTransformerRatio,double inverterTap) {
        return new Chvdc2OrderControls(this,inverterTransformerRatio,inverterTap);
    }
    /** Prepare physical line coefficients once. Reactances are per bridge in ohms at the supplied Hz.
     * Any source-specific capacitance suppression must be established separately, not inferred from a local step. */
    public Chvdc2DcLine prepareDcLine(double lineOhms,int bridges,double rectifierCommutationOhms,
            double inverterCommutationOhms,double frequencyHz) {
        if(bridges<=0||!Double.isFinite(rectifierCommutationOhms)||rectifierCommutationOhms<0
                ||!Double.isFinite(inverterCommutationOhms)||inverterCommutationOhms<0
                ||!Double.isFinite(frequencyHz)||frequencyHz<=0)
            throw new IllegalArgumentException("Positive bridge count/frequency and nonnegative commutation reactances required");
        double henries=(value(Constant.L_LINE_MH)+value(Constant.L_SMOOTH_R_MH)+value(Constant.L_SMOOTH_I_MH))*.001
                +bridges*1.75*(rectifierCommutationOhms+inverterCommutationOhms)/(2*Math.PI*frequencyHz);
        return new Chvdc2DcLine(lineOhms,henries,value(Constant.C_LINE_MICRO_F)*1e-6,lineDynamicsEnabled());
    }
}
