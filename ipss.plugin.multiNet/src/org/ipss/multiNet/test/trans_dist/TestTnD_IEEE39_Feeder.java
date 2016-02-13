package org.ipss.multiNet.test.trans_dist;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSolverImpl;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDynEventProcessor;
import org.ipss.multiNet.algo.SubNetworkProcessor;
import org.ipss.multiNet.algo.powerflow.TDMultiNetPowerflowAlgorithm;
import org.ipss.threePhase.basic.Branch3Phase;
import org.ipss.threePhase.basic.Bus3Phase;
import org.ipss.threePhase.basic.IEEEFeederLineCode;
import org.ipss.threePhase.basic.Load3Phase;
import org.ipss.threePhase.basic.impl.Load3PhaseImpl;
import org.ipss.threePhase.dynamic.DStabNetwork3Phase;
import org.ipss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.ipss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.ipss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformer;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestTnD_IEEE39_Feeder {
	
	@Test
	public void test_3phase3Seq_IEEE39Bus_Feeder() throws InterpssException{
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
	    
	    
	    
	    
	    double PVPenetrationLevel = .00;
	    double PVIncrement = PVPenetrationLevel/(1-PVPenetrationLevel) ;
	    double ACMotorPercent = 40;
	    double IndMotorPercent = 5;
	    double ACPhaseUnbalance = 5.0;
	   
	    
	
	    
	   
	    double baseVolt = 12470;
		int feederBusNum = 9;
		
		double loadPF = 0.97;
		double loadUnbalanceFactor = 0.1;
		
		double [] loadDistribution = new double[]{0.25,0.20,0.15,0.15,0.1,0.1,0.05};
		double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.0,1.5,2,2}; // unit in mile
		//double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; // unit in mile
		
		
		double sysMVABASE = dsNet.getBaseMva();
		
		/**
		 * --------------------- Feeders below Bus 15---------------------------- 
		 */
		double netTotalLoad15 = dsNet.getBus("Bus15").getContributeLoadList().get(0).getLoadCP().getReal();
	    dsNet.getBus("Bus15").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus15").setLoadCode(AclfLoadCode.NON_LOAD);
	    
		 
		 double totalLoad15 = netTotalLoad15*(1+PVIncrement)*sysMVABASE;
		 double XfrMVA = totalLoad15/0.8;
		createFeeder(dsNet, (Bus3Phase) dsNet.getBus("Bus15"), 150, baseVolt,feederBusNum,totalLoad15,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
	
		/**
		 * --------------------- Feeders below Bus 16---------------------------- 
		 */
		 double netTotalLoadBus16 = dsNet.getBus("Bus16").getContributeLoadList().get(0).getLoadCP().getReal();
		 dsNet.getBus("Bus16").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus16").setLoadCode(AclfLoadCode.NON_LOAD);
		
		
		 double totalLoadBus16 = netTotalLoadBus16*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus16/0.8;
		createFeeder(dsNet, (Bus3Phase) dsNet.getBus("Bus16"), 160, baseVolt,feederBusNum,totalLoadBus16,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		
		/**
		 * --------------------- Feeders below Bus 18---------------------------- 
		 */
		double netTotalLoadBus18 =dsNet.getBus("Bus18").getContributeLoadList().get(0).getLoadCP().getReal();
		 dsNet.getBus("Bus18").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus18").setLoadCode(AclfLoadCode.NON_LOAD);
		 
		 double totalLoadBus18 = netTotalLoadBus18*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus18/0.8;
		createFeeder(dsNet, (Bus3Phase) dsNet.getBus("Bus18"), 180, baseVolt,feederBusNum,totalLoadBus18,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		
		
	    
		/**
		 * --------------------- Feeders below Bus 26---------------------------- 
		 */
		double netTotalLoadBus26 = dsNet.getBus("Bus26").getContributeLoadList().get(0).getLoadCP().getReal();
	    dsNet.getBus("Bus26").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus26").setLoadCode(AclfLoadCode.NON_LOAD);
	    
		 
		 double totalLoadBus26 = netTotalLoadBus26*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus26/0.8;
		createFeeder(dsNet, (Bus3Phase) dsNet.getBus("Bus26"), 260, baseVolt,feederBusNum,totalLoadBus26,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
	
		/**
		 * --------------------- Feeders below Bus 27---------------------------- 
		 */
		 double netTotalLoadBus27 = dsNet.getBus("Bus27").getContributeLoadList().get(0).getLoadCP().getReal();
		 dsNet.getBus("Bus27").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus27").setLoadCode(AclfLoadCode.NON_LOAD);
		
		
		 double totalLoadBus27 = netTotalLoadBus27*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus27/0.8;
		createFeeder(dsNet, (Bus3Phase) dsNet.getBus("Bus27"), 270, baseVolt,feederBusNum,totalLoadBus27,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		
		/**
		 * --------------------- Feeders below Bus 28---------------------------- 
		 */
		double netTotalLoadBus28 =dsNet.getBus("Bus28").getContributeLoadList().get(0).getLoadCP().getReal();
		 dsNet.getBus("Bus28").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus28").setLoadCode(AclfLoadCode.NON_LOAD);
		 
		 double totalLoadBus28 = netTotalLoadBus28*(1+PVIncrement)*sysMVABASE;
		 XfrMVA = totalLoadBus28/0.8;
		createFeeder(dsNet, (Bus3Phase) dsNet.getBus("Bus28"), 280, baseVolt,feederBusNum,totalLoadBus28,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		
	    
	    
		
		
		//TODO select 6 buses in the load center to replace them by detailed feeders

	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
			 proc.addSubNetInterfaceBranch("Bus15->Bus150(0)",false);
			 proc.addSubNetInterfaceBranch("Bus16->Bus160(0)",false);
			 proc.addSubNetInterfaceBranch("Bus18->Bus180(0)",false);
			 proc.addSubNetInterfaceBranch("Bus26->Bus260(0)",false);
			 proc.addSubNetInterfaceBranch("Bus27->Bus270(0)",false);
			 proc.addSubNetInterfaceBranch("Bus28->Bus280(0)",false);
			
			 
			 proc.splitFullSystemIntoSubsystems(true);
			 
			 // currently, if a fault at transmission system is to be considered, then it should be set to 3phase
			 proc.set3PhaseSubNetByBusId("Bus1");
			//TODO this has to be manually identified
			 proc.set3PhaseSubNetByBusId("Bus151");
			 proc.set3PhaseSubNetByBusId("Bus161");
			 proc.set3PhaseSubNetByBusId("Bus181");   
			 proc.set3PhaseSubNetByBusId("Bus261"); 
			 proc.set3PhaseSubNetByBusId("Bus271"); 
			 proc.set3PhaseSubNetByBusId("Bus281"); 
	    
		
			 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm(dsNet,proc);
			 
			 System.out.println(tdAlgo.getTransmissionNetwork().net2String());
			    
			 assertTrue(tdAlgo.powerflow()); 
			 
			
			 
			 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
			 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
		
		
//		  
//		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
//		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
//		    
//			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
//			dstabAlgo.setSimuStepSec(0.005d);
//			dstabAlgo.setTotalSimuTimeSec(1d);
//			
//			StateMonitor sm = new StateMonitor();
//			sm.addBusStdMonitor(new String[]{"Bus17","Bus18","Bus15","Bus16","Bus28"});
//			sm.addGeneratorStdMonitor(new String[]{"Bus30-mach1","Bus31-mach1","Bus34-mach1","Bus39-mach1"});
//			// set the output handler
//			dstabAlgo.setSimuOutputHandler(sm);
//			dstabAlgo.setOutPutPerSteps(1);
//			
//			IpssLogger.getLogger().setLevel(Level.INFO);
//			
//		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0,0),null,0.5d,0.05),"3phaseFault@Bus5");
//			
//	        // TODO a special 3-phase 3seq dstab algorithm object, with the following two setting as default
//			//dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
//			//dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
//		  
//			 if(dstabAlgo.initialization()){
//				 dstabAlgo.performSimulation();
//			 }
//		   
//			 System.out.println(sm.toCSVString(sm.getMachPeTable()));
//				
//		     System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		 }

	/**
	 * The first bus is feeder sending end, no load is connected; all the loads are connected at bus [2,...BusNum];
	 * 
	 * The base case of the feeder is assumed to serve 8 MW load, feeder impedances are re-scaled based on the totalMW
	 * @param totalMW
	 * @return
	 * @throws InterpssException
	 */
public DStabNetwork3Phase createFeeder(DStabNetwork3Phase net,Bus3Phase sourceBus, int startBusNum, double baseVolt, int BusNum, double totalMW, double XfrMVA, double loadPF, double[] loadPercentAry, double loadUnbalanceFactor, double[] sectionLength) throws InterpssException{
		
	    double scaleFactor = totalMW;
	    double zscaleFactor =  totalMW/8.0; 
	    double q2pfactor = Math.tan(Math.acos(loadPF));
	
		
		int loadIdx = 0;
		for(int i =startBusNum;i<startBusNum+BusNum;i++){
			Bus3Phase bus = ThreePhaseObjectFactory.create3PDStabBus("Bus"+i, net);
			bus.setAttributes("feeder bus "+i, "");
			bus.setBaseVoltage(baseVolt);
			
			
			// set the bus to a non-generator bus
			bus.setGenCode(AclfGenCode.GEN_PQ);
			// set the bus to a constant power load bus
			bus.setLoadCode(AclfLoadCode.CONST_P);
			
			if(i ==startBusNum){
				bus.setBaseVoltage(69000.0);
				bus.setGenCode(AclfGenCode.NON_GEN);
				// set the bus to a constant power load bus
				bus.setLoadCode(AclfLoadCode.NON_LOAD);
			}
			
			if(i>=startBusNum+2){
				Load3Phase load1 = new Load3PhaseImpl();
				Complex3x1 load3Phase = new Complex3x1(new Complex(0.01,0.01*q2pfactor),new Complex(0.01,0.01*q2pfactor).multiply(1-loadUnbalanceFactor),new Complex(0.01,0.01*q2pfactor).multiply(1+loadUnbalanceFactor));
				load1.set3PhaseLoad(load3Phase.multiply(scaleFactor*loadPercentAry[loadIdx++]));
				bus.getThreePhaseLoadList().add(load1);
			}
			// shunt compensation
			if(i ==startBusNum+3 || i == startBusNum+5 || i==startBusNum+7){
				Load3Phase Shuntload = new Load3PhaseImpl();
				Complex3x1 shuntY = new Complex3x1(new Complex(0,-0.0005),new Complex(0.0,-0.0005),new Complex(0.0,-0.0005));
				Shuntload.set3PhaseLoad(shuntY.multiply(scaleFactor));
				bus.getThreePhaseLoadList().add(Shuntload);
			}
			
		}
		

		// add step down transformer between source bus and bus1
		
		Branch3Phase xfr1 = ThreePhaseObjectFactory.create3PBranch(sourceBus.getId(), net.getBus("Bus"+startBusNum).getId(), "0", net);
		xfr1.setBranchCode(AclfBranchCode.XFORMER);
		xfr1.setToTurnRatio(1.02);
		xfr1.setZ( new Complex( 0.0, 0.08).multiply(100.0/XfrMVA));
		
		
		AcscXformer xfr01 = acscXfrAptr.apply(xfr1);
		xfr01.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		xfr01.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
	

		
		
		
	Branch3Phase xfr1_2 = ThreePhaseObjectFactory.create3PBranch(net.getBus("Bus"+startBusNum).getId(), net.getBus("Bus"+(startBusNum+1)).getId(), "0", net);
		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
		xfr1_2.setToTurnRatio(1.02);
		xfr1_2.setZ( new Complex( 0.0, 0.06 ).multiply(100.0/XfrMVA));
	
	
	AcscXformer xfr0 = acscXfrAptr.apply(xfr1_2);
		xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);

		
		
		int k =0;
		for(int i =startBusNum+1;i<startBusNum+BusNum-1;i++){
			
			Branch3Phase Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus"+i, "Bus"+(i+1), "0", net);
			Line2_3.setBranchCode(AclfBranchCode.LINE);
			
			
			Complex3x3 zabcActual = IEEEFeederLineCode.zMtx601;
			if(k>=3 && k<5)
				zabcActual =  IEEEFeederLineCode.zMtx602;
			else if (k>=5)
				zabcActual =  IEEEFeederLineCode.zMtx606;
			
			zabcActual = zabcActual.multiply(sectionLength[k]/zscaleFactor);
			
			Double zbase = net.getBus("Bus"+i).getBaseVoltage()*net.getBus("Bus"+i).getBaseVoltage()/net.getBaseMva()/1.0E6;
			Line2_3.setZabc(zabcActual.multiply(1/zbase));
			
			
			k++;
		}
		
		
		
		return net; 
		
		
		
	}
}
