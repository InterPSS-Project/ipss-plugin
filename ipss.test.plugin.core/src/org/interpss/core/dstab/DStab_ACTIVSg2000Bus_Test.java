package org.interpss.core.dstab;

import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class DStab_ACTIVSg2000Bus_Test  extends DStabTestSetupBase{
		
		@Test
		public void test_ACTIVSg2000_Dstab() throws InterpssException{
			IpssCorePlugin.init();
			IpssLogger.getLogger().setLevel(Level.WARNING);
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_33);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					
					//NOTE: the original IEEE300Bus_modified_noHVDC.raw could result in oscillation for some faults due to capacitor compensation of long distance lines between 120 and 118
					
					"testData/adpter/psse/v33/ACTIVSg2000/ACTIVSg2000.raw",
					//"testData/adpter/psse/v33/ACTIVSg2000/ACTIVSg2000_dynamics_v2.dyr"
					"testData/adpter/psse/v33/ACTIVSg2000/ACTIVSg2000_dyn_cmld_zone3_v1.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			//System.out.println(parser.toXmlDoc());
            
			
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    BaseDStabNetwork<?, ?> dsNet =simuCtx.getDStabilityNet();
		    
		    //dsNet.setBypassDataCheck(true);
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			
			aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

			//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
			//aclfAlgo.getLfAdjAlgo().setPowerAdjust();
			aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
			aclfAlgo.setTolerance(1.0E-6);
			assertTrue(aclfAlgo.loadflow());
			//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.002);
			dstabAlgo.setTotalSimuTimeSec(20);
			dstabAlgo.setRefMachine(dsNet.getMachine("Bus7353-mach1"));
			

			StateMonitor sm = new StateMonitor();
			
			sm.addBusStdMonitor(new String[]{"Bus7093","Bus7315","Bus7242","Bus7067","Bus7185","Bus7343","Bus7386","Bus7124","Bus7427","Bus7276","Bus7249","Bus7417","Bus7155","Bus7225","Bus7028","Bus7385","Bus7298","Bus7248","Bus7214","Bus7384","Bus7183","Bus7051","Bus7052","Bus7330","Bus7088","Bus7410","Bus7222","Bus7161","Bus7362","Bus7221","Bus7087","Bus7181","Bus7180","Bus7235","Bus7344","Bus7296","Bus7336","Bus7388","Bus7316","Bus7382","Bus7120","Bus7178","Bus7229","Bus7216","Bus7188","Bus7240","Bus7089","Bus7397","Bus7425","Bus7055","Bus7399","Bus7053","Bus7091","Bus7297","Bus7094","Bus7220","Bus7127","Bus7148","Bus7395","Bus7255","Bus7256","Bus7101","Bus7039","Bus7117","Bus7246","Bus7179","Bus7017","Bus7241","Bus7049"});
			sm.addGeneratorStdMonitor(new String[]{"Bus7353-mach1","Bus7167-mach1","Bus7170-mach1","Bus1050-mach1","Bus1080-mach1"});
	
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(50);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus39-mach1"));
			
			/**
			 * 500 kV 
			 *  7227	Coast	HOUSTON 90 0	500	1.02529	512.643	-29.87						0	0	7	Houston	3
				7125	Coast	SUGAR LAND 2 0	500	1.02694	513.472	-25.55						0	0	7	Houston	3
				7037	Coast	SUGAR LAND 3 0	500	1.02045	510.224	-31.26						0	0	7	Houston	3
				7341	Coast	KATY 3 0	500	1.01732	508.659	-27.5						0	0	7	Houston	3
				7346	Coast	THOMPSONS 0	500	1.03235	516.176	-24.13						0	0	7	Houston	3
				7186	Coast	HOUSTON 4 0	500	1.02292	511.46	-26.83						0	0	7	Houston	3
				7047	Coast	KATY 1 0	500	1.0187	509.348	-31.7						0	0	7	Houston	3
				7159	Coast	HOUSTON 5 0	500	1.02463	512.316	-31.32						0	0	7	Houston	3

			 */
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7159",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0,0),null,1.0d,0.08),"3phaseFault@Bus17");
			
			//exciter list
			List<String> error_exc_List = Arrays.asList(//"Bus4134-mach1",
					//"Bus5299-mach1",
					//"Bus5300-mach1",
					//"Bus5326-mach1",
					//"Bus5405-mach1",
					//"Bus6104-mach1",
					//"Bus6105-mach1",
					//"Bus6106-mach1",
					//"Bus6296-mach1",
					//"Bus7024-mach1",
					//"Bus7107-mach1",
					//"Bus7165-mach1",
					//"Bus7323-mach1",
					//"Bus7326-mach1",
					//"Bus7357-mach1",
					//"Bus7373-mach1"
					);
			
			for(BaseDStabBus<? extends DStabGen, ? extends DStabLoad> bus: dsNet.getBusList()) {
				if(bus.isActive() && bus.isGen()) {
					for(DStabGen gen: bus.getContributeGenList()) {
						if(gen.isActive()) {
							if(gen.getMach()!=null &&  error_exc_List.contains(gen.getMach().getId())) {
								gen.getMach().getExciter().setStatus(false);
					              
					            System.out.println("Turn off Exciter @"+gen.getMach().getExciter().getId());
					
							}
						}
					}
				}
			}
			
			//processing genQ limit violation
			/*
			double violation_ratio =0.8;
			for(BaseDStabBus<? extends DStabGen, ? extends DStabLoad> bus: dsNet.getBusList()) {
				if(bus.isActive() && bus.isGen()) {
					for(DStabGen gen: bus.getContributeGenList()) {
						if(gen.isActive()) {
							if(gen.getMach()!=null &&  gen.getMach().getExciter()!=null) {
								//if(gen.getMach().getExciter() instanceof IEEE2005ST4BExciter) {
									if(gen.getGen().getImaginary() > violation_ratio *gen.getQGenLimit().getMax() ||
											gen.getGen().getImaginary() < violation_ratio *gen.getQGenLimit().getMin() ) {
										
									              gen.getMach().getExciter().setStatus(false);
									              
									             System.out.println("Turn off ST4BExciter @"+gen.getMach().getExciter().getId());
									}
//									else {
//										IEEE2005ST4BExciter exciter = (IEEE2005ST4BExciter) gen.getMach().getExciter();
//										exciter.getData().setVrmax(99);
//										exciter.getData().setVrmin(-99);
//										
//										exciter.getData().setVmmax(99);
//										exciter.getData().setVmmin(-99);
//									
//										exciter.getData().setVbmax(9);
//									
//										exciter.getData().setTr(0.05);
//										exciter.getData().setTa(0.01);
//									
//										
//										if(exciter.getData().getKpr()>5) {
//											exciter.getData().setKpr(3.2);
//											exciter.getData().setKir(3.2);
//											
//										}
//										if(exciter.getData().getKi()<0.0001) {
//											exciter.getData().setKi(1);
//										}
											
//										if(exciter.getData().getKim()<0.0001) {
//											exciter.getData().setKim(0.01);
//										}
//											
//										
//									}
									
//								}
							
							}
						}
							
					}
				}
			}
*/
			
			if (dstabAlgo.initialization()) {
				double t1 = System.currentTimeMillis();
				System.out.println("time1="+t1);
				System.out.println("Running DStab simulation ...");
				//System.out.println(dsNet.getMachineInitCondition());
				dstabAlgo.performSimulation();
				double t2 = System.currentTimeMillis();
				System.out.println("used time="+(t2-t1)/1000.0);

			}
			System.out.println(sm.toCSVString(sm.getMachSpeedTable()));
			System.out.println(sm.toCSVString(sm.getMachAngleTable()));
			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			
			assertTrue(sm.getMachSpeedTable().get("Bus1050-mach1").get(0).getValue()-sm.getMachSpeedTable().get("Bus1050-mach1").get(10).getValue()<1.0E-4);
			assertTrue(sm.getMachSpeedTable().get("Bus1080-mach1").get(0).getValue()-sm.getMachSpeedTable().get("Bus1080-mach1").get(10).getValue()<1.0E-4);
			
		}
		
		//@Test
		public void test_ACTIVSg2000_Dstab_compositeLoadModel() throws InterpssException{
			IpssCorePlugin.init();
			IpssLogger.getLogger().setLevel(Level.WARNING);
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					
					//NOTE: the original "IEEE300Bus_modified_noHVDC.raw" case could result in oscillation for some faults (at buses 4 or 182) due to capacitor compensation of long distance lines between 120 and 118
					
					"testData/adpter/psse/v30/IEEE300/IEEE300Bus_modified_noHVDC_v2.raw",
					//"testData/adpter/psse/v30/IEEE300/IEEE300_dyn_v2.dyr"
					"testData/adpter/psse/v30/IEEE300/IEEE300_dyn_cmld_zone1.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			//System.out.println(parser.toXmlDoc());
            
			
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    BaseDStabNetwork<?, ?> dsNet =simuCtx.getDStabilityNet();
		    
		    //dsNet.setBypassDataCheck(true);
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			
			aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

			aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
			aclfAlgo.setTolerance(1.0E-6);
			assertTrue(aclfAlgo.loadflow());
			//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.002);
			dstabAlgo.setTotalSimuTimeSec(10.0);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus10030-mach1"));
			
			List<String> busIdList = new ArrayList<>();
			for(BaseDStabBus<?, ?> b: dsNet.getBusList()) {
				busIdList.add(b.getId());
				
				if(b.getZone().getNumber()!=1)
				     b.setInfoOnlyDynModel(null);
			}
			
			
			
			

			StateMonitor sm = new StateMonitor();
			
			sm.addBusStdMonitor(busIdList.toArray(new String[] {}));
			
			//sm.addBusStdMonitor(new String[]{"Bus10000","Bus10001","Bus10015","Bus10016","Bus10028"});
			//sm.addBusStdMonitor(new String[]{"Bus1_loadBus","Bus33_loadBus","Bus562_loadBus"});
			
			sm.addGeneratorStdMonitor(new String[]{"Bus10003-mach1","Bus10005-mach1","Bus10008-mach1","Bus10009-mach1"});
			
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus1_loadBus");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus33_loadBus");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1_A@Bus1_loadBus");
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(10);
			
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus39-mach1"));
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
			String faultBusId = "Bus7"; //3, 5, 12 182  157 7 167  135
			
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(faultBusId,dsNet,SimpleFaultCode.GROUND_3P,new Complex(0,0),null,1.0d,0.1),"3phaseFault@"+faultBusId);
			

			if (dstabAlgo.initialization()) {
				double t1 = System.currentTimeMillis();
				System.out.println("time1="+t1);
				System.out.println("Running DStab simulation ...");
				System.out.println(dsNet.getMachineInitCondition());
				dstabAlgo.performSimulation();
				double t2 = System.currentTimeMillis();
				System.out.println("used time="+(t2-t1)/1000.0);

			}
			/*
			System.out.println(sm.toCSVString(sm.getMachSpeedTable()));
			System.out.println(sm.toCSVString(sm.getMachAngleTable()));
			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			System.out.println(sm.toCSVString(sm.getMotorPTable()));
			System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
			*/
			
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm");
			 
			LocalDateTime today = LocalDateTime.now();
			 
			//System.out.println(formatter.format(today));
			
			FileUtil.writeText2File(String.format("C:\\Users\\huan289\\Qiuhua\\FY2019_HADREC\\Test data\\IEEE300\\BusVolts_Fault@%s_%s.csv", faultBusId,formatter.format(today)),sm.toCSVString(sm.getBusVoltTable()));
			
			assertTrue(sm.getMachSpeedTable().get("Bus10003-mach1").get(0).getValue()-sm.getMachSpeedTable().get("Bus10003-mach1").get(10).getValue()<1.0E-4);
			assertTrue(sm.getMotorPTable().get("IndMotor_1_A@Bus1_loadBus").get(0).getValue()-sm.getMotorPTable().get("IndMotor_1_A@Bus1_loadBus").get(10).getValue()<1.0E-4);
			assertTrue(sm.getAcMotorPTable().get("ACMotor_1@Bus1_loadBus").get(0).getValue()-sm.getAcMotorPTable().get("ACMotor_1@Bus1_loadBus").get(10).getValue()<1.0E-4);
			

		}
		
		
		

}
