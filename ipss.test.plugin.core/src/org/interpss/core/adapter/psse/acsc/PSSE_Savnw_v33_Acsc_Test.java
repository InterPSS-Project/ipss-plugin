package org.interpss.core.adapter.psse.acsc;

import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.ieee.odm.model.acsc.AcscModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.AcscOutFunc;
import org.interpss.mapper.bean.aclf.AclfNet2BeanMapper;
import org.interpss.mapper.odm.ODMAclfParserMapper;
import org.interpss.mapper.odm.ODMAcscParserMapper;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.sc.ScBusModelType;
import com.interpss.core.algo.sc.SimpleFaultAlgorithm;
import com.interpss.core.net.Branch;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class PSSE_Savnw_v33_Acsc_Test extends CorePluginTestSetup {
	@Test
	public void testDataInputAndACSC() throws Exception {
		
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile(NetType.AcscNet, new String[]{
				"testData/adpter/psse/v33/savnw.raw",
				"testData/adpter/psse/v33/savnw.seq"
				
		}));
		AcscModelParser acscParser =(AcscModelParser) adapter.getModel();
		
		
		acscParser.stdout();
		
		AcscNetwork net = new ODMAcscParserMapper().map2Model(acscParser).getAcscNet();
		
		//set the order in original sequence for better testing
//		for(int i=1;i<=net.getNoBus();i++){
//			net.getBus("Bus"+i).setSortNumber(i-1);
//		}
//		net.setBusNumberArranged(true);
		
		//System.out.println(net.net2String());
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.PQ);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  	
  		assertTrue( net.isLfConverged());

		
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		
	  	SimpleFaultAlgorithm acscAlgo = CoreObjectFactory.createSimpleFaultAlgorithm(net);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus202", acscAlgo, true /* cacheBusScVolt */ );
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
		//pre fault profile : solved power flow
		acscAlgo.setScBusModelType(ScBusModelType.LOADFLOW_VOLT);
		
		acscAlgo.calBusFault(fault);
	  	System.out.println(fault.getFaultResult().getSCCurrent_012());
	  	
	  	System.out.println(AcscOutFunc.faultResult2String(net,acscAlgo));
	  	
	  	/*
	  	 *  08/03/2016 benchmarked with Powerworld, using powerflow based voltage
	  	 *  
	  	 * 0.0000 + j0.0000  -18.9950 + j64.09628  0.0000 + j0.0000


              Bus Fault Info
              ==============

          Fault Id:      Bus201_Ground_3P_
          Bus name:      HYDRO       
          Fault type:    Ground_3P
          Fault current: 66.85165 (106.5) pu    7719.36 amps

      BusID     BusName   BasekV             FaultVoltage            ContribAmps
                                         (pu)          (volts)      (pu)       (amps)
     --------   --------- -------    ---------------   --------   --------   ----------
     Bus101       NUC-A           21.6   0.59744 (25.5)    12904.7          0            0
     Bus102       NUC-B           21.6   0.59744 (25.5)    12904.7          0            0
     Bus151       NUCPANT          500   0.40241 (20.8)   201205.4          0            0
     Bus152       MID500           500   0.40288 (0.4)    201441.6          0            0
     Bus153       MID230           230   0.41703 (-1.9)      95916          0            0
     Bus154       DOWNTN           230   0.4441 (-9.3)    102142.4          0            0
     Bus201       HYDRO            500   0.0000 (0)              0          0            0
     Bus202       EAST500          500   0.31863 (-2.8)     159313          0            0
     Bus203       EAST230          230   0.38516 (-7.5)    88586.4          0            0
     Bus204       SUB500           500   0.30755 (-10.2)  153774.7          0            0
     Bus205       SUB230           230   0.45451 (-8.7)   104536.3          0            0
     Bus206       URBGEN            18   0.70454 (0.3)     12681.8          0            0
     Bus211       HYDRO_G           20   0.39976 (24.2)     7995.3          0            0
     Bus3001      MINE             230   0.72055 (0.2)      165726          0            0
     Bus3002      E. MINE          500   0.67905 (-0.4)   339524.7          0            0
     Bus3003      S. MINE          230   0.67963 (-0.8)   156314.2          0            0
     Bus3004      WEST             500   0.52547 (-2.1)   262736.1          0            0
     Bus3005      WEST             230   0.53623 (-3.6)   123331.9          0            0
     Bus3006      UPTOWN           230   0.45125 (-2.3)   103786.6          0            0
     Bus3007      RURAL            230   0.51294 (-7)     117975.6          0            0
     Bus3008      CATDOG           230   0.50351 (-7.5)   115807.5          0            0
     Bus3011      MINE_G          13.8   0.80003 (1.6)     11040.4          0            0
     Bus3018      CATDOG_G        13.8   0.67941 (-0.5)     9375.9          0            0
	  	 */
	  	
		//System.out.println(fault.getFaultResult().getBusVoltage_012().get);
	  	
	  	
	  	// TODO check the Zgen in ACSC processing
	  	
	  	
	  	/*
	  	 *  id: Bus206
     number: 206
     name: URBGEN      
     desc: null
     status: true (booleanFlag: false, weight: (0.0, 0.0), intFlag: 0, sortNumber: 0) (extensionObject: null)
     area(number): 2(2)
     zone(number): 2(2)
     baseVoltage: 18000.0
     genCode: GenPQ
     loadCode: NonLoad
     Desired voltageMag: 1.0
     Desired voltageAng: 0.0
     voltageMag: 1.02362
     voltageAng: -0.05183453345497959
     gen:        8.0000 + j6.0000
     capacitor:  0.0000
     load:       0.0000 + j0.0000
     shuntY:     0.0000 + j0.0000
     distFactor: 0.0000
     vLimit:     ( 0.0, 0.0 )
   LF Results : 
      voltage   : 1.02362 pu   18425.1600 v
      angle     : -2.9699 deg
      gen       : 8.0000 + j6.0000 pu   800000.0000 + j600000.0000 kva
      load      : 0.0000 + j0.0000 pu   0.0000 + j0.0000 kva

Contributing Gen:
, status: true, gen: 8.0000 + j6.0000, desiredVoltMag: 0.9800, remoteVControlBusId: Bus205)


  SC Info:  
     scCode:  NonContri
     z1:      0.0100 + j0.2500
     z2:      0.0000 + j10000000000.0000
     z0:      0.0000 + j10000000000.0000
     groundCode: Ungrounded
     groundZpu: 0.0000 + j10000000000.0000
	  	 */
	}
	
	@Test
	public void calcZone3Setting() throws Exception {
		   
		
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile(NetType.AcscNet, new String[]{
				"testData/adpter/psse/v33/savnw.raw",
				"testData/adpter/psse/v33/savnw.seq"
				
		}));
		AcscModelParser acscParser =(AcscModelParser) adapter.getModel();
	
		//acscParser.stdout();
		AcscNetwork net = new ODMAcscParserMapper().map2Model(acscParser).getAcscNet();
		
		//System.out.println(net.net2String());
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.PQ);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  	
  		assertTrue( net.isLfConverged());
  		
  		int relayBusNum = 151;
  		int relayRemoteBusNum = 152;
  		String circuitID ="1";
  		
  		double[] zone3Setting = calcBranchZone3Settings(relayBusNum, relayRemoteBusNum, circuitID, net);
  		double[] zone12Setting = calcBranchZone12Settings(relayBusNum, relayRemoteBusNum,circuitID, net);
  		
  		System.out.println("Reach, centerline angle = "+ zone3Setting[1]+","+zone3Setting[2]);
  		
  		String dyrFileName = "Bus"+relayBusNum+"_zone3_setting.dyr";
  		
  		outputPSSEDyrFile(relayBusNum, relayRemoteBusNum, circuitID, zone12Setting,zone3Setting, dyrFileName); 
		
	}
	
	
	
	private boolean outputPSSEDyrFile(int relayBusNum, int relayRemoteBusNum, String circuitID, 
			double[] zone12Settings,double[] zone3Settings, String dyrFileName) 
					throws UnsupportedEncodingException, FileNotFoundException, IOException{
		/*
		 * Format:
		 * IBUS, 'DISTR1',JBUS, ID, RS, ICON(M) to ICON(M+10), CON(J) to CON(J+23)
		 * 
		 * 
		 * Example:
		 *     151 'DISTR1'          152           1             1            1
               1            0            0            0            0
               0            0            0            0            0
          // zone1                                           //zone 2
          1.0000      0.36800E-01   85.000      0.18400E-01   18.000
                                                //zone 3 
         0.55200E-01   85.000      0.27600E-01  0.10000E+07   0.0000
                                     //J+12 //treshold currrent //Self trip breaker
          0.0000       0.0000       0.0000      1.20000       2.0000
          //recloser    //transfer trip time     //blinder 1
         0.10000E+07  0.10000E+07  0.10000E+07   0.0000       0.0000
                       //blinder 2
          0.0000       0.0000       0.0000       0.0000    /
		 * 
		 */
		int RS = 1;  // Relay slot
		int relayType = 1; // 1- mho, 2 - impedance, 3 - reactance distance
		int monitorOperate = 1; 	
		
        double  zone1_operating_time = zone12Settings[0];
		double  zone1_reach = zone12Settings[1];
		double  zone1_center_angle = zone12Settings[2];
		double  zone1_center_dist = zone12Settings[3];
		
		double  zone2_pickup_time= zone12Settings[4];
		double  zone2_reach = zone12Settings[5];
		double  zone2_center_angle = zone12Settings[6];
		double  zone2_center_dist = zone12Settings[7];
		
		double  zone3_pickup_time= zone3Settings[0];
		double  zone3_reach = zone3Settings[1];
		double  zone3_center_angle = zone3Settings[2];
		double  zone3_center_dist = zone3Settings[3];
		
		double threshold_current = 1.2;
		double self_trip_breaker_time  = 2.0;
		double self_trip_recloser_time = 1.0E7;
		double transfer_trip_breaker_time  = 1.0E7;
		double transfer_trip_recloser_time = 1.0E7;
		
		
		int blinder1_type = (int) zone3Settings[4];
		double blinder1_intercept = zone3Settings[5];
		double blinder1_rotation = zone3Settings[6];
		
		int blinder2_type = (int) zone3Settings[7];
		double blinder2_intercept = zone3Settings[8];
		double blinder2_rotation = zone3Settings[9];
        
		StringBuffer sb = new StringBuffer();
		sb.append(relayBusNum+", 'DISTR1'");
		sb.append(", "+relayRemoteBusNum);
		sb.append(", "+circuitID);
		sb.append(", "+RS);
		
		// ICON(M)
		sb.append(", "+relayType);      // M
		sb.append(", "+monitorOperate); //M+1
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);    //M+10
		
		//CON(J)
		//zone 1
		sb.append(", "+ zone1_operating_time );
		sb.append(", "+ zone1_reach );
		sb.append(", "+ zone1_center_angle);
		sb.append(", "+ zone1_center_dist );
		
		// zone 2
		sb.append(", "+ zone2_pickup_time );
		sb.append(", "+ zone2_reach );
		sb.append(", "+ zone2_center_angle);
		sb.append(", "+ zone2_center_dist );
		
		//zone 3
		sb.append(", "+ zone3_pickup_time );
		sb.append(", "+ zone3_reach );
		sb.append(", "+ zone3_center_angle);
		sb.append(", "+ zone3_center_dist );
		
		
		//J+12 TO J+23
		
		sb.append(", "+ 0);
		sb.append(", "+ 1.2);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append(", "+ 0);
		sb.append("  /\n");
		
		//open file and write the data to file
		
		try {
		    Files.write(Paths.get(dyrFileName), sb.toString().getBytes());
		    
		    IpssLogger.getLogger().info("The zone 1/2/3 protection data is saved to :"+ dyrFileName);
		    System.out.println("The zone 1/2/3 protection data is saved to :"+ dyrFileName);
		
		} catch (IOException e) {
		    e.printStackTrace();
		    return false;
		}
	
		
		return true;
		
	}
	
	/**
	 * output data format: 
	 *    1) zone 3 pickup time; 
	 *    2) reach of zone 3 relay in pu; 
	 *    3) centerline angle, i.e., angle(Z), in degrees.
	 *    4) 1st blinder type, +-1, +-2
	 *    5) 1st blinder type,
	 *    6) 1st blinder rotation,
	 *    7) 2st blinder type, +-1, +-2
	 *    8) 2st blinder type,
	 *    9) 3st blinder rotation
	 *  
	 * @param BranchId
	 * @param RelayAtFromSide
	 * @param net
	 * @return
	 * @throws InterpssException 
	 */
	private double[] calcBranchZone3Settings(int relayBusNum, int relayRemoteBusNum, String circuitID, AcscNetwork net) 
			throws InterpssException{
		
		  // String branchId = ""; 
        boolean relayAtFromSide = true;
         
        String relayBusId = "Bus"+relayBusNum;
        String relayRemoteBusId = "Bus"+relayRemoteBusNum;
		
		
		List<AcscBranch>  remoteBranchList = new ArrayList<>();
		List<AcscBus>  remoteBusList = new ArrayList<>();
		Hashtable<String, Complex> apparentImpedanceTable = new Hashtable<>();
		
		double zone3_pickup_time = 90; // in cycles
		
		int blinder1_type = 0;
		double blinder1_intercept = 0;
		double blinder1_rotation = 0;
		
		int blinder2_type = 0;
		double blinder2_intercept = 0;
		double blinder2_rotation = 0;
		
		
		// initialize the short circuit analysis algorithm
		SimpleFaultAlgorithm acscAlgo = CoreObjectFactory.createSimpleFaultAlgorithm(net);
		
		// 1. find the relay location bus
		AcscBranch relayBranch = net.getBranch(relayBusId,relayRemoteBusId,circuitID);
		
		if(relayBranch ==null){
			relayBranch = net.getBranch(relayRemoteBusId,relayBusId,circuitID);
			
			if(relayBranch==null){
				IpssLogger.getLogger().severe("No line is found for the input bus numbers:"+relayBusNum+","+relayRemoteBusNum);
			    return null;
			}
			else
				relayAtFromSide = false;
		}
		
		double zAngle = 0.0;
		
		//The maximum magnitude of the calculated apparent impedances 
		double maxZapp = 0.0;
		
		if(relayBranch != null){
			
			System.out.println("Relay branch Z ="+relayBranch.getZ());
			
			zAngle = ComplexFunc.arg(relayBranch.getZ())*180/Math.PI; // converted to degrees
			
			AcscBus relayBus = relayAtFromSide? relayBranch.getFromAcscBus():relayBranch.getToAcscBus();
			
			AcscBus relayRemoteBus = relayAtFromSide? relayBranch.getToAcscBus():relayBranch.getFromAcscBus();
			
			System.out.println("RelayBus ="+relayBus.getId()+","+"RelayRemoteBus ="+relayRemoteBus.getId());
			
			// 2. find all the connected branches connected to relayRemoteBus, except the relayBranch.
			// and then find the two-bus away remote bus, apply a three-phase fault on it.
			
			for(Branch bra:relayRemoteBus.getBranchList()){
				// not include the relay branch itself
				if(!bra.getId().equals(relayBranch.getId())){
				    remoteBranchList.add((AcscBranch)bra);
				    AcscBus twoBusAwayBus = null;
				    try {
				    	twoBusAwayBus = (AcscBus) bra.getOppositeBus(relayRemoteBus);
					} catch (InterpssException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				    // take into account of lines parallel to the relay branch
				    if(twoBusAwayBus !=null && !twoBusAwayBus.getId().equals(relayBus.getId())) {
				    	remoteBusList.add(twoBusAwayBus);
				    
				        // 3. run the short circuit analysis
				    	
				    	System.out.println("Remote fault bus:"+twoBusAwayBus.getId());
				
				    	AcscBusFault fault = CoreObjectFactory.createAcscBusFault(twoBusAwayBus.getId(), acscAlgo, true /* cacheBusScVolt */ );
						fault.setFaultCode(SimpleFaultCode.GROUND_3P);
						fault.setZLGFault(new Complex(0.0, 0.0));
						fault.setZLLFault(new Complex(0.0, 0.0));
						
						//pre fault profile : solved power flow
						acscAlgo.setScBusModelType(ScBusModelType.LOADFLOW_VOLT);
						
						try {
							acscAlgo.calBusFault(fault);
						} catch (InterpssException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						// get the measured current and voltage at the relay point
						Complex3x1 relayBranchCurrent = relayAtFromSide?fault.getFaultResult().calBranchScAmpFrom2To(relayBranch):
							              fault.getFaultResult().calBranchScAmpTo2From(relayBranch);
						
						
						
						Complex3x1 relayBusVoltage = fault.getFaultResult().getBusVoltage_012(relayBus);
						
						System.out.println("Relay bus V and I:"+relayBusVoltage.b_1.toString()+","+relayBranchCurrent.b_1.toString());
								
						// calculate the apparent impedance
						Complex Zapp = relayBusVoltage.b_1.divide(relayBranchCurrent.b_1);
						
						apparentImpedanceTable.put(twoBusAwayBus.getId(), Zapp);
					    
				    	
				    }
				}
				
			}
			
			// compare all the apparent impedances, the maximum value will be used
			
			for (Complex Zapp: apparentImpedanceTable.values()){
				  if(Zapp.abs() > maxZapp) maxZapp = Zapp.abs();
			}
			
			 System.out.println("Apparent impedances:"+apparentImpedanceTable.toString());
		}
		
		
		// return results
		// TODO calculate the blinder settings
		// TODO need to have the blinder consideration in the input
		
		/*
		 * % Rated current
			d.Irated_i = 12;
			%% Maximum load
			%% 85% voltage and 115% of maximum load
			d.Zload_i = (0.85/d.Irated_i/1.15)*exp(i*30*pi/180); % 30 degree
			%% Blinder intercept
			d.Zblind_i = (0.85/d.Irated_i/1.15)*(cos(30*pi/180) - sin(30*pi/180)/tan(d.Zang_i*pi/180));

		 */
		
		return new double[]{zone3_pickup_time,maxZapp,zAngle,0.5*maxZapp,blinder1_type,blinder1_intercept,blinder1_rotation,blinder2_type,blinder2_intercept,blinder2_rotation };
		
		
	}
	/**
	 * output format:
	 *    zone1_operating_time, zone1_reach, zone1_centerline_angle, zone1_center_distance,
	 *    zone2_pickup, zone2_reach, zone2_centerline_angle, zone2_center_distance.
	 * 
	 * @param branchId
	 * @param relayAtFromSide
	 * @param net
	 * @return
	 * @throws InterpssException
	 */
	private double[] calcBranchZone12Settings(int relayBusNum, int relayRemoteBusNum, String circuitID, AcscNetwork net) throws InterpssException{
		       
		        // String branchId = ""; 
		        boolean relayAtFromSide = true;
		         
		        String relayBusId = "Bus"+relayBusNum;
		        String relayRemoteBusId = "Bus"+relayRemoteBusNum;
		         
		       // 1. find the relay location bus
		       
		        
				AcscBranch relayBranch = net.getBranch(relayBusId,relayRemoteBusId,circuitID);
				
				if(relayBranch ==null){
					relayBranch = net.getBranch(relayRemoteBusId,relayBusId,circuitID);
					if(relayBranch==null){
						IpssLogger.getLogger().severe("No line is found for the input bus numbers:"+relayBusNum+","+relayRemoteBusNum);
					    return null;
					}
					else
						relayAtFromSide = false;
				}
				
					
				
				double zAngle = 0.0;
				
				//The maximum magnitude of the calculated apparent impedances 
				double Z1app = 0.0;
				double Z2app = 0.0;
				double zone1_operating_time = 1.0; // depends on the voltage level, 2 cycles for <= 138 kV, 1 cycle for >= 230 kV
				double zone2_pickup_time = 35.0; //
				
				
				if(relayBranch != null){
					// check the voltage level
					if(relayBranch.isLine()){
						if(relayBranch.getFromAclfBus().getBaseVoltage()>=230000){
							zone1_operating_time = 1.0;
						}
						else{ // Do we need to consider voltage level less than 69 kV
							zone1_operating_time = 2.0;
						}
					}
					else{
						// warning, transformer is not considered yet.
					}
					
					System.out.println("Relay branch Z ="+relayBranch.getZ());
					
					// zone1: 80% line impedance
					Z1app = relayBranch.getZ().abs()*0.8;
					
					// zone1: 120% line impedance
					Z2app = relayBranch.getZ().abs()*1.2;
					
					zAngle = ComplexFunc.arg(relayBranch.getZ())*180/Math.PI; // converted to degrees
					
					AcscBus relayBus = relayAtFromSide? relayBranch.getFromAcscBus():relayBranch.getToAcscBus();
					
					
				}
				else return null;
				
		return new double[] {zone1_operating_time,Z1app, zAngle, 0.5*Z1app,zone2_pickup_time,Z2app,zAngle,0.5*Z2app};
	     
		
	}
		
		
	

}
