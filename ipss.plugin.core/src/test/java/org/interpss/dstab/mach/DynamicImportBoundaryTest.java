package org.interpss.dstab.mach;
import com.interpss.dstab.mach.model.*;
import com.interpss.dstab.mach.ieeevc.*;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import com.interpss.dstab.renewable.Dera1Model;
import com.interpss.dstab.svc.Csvgn5Model;
import com.interpss.dstab.svc.Svsmo1t2Model;
import com.interpss.dstab.svc.Svsmo3t2Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;

/** Public-fixture boundary regressions, distinct from private inventory and trajectory qualification. */
class DynamicImportBoundaryTest {
    private static final Path DATA=Path.of("..","ipss.test.plugin.core","testData");
    private static final Path SMIB=DATA.resolve("adpter/psse/v33/SMIB");
    @TempDir Path temp;

    private com.interpss.simu.SimuContext load(Path raw,String input) throws Exception {
        IpssCorePlugin.init();
        Path dyr=Files.createTempFile(temp,"boundary-",".dyr");
        Files.writeString(dyr,input);
        return new PSSEMultiFileLoader().loadDStab(raw.toString(),dyr.toString());
    }

    private String field(String text,String model,int index,String value) {
        return text.lines().map(line->{
            if(!line.contains("'"+model+"'"))return line;
            String[] fields=line.trim().split("\\s+");fields[index]=value;
            return String.join(" ",fields);
        }).collect(java.util.stream.Collectors.joining("\n"));
    }

    private DStabGen gen(com.interpss.simu.SimuContext context) {
        return (DStabGen)context.getDStabilityNet().getDStabBus("Bus1").getContributeGen("1");
    }

    @Test void realConstantSelectorsAcceptDecimalAndExponentSpellings() throws Exception {
        String rex=Files.readString(SMIB.resolve("SMIB_v33_genrou_rexsys.dyr"));
        rex=field(field(rex,"REXSYS",18,"1.0000"),"REXSYS",33,"0E0");
        var context=load(SMIB.resolve("SMIB_v33.raw"),rex);
        var exc=(com.interpss.dstab.controller.exc.rexsys.RexsysExciter)gen(context).getMach().getExciter();
        assertNotNull(exc);assertEquals(1,exc.getData().getFbf());assertEquals(0,exc.getData().getFlimf());
        String scrx=Files.readString(SMIB.resolve("SMIB_v33_genrou_scrx.dyr"));
        context=load(SMIB.resolve("SMIB_v33.raw"),field(scrx,"SCRX",9,"0.0000"));
        var staticExc=(com.interpss.dstab.controller.exc.scrx.ScrxExciter)gen(context).getMach().getExciter();
        assertNotNull(staticExc);assertEquals(0,staticExc.getData().getCswitch());
    }

    @Test void fractionalAndOutOfRangeRealSelectorsAreNotTruncated() throws Exception {
        String rex=Files.readString(SMIB.resolve("SMIB_v33_genrou_rexsys.dyr"));
        for(String value:new String[]{"1.25","2147483648","NaN","Infinity"}) {
            var context=load(SMIB.resolve("SMIB_v33.raw"),field(rex,"REXSYS",18,value));
            assertNull(gen(context).getMach().getExciter(),value);
        }
    }

    @Test void zeroFrequencyGainsRemainFiniteAndDoNotCreateFrequencyPowerResponse() throws Exception {
        String text=Files.readString(SMIB.resolve("SMIB_v33_dera1.dyr"));
        text=field(field(text,"DERA1",17,"0"),"DERA1",18,"0");
        var context=load(SMIB.resolve("SMIB_v33_wt3g2.raw"),text);
        var model=assertInstanceOf(Dera1Model.class,gen(context).getDynamicGenDevice());
        assertEquals(0,model.getData().ddn());assertEquals(0,model.getData().dup());
        model.initializeWithSignals(1,.5,.1,1);
        for(int i=0;i<20;i++) {
            assertTrue(model.nextStepWithSignals(.0005,DynamicSimuMethod.MODIFIED_EULER,0,1,.5,.1,.99));
            assertTrue(model.nextStepWithSignals(.0005,DynamicSimuMethod.MODIFIED_EULER,1,1,.5,.1,.99));
        }
        assertEquals(.5,model.getIpcmd(1,.5,.1,.99),1e-10);
        for(double state:model.getStateSnapshot())assertTrue(Double.isFinite(state));
    }

    @Test void csvgnZeroRemoteBusSelectsLocalSensingWithoutRewritingTheIcon() throws Exception {
        String text=Files.readString(SMIB.resolve("SMIB_v33_csvgn5.dyr"));
        var context=load(SMIB.resolve("SMIB_v33_csvgn5.raw"),field(text,"CSVGN5",3,"0"));
        var model=assertInstanceOf(Csvgn5Model.class,gen(context).getDynamicGenDevice());
        assertSame(model.getDStabBus(),model.getRemoteBus());assertEquals(0,model.getData().remoteBusNumber());
        assertTrue(model.initStates(model.getDStabBus()));
    }

    @Test void switchedShuntZeroRemoteBusSelectsTheHost() throws Exception {
        Path root=DATA.resolve("psse/v35");
        String text=Files.readString(root.resolve("PSSE_5Bus_svsmo1t2_synthetic.dyr"));
        var context=load(root.resolve("PSSE_5Bus_svsmo1t2_synthetic.raw"),field(text,"SVSMO1T2",2,"0"));
        var bus=context.getDStabilityNet().getDStabBus("Bus4");
        var model=bus.getDynamicBusDeviceList().stream().filter(Svsmo1t2Model.class::isInstance)
                .map(Svsmo1t2Model.class::cast).findFirst().orElseThrow();
        assertSame(bus,model.getRemoteBus());assertEquals(0,model.getData().remoteBusNumber());
    }

    @Test void zeroInnerRatioDoesNotRejectAnActiveStatcomDeadband() throws Exception {
        Path root=DATA.resolve("psse/v33/SVSMO3T2");
        String text=Files.readString(root.resolve("smib.dyr")).replace("\n0 0 .1 .001", "\n.02 0 .1 .001");
        assertTrue(text.contains("\n.02 0 .1 .001"));
        var context=load(root.resolve("smib.raw"),text);
        var model=context.getDStabilityNet().getDStabBus("Bus3").getDynamicBusDeviceList().stream()
                .filter(Svsmo3t2Model.class::isInstance).map(Svsmo3t2Model.class::cast).findFirst().orElseThrow();
        assertEquals(.02,model.getData().deadband());assertEquals(0,model.getData().deadbandRatio());
        assertTrue(model.initStates(model.getDStabBus()));
    }

    @Test void equalInductionReactancesUseTheFiniteSingleCageBoundary() throws Exception {
        String text=Files.readString(SMIB.resolve("SMIB_v33_wt1g1.dyr"));
        text=field(field(text,"WT1G1",6,".21"),"WT1G1",7,".21");
        var context=load(SMIB.resolve("SMIB_v33_wt1g1.raw"),text);
        var machine=assertInstanceOf(Wt1g1Machine.class,gen(context).getMach());
        assertFalse(machine.getWt1g1Data().twoCage());
        assertEquals(.21,machine.getWt1g1Data().xp());assertEquals(.21,machine.getWt1g1Data().xpp());
        var algo=context.getDynSimuAlgorithm();assertTrue(algo.getAclfAlgorithm().loadflow());
        algo.setSimuStepSec(.0005);algo.setTotalSimuTimeSec(.01);
        algo.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        assertTrue(algo.initialization());assertTrue(algo.performSimulation());
        assertTrue(Double.isFinite(machine.getIgen().abs()));
    }

    @Test void negativeCompensationRetainsItsSignAndInitializes() throws Exception {
        String text=Files.readString(SMIB.resolve("SMIB_v33_genqec.dyr"));
        var context=load(SMIB.resolve("SMIB_v33.raw"),field(text,"GENQEC",19,"-.15"));
        var machine=assertInstanceOf(GenqecMachine.class,gen(context).getMach());
        assertEquals(-.15,machine.getGenqecData().kw());
        var bus=machine.getDStabBus();bus.initStates();assertTrue(machine.initStates(bus));
        assertTrue(Double.isFinite(machine.calculateIfd(com.interpss.dstab.mach.MachineIfdBase.MACHINE)));
    }

    @Test void zeroDurationRampIsDisabledOnBothSidesOfItsNominalEventTime() {
        var data=new Gewtgdu1Data(9999,5,30,.05,.05,30);
        var model=new Gewtgdu1Model(data);model.initialize(10);
        for(double time:new double[]{0,.049,.05,.051,.1,10000})assertEquals(0,model.rampAt(time),0);
        for(int i=0;i<20;i++){model.step(.01,0);model.step(.01,1);}
        assertEquals(10,model.getWindSpeed(),0);assertSame(data,model.getData());
        var positive=new Gewtgdu1Model(new Gewtgdu1Data(9999,5,30,.05,.1,30));
        assertEquals(15,positive.rampAt(.075),1e-12);assertEquals(30,positive.rampAt(.11),0);
    }

    @Test void areaLoadCharacteristicSkipsZeroPlaceholdersWithoutPartialAttachment() throws Exception {
        var context=load(SMIB.resolve("SMIB_v33.raw"),"1 'GENCLS' 1 3 0 /\n2 'GENCLS' 1 99999 0 /\n");
        var network=context.getDStabilityNet();
        var topology=new org.interpss.fadapter.builder.AclfNetworkBuilder(network);
        topology.addContributeLoad("Bus1","Z",true,org.apache.commons.math3.complex.Complex.ZERO,
                org.apache.commons.math3.complex.Complex.ZERO,org.apache.commons.math3.complex.Complex.ZERO,null,false);
        network.getDStabBus("Bus1").setLoadCode(com.interpss.core.aclf.AclfLoadCode.NON_LOAD);
        Path dyr=Files.createTempFile(temp,"area-",".dyr");
        Files.writeString(dyr,"1 'IEELAR' '*' 0 1 0 1 0 0 1 -1 2 1 0 2 1 0 /\n");
        var parser=new org.interpss.fadapter.psse.PSSEDStabDirectParser(
                new org.interpss.fadapter.builder.DStabNetworkBuilder(network)).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(1,network.getDStabBus("Bus2").getDynLoadModelList().size());
        assertNotNull(network.getDStabBus("Bus1").getContributeLoad("Z"));
        var loads=network.getDStabBus("Bus1").getDynLoadModelList();
        assertTrue(loads==null||loads.isEmpty());
    }
}
