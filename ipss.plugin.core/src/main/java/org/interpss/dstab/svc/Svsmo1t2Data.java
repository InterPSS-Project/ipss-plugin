package org.interpss.dstab.svc;

import java.util.List;

/** Published PSS/E SVSMO1T2 parameters in DYR order. */
public record Svsmo1t2Data(int remoteBusNumber,
        List<MssDevice> mssDevices, int mssSwitchingFlag, int slopeFlag,
        double uvSbMax, double uv1, double uv2, double uvTrip,
        double ov1, double ov2, double uvTime1, double uvTime2,
        double ovTime1, double ovTime2, double xc1, double xc2, double xc3,
        double vUpper, double vLower, double tc1, double tb1,
        double tc2, double tb2, double kpv, double kiv,
        double veMax, double veMin, double firingTime,
        double bShort, double bMax, double bMin, double shortTime,
        double kps, double kis, double vrMax, double vrMin,
        double deadbandOuter, double deadbandInner, double deadbandTime,
        double pllDelay, double epsilonMvar, double bLargeCapMvar,
        double bSmallCapMvar, double bLargeIndMvar, double bSmallIndMvar,
        double mssBreakerTime, double largeDelay, double smallDelay,
        double capacitorDischargeTime, double vrefMin, double vrefMax) {

    public record MssDevice(int enabled, String id) { }

    public Svsmo1t2Data {
        mssDevices = List.copyOf(mssDevices);
        if (remoteBusNumber == 0) {
            throw new IllegalArgumentException("SVSMO1T2 remote bus must be nonzero");
        }
        if (mssDevices.size() != 8) {
            throw new IllegalArgumentException("SVSMO1T2 requires exactly eight MSS slots");
        }
        if ((mssSwitchingFlag != 0 && mssSwitchingFlag != 1)
                || (slopeFlag != 0 && slopeFlag != 1)) {
            throw new IllegalArgumentException("SVSMO1T2 flags must be zero or one");
        }
        if (tb1 < 0.0 || tb2 < 0.0 || firingTime < 0.0
                || deadbandTime < 0.0 || pllDelay < 0.0
                || mssBreakerTime < 0.0 || largeDelay < 0.0
                || smallDelay < 0.0 || capacitorDischargeTime < 0.0) {
            throw new IllegalArgumentException("SVSMO1T2 time constants must be nonnegative");
        }
        if (veMax < veMin || bShort < bMax || bMax < bMin
                || vrMax < vrMin || vUpper < vLower || vrefMax < vrefMin) {
            throw new IllegalArgumentException("SVSMO1T2 limits are inverted");
        }
        if (deadbandOuter < 0.0 || deadbandInner < 0.0
                || deadbandInner > deadbandOuter) {
            throw new IllegalArgumentException("SVSMO1T2 deadbands are inconsistent");
        }
    }
}
