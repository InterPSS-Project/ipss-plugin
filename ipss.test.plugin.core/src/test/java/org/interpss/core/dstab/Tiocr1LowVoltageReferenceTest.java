package org.interpss.core.dstab;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import java.security.MessageDigest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
/** Low-voltage relay load-removal trajectory discriminator. */
public class Tiocr1LowVoltageReferenceTest {
 @ParameterizedTest @CsvSource({"false,1","true,1","false,2","true,2"})
 void fullDeepFault(boolean staggered,int faultBus)throws Exception {
  Path archive=Path.of("testData/private/verification-artifacts/tiocr-low-voltage-20261104/captures.zip");
  Assumptions.assumeTrue(Files.isRegularFile(archive),"Retain paired low-voltage captures");
  assertEquals("023e428361fb35e40ede14293dcb361293e02d242e21aab402cff10bbed71285",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
  Path capture=Files.createTempDirectory(Path.of("target"),"tiocr-low-reference-");
  try(var z=new ZipFile(archive.toFile())) {
   byte[] a=z.getInputStream(z.getEntry("bus"+faultBus+"/a/native.csv")).readAllBytes();
   assertArrayEquals(a,z.getInputStream(z.getEntry("bus"+faultBus+"/b/native.csv")).readAllBytes(),"Cold repeatability of every sample");
   Files.write(capture.resolve("native.csv"),a);
   for(String f:List.of("network.raw","dynamic.dyr"))Files.write(capture.resolve(f),z.getInputStream(z.getEntry("bus"+faultBus+"/a/"+f)).readAllBytes());
  }
  var reference=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(capture.resolve("native.csv"))).stream().filter(r->r.get("time_s")>=0).toList();
  Path out=Files.createTempDirectory(Path.of("target"),"tiocr-deep-bus"+faultBus+"-");
  HspUsageRankedModelTest.run("tiocr1-load-only",false,staggered,false,capture.resolve("network.raw"),capture.resolve("dynamic.dyr"),out,.02,"Bus"+faultBus);
  var actual=Tiocr1ResetReferenceTest.csv(Files.readAllBytes(out.resolve("tiocr1-load-only"+(staggered?"-staggered":"")+"-fault.csv")));
  assertEquals(reference.size(),actual.size());
  assertEquals(1004,actual.size(),"Complete nonnegative grid and both event sides");
  double minLoad=reference.stream().mapToDouble(r->r.get("V2")).min().orElseThrow();
  if(faultBus==2)assertTrue(minLoad<.5,"Load-bus profile crosses the current-reduction knee");
  else assertTrue(minLoad>.5,"Opposite-location control leaves the load above the knee");
  double deadline=reference.stream().mapToDouble(r->r.get("VAR2")).filter(t->t<9999).min().orElseThrow();
  var actionRows=actual.stream().filter(r->Math.abs(r.get("time_s")-deadline)<5e-6).toList();
  assertEquals(2,actionRows.size(),"Exact pre/post physical action observations");
  assertEquals(0.,actionRows.get(0).get("RELAY.ShedFraction"));
  assertEquals(.1,actionRows.get(1).get("RELAY.ShedFraction"),1e-12);
  assertEquals(0.,actionRows.get(0).get("RELAY.Revision"));
  assertEquals(1.,actionRows.get(1).get("RELAY.Revision"));
  if(faultBus==2)assertTrue(actionRows.get(0).get("V2")<.5,"Action occurs on the reduced-current branch");
  for(String k:List.of("V1","V2","P_PU","Q_PU","ANGLE_DEG","VAR1")) {
   String akey=k.equals("VAR1")?"RELAY.Position":k;double sum=0,max=0,minref=Double.POSITIVE_INFINITY,maxref=Double.NEGATIVE_INFINITY;
   for(int i=0;i<actual.size();i++){var a=actual.get(i);var r=reference.get(i);assertEquals(r.get("time_s"),a.get("time_s"),5e-6);double d=a.get(akey)-r.get(k);sum+=d*d;max=Math.max(max,Math.abs(d));minref=Math.min(minref,r.get(k));maxref=Math.max(maxref,r.get(k));}
   double rmse=Math.sqrt(sum/actual.size()),normalized=rmse/(maxref-minref);
   if(k.equals("VAR1"))assertTrue(max<(staggered?3e-7:8e-5),"Complete relay position trajectory");
   else if(k.equals("ANGLE_DEG")){assertTrue(rmse<.01);assertTrue(rmse<9e-5);}
   else {assertTrue(normalized<.001);assertTrue(normalized<(staggered?5e-6:6e-5),k+" complete normalized error "+normalized);}
   System.out.println("DEEP "+staggered+" bus"+faultBus+" "+k+" peak="+max+" rmse="+Math.sqrt(sum/actual.size())+" nrmse="+Math.sqrt(sum/actual.size())/(maxref-minref)+" min="+minref);
  }
 }
}
