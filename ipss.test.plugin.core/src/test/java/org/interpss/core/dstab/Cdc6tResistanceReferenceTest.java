package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.interpss.CorePluginTestSetup;
import com.interpss.dstab.hvdc.model.*;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

/** Finite converter loss: independent full flat/fault states and terminal boundaries under both solvers. */
public class Cdc6tResistanceReferenceTest extends CorePluginTestSetup {
 static java.util.stream.Stream<Arguments> profiles(){return java.util.stream.Stream.of("zero","rectifier","inverter")
  .flatMap(end->java.util.stream.Stream.of("flat","fault").flatMap(profile->java.util.stream.Stream.of(false,true).map(shared->Arguments.of(end,profile,shared))));}
 @ParameterizedTest @MethodSource("profiles")
 void finiteResistanceMatchesIndependentControls(String end,String profile,boolean staggered,@TempDir Path folder)throws Exception {
  Path path=Path.of("testData/private/verification-artifacts/cdc6t-resistance-20261104/captures.zip");
  Assumptions.assumeTrue(Files.isRegularFile(path),"Retain independent finite-resistance controls");
  try(var input=Files.newInputStream(path)){
   var digest=java.security.MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int count;
   while((count=input.read(buffer))>=0)digest.update(buffer,0,count);
   assertEquals("ebfbee3b692c0530014813678f8284f2d4c2cab59632e0afc14caaf66278e534",HexFormat.of().formatHex(digest.digest()));
  }
  List<String> raw;List<Map<String,Double>> reference;Cdc6tParameters parameters;
  try(var z=new ZipFile(path.toFile())) {
   String prefix=end+"/"+profile+"/a/";
   byte[] csv=z.getInputStream(z.getEntry(prefix+"native.csv")).readAllBytes();
   assertArrayEquals(csv,z.getInputStream(z.getEntry(end+"/"+profile+"/b/native.csv")).readAllBytes());
   reference=Tiocr1ResetReferenceTest.csv(csv);
   raw=new ArrayList<>(new String(z.getInputStream(z.getEntry(prefix+"network.raw")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList());
   var records=PsseDyrRecordReader.read(new java.io.InputStreamReader(z.getInputStream(z.getEntry(prefix+"dynamic.dyr")),java.nio.charset.StandardCharsets.UTF_8),"resistance/dynamic.dyr");
   parameters=Cdc6tRecordMapper.prepare(records.stream().filter(r->r.canonicalModelName().equals("CDC6T")).findFirst().orElseThrow());
   var manifest=new com.fasterxml.jackson.databind.ObjectMapper().readTree(z.getInputStream(z.getEntry(prefix+"manifest.json")));
   assertEquals("complete",manifest.path("status").asText());
   for(int c=0;c<32;c++)assertEquals((double)(float)manifest.path("entered_constants").get(c).asDouble(),manifest.path("initialized_constants").get(c).asDouble(),0.,"Unchanged entered constant "+c);
   String log=new String(z.getInputStream(z.getEntry(prefix+"native.log")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   assertTrue(log.contains("Initial conditions check O.K."));assertFalse(log.toUpperCase(Locale.ROOT).contains("NETWORK NOT CONVERGED"));
  }
  double rr=end.equals("rectifier")?.5:0,ri=end.equals("inverter")?.5:0;
  var r=new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,rr,13.5939,1e8);
  var i=new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,ri,13.3174,1e8);
  double[] boundary=new double[8],boundaryMax=new double[6];String[] channels={"VAR5","VAR6","VAR12","VAR7","VAR8","VAR13"};
  int binding=0;double currentError=0;
  for(var row:reference){
   double measured=row.get("STATE0"),desired=500e6/measured,ceiling=Math.max(800,Math.min(1200,800+(measured-480000)*.02));
   currentError=Math.max(currentError,Math.abs(Math.min(desired,ceiling)-row.get("VAR11")));
   if(ceiling<desired-1)binding++;
   r.evaluate(row.get("V7"),row.get("VAR10"),row.get("VAR11"),boundary,0);
   i.evaluate(row.get("V9"),row.get("VAR9"),row.get("VAR11"),boundary,3);
   for(int j=0;j<6;j++)boundaryMax[j]=Math.max(boundaryMax[j],Math.abs((j%3==2?Math.toDegrees(boundary[j]):boundary[j])-row.get(channels[j])));
   assertEquals((5+4*rr+4*ri)*row.get("VAR11")*row.get("VAR11")/1e8,row.get("VAR5")+row.get("VAR7"),2e-6,"Independent total dissipative loss");
  }
  assertTrue(currentError<3.9e-5,"Existing all-sample current ceiling envelope in amperes");
  if(profile.equals("fault"))assertTrue(binding>0,"Fault exercises a binding current ceiling");else assertEquals(0,binding);
  var f=Cdc6tCoupledNetworkTest.load(folder,raw,parameters);var a=f.context().getDynSimuAlgorithm();var n=f.context().getDStabilityNet();
  double h=.0005;a.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);a.setSimuStepSec(h);a.setTotalSimuTimeSec(.5);
  a.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
  var recorder=new Cdc6tCoupledReferenceTest.Recorder(f,.05,.1);
  a.setSolver(staggered?new Cdc6tCoupledReferenceTest.Shared(recorder):new Cdc6tCoupledReferenceTest.Ordinary(recorder));
  if(profile.equals("fault"))n.addDynamicEvent(com.interpss.dstab.DStabObjectFactory.createBusFaultEvent("Bus7",n,
   com.interpss.core.acsc.fault.SimpleFaultCode.GROUND_3P,new org.apache.commons.math3.complex.Complex(0,.2),null,.05,.05),"fault");
  assertTrue(a.initialization());a.setSimuTime(-2*h);recorder.enabled=true;
  while(a.getSimuTime()<.5-h/2){
   assertTrue(a.solveDEqnStep(true));f.runtime().readBoundary(boundary,0);
   double amperes=(boundary[6]-boundary[7])/5;
   assertTrue(amperes>=400-1e-8&&amperes<=1200+1e-8,"Physical delivered current stays within prepared bounds");
   assertEquals((5+4*rr+4*ri)*amperes*amperes/1e8,boundary[0]+boundary[3],2e-12,"Physical total converter plus line losses");
  }
  recorder.capture(a.getSimuTime());assertEquals(reference.size(),recorder.rows.size());
  var out=Files.createTempDirectory(Files.createDirectories(Path.of("target/cdc6t-resistance")),end+"-"+profile+"-"+staggered+"-");
  double[] max=Cdc6tProtectedNetworkTest.writeComparison(out,reference,recorder.rows,h);
  double[] limits=staggered?new double[]{4.2e-6,.00046,4e-6,.0017,1.8,.0085,1.7,.000115,.00006,.00052,.00011,.00008,.00082,2.9,2.9}
    :new double[]{.00014,.0085,.000008,.0075,125,.031,125,.004,.0007,.022,.004,.0023,.004,10,10};
  var failures=new ArrayList<String>();
  for(int j=0;j<max.length;j++)if(max[j]>=limits[j])failures.add(Cdc6tCoupledReferenceTest.KEYS[j]+"="+max[j]+" limit="+limits[j]);
  for(int j=0;j<6;j++)if(boundaryMax[j]>=(j%3==2?.0003:j%3==1?1e-5:1.2e-6))failures.add("measured "+channels[j]+"="+boundaryMax[j]);
  System.out.println("CDC6T resistance "+end+" "+profile+" "+staggered+" measured="+Arrays.toString(boundaryMax));
  assertEquals(List.of(),failures,"Existing conducting reference envelopes");
 }
}
