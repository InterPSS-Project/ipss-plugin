package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.algo.*;
import org.interpss.dstab.svc.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.core.acsc.fault.SimpleFaultCode;

/** Ordering discriminator; independent electrical-removal qualification is separate. */
public class Svsmo3t2MssTripOrderingTest extends CorePluginTestSetup {
    @Test void twoControllersSeparateBankMutationFromScheduledEvent(@TempDir Path temp) throws Exception {
        for (boolean staggered : new boolean[]{false,true}) for (String scenario : List.of("none","mss","scheduled")) {
            Path fixture=Path.of("testData/psse/v33/SVSMO3T2");
            Path folder=Files.createDirectory(temp.resolve(scenario+staggered));
            String raw=Files.readString(fixture.resolve("smib.raw"));
            raw=raw.replace("0 / END OF FIXED SHUNT DATA", "3,'S0',0,0,1\n0 / END OF FIXED SHUNT DATA");
            String facts=raw.lines().filter(l->l.startsWith("'STATCOM A'")).findFirst().orElseThrow();
            raw=raw.replace(facts,facts+"\n"+facts.replace("STATCOM A","STATCOM B"));
            Files.writeString(folder.resolve("network.raw"),raw);
            String dyr=Files.readString(fixture.resolve("smib.dyr"));
            Files.writeString(folder.resolve("models.dyr"),dyr+"\n"+dyr.substring(dyr.indexOf("'STATCOM A'")).replace("STATCOM A","STATCOM B"));
            var context=new PSSEMultiFileLoader().loadDStab(folder.resolve("network.raw").toString(),folder.resolve("models.dyr").toString());
            var net=context.getDStabilityNet(); var bus=net.getDStabBus("Bus3");
            var originals=bus.getDynamicBusDeviceList().stream().filter(Svsmo3t2Model.class::isInstance).map(Svsmo3t2Model.class::cast).toList();
            assertEquals(2, originals.size());
            bus.getDynamicBusDeviceList().removeAll(originals);
            var a=new Svsmo3t2Model(bus,bus,originals.get(0).getStaticDevice(),Svsmo3t2MssKernelTest.data(Map.of("tripVoltage",.99,"tripTime",.01),0));
            var b=new Svsmo3t2Model(bus,bus,originals.get(1).getStaticDevice(),Svsmo3t2MssKernelTest.data(Map.of("mssSwitchingFlag",scenario.equals("mss")?1:0,"mssUpperCurrent",-.1,"mssLowerCurrent",-.5,"mssSwitchingTime",.02,"mssBreakerTime",0.),1));
            bus.getDynamicBusDeviceList().add(a);bus.getDynamicBusDeviceList().add(b);
            var algo=context.getDynSimuAlgorithm(); net.setStaticLoadIncludedInYMatrix(true);net.setStaticLoadModel(StaticLoadModel.CONST_Z);net.setReactiveStaticLoadModel(StaticLoadModel.CONST_Z);
            algo.getAclfAlgorithm().setTolerance(1e-10);algo.getAclfAlgorithm().getLfAdjAlgo().setLocalContinuousSvcAsPv(true);assertTrue(algo.getAclfAlgorithm().loadflow());
            algo.setSimuStepSec(.0005);algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algo.setSimuOutputHandler(new StateMonitor());
            if(scenario.equals("scheduled"))net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1",net,SimpleFaultCode.GROUND_3P,new Complex(0,1e6),null,.02,.01),"scheduled");
            algo.setSolver(staggered?new StaggeredEulerSolver(algo):new DefaultDynamicSimulationSolver(algo));
            assertTrue(algo.initialization());a.setScheduledVoltage(a.getScheduledVoltage()+.03);
            int trip=-1,bank=-1,detach=-1;double currentAtBank=Double.NaN; Map<String,Double> frozen=null;var lines=new ArrayList<String>();lines.add("time,voltage,current,lag,tripped,detached,bank_operations");
            for(int i=0;i<100;i++){
                assertTrue(algo.solveDEqnStep(true));
                if(a.isTripped()&&trip<0)trip=i;
                if(b.getMssController().completionCount()>0&&bank<0){bank=i;currentAtBank=a.getCurrent();}
                if(a.isNetworkDetached()&&detach<0) { detach=i; frozen=a.getNamedStates(); }
                if(frozen!=null) {
                    assertEquals(frozen,a.getNamedStates(), "Detached coordinates stay frozen");
                    assertEquals(0,a.getCurrent(),0);
                }
                assertFalse(b.isNetworkDetached(), "The active switching peer remains attached");
                lines.add(algo.getSimuTime()+","+bus.getVoltageMag()+","+a.getCurrent()+","+a.getNamedStates().values().toArray()[2]+","+a.isTripped()+","+a.isNetworkDetached()+","+b.getMssController().completionCount());
            }
            Path output=Path.of("target/svs-mss-trip-"+scenario+"-"+staggered+".csv");Files.write(output,lines);
            assertTrue(trip>=0);
            if(scenario.equals("mss")) {
                assertTrue(bank>trip); assertEquals(bank,detach);
                assertEquals(1,b.getMssController().completionCount());
            } else if(scenario.equals("scheduled")) { assertTrue(detach>trip); assertEquals(-1,bank); }
            else { assertEquals(-1,detach); assertTrue(a.getCurrent()>0); }
            System.out.println("SVS_ORDER "+scenario+" staggered="+staggered+" trip="+trip+" bank="+bank+" detach="+detach+" currentAtBank="+currentAtBank);
        }
    }
    @Test void independentColdPairsDistinguishBankRemovalFromStoredState() throws Exception {
        byte[] bytes;
        try (var stream = getClass().getResourceAsStream("/dynamic-models/svsmo3t2/mss-trip-boundaries.zip")) {
            assertNotNull(stream); bytes = stream.readAllBytes();
        }
        assertEquals("c692d31dd5e40ea1f26cbde4f056af75fee1d807a33d3cfd899513dbcae8870c",
                HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        var files = new HashMap<String,String>();
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
                files.put(entry.getName(), new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        var removal = new HashMap<String,Double>();
        for (String scenario : List.of("none", "mss", "scheduled")) {
            var a = files.get(scenario + "-a/reference.csv").lines().map(l -> l.split(",")).toList();
            var b = files.get(scenario + "-b/reference.csv").lines().map(l -> l.split(",")).toList();
            assertEquals(scenario.equals("none") ? 306 : 307, a.size(), "Every source row plus header");
            assertEquals(a.size(), b.size()); assertArrayEquals(a.getFirst(), b.getFirst());
            var header = Arrays.asList(a.getFirst());
            int current = header.indexOf("A_VAR_0"), lag = header.indexOf("A_STATE_2");
            int transition = -1;
            for (int row = 1; row < a.size(); row++) {
                for (int column = 0; column < header.size(); column++) {
                    double actual = Double.parseDouble(a.get(row)[column]);
                    assertTrue(Double.isFinite(actual));
                    assertTrue(Double.isFinite(Double.parseDouble(b.get(row)[column])));
                    // Only unassigned MSS slot timers have undefined storage.
                    // The raw samples remain in the archive, without filtering.
                    if (!header.get(column).matches("[AB]_VAR_(1[3-9]|20)")
                            || (scenario.equals("mss") && header.get(column).equals("B_VAR_13")))
                        assertEquals(actual, Double.parseDouble(b.get(row)[column]), 0,
                                scenario + " row=" + row + " channel=" + header.get(column));
                }
                if (Double.parseDouble(a.get(row)[current]) == 0 && transition < 0) transition = row;
                if (transition >= 0) assertEquals(0, Double.parseDouble(a.get(row)[current]), 0);
            }
            assertTrue(transition > 1);
            var left = a.get(transition - 1); var right = a.get(transition);
            assertEquals(left[0], right[0], "Retain both physical event sides");
            assertTrue(Double.parseDouble(left[current]) > 0);
            assertEquals(left[lag], right[lag], "Removal changes injection, not stored lag at this boundary");
            assertTrue(Double.parseDouble(right[lag]) > 0);
            removal.put(scenario, Double.parseDouble(right[0]));
        }
        assertEquals(.069, removal.get("mss"), 2e-7);
        assertEquals(.075, removal.get("scheduled"), 2e-7);
        assertEquals(.1, removal.get("none"), 2e-7);
    }

}
