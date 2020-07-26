package org.interpss.threePhase.test.dataparser;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map.Entry;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.LineConfiguration;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.junit.Test;

import com.interpss.core.abc.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformer;
import com.interpss.core.net.Bus;

public class TestOpenDSSDataParser {
	
	//@Test
	public void test_LineCodeParser(){
		OpenDSSDataParser parser = new OpenDSSDataParser();
		
		parser.getLineCodeParser().parseLineCodeFile("testData\\feeder\\IEEE123\\IEEELineCodes.DSS");
		
		System.out.print("line code table:\n" );
		
		for(Entry<String, LineConfiguration> configSet: parser.getLineConfigTable().entrySet()){
			System.out.println(configSet.getKey()+": " +configSet.getValue().toString());
		}
		
		/*
		 * New linecode.1 nphases=3 BaseFreq=60
			~ rmatrix = [0.086666667 | 0.029545455 0.088371212 | 0.02907197 0.029924242 0.087405303]
			~ xmatrix = [0.204166667 | 0.095018939 0.198522727 | 0.072897727 0.080227273 0.201723485]
			~ cmatrix = [2.851710072 | -0.920293787  3.004631862 | -0.350755566  -0.585011253 2.71134756]
		 */
		LineConfiguration linecode_1 = parser.getLineConfigTable().get("1");
		
		assertTrue(linecode_1.getNphases()==3);
		Complex3x3 zabc1 = new Complex3x3(new Complex[][]{
			{new Complex(0.086666667, 0.204166667), new Complex(0.029545455, 0.095018939), new Complex(0.02907197, 0.072897727)},
			{new Complex(0.029545455, 0.095018939), new Complex(0.088371212, 0.198522727), new Complex(0.029924242, 0.080227273)},
			{new Complex(0.02907197, 0.072897727),  new Complex(0.029924242, 0.080227273) , new Complex(0.087405303, 0.201723485)}});
		
		Complex3x3 yabc1 = new Complex3x3(new Complex[][]{
			{new Complex(0.0, 2.851710072), new Complex(0.0, -0.920293787), new Complex(0.0, -0.350755566)},
			{new Complex(0.0, -0.920293787), new Complex(0.0, 3.004631862), new Complex(0.0, -0.585011253)},
			{new Complex(0.0, -0.350755566),  new Complex(0.0, -0.585011253) , new Complex(0.0, 2.71134756)}});
			
		assertTrue(linecode_1.getZ3x3Matrix().subtract(zabc1).absMax()<1.0E-6);
		assertTrue(linecode_1.getShuntY3x3Matrix().subtract(yabc1).absMax()<1.0E-6);
		
		
		/*
		 * New linecode.7 nphases=2 BaseFreq=60
			~ rmatrix = [0.086666667 | 0.02907197  0.087405303]
			~ xmatrix = [0.204166667 | 0.072897727  0.201723485]
			~ cmatrix = [2.569829596 | -0.52995137  2.597460011]
		 */
		
       LineConfiguration linecode_7 = parser.getLineConfigTable().get("7");
		
		assertTrue(linecode_7.getNphases()==2);
		Complex3x3 zabc7 = new Complex3x3(new Complex[][]{
			{new Complex(0.086666667, 0.204166667), new Complex(0.02907197, 0.072897727), new Complex(0.)},
			{new Complex(0.02907197, 0.072897727), new Complex(0.087405303, 0.201723485), new Complex(0.)},
			{new Complex(0.0),  new Complex(0.0) , new Complex(0.)}});
			
		assertTrue(linecode_7.getZ3x3Matrix().subtract(zabc7).absMax()<1.0E-6);
		
		/*
		 * New linecode.9 nphases=1 BaseFreq=60
			~ rmatrix = [0.251742424]
			~ xmatrix = [0.255208333]
			~ cmatrix = [2.270366128]
		 */
		
        LineConfiguration linecode_9 = parser.getLineConfigTable().get("9");
		
		assertTrue(linecode_9.getNphases()==1);
		Complex3x3 zabc9 = new Complex3x3(new Complex[][]{
			{new Complex(0.251742424, 0.255208333), new Complex(0.0), new Complex(0.)},
			{new Complex(0.0), new Complex(0.0), new Complex(0.)},
			{new Complex(0.0),  new Complex(0.0) , new Complex(0.)}});
		
		Complex3x3 yabc9 = new Complex3x3(new Complex(0,2.270366128), new Complex(0.), new Complex(0.));
			
		assertTrue(linecode_9.getZ3x3Matrix().subtract(zabc9).absMax()<1.0E-6);
		
		assertTrue(linecode_9.getShuntY3x3Matrix().subtract(yabc9).absMax()<1.0E-6);
		
		 LineConfiguration linecode_11 = parser.getLineConfigTable().get("11");
		 
		 System.out.println("linecode 11 zabc :\n "+linecode_11.getZ3x3Matrix().toString());
		 
		 Complex3x3 zabc11 = new Complex3x3(new Complex(0.251742424,0.255208333), new Complex(0.), new Complex(0.));
		 
		 assertTrue(linecode_11.getZ3x3Matrix().subtract(zabc11).absMax()<1.0E-6);
		 
	}
	
	@Test
	public void testDataParser(){
		
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		
		OpenDSSDataParser parser = new OpenDSSDataParser();
		
		//parser.setDebugMode(True);
		
		parser.parseFeederData("testData\\feeder\\IEEE123","IEEE123Master.dss");
		
		
		DStabNetwork3Phase distNet = parser.getDistNetwork();
		
		
		
		//System.out.println(parser.getDistNetwork().net2String());
		
		String netStrFileName = "testData\\feeder\\IEEE123\\ieee123_netString.dat";
		try {
			Files.write(Paths.get(netStrFileName), parser.getDistNetwork().net2String().getBytes());
		} catch (IOException e) {
			
			e.printStackTrace();
		}
		
		
		System.out.println("total number of buses: "+distNet.getNoActiveBus());
//		assertTrue(distNet.getNoActiveBus()==123);
		
//		for(Bus b: distNet.getBusList()){
//			System.out.println(b.getId());
//		}
		
		//1. test lineParser
		  //1.a  Zabc, Yabc for 3phase line
		
		/*New Line.L10    Phases=3 Bus1=8.1.2.3    Bus2=13.1.2.3   LineCode=1    Length=0.3*/
		//NOTE: all are converted to lowercase
		 DStab3PBranch line3phase = parser.getBranchByName("l10");
		 
		 LineConfiguration lc1 = parser.getLineConfigTable().get("1");
		 assertTrue(line3phase.getZabc().subtract(lc1.getZ3x3Matrix().multiply(0.3)).absMax()<1.0E-6);
		   
		
		  //1.b  Zabc, Yabc for 1-phase and 2-phase lines
		 
		   //New Line.L27    Phases=2 Bus1=26.1.3     Bus2=27.1.3     LineCode=7    Length=0.275
		  DStab3PBranch line2phase = parser.getBranchByName("l27");
		  System.out.println("L27 Zabc =" +line2phase.getZabc().toString());
		  double length = 0.275;
		  
		  LineConfiguration lc7 = parser.getLineConfigTable().get("7");
		  assertTrue(line2phase.getZabc().aa.subtract(lc7.getZ3x3Matrix().aa.multiply(length)).abs()<1.0E-9);
		  assertTrue(line2phase.getZabc().ac.subtract(lc7.getZ3x3Matrix().ac.multiply(length)).abs()<1.0E-9);
		  assertTrue(line2phase.getZabc().cc.subtract(lc7.getZ3x3Matrix().cc.multiply(length)).abs()<1.0E-9);
		  assertTrue(line2phase.getZabc().ab.abs()<1.0E-9);
		  assertTrue(line2phase.getZabc().ba.abs()<1.0E-9);
		  
		  //New Line.L28    Phases=1 Bus1=26.3       Bus2=31.3       LineCode=11   Length=0.225
		  DStab3PBranch line1phase = parser.getBranchByName("l28");
		  LineConfiguration lc11 = parser.getLineConfigTable().get("11");
		  length = 0.225;
		  
		  assertTrue(line1phase.getZabc().cc.subtract(lc11.getZ3x3Matrix().cc.multiply(length)).abs()<1.0E-9);
		  assertTrue(line1phase.getZabc().bb.abs()<1.0E-9);
		  assertTrue(line1phase.getZabc().aa.abs()<1.0E-9);
		  assertTrue(line1phase.getZabc().ab.abs()<1.0E-9);
		  assertTrue(line1phase.getZabc().ac.abs()<1.0E-9);
		  
		//2. test transformer parser
		  /*
		   * New Transformer.XFM1  Phases=3   Windings=2 Xhl=2.72
			~ wdg=1 bus=61s       conn=Delta kv=4.16    kva=150    %r=0.635
			~ wdg=2 bus=610       conn=Delta kv=0.48    kva=150    %r=0.635
		   */
		  
		  DStab3PBranch xfr1 = parser.getBranchByName("xfm1");
		  assertTrue(xfr1.getZ().subtract(new Complex(0,2.72)).abs()<1.0E-9);
		  assertTrue(xfr1.getFromBus().getId().equals("61s"));
		  assertTrue(xfr1.getToBus().getId().equals("610"));
		  
		  //TODO in the future, could add the nominalKV info to the transformer
		  assertTrue(Math.abs(xfr1.getFromTurnRatio()-4160)<1.0E-6);
		  assertTrue(Math.abs(xfr1.getToTurnRatio()-480)<1.0E-6);
		  
		  assertTrue(Math.abs(xfr1.getXfrRatedKVA()-150)<1.0E-6);
		  
		  AcscXformer xfr10 = acscXfrAptr.apply(xfr1);
		  assertTrue(xfr10.getFromConnect()==XfrConnectCode.DELTA);
		  assertTrue(xfr10.getToConnect()==XfrConnectCode.DELTA);
		  
		  /*
		   * new transformer.reg4a phases=1          windings=2        buses=[160.1 160r.1]   conns=[wye wye]       kvs=[2.402 2.402] kvas=[2000 2000] XHL=.01 %LoadLoss=0.00001 ppm=0.0 
		   */
		  
		  DStab3PBranch xfr2 = parser.getBranchByName("reg4a");
		  assertTrue(xfr2.getZ().subtract(new Complex(0,0.01)).abs()<1.0E-9);
		  assertTrue(xfr2.getFromBus().getId().equals("160"));
		  assertTrue(xfr2.getToBus().getId().equals("160r"));
		  
		  //TODO in the future, could add the nominalKV info to the transformer
		  assertTrue(Math.abs(xfr2.getFromTurnRatio()-2402)<1.0E-6);
		  assertTrue(Math.abs(xfr2.getToTurnRatio()-2402)<1.0E-6);
		  
		  assertTrue(Math.abs(xfr2.getXfrRatedKVA()-2000)<1.0E-6);
		  
		  AcscXformer xfr20 = acscXfrAptr.apply(xfr2);
		  assertTrue(xfr20.getFromConnect()==XfrConnectCode.WYE_SOLID_GROUNDED);
		  assertTrue(xfr20.getToConnect()==XfrConnectCode.WYE_SOLID_GROUNDED);
		  
		//3. test load parser
		   
		   /* 1-phase wye
		    * New Load.S1a   Bus1=1.1    Phases=1 Conn=Wye   Model=1 kV=2.4   kW=40.0  kvar=20.0
		    */
		  DStab3PBus bus1 = (DStab3PBus) distNet.getBus("1");
		  DStab1PLoad ld_s1a= (DStab1PLoad) bus1.getContributeLoad("s1a");
		  assertTrue(ld_s1a.getLoadCP().subtract(new Complex(40,20)).abs()<1.0E-9);
		  assertTrue(ld_s1a.getLoadConnectionType()==LoadConnectionType.SINGLE_PHASE_WYE);
		  assertTrue(Math.abs(ld_s1a.getNominalKV()-2.4)<1.0E-9);
		  assertTrue(ld_s1a.getCode()==AclfLoadCode.CONST_P);
		  assertTrue(ld_s1a.getPhaseCode()==PhaseCode.A);
		  
		  
		  /*
		   * 1-phase delta
		   * New Load.S35a  Bus1=35.1.2 Phases=1 Conn=Delta Model=1 kV=4.160 kW=40.0  kvar=20.0
		   */
		  DStab3PBus bus35 = (DStab3PBus) distNet.getBus("35");
		  DStab1PLoad ld_s35a= (DStab1PLoad) bus35.getContributeLoad("s35a");
		  assertTrue(ld_s35a.getLoadCP().subtract(new Complex(40,20)).abs()<1.0E-9);
		  assertTrue(ld_s35a.getLoadConnectionType()==LoadConnectionType.SINGLE_PHASE_DELTA);
		  assertTrue(Math.abs(ld_s35a.getNominalKV()-4.160)<1.0E-9);
		  assertTrue(ld_s35a.getCode()==AclfLoadCode.CONST_P);
		  assertTrue(ld_s35a.getPhaseCode()==PhaseCode.AB);
		  
		  /*
		   * New Load.S48   Bus1=48     Phases=3 Conn=Wye   Model=2 kV=4.160 kW=210.0 kVAR=150.0
		   */
		  DStab3PBus bus48 = (DStab3PBus) distNet.getBus("48");
		  DStab3PLoad ld_s48= (DStab3PLoad) bus48.getThreePhaseLoadList().get(0); // only one load
		  System.out.println("ld_s48 = "+ld_s48.getInit3PhaseLoad().toString());
		  assertTrue(ld_s48.getInit3PhaseLoad().a_0.subtract(new Complex(70.0,50.0)).abs()<1.0E-9);
		  assertTrue(ld_s48.getInit3PhaseLoad().b_1.subtract(new Complex(70,50)).abs()<1.0E-9);
		  assertTrue(ld_s48.getInit3PhaseLoad().c_2.subtract(new Complex(70,50)).abs()<1.0E-9);
		  assertTrue(ld_s48.getLoadConnectionType()==LoadConnectionType.THREE_PHASE_WYE);
		  assertTrue(Math.abs(ld_s48.getNominalKV()-4.160)<1.0E-9);
		  assertTrue(ld_s48.getCode()==AclfLoadCode.CONST_Z);
		  assertTrue(ld_s48.getPhaseCode()==PhaseCode.ABC);
		  
		//4. test capacitor parser
		
	}
	
	//@Test
	public void testCalcBaseVoltage(){
		
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.parseFeederData("testData\\feeder\\IEEE123","IEEE123Master.dss");
		
		parser.calcVoltageBases();
		DStabNetwork3Phase distNet = parser.getDistNetwork();
		
		System.out.println("bus Id, baseVoltage");
		double vll4160 = 4160.0;
		double vll480 = 480.0;
		double sumDiff = 0.0;
		for(Bus b: distNet.getBusList()){
			System.out.println(b.getId()+","+b.getBaseVoltage());
			if(b.getId().equals("610"))
				sumDiff += b.getBaseVoltage()-vll480;
			else
				sumDiff += b.getBaseVoltage()-vll4160;
				
		}
		
		assertTrue(Math.abs(sumDiff)<1.0E-9);
	}
	
	@Test
	public void testConvertValuesToPU(){
		
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		
		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.parseFeederData("testData\\feeder\\IEEE123","IEEE123Master.dss");
		
		parser.calcVoltageBases();
		
		double mvaBase = 10.0;
		parser.convertActualValuesToPU(10.0);
		
		DStabNetwork3Phase distNet = parser.getDistNetwork();
		
		
		//1. test lineParser
		  //1.a  Zabc, Yabc for 3phase line
		
		/*New Line.L10    Phases=3 Bus1=8.1.2.3    Bus2=13.1.2.3   LineCode=1    Length=0.3*/
		//NOTE: all are converted to lowercase
		 DStab3PBranch line3phase = parser.getBranchByName("l10");
		 double length = 0.3;
		 double zbase = 4.160*4.160/mvaBase;
		 
		 LineConfiguration lc1 = parser.getLineConfigTable().get("1");
		 assertTrue(line3phase.getZabc().subtract(lc1.getZ3x3Matrix().multiply(length).multiply(1.0/zbase)).absMax()<1.0E-9);
		   
		
		  //1.b  Zabc, Yabc for 1-phase and 2-phase lines
		 
		   //New Line.L27    Phases=2 Bus1=26.1.3     Bus2=27.1.3     LineCode=7    Length=0.275
		  DStab3PBranch line2phase = parser.getBranchByName("l27");
		  System.out.println("L27 Zabc =" +line2phase.getZabc().toString());
		  length = 0.275;
		  
		  LineConfiguration lc7 = parser.getLineConfigTable().get("7");
		  assertTrue(line2phase.getZabc().aa.subtract(lc7.getZ3x3Matrix().aa.multiply(length).multiply(1.0/zbase)).abs()<1.0E-9);
		  assertTrue(line2phase.getZabc().ac.subtract(lc7.getZ3x3Matrix().ac.multiply(length).multiply(1.0/zbase)).abs()<1.0E-9);
		  assertTrue(line2phase.getZabc().cc.subtract(lc7.getZ3x3Matrix().cc.multiply(length).multiply(1.0/zbase)).abs()<1.0E-9);
		  assertTrue(line2phase.getZabc().ab.abs()<1.0E-9);
		  assertTrue(line2phase.getZabc().ba.abs()<1.0E-9);
		  
		  //New Line.L28    Phases=1 Bus1=26.3       Bus2=31.3       LineCode=11   Length=0.225
		  DStab3PBranch line1phase = parser.getBranchByName("l28");
		  LineConfiguration lc11 = parser.getLineConfigTable().get("11");
		  length = 0.225;
		  
		  assertTrue(line1phase.getZabc().cc.subtract(lc11.getZ3x3Matrix().cc.multiply(length).multiply(1.0/zbase)).abs()<1.0E-9);
		  assertTrue(line1phase.getZabc().bb.abs()<1.0E-9);
		  assertTrue(line1phase.getZabc().aa.abs()<1.0E-9);
		  assertTrue(line1phase.getZabc().ab.abs()<1.0E-9);
		  assertTrue(line1phase.getZabc().ac.abs()<1.0E-9);
		  
		//2. test transformer parser
		  /*
		   * New Transformer.XFM1  Phases=3   Windings=2 Xhl=2.72
			~ wdg=1 bus=61s       conn=Delta kv=4.16    kva=150    %r=0.635
			~ wdg=2 bus=610       conn=Delta kv=0.48    kva=150    %r=0.635
		   */
		  
		  DStab3PBranch xfr1 = parser.getBranchByName("xfm1");
		  assertTrue(xfr1.getZ().subtract(new Complex(0,2.72).multiply(1.0/zbase)).abs()<1.0E-9);
		  assertTrue(xfr1.getFromBus().getId().equals("61s"));
		  assertTrue(xfr1.getToBus().getId().equals("610"));
		  
		  //TODO in the future, could add the nominalKV info to the transformer
		  assertTrue(Math.abs(xfr1.getFromTurnRatio()/xfr1.getToTurnRatio()-1.0)<1.0E-6);

		  
		  assertTrue(Math.abs(xfr1.getXfrRatedKVA()-150)<1.0E-6);
		  
		  AcscXformer xfr10 = acscXfrAptr.apply(xfr1);
		  assertTrue(xfr10.getFromConnect()==XfrConnectCode.DELTA);
		  assertTrue(xfr10.getToConnect()==XfrConnectCode.DELTA);
		  
		  /*
		   * new transformer.reg4a phases=1          windings=2        buses=[160.1 160r.1]   conns=[wye wye]       kvs=[2.402 2.402] kvas=[2000 2000] XHL=.01 %LoadLoss=0.00001 ppm=0.0 
		   */
		  
		  DStab3PBranch xfr2 = parser.getBranchByName("reg4a");
		  assertTrue(xfr2.getZ().subtract(new Complex(0,0.01).multiply(1.0/zbase)).abs()<1.0E-9);
		  assertTrue(xfr2.getFromBus().getId().equals("160"));
		  assertTrue(xfr2.getToBus().getId().equals("160r"));
		  
		  double basevolt = 4160/Math.sqrt(3);
		  
		  //TODO in the future, could add the nominalKV info to the transformer
		  System.out.println("reg4a tap = "+xfr2.getFromTurnRatio());
		  assertTrue(Math.abs(xfr2.getFromTurnRatio()-2402.0/basevolt)<1.0E-6);
		  assertTrue(Math.abs(xfr2.getToTurnRatio()-2402.0/basevolt)<1.0E-6);
		  
		  assertTrue(Math.abs(xfr2.getXfrRatedKVA()-2000)<1.0E-6);
		  
		  AcscXformer xfr20 = acscXfrAptr.apply(xfr2);
		  assertTrue(xfr20.getFromConnect()==XfrConnectCode.WYE_SOLID_GROUNDED);
		  assertTrue(xfr20.getToConnect()==XfrConnectCode.WYE_SOLID_GROUNDED);
		  
		//3. test load parser
		   
		   /* 1-phase wye
		    * New Load.S1a   Bus1=1.1    Phases=1 Conn=Wye   Model=1 kV=2.4   kW=40.0  kvar=20.0
		    */
		  double baseKVA1P = distNet.getBaseKva()/3.0;
		  DStab3PBus bus1 = (DStab3PBus) distNet.getBus("1");
		  DStab1PLoad ld_s1a= (DStab1PLoad) bus1.getContributeLoad("s1a");
		  assertTrue(ld_s1a.getLoadCP().subtract(new Complex(40,20).divide(baseKVA1P)).abs()<1.0E-9);
		  assertTrue(ld_s1a.getLoadConnectionType()==LoadConnectionType.SINGLE_PHASE_WYE);
		  assertTrue(Math.abs(ld_s1a.getNominalKV()-2.4)<1.0E-9);
		  assertTrue(ld_s1a.getCode()==AclfLoadCode.CONST_P);
		  assertTrue(ld_s1a.getPhaseCode()==PhaseCode.A);
		  
		  
		  /*
		   * 1-phase delta
		   * New Load.S35a  Bus1=35.1.2 Phases=1 Conn=Delta Model=1 kV=4.160 kW=40.0  kvar=20.0
		   */
		  DStab3PBus bus35 = (DStab3PBus) distNet.getBus("35");
		  DStab1PLoad ld_s35a= (DStab1PLoad) bus35.getContributeLoad("s35a");
		  assertTrue(ld_s35a.getLoadCP().subtract(new Complex(40,20).divide(baseKVA1P)).abs()<1.0E-9);
		  assertTrue(ld_s35a.getLoadConnectionType()==LoadConnectionType.SINGLE_PHASE_DELTA);
		  assertTrue(Math.abs(ld_s35a.getNominalKV()-4.160)<1.0E-9);
		  assertTrue(ld_s35a.getCode()==AclfLoadCode.CONST_P);
		  assertTrue(ld_s35a.getPhaseCode()==PhaseCode.AB);
		  
		  /*
		   * New Load.S48   Bus1=48     Phases=3 Conn=Wye   Model=2 kV=4.160 kW=210.0 kVAR=150.0
		   */
		  DStab3PBus bus48 = (DStab3PBus) distNet.getBus("48");
		  DStab3PLoad ld_s48= (DStab3PLoad) bus48.getThreePhaseLoadList().get(0); // only one load
		  System.out.println("ld_s48 = "+ld_s48.getInit3PhaseLoad().toString());
		  assertTrue(ld_s48.getInit3PhaseLoad().a_0.subtract(new Complex(70.0,50.0).divide(baseKVA1P)).abs()<1.0E-9);
		  assertTrue(ld_s48.getInit3PhaseLoad().b_1.subtract(new Complex(70,50).divide(baseKVA1P)).abs()<1.0E-9);
		  assertTrue(ld_s48.getInit3PhaseLoad().c_2.subtract(new Complex(70,50).divide(baseKVA1P)).abs()<1.0E-9);
		  assertTrue(ld_s48.getLoadConnectionType()==LoadConnectionType.THREE_PHASE_WYE);
		  assertTrue(Math.abs(ld_s48.getNominalKV()-4.160)<1.0E-9);
		  assertTrue(ld_s48.getCode()==AclfLoadCode.CONST_Z);
		  assertTrue(ld_s48.getPhaseCode()==PhaseCode.ABC);
		  
		//4. test capacitor parser
	}
	

}
