package org.interpss.core.adapter.psse.acsc;

import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.acsc.AcscModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.AcscOutFunc;
import org.interpss.mapper.odm.ODMAcscParserMapper;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.ComplexFunc;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.sc.ScBusVoltageType;
import com.interpss.core.algo.sc.SimpleFaultAlgorithm;
import com.interpss.core.net.Branch;

public class IEEE300Bus_Zone_setting  extends CorePluginTestSetup {
	
	//@Test
	public void testDataInputAndACSC() throws Exception {
		
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.AcscNet, new String[]{
				"testData/adpter/psse/v30/IEEE300/IEEE300Bus_modified_noHVDC.raw",
				"testData/adpter/psse/v30/IEEE300/IEEE300.seq"
		}));
		AcscModelParser acscParser =(AcscModelParser) adapter.getModel();
		
		//acscParser.stdout();
		
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

		
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		
//	  	SimpleFaultAlgorithm acscAlgo = CoreObjectFactory.createSimpleFaultAlgorithm(net);
//  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus202", acscAlgo );
//		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
//		fault.setZLGFault(new Complex(0.0, 0.0));
//		fault.setZLLFault(new Complex(0.0, 0.0));
//		
//		//pre fault profile : solved power flow
//		acscAlgo.setScBusVoltage(ScBusVoltageType.LOADFLOW_VOLT);
//		
//		acscAlgo.calculateBusFault(fault);
//	  	System.out.println(fault.getFaultResult().getSCCurrent_012());
//	  	
//	  	System.out.println(AcscOutFunc.faultResult2String(net,acscAlgo));
//	  	
//	  	
	}
	
	@Test
	public void calcZone3Setting() throws Exception {
		   
		
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.AcscNet, new String[]{
				"testData/adpter/psse/v30/IEEE300/IEEE300Bus_modified_noHVDC.raw",
				"testData/adpter/psse/v30/IEEE300/IEEE300.seq"
				
		}));
		AcscModelParser acscParser =(AcscModelParser) adapter.getModel();
	
		//acscParser.stdout();
		AcscNetwork net = new ODMAcscParserMapper().map2Model(acscParser).getAcscNet();
		
		//System.out.println(net.net2String());
		
//		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
//	  	algo.setLfMethod(AclfMethod.PQ);
//	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
//	  	algo.loadflow();
		net.setLfConverged(true);
  	
  		assertTrue( net.isLfConverged());
  		

  		String dyrFileName = "IEEE300Bus_345kV_zone123_setting.dyr";
  		StringBuffer sb = new StringBuffer();
  		
        for(AcscBranch bra:net.getBranchList()){
        	if(bra.isActive()&&bra.isLine() && Math.abs(bra.getFromBus().getBaseVoltage()-345000.0)<0.1){
        		
        		
          		
          		int relayBusNum = (int) bra.getFromBus().getNumber();
          		int relayRemoteBusNum = (int) bra.getToBus().getNumber();
          		String circuitID =bra.getCircuitNumber();
          		
          		double[] zone3Setting = calcBranchZone3Settings(relayBusNum, relayRemoteBusNum, circuitID, net);
          		
          		if(zone3Setting !=null){
          		
	          		double[] zone12Setting = calcBranchZone12Settings(relayBusNum, relayRemoteBusNum,circuitID, net);
	          		
	          		System.out.println("Reach, centerline angle = "+ zone3Setting[1]+","+zone3Setting[2]);
	          		
	          		
	          		String zoneProtectDyrString = getZoneProtectionDyrString(relayBusNum, relayRemoteBusNum, circuitID, zone12Setting,zone3Setting);
	          		sb.append(zoneProtectDyrString);
          		}
          		
          		
        	}
        }
  		
        /*
  		int relayBusNum = 195;
  		int relayRemoteBusNum = 219;
  		String circuitID ="1";
  		
  		double[] zone3Setting = calcBranchZone3Settings(relayBusNum, relayRemoteBusNum, circuitID, net);
  		double[] zone12Setting = calcBranchZone12Settings(relayBusNum, relayRemoteBusNum,circuitID, net);
  		
  		System.out.println("Reach, centerline angle = "+ zone3Setting[1]+","+zone3Setting[2]);
  		
  		String dyrFileName = "Bus"+relayBusNum+"_zone3_setting.dyr";
  		
  		String zoneProtectDyrString = getZoneProtectionDyrString(relayBusNum, relayRemoteBusNum, circuitID, zone12Setting,zone3Setting);
  		*/
  		
  		outputPSSEDyrFile(sb.toString(), dyrFileName); 
		
	}
	
	
	
	private String getZoneProtectionDyrString(int relayBusNum, int relayRemoteBusNum, String circuitID,double[] zone12Settings, double[] zone3Settings) 
	{
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
		return sb.toString();
	}

	private boolean outputPSSEDyrFile(String zoneSettings, String dyrFileName) 
					throws UnsupportedEncodingException, FileNotFoundException, IOException{
		
		
		//open file and write the data to file
		
		try {
		    Files.write(Paths.get(dyrFileName), zoneSettings.getBytes());
		    
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
				IpssLogger.getLogger().severe("No line is found for the input bus numbers and ID:"+relayBusNum+","+relayRemoteBusNum+","+circuitID);
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
			
			for(Branch bra:relayRemoteBus.getConnectedPhysicalBranchList()){
				// not include the relay branch itself
				if(!bra.getId().equals(relayBranch.getId())){
				    remoteBranchList.add((AcscBranch)bra);
				    AcscBus twoBusAwayBus = null;
				    try {
				    	twoBusAwayBus = (AcscBus) bra.getConnectedPhysicalOppositeBus(relayRemoteBus);
					} catch (InterpssException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				    // take into account of lines parallel to the relay branch
				    if(twoBusAwayBus !=null && !twoBusAwayBus.getId().equals(relayBus.getId())) {
				    	remoteBusList.add(twoBusAwayBus);
				    
				        // 3. run the short circuit analysis
				    	
				    	System.out.println("Remote fault bus:"+twoBusAwayBus.getId());
				
				    	AcscBusFault fault = CoreObjectFactory.createAcscBusFault(twoBusAwayBus.getId(), acscAlgo );
						fault.setFaultCode(SimpleFaultCode.GROUND_3P);
						fault.setZLGFault(new Complex(0.0, 0.0));
						fault.setZLLFault(new Complex(0.0, 0.0));
						
						//pre fault profile : solved power flow
						acscAlgo.setScBusVoltage(ScBusVoltageType.LOADFLOW_VOLT);
						
						try {
							acscAlgo.calculateBusFault(fault);
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
			// take the direction into account, only if abs{angle(Zpp)-angle(Zbranch)}<90 will be considered
			for (Complex Zapp: apparentImpedanceTable.values()){
				  if(Math.abs(ComplexFunc.arg(Zapp)*180/Math.PI - zAngle)<90.0){
				      if(Zapp.abs() > maxZapp) maxZapp = Zapp.abs();
				  }
			}
			if(maxZapp<=relayBranch.getZ().abs()){
				IpssLogger.getLogger().severe("No proper zone 3 setting can be found, relayBus, remoteBus: "+relayBusId+","+relayRemoteBusId);
			    return null;
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
		// ADD 10% margin, so maxZpp is multiplied by 1.1
		return new double[]{zone3_pickup_time,maxZapp*1.1,zAngle,0.55*maxZapp,blinder1_type,blinder1_intercept,blinder1_rotation,blinder2_type,blinder2_intercept,blinder2_rotation };
		
		
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
