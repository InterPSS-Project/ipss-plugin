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

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformer;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestTnD_IEEE9_8BusFeeder {
	
	@Test
	public void test_IEEE9_8Busfeeder_powerflow() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				"testData/IEEE9Bus/ieee9_dyn.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		
		// The only change to the normal data import is the use of ODM3PhaseDStabParserMapper
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
		
		double loadPF = 0.95;
		double loadUnbalanceFactor = 0.2;
		
		double [] loadDistribution = new double[]{0.25,0.20,0.15,0.15,0.1,0.1,0.05};
		double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.0,1.5,2,2}; // unit in mile
		//double [] feederSectionLenghth = new double[]{0.5,0.5,1.0,1.5,0.5,0.5, 1}; // unit in mile
		
		/**
		 * --------------------- Feeders below Bus 5---------------------------- 
		 */
		
	    dsNet.getBus("Bus5").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);
	    
		 double netTotalLoad = 120;
		 double totalLoad = netTotalLoad*(1+PVIncrement);
		 double XfrMVA = 150;
		createFeeder(dsNet, (Bus3Phase) dsNet.getBus("Bus5"), 10, baseVolt,feederBusNum,totalLoad,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
	
		/**
		 * --------------------- Feeders below Bus 6---------------------------- 
		 */
		
		   dsNet.getBus("Bus6").getContributeLoadList().remove(0);
		    dsNet.getBus("Bus6").setLoadCode(AclfLoadCode.NON_LOAD);
		
		 double netTotalLoadBus6 = 90;
		 double totalLoadBus6 = netTotalLoadBus6*(1+PVIncrement);
		 XfrMVA = 120;
		createFeeder(dsNet, (Bus3Phase) dsNet.getBus("Bus6"), 20, baseVolt,feederBusNum,totalLoadBus6,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		
		/**
		 * --------------------- Feeders below Bus 8---------------------------- 
		 */
		 dsNet.getBus("Bus8").getContributeLoadList().remove(0);
		 dsNet.getBus("Bus8").setLoadCode(AclfLoadCode.NON_LOAD);
		 double netTotalLoadBus8 = 100;
		 double totalLoadBus8 = netTotalLoadBus8*(1+PVIncrement);
		 XfrMVA = 150;
		createFeeder(dsNet, (Bus3Phase) dsNet.getBus("Bus8"), 30, baseVolt,feederBusNum,totalLoadBus8,XfrMVA, loadPF,loadDistribution,loadUnbalanceFactor,feederSectionLenghth);
		
		
		
		  
	    SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 proc.addSubNetInterfaceBranch("Bus5->Bus10(0)",false);
		 proc.addSubNetInterfaceBranch("Bus6->Bus20(0)",false);
		proc.addSubNetInterfaceBranch("Bus8->Bus30(0)",false);
		 
		 proc.splitFullSystemIntoSubsystems(true);
		 
		 // currently, if a fault at transmission system is to be considered, then it should be set to 3phase
		 proc.set3PhaseSubNetByBusId("Bus5");
		//TODO this has to be manually identified
		 proc.set3PhaseSubNetByBusId("Bus11");
		 proc.set3PhaseSubNetByBusId("Bus21");
		 proc.set3PhaseSubNetByBusId("Bus31");
		 
		 
		 System.out.println("external boundary bus: "+proc.getExternalSubNetBoundaryBusIdList());
		 
		 System.out.println("internal boundary bus: "+proc.getInternalSubNetBoundaryBusIdList());
		 
	    
	    
	    //TODO create TDMultiNetPowerflowAlgo
	    
		 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm(dsNet,proc);
		
		 System.out.println(tdAlgo.getTransmissionNetwork().net2String());
	    
		 assertTrue(tdAlgo.powerflow()); 
		 
		 
		 
		 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
	
	
	
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
