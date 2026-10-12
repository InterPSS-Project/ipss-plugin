package org.interpss.multiNet.algo;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;

class VoltageSourceBoundaryCorrectionTest {
    @Test void stiffSinglePortSatisfiesPhysicalCircuitResidual(){verify(1);}
    @Test void mutualPortsAndUnbalancedAdmittancesSatisfyPhysicalCircuitResidual(){verify(2);}
    @Test void finiteSourceDerivativeMatchesUnreducedFeederCircuit(){
        var physical=new Complex3x3(new Complex(1000,.2),new Complex(.5,.2),new Complex(.7,.1));
        var y=Complex.ONE.divide(new Complex(0,1e-5));var ys=new Complex3x3(y,y,y);
        var port=physical.add(ys).inv();var derivative=VoltageSourceBoundaryCorrection.effectiveAdmittance(port,y);
        var voltage=new Complex3x1(Complex.ONE,new Complex(-.5,-.866),new Complex(-.5,.866));
        var feederVoltage=port.multiply(ys.multiply(voltage));
        assertEquals(0,derivative.multiply(voltage).subtract(physical.multiply(feederVoltage)).absMax(),1e-9);
    }
    @Test void nonfiniteCorrectionsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->VoltageSourceBoundaryCorrection.requireFinite(
            new Complex3x1(Complex.NaN,Complex.ZERO,Complex.ZERO)));
    }
    private void verify(int count){
        var z=new Complex3x3[count][count];var admittances=new ArrayList<Complex3x3>();
        var source=new ArrayList<Complex3x1>();var injections=new ArrayList<Complex3x1>();var residual=new ArrayList<Complex3x1>();
        for(int i=0;i<count;i++){
            for(int j=0;j<count;j++)z[i][j]=i==j?new Complex3x3(new Complex(.05,.2),new Complex(.06,.21),new Complex(.04,.3))
                :new Complex3x3(new Complex(.005,.02),new Complex(.005,.02),new Complex(.005,.02));
            var y=new Complex3x3();y.aa=new Complex(1000*(i+1),.2);y.bb=new Complex(.5,.2);y.cc=new Complex(.7,.1);
            y.ab=y.ba=new Complex(-.01,0);y.ac=y.ca=new Complex(-.02,0);admittances.add(y);
            var e=new Complex3x1(Complex.ONE,new Complex(-.5,-Math.sqrt(3)/2),new Complex(-.5,Math.sqrt(3)/2));source.add(e);
            var current=new Complex3x1(new Complex(.1,.02),new Complex(.02,.01),new Complex(.03,.02));injections.add(current);
            residual.add(current.subtract(y.multiply(e))); // Old transmission injection is zero: V_old=E.
        }
        var dv=VoltageSourceBoundaryCorrection.solve(z,admittances,residual);var voltage=new ArrayList<Complex3x1>();
        var current=new ArrayList<Complex3x1>();
        for(int i=0;i<count;i++){
            voltage.add(source.get(i).add(dv.get(i)));
            current.add(injections.get(i).subtract(admittances.get(i).multiply(voltage.get(i))));
        }
        // Independent circuit equation V_i = E_i + sum_j Z_ij I_j(V_j).
        for(int i=0;i<count;i++){
            var expected=source.get(i);
            for(int j=0;j<count;j++)expected=expected.add(z[i][j].ToAbc().multiply(current.get(j)));
            assertEquals(0,voltage.get(i).subtract(expected).absMax(),1e-10);
        }
        assertTrue(voltage.get(0).a_0.abs()<.02);assertTrue(voltage.get(0).b_1.abs()>.5);
    }
}
