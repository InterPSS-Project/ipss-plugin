package org.interpss.threePhase.state;

import com.google.gson.Gson;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.StaticLoadModel;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.TestBase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DStab3PNetworkStateTest {
    @Test void jsonRoundTripRestoresTopologySettingsAndMachines()throws Exception{
        org.interpss.IpssCorePlugin.init();
        var source=new TestBase().create2BusSys();source.setId("phase-original");
        for(var bus:source.getBusList())for(var load:bus.getThreePhaseLoadList())if(load.getId().isEmpty())load.setId("L-"+bus.getId());
        source.setNetworkType(NetworkType.TRANSMISSION);source.setStaticLoadModel(StaticLoadModel.CONST_P);
        source.setStaticLoadSwitchVolt(.72);source.setStaticLoadSwitchDeadZone(.04);
        source.setNetEqnIterationNoEvent(4);source.setNetEqnIterationWithEvent(7);
        var currents=new java.util.Hashtable<String,Complex3x1>();
        currents.put(source.getBusList().getFirst().getId(),new Complex3x1(new Complex(.01,.02),Complex.ZERO,Complex.ZERO));
        source.set3phaseCustomCurrInjTable(currents);
        var machine=source.getMachine("1");double inertia=machine.getH();
        var branch=source.getBranchList().getFirst();var branchId=branch.getId();
        var gson=new Gson();var json=gson.toJson(new DStab3PNetworkState(source));
        machine.setH(99);source.removeBranch(branchId);currents.values().iterator().next().a_0=Complex.ZERO;
        var state=gson.fromJson(json,DStab3PNetworkState.class);
        var restored=DStab3PNetworkState.create(state);
        assertEquals(2,restored.getNoBus());assertEquals(1,restored.getNoBranch());assertEquals(0,source.getNoBranch());
        assertEquals(inertia,restored.getMachine(machine.getId()).getH());assertNotSame(machine,restored.getMachine(machine.getId()));
        assertSame(restored.getBus(machine.getDStabBus().getId()),restored.getMachine(machine.getId()).getDStabBus());
        assertSame(restored.getBus(branch.getFromBus().getId()),restored.getBranch(branchId).getFromBus());
        assertSame(restored.getBus(branch.getToBus().getId()),restored.getBranch(branchId).getToBus());
        assertEquals(StaticLoadModel.CONST_P,restored.getStaticLoadModel());assertEquals(.72,restored.getStaticLoadSwitchVolt());
        assertEquals(.04,restored.getStaticLoadSwitchDeadZone());assertEquals(4,restored.getNetEqnIterationNoEvent());assertEquals(7,restored.getNetEqnIterationWithEvent());
        assertEquals(new Complex(.01,.02),restored.get3phaseCustomCurrInjTable().values().iterator().next().a_0);
        var detached=restored.<org.interpss.threePhase.dynamic.DStabNetwork3Phase>jsonCopy();
        assertNotSame(restored,detached);assertEquals(inertia,detached.getMachine(machine.getId()).getH());
        var repeated=DStab3PNetworkState.create(state);restored.getMachine(machine.getId()).setH(77);
        assertEquals(inertia,repeated.getMachine(machine.getId()).getH());
        assertTrue(com.interpss.core.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(repeated).loadflow());
        assertFalse(source.isLfConverged());
    }
}
