package org.interpss.dstab.control.uel.psse.uel2c;

/** IEEE 421.5-2016 UEL2C parameters in native record order. */
public record Uel2cData(int k1, int k2, int thirdQuadrantMode, int gainMode,
        double tuv, double tup, double tuq, double kui, double kul,
        double vuimax, double vuimin, double kuf, double kfb, double tul,
        double tu1, double tu2, double tu3, double tu4,
        double p0, double q0, double p1, double q1, double p2, double q2,
        double p3, double q3, double p4, double q4, double p5, double q5,
        double p6, double q6, double p7, double q7, double p8, double q8,
        double p9, double q9, double p10, double q10,
        double vulmax, double vulmin, double vbias, double kfix, double tadj,
        double tqref, double vuelmax2, double vuelmin2,
        double vuelmax1, double vuelmin1, double xq) {

    public Uel2cData {
        if (k1 < 0 || k1 > 2 || k2 < 0 || k2 > 2
                || thirdQuadrantMode < 0 || thirdQuadrantMode > 1
                || gainMode < 1 || gainMode > 2) {
            throw new IllegalArgumentException("UEL2C selectors are invalid");
        }
        if (!finite(tuv,tup,tuq,kui,kul,vuimax,vuimin,kuf,kfb,tul,tu1,tu2,tu3,tu4,
                p0,q0,p1,q1,p2,q2,p3,q3,p4,q4,p5,q5,p6,q6,p7,q7,p8,q8,p9,q9,p10,q10,
                vulmax,vulmin,vbias,kfix,tadj,tqref,vuelmax2,vuelmin2,vuelmax1,vuelmin1,xq)) {
            throw new IllegalArgumentException("UEL2C parameters must be finite");
        }
        if (tuv < 0 || tup < 0 || tuq < 0 || tul < 0 || tu2 < 0 || tu4 < 0
                || tadj < 0 || tqref < 0 || vuimax < vuimin || vulmax < vulmin
                || vuelmax2 < vuelmin2 || vuelmax1 < vuelmin1 || xq <= 0) {
            throw new IllegalArgumentException("UEL2C limits or time constants are invalid");
        }
        double[] ps = {p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10};
        int count = pointCount(ps);
        if (count < 2) throw new IllegalArgumentException("UEL2C requires at least two P-Q points");
        for (int i=1;i<count;i++) if (ps[i] <= ps[i-1]) {
            throw new IllegalArgumentException("UEL2C P points must be strictly increasing");
        }
    }

    public double[] pPoints() { return new double[]{p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10}; }
    public double[] qPoints() { return new double[]{q0,q1,q2,q3,q4,q5,q6,q7,q8,q9,q10}; }

    public int pointCount() { return pointCount(pPoints()); }
    private static int pointCount(double[] ps) {
        int count=ps.length;
        for(int i=2;i<ps.length;i++) if(Math.abs(ps[i])<1e-12){count=i;break;}
        return count;
    }
    private static boolean finite(double... values){for(double value:values)if(!Double.isFinite(value))return false;return true;}
}
