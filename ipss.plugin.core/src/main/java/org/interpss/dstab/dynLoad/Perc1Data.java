package org.interpss.dstab.dynLoad;

/** Parameters for the PSS/E/PowerWorld PERC1 power-electronic load model. */
public record Perc1Data(
        double lfm, double qpRatio, double dbfl, double dbfh, double kdroop,
        double kvp, double tvp, double kvq, double tvq,
        double tap, double tbp, double taq, double tbq,
        double nP, double nQ, double ipmax, double ipmin,
        double iqmax, double iqmin, double fcease, double vcease,
        double tcease, double tdelay, double vrecon, double trecon,
        double tramp, double frecon, double tt, double tv, double tf) {

    public static Perc1Data defaults() {
        return new Perc1Data(.80, .66, 0, 0, 0, 0, .10, 0, .10,
                0, 0, 0, 0, 0, 1, 1, 0, .66, -.66, 1, .5,
                .01, 0, .6, .05, 1, 1, .02, .02, .02);
    }
}
