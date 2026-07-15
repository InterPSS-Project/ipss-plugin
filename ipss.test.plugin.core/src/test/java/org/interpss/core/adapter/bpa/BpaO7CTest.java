package org.interpss.core.adapter.bpa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.core.dstab.DStabTestSetupBase;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.bpa.BPADirectParser;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class BpaO7CTest extends DStabTestSetupBase {
	//@Test
	public void sys2011_lfTestCase() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/07c-dc2load.dat");
		System.out.print("branch num="+net.getBranchList().size());
		System.out.print("bus num="+net.getBusList().size());
		assertTrue(net.getBranchList().size()==707);
		assertTrue(net.getBusList().size()==536);
		
		LoadflowAlgorithm  algo=LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		net.accept(algo);
		System.out.println(AclfOutFunc.loadFlowSummary(net));
	}

	/*Test data: 
	 * 07c_0615.dat : explicitly add switch shuntVar to compensate the un-planned shuntVar of BPA for BE type Bus
	 * [test data updated by Tony 06/15]
	 * 07c_0615_notBE.dat: change BE type for non-Gen Buses to B type.
	 */
	//@Test
	public void sys2010_lfTestCase() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/07c_0615_notBE.dat");
		System.out.print("branch num="+net.getBranchList().size());
		System.out.print("bus num="+net.getBusList().size());
		//assertTrue(net.getBranchList().size()==215);
		assertTrue(net.getBusList().size()==141);
		
		LoadflowAlgorithm  algo=LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		assertTrue(net.accept(algo));

		//get the genResult
		
		for(AclfBus bus:net.getBusList()){
			if(bus.isGen()){
				System.out.println(bus.getName()+", "+bus.getId()+" ,p= "+bus.calNetGenResults().getReal()+",q= "+bus.calNetGenResults().getImaginary());
			}
		}

		assertTrue(Math.abs(net.getBus("Bus1").getVoltageMag()-1.02484)<0.0001);
		AclfBranch bra= (AclfBranch) net.getBranchList().get(0);
		assertTrue(Math.abs(bra.powerFrom2To().getReal()-16.86)<0.001);
	}

	// NOTE: sys2010_noFaultTestCase and sys2010_XmlLftestCase removed - 
	// they depend on ODM DStab pipeline (BPAAdapter + DStabModelParser + ODMDStabParserMapper)
	// and ODM XML parsing (ODMObjectFactory + AclfModelParser + ODMAclfParserMapper)
	// which are being removed as part of the ODM dependency migration.
}
