package org.interpss.plugin.aclf.ecf;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.interpss.plugin.aclf.ecf.EcfLoadModel;

class EcfLoadDerivativeTest {
    @ParameterizedTest @ValueSource(doubles={-.4,0,.4})
    void explicitSquaredVoltagePartialsAndImplicitPfChainRuleMatchIndependentDifferences(double q) {
        double p=.7,x=.91,y=-.17,d=x*x+y*y,h=1e-6;
        var model=new EcfLoadModel(p,q); var value=model.evaluate(x,y,d);
        Complex[] expected={value.dReal(),value.dImaginary(),value.dSquaredMagnitude()};
        for(int k=0;k<3;k++) {
            double[] plus={x,y,d},minus={x,y,d}; plus[k]+=h;minus[k]-=h;
            var actual=independent(p,q,plus[0],plus[1],plus[2]).subtract(independent(p,q,minus[0],minus[1],minus[2])).divide(2*h);
            assertEquals(0,actual.subtract(expected[k]).abs(),2e-9);
        }
        var actual=new Complex(p,q).divide(new Complex(x,y)).conjugate();
        assertEquals(0,actual.subtract(value.current()).abs(),1e-14);
        for(int k=0;k<2;k++) {
            double xp=x+(k==0?h:0),yp=y+(k==1?h:0),xm=x-(k==0?h:0),ym=y-(k==1?h:0);
            var fd=independent(p,q,xp,yp,xp*xp+yp*yp).subtract(independent(p,q,xm,ym,xm*xm+ym*ym)).divide(2*h);
            var analytic=expected[k].add(value.dSquaredMagnitude().multiply(k==0?2*x:2*y));
            assertEquals(0,fd.subtract(analytic).abs(),2e-9);
        }
    }
    private static Complex independent(double p,double q,double x,double y,double d) {
        return new Complex(p,q).conjugate().multiply(new Complex(x,y)).divide(d);
    }
    @Test void invalidDomainIsRejectedWithoutClippingTheEquations() {
        var model=new EcfLoadModel(.7,.2);
        assertThrows(IllegalArgumentException.class,()->model.evaluate(1,0,0));
        assertThrows(IllegalArgumentException.class,()->model.evaluate(Double.NaN,0,1));
    }
    @Test void weightedHessianMatchesFiniteDifferencesOfAllLocalPartials() {
        var model=new EcfLoadModel(.7,-.3);double[] x={.91,-.17,.87};double h=1e-6,wr=.4,wi=-.8;
        var hessian=model.weightedHessian(x[0],x[1],x[2],wr,wi);
        for(int j=0;j<3;j++) {
            double[] plus=x.clone(),minus=x.clone();plus[j]+=h;minus[j]-=h;
            var a=model.evaluate(plus[0],plus[1],plus[2]);var b=model.evaluate(minus[0],minus[1],minus[2]);
            Complex[] ap={a.dReal(),a.dImaginary(),a.dSquaredMagnitude()},bp={b.dReal(),b.dImaginary(),b.dSquaredMagnitude()};
            for(int i=0;i<3;i++) {
                var fd=ap[i].subtract(bp[i]).divide(2*h);
                assertEquals(wr*fd.getReal()+wi*fd.getImaginary(),hessian[i][j],2e-9);
            }
        }
    }
}
