package org.interpss.core.adapter.psse.raw.acsc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.acsc.AcscModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AcscOutFunc;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.util.TestUtilFunc;
import org.interpss.odm.mapper.ODMAcscParserMapper;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.sc.ScBusModelType;
import com.interpss.core.algo.sc.SimpleFaultAlgorithm;


public class IEEE9Bus_Acsc_test {
	
	@Test
	public void testIeee9SeqY() throws InterpssException, IpssNumericException{
			IpssCorePlugin.init();
			PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.AcscNet, new String[]{
					"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
					"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq"
			}));
			AcscModelParser acscParser =(AcscModelParser) adapter.getModel();
			//acscParser.stdout();
			
			AcscNetwork net = new ODMAcscParserMapper().map2Model(acscParser).getAcscNet();
			
			//set the order in original sequence for better testing
			for(int i=1;i<=net.getNoBus();i++){
				net.getBus("Bus"+i).setSortNumber(i-1);
			}
			net.setBusNumberArranged(true);
			
			LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		  	algo.setLfMethod(AclfMethodType.PQ);
		  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		  	algo.loadflow();
	  	
	  		assertTrue( net.isLfConverged());
	  		//System.out.println(AclfOutFunc.loadFlowSummary(net));
	  		//System.out.println(net.net2String());
	  		
	  		//check the transformer type and SC grounding info
	  		/*com.interpss.core.acsc.impl.AcscBranchImpl@5ac86ba5 (id: Bus2->Bus7(1), name: Gen2_to_Bus2_cirId_1, desc: , number: 0, status: true) (booleanFlag: false, intFlag: 0, weight: (0.0, 0.0), sortNumber: 0, areaId: 1, zoneId: 1, ownerId: , statusChangeInfo: NoChange) (extensionObject: null)
			     circuitNumber: 1
			     branchCode:    XFormer
			     z:          0.0000 + j0.0625
			    Ratio  :   from side 1.0000   to side  1.0000 pu
			    Z multiplying factor: 1.0000    Z Adj Table number: 0
			     fromShuntY: 0.0000 + j0.0000 pu
			     toShuntY:   0.0000 + j0.0000 pu
			     mvaRating1,mvaRating2,mvaRating3:   0.0000, 0.0000, 0.0000
			   LF results 
			      p+jq(f->t) : 1.63002 + j0.06594 pu   163002.46414 + j6593.60204 kva
			      p+jq(t->f) : -1.63002 + j0.09238 pu   -163002.46414 + j9238.21171 kva
			      current    : -1.57971 + j-0.19395 pu    399.51847 Amps
			
			  SC Info:
			     z0:      0.0000 + j0.0625
			     From Connection:      Delta
			     From Grounding:      Ungrounded
			     To Connection:      Wye
			     To Grounding:      SolidGrounded
			     */
	  		AcscBranch xfr_2_7= net.getBranch("Bus2->Bus7(1)");
	  		assertEquals(xfr_2_7.getZ0().getReal(), 0, 1.0E-4);
	  		assertEquals(xfr_2_7.getZ0().getImaginary(), 0.0625, 1.0E-4);
	  		assertTrue(xfr_2_7.getFromGrounding().getXfrConnectCode()==XFormerConnectCode.DELTA);
	  		assertTrue(xfr_2_7.getFromGrounding().getGroundCode()==BusGroundCode.UNGROUNDED);
	  		
	  		assertTrue(xfr_2_7.getToGrounding().getXfrConnectCode()==XFormerConnectCode.WYE);
	  		assertTrue(xfr_2_7.getToGrounding().getGroundCode()==BusGroundCode.SOLID_GROUNDED);
	  		
	  		net.initialization(ScBusModelType.LOADFLOW_VOLT);
	  		
	  		/*
	  		 * ***********************************
	  		 *       Positive sequence
	  		 * ***********************************      
	  		 * 
	  		 */
	  		
	        ISparseEqnComplex posYMatrix = net.formScYMatrix(SequenceCode.POSITIVE, ScBusModelType.LOADFLOW_VOLT, false);
	        
	        //Gen Bus: Bus 1
	        //Yii: 0.0 + (-42.63668430335097i)
	        assertTrue(posYMatrix.getA(0, 0).getReal()==0);
	        assertTrue(Math.abs(posYMatrix.getA(0, 0).getImaginary()+42.6366)<1.0E-4);
	        //Yij (bus1->bus4): -0.0 + (17.636684303350968i)
	        assertTrue(posYMatrix.getA(0, 3).getReal()==0);
	        assertTrue(Math.abs(posYMatrix.getA(0, 3).getImaginary()-17.6366)<1.0E-4);
	        
	        //Load Bus: Bus 5
	        //Yii: 3.81 - j17.84
	        assertTrue(Math.abs(posYMatrix.getA(4, 4).getReal()-3.81)<1.0E-2);
	        assertTrue(Math.abs(posYMatrix.getA(4, 4).getImaginary()+17.84)<1.0E-2);
	        
	        //Y54:-1.37 + j11.60
	        assertTrue(Math.abs(posYMatrix.getA(4, 3).getReal()+1.37)<1.0E-2);
	        assertTrue(Math.abs(posYMatrix.getA(4, 3).getImaginary()-11.60)<1.0E-2);
	        
	        //Non-Gen, Non-Load: Bus7
	        //Yii 2.80 - j35.45
	        assertTrue(Math.abs(posYMatrix.getA(6, 6).getReal()-2.80)<1.0E-2);
	        assertTrue(Math.abs(posYMatrix.getA(6, 6).getImaginary()+35.45)<1.0E-2);
	       
	        
	        /*
	  		 * ***********************************
	  		 *       Negative sequence
	  		 * ***********************************      
	  		 * 
	  		 */
	        
       ISparseEqnComplex negYMatrix = net.formScYMatrix(SequenceCode.NEGATIVE, ScBusModelType.LOADFLOW_VOLT, false);
	        
	        //Gen Bus: Bus 1
	        //Yii: 0.0 + (-42.63668430335097i)
	        assertTrue(negYMatrix.getA(0, 0).getReal()==0);
	        assertTrue(Math.abs(negYMatrix.getA(0, 0).getImaginary()+42.6366)<1.0E-4);
	        //Yij (bus1->bus4): -0.0 + (17.636684303350968i)
	        assertTrue(negYMatrix.getA(0, 3).getReal()==0);
	        assertTrue(Math.abs(negYMatrix.getA(0, 3).getImaginary()-17.6366)<1.0E-4);
	        
	        //Load Bus: Bus 5
	        //Yii: 3.81 - j17.84
	        assertTrue(Math.abs(negYMatrix.getA(4, 4).getReal()-3.81)<1.0E-2);
	        assertTrue(Math.abs(negYMatrix.getA(4, 4).getImaginary()+17.84)<1.0E-2);
	        
	        //Y54:-1.37 + j11.60
	        assertTrue(Math.abs(negYMatrix.getA(4, 3).getReal()+1.37)<1.0E-2);
	        assertTrue(Math.abs(negYMatrix.getA(4, 3).getImaginary()-11.60)<1.0E-2);
	        
	        //Non-Gen, Non-Load: Bus7
	        //Yii 2.80 - j35.45
	        assertTrue(Math.abs(negYMatrix.getA(6, 6).getReal()-2.80)<1.0E-2);
	        assertTrue(Math.abs(negYMatrix.getA(6, 6).getImaginary()+35.45)<1.0E-2);
			
	        /*
	  		 * ***********************************
	  		 *       Zero sequence
	  		 * ***********************************
	  		 * Gen Bus 1,2 and 3 is open from the sequence network
	  		 * 
	  		 */
	        
	        ISparseEqnComplex zeroYMatrix = net.formScYMatrix(SequenceCode.ZERO, ScBusModelType.LOADFLOW_VOLT, false);
	      //Load Bus: Bus 5
	        //Yii: 1.0211168370406916 + (-6.79069203867941i)
	        assertTrue(Math.abs(zeroYMatrix.getA(4, 4).getReal()-1.02)<1.0E-2);
	        assertTrue(Math.abs(zeroYMatrix.getA(4, 4).getImaginary()+6.79)<1.0E-2);
	        
	        //Y54: -0.5460750853242321 + (4.641638225255973i)
	        assertTrue(Math.abs(zeroYMatrix.getA(4, 3).getReal()+0.54)<1.0E-2);
	        assertTrue(Math.abs(zeroYMatrix.getA(4, 3).getImaginary()-4.64)<1.0E-2);
	        
	        //Non-Gen, Non-Load: Bus7
	        //Yii 1.1218907410149137 + (-23.641745252186816i)
	        assertTrue(Math.abs(zeroYMatrix.getA(6, 6).getReal()-1.12)<1.0E-2);
	        // TODO
	        //assertTrue(Math.abs(zeroYMatrix.getA(6, 6).getImaginary()+23.64)<1.0E-2);
	        
	       // MatrixUtil.matrixToMatlabMFile("output/ieee9_zeroYmatrix.m", zeroYMatrix);
	        
		}
	
	@Test
	public void testFaultCalc() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.AcscNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq"
		}));
		AcscModelParser acscParser =(AcscModelParser) adapter.getModel();
		//acscParser.stdout();
		
		AcscNetwork net = new ODMAcscParserMapper().map2Model(acscParser).getAcscNet();
		
		//set the order in original sequence for better testing
		for(int i=1;i<=net.getNoBus();i++){
			net.getBus("Bus"+i).setSortNumber(i-1);
		}
		net.setBusNumberArranged(true);
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  	
  		assertTrue( net.isLfConverged());
  		
  		net.initialization(ScBusModelType.LOADFLOW_VOLT);
		
  	  	//*********************************************
	  	//             Bus4 3P Fault
	  	//********************************************
	  	
  		
	  	SimpleFaultAlgorithm acscAlgo = CoreObjectFactory.createSimpleFaultAlgorithm(net);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus4", acscAlgo, true /* cacheBusScVolt */ );
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
		//pre fault profile : solved power flow
		acscAlgo.setScBusModelType(ScBusModelType.LOADFLOW_VOLT);
		
		acscAlgo.calBusFault(fault);
	  	//System.out.println(fault.getFaultResult().getSCCurrent_012());
	  	//System.out.println(fault.getFaultResult().getBusVoltage_012(net.getAcscBus("Bus1")));
	  	
	  	//3p fault @Bus4
	  	//fault current
	  	//0.0000 + j0.0000  -1.4243 + j15.62133  0.0000 + j0.0000
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getSCCurrent_012(), 
	  			0.0, 0.0, -1.4243, 15.62133, 0.0, 0.0) );
	  	//voltage @Bus1
	  	//0.0000 + j0.0000  0.61592 + j0.01616  0.0000 + j0.0000
	  	//IBusScVoltage busResult = (IBusScVoltage)fault.getFaultResult();
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getBusVoltage_012(net.getBus("Bus1")), 
	  			0.0, 0.0, 0.61592, 0.01616, 0.0, 0.0) );
	  	

	  	
	  	
	  	//*********************************************
	  	//             Bus1 L-L Fault
	  	//********************************************
	  	
	  	fault = CoreObjectFactory.createAcscBusFault("Bus1", acscAlgo, true /* cacheBusScVolt */ );
		fault.setFaultCode(SimpleFaultCode.GROUND_LL);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
		//pre fault profile : solved power flow
		acscAlgo.setScBusModelType(ScBusModelType.LOADFLOW_VOLT);
		
		acscAlgo.calBusFault(fault);
	  	//System.out.println(fault.getFaultResult().getSCCurrent_012());
	  	System.out.println(fault.getFaultResult().getBusVoltage_012(net.getBus("Bus4")));
	  	
	    //seq voltage @Bus4
	  	//0.0000 + j0.0000  0.61996 + j-0.00357  0.40527 + j-0.0355
	  	//sqrt(0.61996^2+0.00357^2) = 0.61997
	  	//sqrt(0.40527^2+0.0355^2) = 0.40682
	  	
	  	
	  	/*PWD: Fault Data - Buses
	  	 Seq. Volt +	 Seq. Volt -
	  	      0.61997	      0.40682
         */
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getBusVoltage_012(net.getBus("Bus4")), 
	  			0.0, 0.0, 0.61996, -0.00357, 0.40527, -0.0355) );
	  	
	  	//output fault analysis result
	  	System.out.println(AcscOutFunc.faultResult2String(net,acscAlgo));
	  	
	  	
	  	
	  	
	  	//*********************************************
	  	//             Bus4 LG Fault
	  	//********************************************
	  	fault = CoreObjectFactory.createAcscBusFault("Bus4", acscAlgo, true /* cacheBusScVolt */ );
		fault.setFaultCode(SimpleFaultCode.GROUND_LG);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
		//pre fault profile : solved power flow
		acscAlgo.setScBusModelType(ScBusModelType.LOADFLOW_VOLT);
		
		acscAlgo.calBusFault(fault);
	  	//System.out.println(fault.getFaultResult().getSCCurrent_012());
	  	//System.out.println(fault.getFaultResult().getBusVoltage_012(net.getAcscBus("Bus1")));
	  	
	  	
	    //seq voltage @Bus1
	  	//0.0000 + j0.0000  0.88659 + j0.01024  -0.15334 + j0.01034
		// TODO
	  	//assertTrue(TestUtilFunc.compare(fault.getFaultResult().getBusVoltage_012(net.getBus("Bus1")), 
	  	//		0.0, 0.0, 0.88659, 0.01024, -0.15334, 0.01034) );
		
	}
	
	@Test
	public void testFaultCalc_compare() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.AcscNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_newGenBase.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_null.seq"
		}));
		AcscModelParser acscParser =(AcscModelParser) adapter.getModel();
		//acscParser.stdout();
		
		AcscNetwork net = new ODMAcscParserMapper().map2Model(acscParser).getAcscNet();
		net.setLfDataLoaded(true);
		net.setPositiveSeqDataOnly(true);

		net.initialization(ScBusModelType.LOADFLOW_VOLT);
		
		//set the order in original sequence for better testing
		for(int i=1;i<=net.getNoBus();i++){
			net.getBus("Bus"+i).setSortNumber(i-1);
		}
		net.setBusNumberArranged(true);
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  	
  		assertTrue( net.isLfConverged());
		
  	  	//*********************************************
	  	//             Bus4 3P Fault
	  	//********************************************
  		
	  	SimpleFaultAlgorithm acscAlgo = CoreObjectFactory.createSimpleFaultAlgorithm(net);
		//pre fault profile : solved power flow
		acscAlgo.setScBusModelType(ScBusModelType.LOADFLOW_VOLT);
	  	
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus4", acscAlgo, true /* cacheBusScVolt */ );
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
		acscAlgo.calBusFault(fault);
	  	//System.out.println(fault.getFaultResult().getSCCurrent_012());
	  	//System.out.println(fault.getFaultResult().getBusVoltage_012(net.getAcscBus("Bus1")));
	  	
	  	//3p fault @Bus4
	  	//fault current
	  	//0.0000 + j0.0000  -1.42636 + j15.62254  0.0000 + j0.0000
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getSCCurrent_012(), 
	  			0.0, 0.0, -1.42636, 15.62254, 0.0, 0.0) );
	  	//voltage @Bus1
	  	//0.0000 + j0.0000  0.61592 + j0.01616  0.0000 + j0.0000
	  	//IBusScVoltage busResult = (IBusScVoltage)fault.getFaultResult();
	  	assertTrue(TestUtilFunc.compare(fault.getFaultResult().getBusVoltage_012(net.getBus("Bus1")), 
	  			0.0, 0.0, 0.61592, 0.01616, 0.0, 0.0) );
	}
}
