package org.interpss.core.dstab;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.renewable.*;
import com.interpss.dstab.renewable.*;
import org.interpss.dstab.algo.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.*;
import com.interpss.dstab.*;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.core.acsc.fault.SimpleFaultCode;
/** Opt-in accepted voltage memory against complete independent coupled trajectories. */
@Tag("extended")
public class ReeccPlantNetworkConformanceTest {
    @Test void acceptedMemorySupportsBothMethodsWithFullNetworkFeedback() throws Exception {
        ReeccPlantReplayConformanceTest.verifyCaptures();ReeccPlantReplayConformanceTest.verifyBoundCaptures();
        for(String profile:List.of("plant-flat","plant-fault","plant-qbound-fault"))
            for(boolean history:new boolean[]{false,true})run(profile,history,false);
    }
    @Test void endpointReuseDoesNotChangeThePhysicalMemoryRecurrence() throws Exception {
        ReeccPlantReplayConformanceTest.verifyCaptures();run("plant-fault",true,true);
    }
    void run(String profile,boolean history,boolean reuse) throws Exception {
        double h=.00025;
        Path folder=Path.of("testData/private/verification-artifacts/"+(profile.equals("plant-qbound-fault")?"reecc1-plant-qbound-20261104":"reecc1-plant-remote-20261104"),profile+"-a");
        var reference=org.interpss.core.dstab.reference.RepcaNativeVerification.rows(Files.readString(folder.resolve("native.csv")));
        var context=new PSSEMultiFileLoader().loadDStab(folder.resolve("network.raw").toString(),folder.resolve("dynamic.dyr").toString());
        var network=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
        var edge=new HashMap<Integer,double[]>();var accepted=new ArrayList<double[]>();
        final Runnable[] capture={null};final long[] counts={0,0};
        DefaultDynamicSimulationSolver solver=history?new StaggeredEulerSolver(algorithm){
            @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
                boolean ok=super.networkSolutionStep();counts[0]++;counts[1]+=getLastNetworkSolutionDiagnostics().iterations();assertTrue(ok);assertEquals(50,getLastNetworkSolutionDiagnostics().iterationLimit());assertTrue(getLastNetworkSolutionDiagnostics().iterations()<=50);assertTrue(getLastNetworkSolutionDiagnostics().maximumVoltageChange()<=1.e-4);
                if(capture[0]!=null&&eventBoundaryThisStep&&"INITIAL".equals(networkSolutionStage))capture[0].run();return ok;
            }
        }:new DefaultDynamicSimulationSolver(algorithm){
            @Override public boolean networkSolutionStep() throws com.interpss.dstab.common.DStabSimuException {
                boolean ok=super.networkSolutionStep();counts[0]++;counts[1]+=getLastNetworkSolutionDiagnostics().iterations();assertTrue(ok);assertEquals(50,getLastNetworkSolutionDiagnostics().iterationLimit());assertTrue(getLastNetworkSolutionDiagnostics().iterations()<=50);assertTrue(getLastNetworkSolutionDiagnostics().maximumVoltageChange()<=1.e-4);
                if(capture[0]!=null&&eventBoundaryThisStep&&"INITIAL".equals(networkSolutionStage))capture[0].run();return ok;
            }
        };
        if(history){((StaggeredEulerSolver)solver).setNetworkSolutionTolerance(1.e-4);((StaggeredEulerSolver)solver).setReuseNetworkEndpoint(reuse);}solver.setNetworkAccelerationEnabled(true);algorithm.setSolver(solver);
        network.setBypassDataCheck(true);network.setAllowGenWithoutMach(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(h);algorithm.setTotalSimuTimeSec(.3);algorithm.setOutPutPerSteps(1);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        if(profile.endsWith("fault"))network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",network,SimpleFaultCode.GROUND_3P,new Complex(0.,100./(230.*230./100.)),null,.05,.05),"PlantFault");
        var converter=(Regca1Model)((DStabGen)network.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice();
        converter.setNetworkIterationHighVoltageEnabled(true);
        converter.setNetworkVoltageMemoryPolicy(Regca1Model.NetworkVoltageMemoryPolicy.ACCEPTED_BOUNDARY);
        assertTrue(algorithm.initialization());
        var model=(Reecc1Model)converter.getActiveElectricalController();
        assertEquals(100.,network.getBaseMva(),0.);assertEquals(100.,converter.getParentGen().getMvaBase(),0.);
        var initialPower=converter.getStates(null);
        double p0=((Number)initialPower.get("REGCA1_P")).doubleValue(),q0=((Number)initialPower.get("REGCA1_Q")).doubleValue();
        java.util.function.Supplier<double[]> snapshot=()->snapshot(network,converter,model,history,p0,q0);
        capture[0]=()->edge.put((int)Math.round(algorithm.getSimuTime()/h),snapshot.get());
        long startCalls=counts[0],startIterations=counts[1],startMixes=solver.getNetworkAccelerationApplications();
        accepted.add(snapshot.get());
        for(int i=1;i<=1200;i++){assertTrue(algorithm.solveDEqnStep(true));accepted.add(snapshot.get());}
        var names=new ArrayList<String>();for(int i=0;i<7;i++)names.add("ELEC_STATE"+i);for(int i=0;i<7;i++)names.add("PLANT_STATE"+i);
        names.addAll(List.of("ELEC_VAR2","ELEC_VAR3","ELEC_VAR4","PREF_INPUT","QEXT_INPUT","V1","V2","V3","P_PU","Q_PU"));
        var maximum=new double[names.size()];var at=new double[names.size()];
        double[] limits=history?new double[]{1.5e-4,1.e-10,1.e-5,3.e-5,1.e-10,1.e-5,1.e-8,1.e-10,1.e-4,6.e-6,2.e-5,1.e-4,3.e-6,1.e-5,4.e-5,2.e-4,6.e-8,1.e-5,3.e-5,1.5e-4,8.e-5,2.e-5,1.e-4,2.e-4}:
            new double[]{8.e-4,1.e-10,1.e-5,2.5e-4,1.e-10,6.e-5,1.e-8,1.e-10,6.e-4,5.e-5,2.e-4,5.e-4,2.e-5,6.e-5,2.5e-4,1.5e-3,6.e-8,6.e-5,2.e-4,4.e-4,2.e-4,3.e-5,5.e-4,1.2e-3};
        double[] previousActual=null;
        var trace=new StringBuilder("time_s");for(String name:names)trace.append(',').append(name).append("_actual,").append(name).append("_reference");trace.append(",physical_p,physical_q,algebraic_ip,algebraic_iq,A1_actual_deg,A2_actual_deg,A3_actual_deg,ip_state,iq_state,v_saved,v_gain,ip_source_frame,iq_source_frame,ip_branch_frame,iq_branch_frame\n");
        double previous=Double.NaN;int duplicates=0;
        for(var row:reference){double t=row.get("time_s");int index=Math.max(0,(int)Math.round(t/h));
            boolean duplicate=t>=0&&t==previous;double[] actual=duplicate&&profile.endsWith("fault")?edge.get(index):accepted.get(index);
            if(duplicate)duplicates++;assertNotNull(actual,"Missing event endpoint "+t+" keys "+edge.keySet());trace.append(t);
            for(int i=0;i<names.size();i++){double expected=row.get(names.get(i)),error=Math.abs(actual[i]-expected);assertTrue(error<=(profile.equals("plant-flat")?1.e-6:limits[i]),profile+" history="+history+" "+names.get(i)+" t="+t+" error="+error);if(error>maximum[i]){maximum[i]=error;at[i]=t;}trace.append(',').append(actual[i]).append(',').append(expected);}
            assertEquals(row.get("P_PU"),actual[24],profile.equals("plant-flat")?1.e-6:limits[22],"Physical source P");
            assertEquals(row.get("Q_PU"),actual[25],profile.equals("plant-flat")?1.e-6:limits[23],"Physical source Q");
            for(int bus=0;bus<3;bus++)assertEquals(row.get("A"+(bus+1)+"_DEG"),actual[28+bus],new double[]{.01,.004,.0005}[bus]);
            if(previousActual!=null)assertEquals(.2*previousActual[33]+.8*previousActual[19],actual[33],2.e-14,"One saved-voltage update per accepted sample, including events");
            previousActual=actual;
            for(int i=names.size();i<actual.length;i++)trace.append(',').append(actual[i]);trace.append('\n');previous=t;
        }
        assertEquals(2,duplicates);Files.writeString(Path.of("../target/reecc-plant-network-qualified-"+profile+"-"+history+"-reuse"+reuse+".csv"),trace);
        assertEquals(0,solver.getNetworkAccelerationApplications()-startMixes);assertFalse(converter.supportsNetworkIterationAcceleration());assertFalse(converter.supportsUncompensatedNetworkSolve());
        System.out.println("Network work history="+history+" reuse="+reuse+" calls="+(counts[0]-startCalls)+" iterations="+(counts[1]-startIterations)+" mixes="+(solver.getNetworkAccelerationApplications()-startMixes)+" accelerationEligible="+converter.supportsNetworkIterationAcceleration()+" uncompensatedEligible="+converter.supportsUncompensatedNetworkSolve());
        System.out.println("REECC network "+profile+" history="+history+" names="+names+" max="+Arrays.toString(maximum)+" at="+Arrays.toString(at)+" edges="+edge.keySet());
    }
    static double[] snapshot(BaseDStabNetwork<?,?> network,Regca1Model converter,Reecc1Model model,boolean history,double p0,double q0){
        var values=new ArrayList<Double>(model.getNamedStates().values());var plant=model.getPlantController();for(double v:plant.getNativeStates())values.add(v);
        values.addAll(List.of(history?model.getTwoStepIpcmd():model.getIpcmd(),history?-model.getTwoStepIqcmd():-model.getIqcmd(),model.getStateOfCharge(),p0+plant.getPref(),q0+plant.getQref()));
        for(int bus=1;bus<=3;bus++)values.add(network.getBus("Bus"+bus).getVoltageMag());
        var output=converter.getStates(null);values.add(((Number)output.get("REGCA1_P")).doubleValue());values.add(((Number)output.get("REGCA1_Q")).doubleValue());
        Complex power=network.getBus("Bus1").getVoltage().multiply(converter.uncompensatedNetworkCurrent().conjugate());values.add(power.getReal());values.add(power.getImaginary());values.add(model.getIpcmd());values.add(-model.getIqcmd());
        for(int i=1;i<=3;i++)values.add(Math.toDegrees(network.getBus("Bus"+i).getVoltage().getArgument()));
        var memory=converter.getNetworkHighVoltageState();
        values.add(converter.getIpRegulatorState());values.add(converter.getIqRegulatorState());
        values.add(memory.savedVoltage());values.add(memory.outputAvailable()?memory.deceleratedVoltage():network.getBus("Bus1").getVoltageMag());
        Complex voltage=network.getBus("Bus1").getVoltage();
        Complex rotation=new Complex(Math.cos(-voltage.getArgument()),Math.sin(-voltage.getArgument()));
        Complex source=converter.uncompensatedNetworkCurrent().multiply(rotation);
        Complex branch=voltage.subtract(network.getBus("Bus2").getVoltage()).divide(new Complex(.005,.1)).multiply(rotation);
        values.add(source.getReal());values.add(-source.getImaginary());values.add(branch.getReal());values.add(-branch.getImaginary());
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
}

