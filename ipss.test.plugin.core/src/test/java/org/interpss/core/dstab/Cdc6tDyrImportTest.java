package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.*;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.*;
import org.interpss.fadapter.psse.dyr.*;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Native named-record loading, deferred preparation and exact typed/imported solver parity. */
public class Cdc6tDyrImportTest extends CorePluginTestSetup {
    @TempDir Path folder;
    private static final String MACHINES="1 'GENCLS' '1' 99999 0 /\n2 'GENCLS' '1' 99999 0 /\n3 'GENCLS' '1' 99999 0 /\n4 'GENCLS' '1' 99999 0 /\n";
    private static double[] constants(){return new double[]{5,20,.05,.05,.2,.3,.02,.2,.3,.02,
            250,500,99,99,400,250,450,500,2000,425,2000,.02,-999,999,-999,0,0,999,999,0,999,.05};}
    private com.interpss.simu.SimuContext source(Path directory)throws Exception {
        Files.createDirectories(directory);
        var lines=new ArrayList<>(Files.readAllLines(Path.of("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC_PsetOnInv.raw")));
        for(int index=3;index<lines.size();index++){
            if(lines.get(index).strip().startsWith("0 /"))break;
            var fields=lines.get(index).split(",",-1);
            if(Integer.parseInt(fields[0].strip())==8){fields[3]="4";lines.set(index,String.join(",",fields));}
        }
        var raw=directory.resolve("network.raw");Files.write(raw,lines);
        return new PSSEMultiFileLoader().loadDStab(raw.toString());
    }
    private static String record(String name,double[] constants){
        return "'"+name.replace("'","''")+"' 'CDC6T' "+String.join(" ",Arrays.stream(constants).mapToObj(Double::toString).toList())+" /\n";
    }
    private PSSEDStabDirectParser parse(com.interpss.simu.SimuContext context,Path directory,String records)throws Exception {
        var file=directory.resolve("models.dyr");Files.writeString(file,MACHINES+records);
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(context.getDStabilityNet())).setStrictImport(true);
        parser.parseDynFile(file.toString());return parser;
    }
    @Test void namedRecordAttachesBeforePowerFlowAndPreservesAllConstants()throws Exception {
        var context=source(folder);var n=context.getDStabilityNet();var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().getFirst();
        owner.setName("Line, O'Brien / 2");
        var parser=parse(context,folder,record(owner.getName(),constants()));
        assertTrue(parser.getLastImportReport().isStrictlyComplete());assertFalse(n.isLfConverged());
        var model=Cdc6tDyrModel.models(n).getFirst();assertNull(model.getBinding());
        assertEquals(owner.getName(),model.getParameters().linkName());assertArrayEquals(constants(),model.getParameters().copySourceConstants());
        assertSame(owner.getRectifier().getBus(),model.getDStabBus());
        assertEquals(1,n.getBus("Bus7").getDynamicBusDeviceList().size());assertEquals(0,n.getBus("Bus9").getDynamicBusDeviceList().size());
    }
    @Test void invalidRecordsAndOwnersNeverAttachPartialPorts()throws Exception {
        for(String problem:List.of("short","long","nan","missing","ambiguous","inactive","capacitor","header")){
            var directory=folder.resolve(problem);var context=source(directory);var n=context.getDStabilityNet();
            var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().getFirst();var c=constants();String name=owner.getName();
            switch(problem){
                case "short"->c=Arrays.copyOf(c,31);
                case "long"->c=Arrays.copyOf(c,33);
                case "nan"->c[2]=Double.NaN;
                case "missing"->name="absent";
                case "ambiguous"->{var duplicate=com.interpss.core.HvdcObjectFactory.createHvdcLine2TLCC();duplicate.setName(name);n.getSpecialBranchList().add(duplicate);}
                case "inactive"->owner.setStatus(false);
                case "capacitor"->owner.getInverter().setCommutingCapacitor(1);
            }
            String input=problem.equals("header")?"7 'CDC6T' '1' "+String.join(" ",Arrays.stream(c).mapToObj(Double::toString).toList())+" /\n":record(name,c);
            assertThrows(com.interpss.common.exp.InterpssException.class,()->parse(context,directory,input),problem);
            assertTrue(Cdc6tDyrModel.models(n).isEmpty(),problem);
            assertEquals(0,n.getBus("Bus7").getDynamicBusDeviceList().size(),problem);
            assertEquals(0,n.getBus("Bus9").getDynamicBusDeviceList().size(),problem);
        }
    }
    @Test void duplicateRecordsCannotDoubleTheInjectionOwner()throws Exception {
        var context=source(folder);var n=context.getDStabilityNet();var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().getFirst();
        var record=record(owner.getName(),constants());
        assertThrows(com.interpss.common.exp.InterpssException.class,()->parse(context,folder,record+record));
        assertEquals(1,Cdc6tDyrModel.models(n).size());assertEquals(0,n.getBus("Bus9").getDynamicBusDeviceList().size());
    }
    @Test void privateNetworkCopyKeepsTheImportAndItsIndependentOwner()throws Exception {
        var context=source(folder);var n=context.getDStabilityNet();var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().getFirst();
        parse(context,folder,record(owner.getName(),constants()));
        var gson=new com.google.gson.Gson();var frozen=gson.toJson(new com.interpss.state.dstab.DStabNetworkState(n));
        var copy=com.interpss.state.dstab.DStabNetworkState.create(gson.fromJson(frozen,com.interpss.state.dstab.DStabNetworkState.class));
        // Nested private preparation must retain the same native identity without changing the source copy.
        frozen=gson.toJson(new com.interpss.state.dstab.DStabNetworkState(copy));
        copy=com.interpss.state.dstab.DStabNetworkState.create(gson.fromJson(frozen,com.interpss.state.dstab.DStabNetworkState.class));
        assertEquals(1,copy.getSpecialBranchList().size(),"Copied static DC-link owner");
        var original=Cdc6tDyrModel.models(n).getFirst();var cloned=Cdc6tDyrModel.models(copy).getFirst();
        assertNotSame(original,cloned);assertSame(copy.getBus("Bus7"),cloned.getDStabBus());
        assertArrayEquals(original.getParameters().copySourceConstants(),cloned.getParameters().copySourceConstants());
        var algorithm=DStabObjectFactory.createDynamicSimuAlgorithm(copy);
        algorithm.setSolver(new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(algorithm));solve(algorithm);
        algorithm.setSimuOutputHandler(new StateMonitor());assertTrue(algorithm.initialization());
        assertEquals(owner.getName(),copy.getSpecialBranchList().getFirst().getName(),"Native owner name restored from captured identity");
        assertNull(original.getBinding());assertNotNull(cloned.getBinding());assertSame(copy,cloned.getBinding().owner().getNetwork());
    }
    @Test void preparingAgainReplacesPortsWithoutDoublingInjection()throws Exception {
        var context=source(folder);var n=context.getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().getFirst();
        parse(context,folder,record(owner.getName(),constants()));solve(context.getDynSimuAlgorithm());
        var model=Cdc6tDyrModel.models(n).getFirst();
        Cdc6tDyrModel.prepareNetwork(n);var original=model.getBinding();
        Cdc6tDyrModel.prepareNetwork(n);var replacement=model.getBinding();
        assertNotSame(original,replacement);
        assertFalse(n.getBus("Bus7").getDynamicBusDeviceList().contains(original.ports().rectifierPort()));
        assertFalse(n.getBus("Bus9").getDynamicBusDeviceList().contains(original.ports().inverterPort()));
        assertTrue(replacement.ports().rectifierPort().attachmentIntact());
        assertEquals(2,n.getBus("Bus7").getDynamicBusDeviceList().size());
        assertEquals(1,n.getBus("Bus9").getDynamicBusDeviceList().size());
    }
    @Test void failedRepreparationRetainsTheExistingTerminalPair()throws Exception {
        var context=source(folder);var n=context.getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().getFirst();
        parse(context,folder,record(owner.getName(),constants()));solve(context.getDynSimuAlgorithm());
        Cdc6tDyrModel.prepareNetwork(n);var model=Cdc6tDyrModel.models(n).getFirst();var original=model.getBinding();
        owner.setStatus(false);
        assertThrows(IllegalArgumentException.class,()->Cdc6tDyrModel.prepareNetwork(n));
        assertSame(original,model.getBinding());
        assertTrue(n.getBus("Bus7").getDynamicBusDeviceList().contains(original.ports().rectifierPort()));
        assertTrue(n.getBus("Bus9").getDynamicBusDeviceList().contains(original.ports().inverterPort()));
    }
    @Test void genericInitializationCannotSilentlyIgnoreTheDeferredModel()throws Exception {
        var context=source(folder);var n=context.getDStabilityNet();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().getFirst();
        parse(context,folder,record(owner.getName(),constants()));
        var model=Cdc6tDyrModel.models(n).getFirst();
        assertTrue(assertThrows(IllegalStateException.class,()->model.initStates(model.getDStabBus()))
                .getMessage().contains("DefaultDynamicSimulationSolver"));
    }
    @ParameterizedTest @CsvSource({"false,false","false,true","true,false","true,true"})
    void importedRuntimeMatchesExplicitTypedFlatAndFaultRuns(boolean shared,boolean fault)throws Exception {
        var explicit=run(folder.resolve("typed"),false,shared,fault);
        var imported=run(folder.resolve("imported"),true,shared,fault);
        assertEquals(explicit.size(),imported.size());
        for(int row=0;row<explicit.size();row++)assertArrayEquals(explicit.get(row),imported.get(row),1e-9,"Sample "+row);
    }
    private List<double[]> run(Path directory,boolean imported,boolean shared,boolean fault)throws Exception {
        var context=source(directory);var n=context.getDStabilityNet();var a=context.getDynSimuAlgorithm();
        var owner=(HvdcLine2TLCC<?>)n.getSpecialBranchList().getFirst();
        parse(context,directory,imported?record(owner.getName(),constants()):"");solve(a);
        Cdc6tNetworkPreparation.Binding typed=null;
        if(!imported){typed=Cdc6tNetworkPreparation.prepare(n,new Cdc6tParameters(owner.getName(),constants()));typed.ports().attach();}
        a.setSimuStepSec(.0005);a.setTotalSimuTimeSec(.12);a.setSimuOutputHandler(new StateMonitor());
        if(shared)a.setSolver(new StaggeredEulerSolver(a));
        if(fault)n.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus7",n,SimpleFaultCode.GROUND_3P,new Complex(0,.2),null,.03,.02),"fault");
        assertTrue(a.initialization());var binding=imported?Cdc6tDyrModel.models(n).getFirst().getBinding():typed;
        var rows=new ArrayList<double[]>();rows.add(frame(n,binding));
        while(a.getSimuTime()<.12-.00025){assertTrue(a.solveDEqnStep(true));rows.add(frame(n,binding));}
        return rows;
    }
    private static double[] frame(com.interpss.dstab.BaseDStabNetwork<?,?> n,Cdc6tNetworkPreparation.Binding b){
        double[] row=new double[13];row[0]=n.getBus("Bus7").getVoltageMag();row[1]=n.getBus("Bus9").getVoltageMag();
        b.runtime().readStaggeredState(row,2);b.runtime().readBoundary(row,5);return row;
    }
    private static void solve(com.interpss.dstab.algo.DynamicSimuAlgorithm a)throws Exception {
        var n=a.getNetwork();n.setStaticLoadIncludedInYMatrix(true);n.setStaticLoadModel(StaticLoadModel.CONST_Z);n.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
        var lf=a.getAclfAlgorithm();lf.setTolerance(1e-10);lf.setMaxIterations(80);lf.getLfAdjAlgo().setApplyAdjustAlgo(false);
        lf.getLfAdjAlgo().getVoltAdjConfig().setHvdcTapControl(false);assertTrue(lf.loadflow());
    }
}
