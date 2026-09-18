package org.interpss.dstab.renewable;

/** Published DERA1/DERAU1 selector and constant order. */
public record Dera1Data(
        int pfFlag, int freqFlag, int pqFlag, int genFlag, int vtripFlag, int ftripFlag,
        double trv, double trf, double dbd1, double dbd2, double kqv, double vref0,
        double tp, double tiq, double ddn, double dup, double fdbd1, double fdbd2,
        double femax, double femin, double pmax, double pmin, double dpmax, double dpmin,
        double tpord, double kpg, double kig, double imax,
        double vl0, double vl1, double vh0, double vh1,
        double tvl0, double tvl1, double tvh0, double tvh1, double vrfrac,
        double fl, double fh, double tfl, double tfh, double tg, double rrpwr,
        double tv, double vpr, double iqhl, double iqll) {

    public Dera1Data {
        if (!binary(pfFlag) || !binary(freqFlag) || !binary(pqFlag)
                || !binary(genFlag) || !binary(vtripFlag) || !binary(ftripFlag)) {
            throw new IllegalArgumentException("DERA1 selectors must be zero or one");
        }
        if (!finite(trv,trf,dbd1,dbd2,kqv,vref0,tp,tiq,ddn,dup,fdbd1,fdbd2,
                femax,femin,pmax,pmin,dpmax,dpmin,tpord,kpg,kig,imax,vl0,vl1,vh0,vh1,
                tvl0,tvl1,tvh0,tvh1,vrfrac,fl,fh,tfl,tfh,tg,rrpwr,tv,vpr,iqhl,iqll)) {
            throw new IllegalArgumentException("DERA1 constants must be finite");
        }
        if (trv < 0 || trf < 0 || tp < 0 || tiq < 0 || tpord < 0 || tv < 0
                || tvl0 < 0 || tvl1 < 0 || tvh0 < 0 || tvh1 < 0 || tfl < 0 || tfh < 0
                || tg <= 0 || ddn <= 0 || dup <= 0 || dpmax <= 0 || dpmin >= 0
                || rrpwr < 0 || imax <= 0 || dbd1 > 0 || dbd2 < 0
                || fdbd1 > 0 || fdbd2 < 0 || femax < femin || pmax < pmin
                || vl1 < vl0 || vh0 < vh1 || vrfrac < 0 || vrfrac > 1
                || fh < fl || iqhl < iqll) {
            throw new IllegalArgumentException("DERA1 limits or time constants are invalid");
        }
    }

    private static boolean binary(int value) { return value == 0 || value == 1; }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
