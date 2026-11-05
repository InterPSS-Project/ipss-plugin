package org.interpss.core.dclf.edclf;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.*;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Test;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

/** Independent dense branch-stamped oracle for published EDCPF equations (5)--(6). */
public class IEEE14_EDclfPaperOracleTest extends CorePluginTestSetup {
    private static final String FILE="testData/adpter/ieee_format/Ieee14Bus.ieee";
    @Test public void publishedLinearEquationMatchesAllPqVoltages() throws Exception {
        AclfNetwork net=CorePluginFactory.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF).load(FILE).getAclfNet();
        var algorithm=DclfAlgoObjectFactory.createEDclfAlgorithm(net,CacheType.SenNotCached);
        assertTrue(algorithm.calculateDclf(DclfMethod.STD));assertTrue(algorithm.transfer2AclfNet(true));
        Complex[] expected=paperEquation(net);
        var actual=DclfAlgoObjectFactory.createEDclfSolver(algorithm).calPQBusVoltMag();
        assertEquals(expected.length,actual.length);
        for(int i=0;i<actual.length;i++)assertEquals(0.,actual[i].subtract(expected[i]).abs(),1.e-10,"Published complex linear equation, PQ index "+i);
        // Preserve the existing compatibility tolerance and independently certify its checkpoints.
        assertEquals(1.01638,expected[0].getReal(),1.e-4);assertEquals(1.01805,expected[1].getReal(),1.e-4);assertEquals(1.03458,expected[8].getReal(),1.e-4);
    }
    @Test public void publicEdclfKeepsThePublishedSingleLinearSolve() throws Exception {
        AclfNetwork net=CorePluginFactory.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF).load(FILE).getAclfNet();
        var algorithm=DclfAlgoObjectFactory.createEDclfAlgorithm(net,CacheType.SenNotCached);
        assertTrue(algorithm.calculateDclf(DclfMethod.STD));assertTrue(algorithm.transfer2AclfNet(true));
        Complex[] expected=paperEquation(net);
        assertTrue(algorithm.calculateEDclf(DclfMethod.STD));int k=0;
        for(var bus:net.getBusList())if(!bus.isPV()&&!bus.isSwing())assertEquals(expected[k++].getReal(),bus.getVoltageMag(),1.e-10,"Single-solve magnitude "+bus.getId());
    }
    static Complex[] paperEquation(AclfNetwork net) throws Exception {
        // Fixture parsing and stamping are independent of core network/Y-matrix helpers.
        int n=14;Complex[][] y=new Complex[n][n];for(var row:y)Arrays.fill(row,Complex.ZERO);
        Complex[] s=new Complex[n];int[] type=new int[n];boolean buses=false,branches=false;
        for(String line:Files.readAllLines(Path.of(FILE))) {
            if(line.startsWith("BUS DATA")){buses=true;continue;}if(line.startsWith("BRANCH DATA")){branches=true;continue;}
            if(line.startsWith("-999")){buses=false;branches=false;continue;}
            if(!buses&&!branches)continue;String[] f=line.strip().split("\\s+");
            if(buses){assertEquals("Bus",f[1]);int i=Integer.parseInt(f[0])-1;type[i]=Integer.parseInt(f[6]);s[i]=new Complex((Double.parseDouble(f[11])-Double.parseDouble(f[9]))/100.,(Double.parseDouble(f[12])-Double.parseDouble(f[10]))/100.);y[i][i]=y[i][i].add(new Complex(Double.parseDouble(f[17]),Double.parseDouble(f[18])));}
            else {int i=Integer.parseInt(f[0])-1,j=Integer.parseInt(f[1])-1;Complex series=new Complex(Double.parseDouble(f[6]),Double.parseDouble(f[7])).reciprocal();Complex charging=new Complex(0.,Double.parseDouble(f[8])/2.);double tap=Double.parseDouble(f[14]);if(tap==0.)tap=1.;y[i][i]=y[i][i].add(series.add(charging).divide(tap*tap));y[j][j]=y[j][j].add(series.add(charging));y[i][j]=y[i][j].subtract(series.divide(tap));y[j][i]=y[j][i].subtract(series.divide(tap));}
        }
        var pq=new ArrayList<Integer>();for(int i=0;i<n;i++)if(type[i]==0)pq.add(i);int m=pq.size();assertEquals(9,m);
        double[][] block=new double[2*m][2*m];double[] rhs=new double[2*m];
        for(int a=0;a<m;a++) {int i=pq.get(a);double theta=net.getBus("Bus"+(i+1)).getVoltageAng();Complex phase=unit(theta);Complex power=s[i].conjugate();Complex b=power.multiply(2.).multiply(phase);
            for(int j=0;j<n;j++)if(type[j]!=0)b=b.subtract(y[i][j].multiply(net.getBus("Bus"+(j+1)).getVoltage()));
            rhs[a]=b.getReal();rhs[a+m]=b.getImaginary();
            for(int bIndex=0;bIndex<m;bIndex++){int j=pq.get(bIndex);Complex value=y[i][j].add(i==j?power:Complex.ZERO);block[a][bIndex]=value.getReal();block[a][bIndex+m]=-value.getImaginary();block[a+m][bIndex]=value.getImaginary();block[a+m][bIndex+m]=value.getReal();}
        }
        var matrix=new Array2DRowRealMatrix(block);var b=new ArrayRealVector(rhs);var solution=new LUDecomposition(matrix).getSolver().solve(b);
        assertEquals(0.,matrix.operate(solution).subtract(b).getLInfNorm(),1.e-12,"Independent dense nodal residual");
        Complex[] result=new Complex[m];for(int i=0;i<m;i++)result[i]=new Complex(solution.getEntry(i),solution.getEntry(i+m)).multiply(unit(-net.getBus("Bus"+(pq.get(i)+1)).getVoltageAng()));return result;
    }
    private static Complex unit(double angle){return new Complex(Math.cos(angle),Math.sin(angle));}
}
