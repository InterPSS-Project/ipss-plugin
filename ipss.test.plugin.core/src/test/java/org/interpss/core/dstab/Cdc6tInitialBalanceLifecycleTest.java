package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.controller.runtime.InitialEndpointAction;
import com.interpss.dstab.mach.ieeevc.IeeeVcEConstMachine;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** Startup preparation must not absorb a subsequent physical initial load action. */
public class Cdc6tInitialBalanceLifecycleTest extends CorePluginTestSetup {
    @TempDir Path folder;

    private Cdc6tCoupledNetworkTest.Fixture load(boolean transfer)throws Exception {
        if(!transfer)return Cdc6tCoupledNetworkTest.load(folder);
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-long-power-20261102/captures.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive));
        try(var z=new ZipFile(archive.toFile())){
            var raw=new ArrayList<>(new String(z.getInputStream(z.getEntry("current/a/network.raw")).readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(z.getInputStream(z.getEntry("current/a/dynamic.dyr")),StandardCharsets.UTF_8),"reference/dynamic.dyr");
            return Cdc6tCoupledNetworkTest.load(folder,raw,Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow()));
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void physicalInitialLoadDoesNotRebaseShaftPower(boolean transfer)throws Exception {
        var f=load(transfer);var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var source=(IeeeVcEConstMachine)n.getMachine("Bus1-mach1");
        var load=new InitialLoad(source);load.setDStabBus(n.getBus("Bus7"));
        var recorder=new Cdc6tCoupledReferenceTest.Recorder(f);
        a.setSolver(new Cdc6tCoupledReferenceTest.Ordinary(recorder));a.setSimuOutputHandler(new StateMonitor());a.setSimuStepSec(.0005);
        assertTrue(a.initialization());assertEquals(1,load.actions);
        assertEquals(load.initialMechanical,source.getPm(),0,"Physical initial action cannot be hidden in shaft power");
        var emf=new Complex(source.getE()*Math.cos(source.getAngle()),source.getE()*Math.sin(source.getAngle()));
        var current=emf.subtract(source.getDStabBus().getVoltage()).divide(new Complex(source.getRa(),source.getXd1()));
        double electrical=emf.multiply(current.conjugate()).getReal();
        assertTrue(Math.abs(electrical-source.getPm())>1e-4,"Physical load action must retain acceleration above the network residual: "+electrical+" / "+source.getPm());
        assertEquals(load.initialAngle,source.getAngle(),0);assertEquals(load.initialEmf,source.getE(),0);
        assertFalse(load.acceptInitialEndpoint());assertEquals(load.initialMechanical,source.getPm(),0);
        assertTrue(a.solveDEqnStep(true));assertEquals(load.initialMechanical,source.getPm(),0);
    }

    @Test void unqualifiedControlledSourceIsRejectedBeforeTransfer()throws Exception {
        var f=load(true);var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
        var source=(IeeeVcEConstMachine)n.getMachine("Bus1-mach1");
        var governor=new org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor("test-governor","Test governor","Test");
        governor.setMachine(source);governor.setStatus(true);
        assertFalse(source.supportsInitialOperatingPointBalance());
        assertThrows(IllegalStateException.class,source::reconcileInitialOperatingPointBalance);
        a.setSimuOutputHandler(new StateMonitor());
        var error=assertThrows(IllegalStateException.class,a::initialization);
        assertTrue(error.getMessage().contains("source balance is not qualified"));
        assertTrue(f.ports().rectifierPort().acceptInitialEndpoint(),"Rejected source must not consume the pending transfer");
    }

    private static final class InitialLoad extends DynamicBusDeviceImpl implements InitialEndpointAction {
        final IeeeVcEConstMachine source;boolean applied;int actions;
        double initialMechanical,initialAngle,initialEmf;
        InitialLoad(IeeeVcEConstMachine source){this.source=source;setId("initial-physical-load");setExtendedDeviceId(getId());setDeviceType(DynamicBusDeviceType.DYNAMIC_LOAD);}
        @Override public boolean initStates(BaseDStabBus<?,?> bus){applied=false;actions=0;return true;}
        @Override public boolean nextStep(double h,com.interpss.dstab.algo.DynamicSimuMethod method,int stage){return true;}
        @Override public boolean afterStep(double h){return true;}
        @Override public boolean updateAttributes(boolean changed){return true;}
        @Override public Hashtable<String,Object> getStates(Object reference){
            var values=new Hashtable<String,Object>();values.put(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());return values;
        }
        @Override public Object getOutputObject(){return applied?new Complex(-.1,0):Complex.ZERO;}
        @Override public boolean acceptInitialEndpoint(){
            if(applied)return false;
            assertEquals(source.getPe(),source.getPm(),1e-5,"Source balanced before physical event");
            initialMechanical=source.getPm();initialAngle=source.getAngle();initialEmf=source.getE();
            applied=true;actions++;return true;
        }
    }
}
