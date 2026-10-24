package org.interpss.dstab.algo;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;

/** Local step resolution for the two-step recurrence, without modifying model coefficients. */
public final class ControllerStepStability {
    private ControllerStepStability(){}
    public static int substeps(double[][] jacobian,double step){
        if(!Double.isFinite(step)||step<=0)throw new IllegalArgumentException("Positive finite integration step required");
        if(jacobian.length==0)return 1;
        for(double[] row:jacobian){
            if(row.length!=jacobian.length)throw new IllegalArgumentException("Square controller Jacobian required");
            for(double value:row)if(!Double.isFinite(value))throw new IllegalArgumentException("Finite controller Jacobian required");
        }
        var eigen=new EigenDecomposition(new Array2DRowRealMatrix(jacobian,false));
        double[] real=eigen.getRealEigenvalues(),imaginary=eigen.getImagEigenvalues();
        for(int count=1;count<=1024;count*=2){
            boolean stable=true;
            for(int i=0;i<real.length;i++){
                // Reserve a 20% step-size margin. A genuinely unstable continuous
                // pole is resolved, not removed by pretending its growth is numerical.
                Complex z=new Complex(real[i],imaginary[i]).multiply(step/count/.8);
                if(real[i]>1e-9){stable&=z.abs()<=1.;continue;}
                Complex coefficient=z.multiply(1.5).add(Complex.ONE);
                Complex root=coefficient.multiply(coefficient).subtract(z.multiply(2.)).sqrt();
                stable&=coefficient.add(root).multiply(.5).abs()<=1.+1e-10
                        &&coefficient.subtract(root).multiply(.5).abs()<=1.+1e-10;
            }
            if(stable)return count;
        }
        throw new IllegalArgumentException("Controller requires more than 1024 local integration steps");
    }
}
