package org.interpss.sample.acsc;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.util.sample.SampleTestingCases;


public class ZBusSample {
	public static void main(String args[]) throws IpssNumericException {
		IpssCorePlugin.init();
		
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
  		SampleTestingCases.load_LF_5BusSystem(net);

		// bus number is arranged during the process to minimize the fill-ins 
		ISparseEqnComplex eqn = net.formYMatrix();
		
		// assume swing connect to the ground
		AclfBus swing = net.getBus("5");
		int busNo = swing.getSortNumber();
		eqn.setA(new Complex(0.0, 1.0e10), busNo, busNo);
		
		// calculate zii of bus "1"
		AclfBus bus1 = net.getBus("1");
		busNo = bus1.getSortNumber();
		eqn.setB2Unity(busNo);
		
		eqn.solveEqn(1.0e-20);
		Complex z = eqn.getX(busNo);
		System.out.println("Zii: " + ComplexFunc.toString(z));  

		// calculate zii of bus "2"
		AclfBus bus2 = net.getBus("2");
		busNo = bus2.getSortNumber();
		eqn.setB2Unity(busNo);
		
		// Y-matrix already LUed, so no need to LU again
		eqn.solveEqn();
		z = eqn.getX(busNo);
		System.out.println("Zii: " + ComplexFunc.toString(z));  
	}	
}
