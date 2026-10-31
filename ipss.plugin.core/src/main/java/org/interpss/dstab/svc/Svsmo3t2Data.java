package org.interpss.dstab.svc;

import java.util.List;
import java.util.Objects;

/** Exact SVSMO3T2 named FACTS data; all currents use the explicit STATCOM base. */
public record Svsmo3t2Data(int remoteBusNumber, int mssSwitchingFlag,
        int slowResetFlag, int nonlinearDroopFlag, List<MssDevice> mssDevices,
        double xc0, double tc1, double tb1, double kp, double ki,
        double veMax, double veMin, double firingTime, double continuousCurrent,
        double deadband, double deadbandRatio, double deadbandTime,
        double kpr, double kir, double currentDeadband, double vrMax, double vrMin,
        double shortCurrentMultiplier, double uv1, double uv2, double ov1, double ov2,
        double tripVoltage, double shortRatingTime, double tripTime,
        double vrefMax, double vrefMin, double tc2, double tb2,
        double thermalLimit, double thermalResetRate, double thermalHysteresis,
        double xc1, double xc2, double xc3, double vUpper, double vLower,
        double mssBreakerTime, double capacitorDischargeTime, double mssSwitchingTime,
        double mssUpperCurrent, double mssLowerCurrent, double unblockTime,
        double statcomBaseMva) {

    /** A zero bus disables the slot; identifiers remain case-preserving. */
    public record MssDevice(int busNumber, String id) {
        public MssDevice {
            id = Objects.requireNonNull(id, "MSS id").trim();
            if (busNumber < 0 || id.isEmpty()) {
                throw new IllegalArgumentException("SVSMO3T2 invalid MSS identity");
            }
        }
    }

    public Svsmo3t2Data {
        mssDevices = List.copyOf(mssDevices);
        if (remoteBusNumber < 0 || mssDevices.size() != 8
                || !flag(mssSwitchingFlag) || !flag(slowResetFlag) || !flag(nonlinearDroopFlag)) {
            throw new IllegalArgumentException("SVSMO3T2 invalid remote bus, flags or MSS slots");
        }
        for (double value : new double[]{xc0, tc1, tb1, kp, ki, veMax, veMin,
                firingTime, continuousCurrent, deadband, deadbandRatio, deadbandTime,
                kpr, kir, currentDeadband, vrMax, vrMin, shortCurrentMultiplier,
                uv1, uv2, ov1, ov2, tripVoltage, shortRatingTime, tripTime,
                vrefMax, vrefMin, tc2, tb2, thermalLimit, thermalResetRate,
                thermalHysteresis, xc1, xc2, xc3, vUpper, vLower, mssBreakerTime,
                capacitorDischargeTime, mssSwitchingTime, mssUpperCurrent,
                mssLowerCurrent, unblockTime, statcomBaseMva}) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("SVSMO3T2 requires finite parameters");
            }
        }
        if (tc1 < 0 || tb1 < 0 || tc2 < 0 || tb2 < 0 || firingTime < 0
                || deadbandTime < 0 || shortRatingTime < 0 || tripTime < 0
                || mssBreakerTime < 0 || capacitorDischargeTime < 0
                || mssSwitchingTime < 0 || unblockTime < 0) {
            throw new IllegalArgumentException("SVSMO3T2 time constants must be nonnegative");
        }
        if (veMax < veMin || vrMax < vrMin || vrefMax < vrefMin
                || vUpper < vLower || uv1 < uv2 || ov2 < ov1 || ov1 < uv1
                || mssUpperCurrent < mssLowerCurrent || continuousCurrent <= 0
                || shortCurrentMultiplier < 1 || statcomBaseMva <= 0) {
            throw new IllegalArgumentException("SVSMO3T2 invalid limits or current/MVA ratings");
        }
        if (deadband < 0 || deadbandRatio < 0 || (deadband > 0 && deadbandRatio < 1)
                || currentDeadband < 0 || thermalLimit < 0 || thermalResetRate < 0
                || thermalHysteresis < 0 || thermalHysteresis > thermalLimit) {
            throw new IllegalArgumentException("SVSMO3T2 invalid deadband or thermal parameters");
        }
    }

    private static boolean flag(int value) { return value == 0 || value == 1; }
}
