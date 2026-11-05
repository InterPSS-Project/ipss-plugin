package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.hvdc.*;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Cross-cause lifecycle qualification; complete state accuracy remains independently gated. */
public class Cdc6tOverlappingProtectionTest extends CorePluginTestSetup {
 @ParameterizedTest @CsvSource({"rectifier,false","rectifier,true","inverter,false","inverter,true"})
 void overlappingCausesRetainCauseSpecificRecovery(String first,boolean staggered,@TempDir Path folder)throws Exception {
  Path path=Path.of("testData/private/verification-artifacts/cdc6t-overlap-20261104/captures.zip");
  Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent cold overlap captures");
  try(var input=Files.newInputStream(path)) {
   var hash=java.security.MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int count;
   while((count=input.read(buffer))>=0)hash.update(buffer,0,count);
   assertEquals("7edf2478ed4e97b61bbef6f157c5d82b11c90671abb5be2ecffb0636d293f8d2",HexFormat.of().formatHex(hash.digest()));
  }
  List<String> raw;List<Map<String,Double>> reference;Cdc6tParameters parameters;
  try(var z=new ZipFile(path.toFile())) {
   String prefix=first+"/a/";
   byte[] csv=z.getInputStream(z.getEntry(prefix+"native.csv")).readAllBytes();
   assertArrayEquals(csv,z.getInputStream(z.getEntry(first+"/b/native.csv")).readAllBytes());
   reference=Tiocr1ResetReferenceTest.csv(csv);
   raw=new ArrayList<>(new String(z.getInputStream(z.getEntry(prefix+"network.raw")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList());
   var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(z.getInputStream(z.getEntry(prefix+"dynamic.dyr")),java.nio.charset.StandardCharsets.UTF_8),"overlap/dynamic.dyr");
   parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
   String log=new String(z.getInputStream(z.getEntry(prefix+"native.log")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   assertTrue(log.contains("Initial conditions check O.K."));assertFalse(log.toUpperCase(Locale.ROOT).contains("NETWORK NOT CONVERGED"));
  }
  var f=Cdc6tCoupledNetworkTest.load(folder,raw,parameters);var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
  double h=.0005;
  a.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(h);a.setTotalSimuTimeSec(.5);
  a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
  var recorder=new Cdc6tCoupledReferenceTest.Recorder(f,.05,.07,.10,.16);
  var internalEvents=new ArrayList<Cdc6tNetworkTransition>();recorder.recordInternalTransitions(internalEvents::add);
  a.setSolver(staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder));
  for(int i=0;i<2;i++) {
   String bus=(first.equals("rectifier")==(i==0))?"Bus7":"Bus9";
   n.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent(bus,n,
    com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new org.apache.commons.math3.complex.Complex(0,.2),null,i==0?.05:.07,i==0?.05:.09),"fault"+i);
  }
  assertTrue(a.initialization());a.setSimuTime(-2*h);recorder.enabled=true;
  var transitions=new ArrayList<Double>();boolean previous=true;double[] boundary=new double[8];
  while(a.getSimuTime()<.5-h/2) {
   assertTrue(a.solveDEqnStep(true));f.runtime().readBoundary(boundary,0);
   boolean conducting=Math.abs(boundary[0])>1e-8;
   if(conducting!=previous){transitions.add(a.getSimuTime());previous=conducting;}
   if(f.protection().operatingMode()==Cdc6tNormalRuntime.OperatingMode.BLOCKED){assertEquals(0,boundary[0],0);assertEquals(0,boundary[3],0);}
  }
  recorder.capture(a.getSimuTime());
  var expected=new ArrayList<Double>();previous=true;
  for(var row:reference)if(row.get("time_s")>=0) {
   boolean conducting=row.get("VAR11")>1;
   if(conducting!=previous){expected.add(row.get("time_s"));previous=conducting;}
  }
  var out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-overlap")),first+"-"+staggered+"-");
  Cdc6tProtectedNetworkTest.writeComparison(out,reference,recorder.rows,h);
  new com.fasterxml.jackson.databind.ObjectMapper().writeValue(out.resolve("event-owners.json").toFile(),
    Map.of("external_schedule",List.of(.05,.07,.1,.16),"internal_transitions",internalEvents,
      "regular_rows",recorder.regularRows,"columns",Cdc6tCoupledReferenceTest.KEYS,
      "first_terminal",first,"staggered",staggered));
  System.out.println("CDC6T overlap "+first+" staggered="+staggered+" expected="+expected+" actual="+transitions);
  assertEquals(first.equals("rectifier")?4:2,expected.size());
  assertEquals(expected.size(),transitions.size(),"Physical current transition count");
  for(int i=0;i<expected.size();i++)assertEquals(expected.get(i),transitions.get(i),2.2*h,"Sampled physical transition "+i);
  if(first.equals("rectifier")) {
   assertTrue(expected.get(1)<.16,"First recovery occurs while opposite fault remains");
   assertEquals(.02,expected.get(2)-expected.get(1),h,"Communication delay starts after first-cause release");
   for(var row:reference)if(row.get("time_s")>=.071&&row.get("time_s")<.1)assertTrue(row.get("VAR16")>90000,"No opposite request retained during first block");
  }
 }
}
