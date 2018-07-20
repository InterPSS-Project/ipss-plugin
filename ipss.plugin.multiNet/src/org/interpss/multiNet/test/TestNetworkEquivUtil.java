package org.interpss.multiNet.test;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.multiNet.equivalent.NetworkEquivUtil;
import org.interpss.multiNet.equivalent.NetworkEquivalent;
import org.interpss.numeric.matrix.MatrixUtil;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestNetworkEquivUtil {
	
	
	@Test
	public void test_NetEquiv_IEEE9Bus() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		    proc.addSubNetInterfaceBranch("Bus4->Bus5(0)");
		    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
		
		    
		    proc.splitFullSystemIntoSubsystems(false);
		    
		 BaseDStabNetwork<?, ?>  subNet = proc.getSubNetworkList().get(1); // index 1 is the subnetwork with only bus 5;
		 
		 System.out.println("subNet size : "+subNet.getBusList().size()); 
		// System.out.println(subNet.net2String());
		 
		 
		 // test sequence network equivalent
		 Complex[][] equivZ1 = NetworkEquivUtil.calcInterfaceSeqZMatrix(subNet, SequenceCode.POSITIVE, 
				 proc.getSubNet2BoundaryBusListTable().get(subNet.getId()));
		 
		 System.out.print(MatrixUtil.complex2DAry2String(equivZ1));
		 
		 //TODO
		 // test 3-seq network equivalent
		 NetworkEquivalent equiv3Seq = NetworkEquivUtil.cal3SeqNetworkTheveninEquiv(subNet, proc.getSubNet2BoundaryBusListTable().get(subNet.getId()));
		/*
		 * aa = (0.011455305443682258, 0.05359768655900525),ab = (0.0, 0.0),ac = (0.0, 0.0)
			ba = (0.0, 0.0),bb = (0.011455305443682258, 0.05359768655900525),bc = (0.0, 0.0)
			ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (0.021653935523081317, 0.1440042923871657)
		 */
		 System.out.print(MatrixUtil.complex3x32DAry2String( equiv3Seq.getMatrix3x3()));
		 
		 
		 NetworkEquivalent equiv3Ph = NetworkEquivUtil.cal3PhaseNetworkTheveninEquiv((DStabNetwork3Phase) subNet, proc.getSubNet2BoundaryBusListTable().get(subNet.getId()));
		 System.out.print(MatrixUtil.complex3x32DAry2String( equiv3Ph.getMatrix3x3()));
		 
		//TODO test 3-phase network equivalent by comparing it with 3-seq network equiv
		 
		 /**
		  *  //3-phase//
		  *  
		  * aa = (0.014854848803637855, 0.08373322183556542),ab = (0.0033995433599555984, 0.03013553527656018),ac = (0.003399543359955598, 0.03013553527656018)
			ba = (0.003399543359955596, 0.03013553527656019),bb = (0.014854848803637853, 0.08373322183556543),bc = (0.0033995433599556023, 0.030135535276560176)
			ca = (0.003399543359955595, 0.030135535276560186),cb = (0.003399543359955602, 0.03013553527656018),cc = (0.014854848803637853, 0.08373322183556543)
			
			
			//3-seq transformed from 3-phase: //
			aa = (0.011455305443682248, 0.05359768655900525),ab = (6.938893903907228E-18, -8.673617379884035E-18),ac = (0.0, -1.734723475976807E-18)
			ba = (6.938893903907228E-18, -6.938893903907228E-18),bb = (0.011455305443682248, 0.05359768655900524),bc = (0.0, -1.734723475976807E-18)
			ca = (0.0, 6.938893903907228E-18),cb = (0.0, 6.938893903907228E-18),cc = (0.02165393552354905, 0.14400429238868578)
		  */
		 System.out.print("3-seq transformed from 3-phase: \n"+equiv3Ph.getMatrix3x3()[0][0].To120().toString());
	}
	
	
	
	@Test
	public void test_SubNetEquiv_IEEE9Bus() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
	}

}
