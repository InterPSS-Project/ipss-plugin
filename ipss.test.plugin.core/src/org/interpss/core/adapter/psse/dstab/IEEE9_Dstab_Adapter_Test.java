package org.interpss.core.adapter.psse.dstab;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.dynLoad.LD1PAC;
import com.interpss.dstab.mach.SalientPoleMachine;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.spring.CoreCommonSpringFactory;

public class IEEE9_Dstab_Adapter_Test {
	//@Test
	public void test_IEEE9Bus_Dstab(){
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
	    
	    /*
	     * check generator data
	     * --------------------------------------
	     * 
	     * <dstabBus scCode="Contributing" id="Bus1" areaNumber="1" zoneNumber="1" number="1" offLine="false" name="BUS-1       ">
                <ownerList id="1">
                    <ownership unit="PU" value="1.0"/>
                </ownerList>
                <baseVoltage unit="KV" value="16.5"/>
                <voltage unit="PU" value="1.04"/>
                <angle unit="DEG" value="0.0"/>
                <genData>
                    <dstabEquivGen code="Swing">
                        <power unit="MVA" re="0.0" im="0.0"/>
                        <desiredVoltage unit="PU" value="1.04"/>
                        <desiredAngle unit="DEG" value="0.0"/>
                        <potiveZ unit="PU" re="0.0" im="0.04"/>
                        <negativeZ unit="PU" re="0.0" im="0.04"/>
                        <zeroZ unit="PU" re="0.0" im="0.04"/>
                    </dstabEquivGen>
                    <dstabContributeGen id="1" offLine="false" name="Gen:1(1)">
                        <desc>PSSE Generator 1 at Bus 1</desc>
                        <power unit="MVA" re="71.64" im="27.05"/>
                        <desiredVoltage unit="PU" value="1.04"/>
                        <qLimit unit="MVAR" max="9999.0" min="-9999.0"/>
                        <pLimit unit="MW" max="9999.0" min="-9999.0"/>
                        <mvaBase unit="MVA" value="100.0"/>
                        <sourceZ unit="PU" re="0.0" im="0.04"/>
                        <mvarVControlParticipateFactor>1.0</mvarVControlParticipateFactor>
                        <potiveZ unit="PU" re="0.0" im="0.04"/>
                        <negativeZ unit="PU" re="0.0" im="0.04"/>
                        <zeroZ unit="PU" re="0.0" im="0.04"/>
                        <eq11MachModel>
                            <desc>GENSAL</desc>
                            <H>23.64</H>
                            <D>0.0</D>
                            <xl>0.0336</xl>
                            <ra>0.0</ra>
                            <xd>0.146</xd>
                            <xq>0.0969</xq>
                            <xd1>0.0608</xd1>
                            <Td01 unit="Sec" value="8.96"/>
                            <seFmt1>
								<se100>0.0</se100>
								<se120>0.0</se120>
								<sliner>1.0</sliner>
                            </seFmt1>
                            <xq11>0.04</xq11>
                            <Tq011 unit="Sec" value="0.06"/>
                            <xd11>0.04</xd11>
                            <Td011 unit="Sec" value="0.04"/>
                        </eq11MachModel>
                    </dstabContributeGen>
                </genData>
                <loadData>
                    <dstabEquivLoad/>
                </loadData>
                <shuntYData>
                    <equivY im="0.0"/>
                </shuntYData>
            </dstabBus>
	     */
	    
	    DStabBus bus1 = dsNet.getDStabBus("Bus1");
	    assertTrue(bus1.getContributeGenList().size()==1);
	    
	    assertTrue(bus1.getContributeGenList().get(0) instanceof DStabGen);
	    DStabGen gen1 =(DStabGen) bus1.getContributeGenList().get(0);
	    assertTrue(bus1.getGenCode()==AclfGenCode.SWING);
	    
	    /*
	     * <power unit="MVA" re="71.64" im="27.05"/>
           <desiredVoltage unit="PU" value="1.04"/>
	     */
	    assertTrue(gen1.getGen().getReal()==0.7164);
	    assertTrue(gen1.getGen().getImaginary()==0.2705);
	    assertTrue(gen1.getDesiredVoltMag()==1.04);
	    
	    /*
	     * <mvaBase unit="MVA" value="100.0"/>
           <sourceZ unit="PU" re="0.0" im="0.04"/>
	     */
	    assertTrue(gen1.getMvaBase()==100);
	    assertTrue(NumericUtil.equals(gen1.getSourceZ(), new Complex(0, 0.04),1.0E-4));
	    assertTrue(NumericUtil.equals(gen1.getPosGenZ(), new Complex(0, 0.04),1.0E-4));
	    
	    /*
	     * <eq11MachModel>
                            <desc>GENSAL</desc>
                            <H>23.64</H>
                            <D>0.0</D>
                            <xl>0.0336</xl>
                            <ra>0.0</ra>
                            <xd>0.146</xd>
                            <xq>0.0969</xq>
                            <xd1>0.0608</xd1>
                            <Td01 unit="Sec" value="8.96"/>
                            <seFmt1>
								<se100>0.0</se100>
								<se120>0.0</se120>
								<sliner>1.0</sliner>
                            </seFmt1>
                            <xq11>0.04</xq11>
                            <Tq011 unit="Sec" value="0.06"/>
                            <xd11>0.04</xd11>
                            <Td011 unit="Sec" value="0.04"/>
                        </eq11MachModel>
	     */
	    SalientPoleMachine mach = (SalientPoleMachine) gen1.getMach();
	    assertTrue(mach.getH()==23.64);
	    assertTrue(mach.getXl()==0.0336);
	    assertTrue(mach.getTd01()==8.96);
	    assertTrue(mach.getTd011()==0.04);
	    assertTrue(mach.getTq011()==0.06);
	    
	    /*
	     * check sequence network data
	     */
	}
	
	@Test
	public void test_IEEE9Bus_Dstab_ACMotor(){
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_Load_ACMotor.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
	    
	    LD1PAC acMotor = (LD1PAC) dsNet.getBus("Bus5").getDynLoadModelList().get(0);
	    
	    System.out.println(acMotor.toString());
	    
	    /*
	     * id: 1
     number: 0
     name: 
     desc: 
     status: true (scripts: null) (deviceType: DynamicMachine) (loadPercent: 100.0, MVABase: 0.0, loadPQ: (0.0, 0.0), initLoadPQ: (0.0, 0.0), equivY: null, compensateCurInj: null, compensateShuntY: null, currInj2Net: (0.0, 0.0)) (stage: 0, loadFactor: 1.0, p: 0.0, q: 0.0, p0: 0.0, q0: 0.0, pac: 0.0, qac: 0.0, powerFactor: 0.98, vstall: 0.65, rstall: 0.124, xstall: 0.114, tstall: 0.033, lFadj: 0.3, kp1: 0.0, np1: 1.0, kq1: 6.0, nq1: 2.0, kp2: 12.0, np2: 3.2, kq2: 11.0, nq2: 2.5, vbrk: 0.86, frst: 0.5, vrst: 0.8, trst: 0.4, cmpKpf: 1.0, cmpKqf: -3.3, fuvr: 0.0, uvtr1: 0.5, ttr1: 0.2, uvtr2: 0.9, ttr2: 5.0, vc1off: 0.5, vc2off: 0.4, vc1on: 0.6, vc2on: 0.5, tth: 10.0, th1t: 1.3, th2t: 4.3, uVRelayTimer1: 0.0, uVRelayTimer2: 0.0, acStallTimer: 0.033, acRestartTimer: 0.4)

	     */
	    assertTrue(acMotor.getLoadPercent()==100.0);
	    assertTrue(acMotor.getVstall()==0.65);
	    assertTrue(acMotor.getLFadj()==0.3);
	  
	}
}
