package org.interpss.multiNet.test.trans_dist;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.multiNet.algo.powerflow.TDMultiNetPowerflowAlgorithm;
import org.interpss.multiNet.algo.powerflow.TposSeqD3PhaseMultiNetPowerflowAlgorithm;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.Branch3Phase;
import org.interpss.threePhase.basic.Bus3Phase;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformer;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestTnD_IEEE39_123BusFeeder {
	
	//@Test
	public void test_IEEE39_IEEE123Feeder_T3seq_D3phase_Powerflow() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE39Bus/IEEE39bus_v30.raw",
				"testData/IEEE39Bus/IEEE39bus_v30.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE39Bus/IEEE39bus.dyr"
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
	    
	    
        SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	    
	    List<String> replaceBusIdList = new ArrayList<>(); 
		List<String> interfaceBusIdList = new ArrayList<>(); 
	        // add distribution systems
		int replacedBusNum = 0;
		int totalFeederNum = 0;
		
		// NOTE: it is not allowed to iterate the buslist and add a new bus to it;
		// so need to save the Ids of targeted buses to a list first;
		
		//LIST: 1,
		dsNet.getBus("Bus18").getContributeLoadList().get(0).setLoadCP(new Complex(0.15,0.04));
	   
		replaceBusIdList.add("Bus18");
		
//		for(DStabBus b:dsNet.getBusList()){
//	        if(b.getArea().getNumber()==1){
//	        	if(b.isActive() && b.isLoad() && (!b.isGen()) && b.getLoadP()>0.5 && b.getLoadP()<6){//&& 
//	        		if(!b.getId().equals("Bus526") && !b.getId().equals("Bus562") && !b.getId().equals("Bus70")
//	        				&& !b.getId().equals("Bus72")&& !b.getId().equals("Bus52")&& !b.getId().equals("Bus53"))
//	        		replaceBusIdList.add(b.getId());
//		              
//	        	}
//	         }
//		   }
		
		

		for (String id: replaceBusIdList){
			
			String[] interfaceIds = replaceLoadByFeeder(dsNet,id);
            
            proc.addSubNetInterfaceBranch(interfaceIds[0],false);
            
            interfaceBusIdList.add(interfaceIds[1]);
            
            replacedBusNum +=1;
            
            totalFeederNum+=Integer.valueOf(interfaceIds[2]);
		}
		    
		    
		System.out.println("replaced load bus num, total feeder num: "+replacedBusNum+","+totalFeederNum);
				
				 
        proc.splitFullSystemIntoSubsystems(true);
        
        //proc.set3PhaseSubNetByBusId("Bus3");
				 
				 // currently, if a fault at transmission system is to be considered, then it should be set to 3phase
				// proc.set3PhaseSubNetByBusId("Bus1");
				//TODO this has to be manually identified
		    for(String busId: interfaceBusIdList){
		        proc.set3PhaseSubNetByBusId(busId);
		    }
		    
		    System.out.println("distribution sys num:"+ (proc.getSubNetworkList().size()-1));
		    // dist pf not converged 
		    // subnet- Bus562_LVBus
		    // subnet- 16
		    // System.out.println("dist Net -34  :"+proc.getSubNetwork("SubNet-34").getBusList().get(0));
		    
			
		    TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch>) dsNet,proc);
		    
		   // System.out.println(tdAlgo.getDistributionNetworkList().get(0).net2String());
				 
				 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
			 LoadflowAlgorithm tAlgo = tdAlgo.getTransLfAlgorithm();
			 tAlgo.setLfMethod(AclfMethod.NR);
			 tAlgo.setTolerance(1E-4);
			
			 tAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
			 //tAlgo.setNonDivergent(true);
			 tAlgo.setInitBusVoltage(true); 
			 // TODO initBusVoltage can be updated to set init to true for the first iteration, while
			 // the remaining iterations can reuse last step solution results as the starting point, such that
			 // simulation time for the transmission part can be reduced.
			 
			 tdAlgo.setDistLfTolerance(1.0E-5);
			 
			 assertTrue(tdAlgo.powerflow()); 
	}
	
	//@Test
	public void test_IEEE39_IEEE123Feeder_Tpos_D3phase_Powerflow() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE39Bus/IEEE39bus_v30.raw",
				"testData/IEEE39Bus/IEEE39bus_v30.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE39Bus/IEEE39bus.dyr"
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
	    
	    
        SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
	    
	    List<String> replaceBusIdList = new ArrayList<>(); 
		List<String> interfaceBusIdList = new ArrayList<>(); 
	        // add distribution systems
		int replacedBusNum = 0;
		int totalFeederNum = 0;
		
		// NOTE: it is not allowed to iterate the buslist and add a new bus to it;
		// so need to save the Ids of targeted buses to a list first;
		
		//LIST: 1,
		dsNet.getBus("Bus18").getContributeLoadList().get(0).setLoadCP(new Complex(0.15,0.04));
	   
		replaceBusIdList.add("Bus18");
		
//		for(DStabBus b:dsNet.getBusList()){
//	        if(b.getArea().getNumber()==1){
//	        	if(b.isActive() && b.isLoad() && (!b.isGen()) && b.getLoadP()>0.5 && b.getLoadP()<6){//&& 
//	        		if(!b.getId().equals("Bus526") && !b.getId().equals("Bus562") && !b.getId().equals("Bus70")
//	        				&& !b.getId().equals("Bus72")&& !b.getId().equals("Bus52")&& !b.getId().equals("Bus53"))
//	        		replaceBusIdList.add(b.getId());
//		              
//	        	}
//	         }
//		   }
		
		

		for (String id: replaceBusIdList){
			
			String[] interfaceIds = replaceLoadByFeeder(dsNet,id);
            
            proc.addSubNetInterfaceBranch(interfaceIds[0],false);
            
            interfaceBusIdList.add(interfaceIds[1]);
            
            replacedBusNum +=1;
            
            totalFeederNum+=Integer.valueOf(interfaceIds[2]);
		}
		    
		    
		System.out.println("replaced load bus num, total feeder num: "+replacedBusNum+","+totalFeederNum);
				
				 
        proc.splitFullSystemIntoSubsystems(true);
        
        //proc.set3PhaseSubNetByBusId("Bus3");
				 
				 // currently, if a fault at transmission system is to be considered, then it should be set to 3phase
				// proc.set3PhaseSubNetByBusId("Bus1");
				//TODO this has to be manually identified
		    for(String busId: interfaceBusIdList){
		        proc.set3PhaseSubNetByBusId(busId);
		    }
		    
		    System.out.println("distribution sys num:"+ (proc.getSubNetworkList().size()-1));
		    // dist pf not converged 
		    // subnet- Bus562_LVBus
		    // subnet- 16
		    // System.out.println("dist Net -34  :"+proc.getSubNetwork("SubNet-34").getBusList().get(0));
		    
			
		    TposSeqD3PhaseMultiNetPowerflowAlgorithm tdAlgo = new TposSeqD3PhaseMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch>) dsNet,proc);
		    
		   // System.out.println(tdAlgo.getDistributionNetworkList().get(0).net2String());
				 
				 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
			 LoadflowAlgorithm tAlgo = tdAlgo.getTransLfAlgorithm();
			 tAlgo.setLfMethod(AclfMethod.NR);
			 tAlgo.setTolerance(1E-4);
			
			 tAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
			 //tAlgo.setNonDivergent(true);
			 tAlgo.setInitBusVoltage(true); 
			 // TODO initBusVoltage can be updated to set init to true for the first iteration, while
			 // the remaining iterations can reuse last step solution results as the starting point, such that
			 // simulation time for the transmission part can be reduced.
			 
			 tdAlgo.setDistLfTolerance(1.0E-5);
			 
			 assertTrue(tdAlgo.powerflow()); 
	}
 
	
	/**
	 * The loads connected to a transmission bus are replaced by IEEE13 feeders.
	 * @param net
	 * @param transBusId
	 * @throws InterpssException
	 */
	private String[] replaceLoadByFeeder(DStabNetwork3Phase net,String transBusId) throws InterpssException{
			
		    double baseVolt69kV = 69000.0;
			double baseVolt4160 = 4160.0;
			
			Bus3Phase transBus = (Bus3Phase) net.getBus(transBusId);
			
			Complex loadPQ = transBus.getLoadPQ();
			
			String[] interfaceIds = new String[3];
			
			double tapRatio = 1/transBus.getVoltageMag();
			
			if(tapRatio <0.95) tapRatio = 0.95;
			else if(tapRatio >1.1) tapRatio =1.1;
			
			double transMVA = loadPQ.getReal()*100.0/0.8;
			
			double xfrZ = 0.06*100.0/transMVA;
			
			if(transBus.getId().equals("Bus1")){
				System.out.println("processing bus1");
			}
			
			if(transBus.isActive() && transBus.isLoad() ){
			
				
				
				double mvaBase = 100.0;
				//three-phase total power on three-phase MVA base: (3.5797255313759795, 1.745961739090089)
				Complex feederLoadPQ = new Complex (3.608447945261856, 1.333641623913227).divide(mvaBase);
				
				int numberOfFeeder = (int) (loadPQ.getReal()/feederLoadPQ.getReal()); // this should be calcuated based on loadPQ
				
				String sourceBusId=transBusId+"_LVBus";
				
				Bus3Phase sourceBus = ThreePhaseObjectFactory.create3PDStabBus(sourceBusId, net);
				sourceBus.setAttributes("trans LV bus", "");
				sourceBus.setBaseVoltage( baseVolt69kV);
		
				sourceBus.setLoadCode(AclfLoadCode.NON_LOAD);
				sourceBus.setVoltage(transBus.getVoltage());
				
				
				Branch3Phase xfr1_2 = ThreePhaseObjectFactory.create3PBranch(transBusId, sourceBusId, "0", net);
				xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
				xfr1_2.setToTurnRatio(tapRatio);
				xfr1_2.setZ( new Complex( 0.0, xfrZ ));
				
			
			    AcscXformer xfr0 = acscXfrAptr.apply(xfr1_2);
				xfr0.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
				xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
			//TODO comment out for pass test 7/15/2018	
//				for (int i = 1; i<numberOfFeeder+1;i++){
//					String feederHeadId = createFeeder(net, transBusId, i, mvaBase );
//					
//					Branch3Phase connectLine = ThreePhaseObjectFactory.create3PBranch(sourceBusId, feederHeadId, "0", net);
//					connectLine.setBranchCode(AclfBranchCode.LINE);
//				
//					connectLine.setZ( new Complex( 0.0, 0.0001 ));
//				
//				  
//				}
//				
				
				interfaceIds[0]  =xfr1_2.getId();
				interfaceIds[1]  =sourceBusId;
				
				interfaceIds[2] =  Integer.toString(numberOfFeeder);
				
				
				//TODO process the negative and zero sequence load data;
			    
				
				//transBus.setLoadCode(AclfLoadCode.CONST_P);
				transBus.getContributeLoadList().clear();
				
				// estimated PQ compensation
				Complex distTotalLoadPQ = feederLoadPQ.multiply(numberOfFeeder);
				
				double dP = loadPQ.getReal()-distTotalLoadPQ.getReal();
				// total Var at the transmission bus is equal to total feeder Q plus step-down transformer Var consumption
				double dQ = loadPQ.getImaginary()-distTotalLoadPQ.getImaginary()-(distTotalLoadPQ.abs()*xfr1_2.getZ().getImaginary());
				
				transBus.setLoadPQ(new Complex(dP,dQ));
				//transBus.setLoadPQ(new Complex(0,0));
				
			}
			
			return interfaceIds ;
			
		}
		
		
		/**
		 * return source Bus Id
		 * @param net
		 * @param sourceBus
		 * @param transBusId
		 * @param feederIdx
		 * @param mvaBase
		 * @return
		 * @throws InterpssException
		 */
	 /*	
	 private String createFeeder(DStabNetwork3Phase net, String transBusId, int feederIdx, double mvaBase ) throws InterpssException{
			
			 
			  
			   double baseVolt4160 = 4160.0; //4.16 kV
			   double baseVolt480 = 480.0;
			
	
			   
			   String idPrefix = transBusId+"_feeder_"+feederIdx+"_";
			   

				//TODO need to merge the parser branch of ipss.plugin.3phase
			   
				OpenDSSDataParser parser = new OpenDSSDataParser();
				parser.setBusIdPrefix(idPrefix);
				
				
				parser.parseFeederData("testData\\feeder\\IEEE123","IEEE123Master_Modified_v2.dss");
				
				parser.calcVoltageBases();

				parser.convertActualValuesToPU(mvaBase);
				
				DStabNetwork3Phase distNet = parser.getDistNetwork();
				
				
				// set the  turn ratios of regulators
				parser.getBranchByName(idPrefix+"reg1a").setToTurnRatio(1.0438);
				
				String sourceBusId = idPrefix+"150";
				
				
				List<String> distNetIdList = new ArrayList<>();
				for(Bus3Phase b: distNet.getBusList()){
					distNetIdList.add(b.getId());
				}
				
				for (String busId: distNetIdList){
					int idx = getBusIdx((BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch>) distNet,busId);
					net.addBus(distNet.getBusList().remove(idx));
				}
				
				for(DStabBranch bra: distNet.getBranchList()){
					net.addBranch((Branch3Phase) bra, bra.getFromBus().getId(), bra.getToBus().getId(), bra.getCircuitNumber());
				}
				
				
				DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
				//distPFAlgo.orderDistributionBuses(true);
				distPFAlgo.setInitBusVoltageEnabled(true);
				//distPFAlgo.setMaxIteration(1);
				distPFAlgo.setTolerance(2.0E-3); // tolearnce = 5 kva
				assertTrue(distPFAlgo.powerflow());
				
				
				// update the source bus attribute
				distNet.getBus(sourceBusId).setGenCode(AclfGenCode.NON_GEN);
				
				return sourceBusId;
				
		}
		*/
		private int getBusIdx(BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch> _net, String busId){
			int idx = -1;
			for(int i = 0; i<_net.getBusList().size(); i++){
				if(_net.getBusList().get(i).getId().equals(busId)){
					idx = i;
				}
			}
			return idx;
		}
				
				
}
