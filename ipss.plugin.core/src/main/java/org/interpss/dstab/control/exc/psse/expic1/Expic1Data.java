package org.interpss.dstab.control.exc.psse.expic1;

/** Exact ordered twenty-four-constant EXPIC1 excitation data. */
public record Expic1Data(double tr,double ka,double ta1,double vr1,double vr2,
        double ta2,double ta3,double ta4,double vrmax,double vrmin,double kf,double tf1,double tf2,
        double efdmax,double efdmin,double ke,double te,double e1,double se1,double e2,double se2,
        double kp,double ki,double kc) {
    public Expic1Data {
        double[] values={tr,ka,ta1,vr1,vr2,ta2,ta3,ta4,vrmax,vrmin,kf,tf1,tf2,
                efdmax,efdmin,ke,te,e1,se1,e2,se2,kp,ki,kc};
        for(double value:values)if(!Double.isFinite(value))throw new IllegalArgumentException("Finite EXPIC1 constants required");
        if(tr<0||ka<0||ta1<0||ta2<0||ta3<0||ta4<0||kf<0||tf1<=0||tf2<0||te<0||kc<0)
            throw new IllegalArgumentException("Invalid EXPIC1 gain or time constant");
        if(ta2==0&&ta3!=0)throw new IllegalArgumentException("EXPIC1 lead time requires its lag pole");
    }
    public static Expic1Data fromNativeParameters(double[] c) {
        if(c.length!=24)throw new IllegalArgumentException("EXPIC1 requires exactly 24 constants");
        return new Expic1Data(c[0],c[1],c[2],c[3],c[4],c[5],c[6],c[7],c[8],c[9],c[10],c[11],
                c[12],c[13],c[14],c[15],c[16],c[17],c[18],c[19],c[20],c[21],c[22],c[23]);
    }
}
