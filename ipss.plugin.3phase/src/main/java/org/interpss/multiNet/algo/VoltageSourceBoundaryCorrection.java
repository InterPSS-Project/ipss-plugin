package org.interpss.multiNet.algo;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;

/** Coupled phase-port Newton system; transmission input blocks use 120 coordinates. */
final class VoltageSourceBoundaryCorrection {
    private VoltageSourceBoundaryCorrection(){}
    static Complex3x3 effectiveAdmittance(Complex3x3 port,Complex sourceAdmittance){
        var ys=new Complex3x3(sourceAdmittance,sourceAdmittance,sourceAdmittance);
        return port.inv().subtract(ys).multiply(port).multiply(ys);
    }
    static void requireFinite(Complex3x1 vector){
        if(!Double.isFinite(vector.a_0.abs())||!Double.isFinite(vector.b_1.abs())||!Double.isFinite(vector.c_2.abs()))
            throw new IllegalArgumentException("Nonfinite voltage-source boundary correction");
    }
    static List<Complex3x1> solve(Complex3x3[][] z,List<Complex3x3> effective,List<Complex3x1> delta){
            int dimension=3*effective.size();var jacobian=new Complex[dimension][dimension];var rhs=new Complex[dimension];
            for(int i=0;i<effective.size();i++){
                var residual=new Complex3x1();
                for(int j=0;j<effective.size();j++){
                    var impedance=z[i][j].ToAbc();residual=residual.add(impedance.multiply(delta.get(j)));
                    var block=impedance.multiply(effective.get(j));
                    var entries=new Complex[][]{{block.aa,block.ab,block.ac},{block.ba,block.bb,block.bc},{block.ca,block.cb,block.cc}};
                    for(int r=0;r<3;r++)for(int c=0;c<3;c++)jacobian[3*i+r][3*j+c]=entries[r][c].add(i==j&&r==c?Complex.ONE:Complex.ZERO);
                }
                rhs[3*i]=residual.a_0;rhs[3*i+1]=residual.b_1;rhs[3*i+2]=residual.c_2;
            }
            var matrix=new org.apache.commons.math3.linear.Array2DRowFieldMatrix<Complex>(jacobian,false);
            var correction=new org.apache.commons.math3.linear.FieldLUDecomposition<Complex>(matrix).getSolver()
                    .solve(new org.apache.commons.math3.linear.ArrayFieldVector<Complex>(rhs,false));
        var result=new java.util.ArrayList<Complex3x1>();
        for(int i=0;i<effective.size();i++)result.add(new Complex3x1(correction.getEntry(3*i),correction.getEntry(3*i+1),correction.getEntry(3*i+2)));
        result.forEach(VoltageSourceBoundaryCorrection::requireFinite);
        return List.copyOf(result);
    }
}
