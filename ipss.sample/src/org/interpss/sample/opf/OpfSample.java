package org.interpss.sample.opf;

import org.interpss.numeric.exp.IpssNumericException;

import com.interpss.core.net.Bus;
import com.interpss.opf.common.visitor.IOpfGenBusVisitor;
import com.interpss.opf.dclf.DclfOpfGenBus;
import com.interpss.opf.dclf.DclfOpfNetwork;

public class OpfSample {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IpssNumericException {
		DclfOpfNetwork net = Opf3BusTestNet.create3BusNetwork();
		
		net.forEachOpfGenBus(new IOpfGenBusVisitor() {
			public void visit(DclfOpfGenBus opfGen) {
				System.out.println("id, coeffA : " + opfGen.getId() + ", " + opfGen.getCoeffA());
			}
		});
		
		for (Bus bus : net.getBusList()) {
			if (net.isOpfGenBus(bus)) {
				DclfOpfGenBus opfGen = net.toOpfGenBus(bus);
				System.out.println("id, coeffA : " + opfGen.getId() + ", " + opfGen.getCoeffA());
			}
		}
	}
}
