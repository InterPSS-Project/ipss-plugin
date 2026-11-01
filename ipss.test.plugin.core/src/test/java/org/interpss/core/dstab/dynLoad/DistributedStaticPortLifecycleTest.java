package org.interpss.core.dstab.dynLoad;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import com.interpss.dstab.device.NetworkIterationAware;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
/** Owned numerical stamp rollback, rebuild and repeated initialization on a synthetic network. */
public class DistributedStaticPortLifecycleTest {
 static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
 static void same(Complex a,Complex b,String message) { check(a.subtract(b).abs()<2e-13,message+": "+a+" vs "+b); }
 static Complex history(Object port) {
  var states=((ICMLStateProvider)port).getNamedStates();
  return new Complex(states.get("STATIC.AcceptedTerminalCurrentReal"),states.get("STATIC.AcceptedTerminalCurrentImag"));
 }
 static Path input() throws Exception {
  String source=Files.readString(Cmldblu1SharedSmibTest.CASE.resolve("cmldblu1-restart.dyr"));
  String original="0.004 0.009 0 0.0 0.0 0.05 0 0.96";
  check(source.contains(original),"Expected public synthetic fixture");
  var path=Files.createTempFile(Path.of("target"),"distribution-control-",".dyr");
  Files.writeString(path,source.replace(original,"0.004 0.009 0 0.0 0.0 0 0.5 0.96"));
  return path;
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
 void deepRepeatedFaultConvergesAndPreservesPassiveNetworkBalance(boolean staggered) throws Exception {
  IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
  var run=Cmldblu1SharedRecording.simulateStaticControl(input(),staggered,.0005,.01,.01);
  var model=run.model;var branches=run.algorithm.getNetwork().getBranchList();
  var transformer=branches.stream().filter(b->b.getFromBus()==model.getDStabBus()
          && b.getToBus()==model.getLowVoltBus()).findFirst().orElseThrow();
  var feeder=branches.stream().filter(b->b.getFromBus()==model.getLowVoltBus()
          && b.getToBus()==model.getLoadBus()).findFirst().orElseThrow();
  check(run.frames.size()==1005,"Retain complete repeated-fault recording");
  int duplicate=0;
  for(int index=0;index<run.frames.size();index++) {
   var frame=run.frames.get(index);var q=frame.values();
   if(index>0 && frame.time()==run.frames.get(index-1).time()) duplicate++;
   Complex terminal=phasor(q.get("V2"),Math.toRadians(q.get("A2")));
   Complex low=phasor(q.get("Low.V"),q.get("Low.A"));
   Complex load=phasor(q.get("Load.V"),q.get("Load.A"));
   double tap=transformer.getToTurnRatio();
   Complex incoming=terminal.subtract(low.divide(tap)).divide(transformer.getZ());
   Complex downstream=low.subtract(load).divide(feeder.getZ());
   Complex demand=new Complex(q.get("STATIC.P")+q.get("ELECTRONIC.P"),q.get("STATIC.Q")+q.get("ELECTRONIC.Q"));
   Complex loadCurrent=demand.divide(load).conjugate().add(model.getLoadBus().getShuntY().multiply(load));
   double power=terminal.multiply(incoming.conjugate()).getReal();
   double losses=incoming.abs()*incoming.abs()*transformer.getZ().getReal()
          +downstream.abs()*downstream.abs()*feeder.getZ().getReal();
   check(power>0,"Passive composite must not export active power");
   check(Math.abs(power-demand.getReal()-losses)<1e-6,"Terminal power equals demand plus losses");
   check(downstream.subtract(loadCurrent).abs()<1e-7,"Load boundary current balance");
   check(incoming.divide(tap).subtract(downstream).subtract(model.getLowVoltBus().getShuntY().multiply(low)).abs()<3e-6,"Low-side current balance");
  }
  check(duplicate==4,"Preserve both sides of every event");
 }
 static Complex phasor(double magnitude,double angle) {
  return new Complex(magnitude*Math.cos(angle),magnitude*Math.sin(angle));
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
 void trialRollbackRebuildAndInitialization(boolean staggered) throws Exception {
  IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
  var fixture=Cmldblu1SharedSmibTest.CASE;
  var input=input();
  var loader=new org.interpss.fadapter.psse.PSSEMultiFileLoader();
  var context=loader.loadDStab(fixture.resolve("load-network.raw").toString(),input.toString());
  var algorithm=context.getDynSimuAlgorithm();check(algorithm.getAclfAlgorithm().loadflow(),"Power flow");
  algorithm.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);
  algorithm.setSimuStepSec(.0005);
  algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
  algorithm.setSolver(staggered ? new org.interpss.dstab.algo.StaggeredEulerSolver(algorithm)
          : new org.interpss.dstab.algo.DefaultDynamicSimulationSolver(algorithm));
  check(algorithm.initialization(),"Dynamic initialization");
  var model=(org.interpss.dstab.dynLoad.impl.Cmldblu1Model)algorithm.getNetwork().getDStabBus("Bus2").getInfoOnlyDynModel();
   var net=algorithm.getNetwork();var parent=model.getDStabBus();var bus=model.getLoadBus();
   var port=bus.getDynLoadModelList().stream().filter(d->d.getId().endsWith("_S")).findFirst().orElseThrow();
   var aware=(NetworkIterationAware)port;var states=(ICMLStateProvider)port;var trialContext=new NetworkIterationAware.Context("INITIAL",.05,.05,50,1e-4);
   int row=bus.getSortNumber();Complex originalV=parent.getVoltage(),stamp=port.getEquivY(),diagonal=net.getYMatrix().getA(row,row);
   var initial=states.getNamedStates();var incoming=history(port);
   parent.setVoltage(originalV.multiply(.2));
   aware.beginNetworkSolution(trialContext);aware.beforeNetworkIteration(0);
   check(port.getEquivY().subtract(stamp).abs()>1,"Deep trial must change numerical stamp");
   same(net.getYMatrix().getA(row,row).subtract(diagonal),port.getEquivY().subtract(stamp),"Matching matrix/current numerical pair");
   check(initial.equals(states.getNamedStates()),"Trial must not publish accepted G/B");
   aware.endNetworkSolution(false);
   same(port.getEquivY(),stamp,"Failed solve restores stamp");same(net.getYMatrix().getA(row,row),diagonal,"Failed solve restores matrix");
   check(initial.equals(states.getNamedStates()),"Failed solve preserves accepted states");check(incoming.equals(history(port)),"Failed solve preserves current history");
   check(net.isYMatrixDirty(),"Rollback invalidates factorization");
   aware.beginNetworkSolution(trialContext);aware.beforeNetworkIteration(0);aware.afterNetworkConvergence();aware.endNetworkSolution(true);
   check(states.getNamedStates().get("STATIC.LowVoltageG")>0,"Accepted preparation retained");
   Complex acceptedStamp=port.getEquivY(),acceptedDiagonal=net.getYMatrix().getA(row,row);
   net.formYMatrix4DStab();same(net.getYMatrix().getA(row,row),acceptedDiagonal,"Rebuild retains accepted stamp once");same(port.getEquivY(),acceptedStamp,"Rebuild retains leaf stamp");
   var acceptedStates=states.getNamedStates();var acceptedHistory=history(port);
   aware.beginNetworkSolution(trialContext);aware.beforeNetworkIteration(0);aware.endNetworkSolution(false);
   check(acceptedStates.equals(states.getNamedStates()),"Later failed solve preserves accepted G/B");check(acceptedHistory.equals(history(port)),"Later failure preserves history");
   parent.setVoltage(originalV);
   int loadCount=bus.getDynLoadModelList().size(),lowCount=model.getLowVoltBus().getDynLoadModelList().size();
   for(int repeat=0;repeat<2;repeat++) {
    check(port.initStates(),"Repeated leaf initialization succeeds");same(port.getEquivY(),stamp,"Repeated initialization resets stamp");
    check(states.getNamedStates().get("STATIC.LowVoltageG")==0,"Repeated initialization clears G/B");
    net.formYMatrix4DStab();same(net.getYMatrix().getA(row,row),diagonal,"Reinitialized matrix has no stale stamp");
    check(bus.getDynLoadModelList().size()==loadCount && model.getLowVoltBus().getDynLoadModelList().size()==lowCount,"No duplicate leaf registration");
   }

 }
}
