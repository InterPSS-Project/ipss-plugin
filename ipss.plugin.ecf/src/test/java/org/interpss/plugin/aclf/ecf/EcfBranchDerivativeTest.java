package org.interpss.plugin.aclf.ecf;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import org.interpss.plugin.aclf.ecf.EcfBranchModel;

class EcfBranchDerivativeTest {
    @Test void currentAndMvaGradientsAndHessiansMatchIndependentTerminalCalculations() {
        var branch=new EcfBranchModel("asymmetric",0,1,new Complex(.8,-8),new Complex(-1,8.3),
                new Complex(-.6,7.9),new Complex(.9,-8.1));
        double[] x={1.02,.04,.94,-.08};double h=1e-6;
        for(boolean from:new boolean[]{false,true}) for(boolean mva:new boolean[]{false,true}) {
            var value=evaluate(branch,from,mva,x);
            var vf=new Complex(x[0],x[1]);var vt=new Complex(x[2],x[3]);
            var first=branch.thermalFirstOrder(from,mva,vf,vt);
            assertEquals(value.value(),first.value(),0);assertArrayEquals(value.gradient(),first.gradient(),0);
            assertEquals(value.value(),branch.thermalValue(from,mva,vf,vt),0);
            for(int j=0;j<4;j++) {
                double[] plus=x.clone(),minus=x.clone();plus[j]+=h;minus[j]-=h;
                var p=evaluate(branch,from,mva,plus);var m=evaluate(branch,from,mva,minus);
                assertEquals(value.gradient()[j],(independent(branch,from,mva,plus)-independent(branch,from,mva,minus))/(2*h),1e-7);
                for(int i=0;i<4;i++) {
                    assertEquals(value.hessian()[i][j],(p.gradient()[i]-m.gradient()[i])/(2*h),1e-7);
                    assertEquals(value.hessian()[i][j],value.hessian()[j][i],1e-12);
                }
            }
        }
        assertNotEquals(evaluate(branch,true,false,x).value(),evaluate(branch,true,true,x).value());
    }
    private static EcfBranchModel.ThermalEvaluation evaluate(EcfBranchModel b,boolean f,boolean m,double[] x) {
        return b.thermal(f,m,new Complex(x[0],x[1]),new Complex(x[2],x[3]));
    }
    private static double independent(EcfBranchModel b,boolean from,boolean mva,double[] x) {
        var vf=new Complex(x[0],x[1]);var vt=new Complex(x[2],x[3]);
        var current=from?b.fromCurrent(vf,vt):b.toCurrent(vf,vt);
        var terminal=from?vf:vt;var quantity=mva?terminal.multiply(current.conjugate()):current;
        return quantity.getReal()*quantity.getReal()+quantity.getImaginary()*quantity.getImaginary();
    }
}
