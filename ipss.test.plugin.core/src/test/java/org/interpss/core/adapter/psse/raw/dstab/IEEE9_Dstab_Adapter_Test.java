package org.interpss.core.adapter.psse.raw.dstab;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.common.CoreCommonFactory;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.mach.SalientPoleMachine;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class IEEE9_Dstab_Adapter_Test {
	@Test
	public void test_IEEE9Bus_Dstab(){
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    BaseDStabNetwork dsNet =simuCtx.getDStabilityNet();
	    
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
	    
	    BaseDStabBus bus1 = dsNet.getDStabBus("Bus1");
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
}
