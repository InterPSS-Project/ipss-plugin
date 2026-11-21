package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import com.interpss.dstab.renewable.RegccModel;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonParser;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;

/** Bounded trajectory gates and explicitly unqualified diagnostics, retaining both event sides. */
class RegccReferenceTest extends CorePluginTestSetup {
    private static final TestConfig CONFIG=TestConfig.load("/regcc/settings.json","RegccReferenceTest");
    private static byte[] resource(String name)throws Exception {
        try(var in=RegccReferenceTest.class.getResourceAsStream("/regcc/"+name)){assertNotNull(in,name);return in.readAllBytes();}
    }
    private static byte[] decode(String name)throws Exception{return Base64.getMimeDecoder().decode(resource(name));}
    @Test void allStatesAndTerminalChannels(@TempDir Path directory)throws Exception {
        var profiles=JsonParser.parseString(new String(resource("profiles.json"),StandardCharsets.UTF_8)).getAsJsonArray();
        assertFalse(profiles.isEmpty());
        var failures=new ArrayList<String>();
        for(var item:profiles) {
            var profile=item.getAsJsonObject();String name=profile.get("name").getAsString();
            double step=profile.get("step_s").getAsDouble();
            byte[] body;try(var input=new GZIPInputStream(new ByteArrayInputStream(decode(name+".csv.gz.b64")))){body=input.readAllBytes();}
            assertEquals(profile.get("sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
            var lines=new String(body,StandardCharsets.UTF_8).lines().toList();assertEquals(profile.get("samples").getAsInt()+1,lines.size());
            String[] columns=lines.getFirst().split(",");var reference=new ArrayList<Map<String,Double>>();
            for(String line:lines.subList(1,lines.size())) {
                String[] fields=line.split(",");assertEquals(columns.length,fields.length);var row=new LinkedHashMap<String,Double>();
                for(int i=0;i<fields.length;i++){double value=Double.parseDouble(fields[i]);assertTrue(Double.isFinite(value));row.put(columns[i],value);}reference.add(row);
            }
            Path raw=directory.resolve(name+".raw"),dyr=directory.resolve(name+".dyr");
            Files.write(raw,decode(name+".raw.b64"));Files.write(dyr,decode(name+".dyr.b64"));
            var context=new PSSEMultiFileLoader().loadDStab(raw.toString(),dyr.toString());
            var net=context.getDStabilityNet();var algo=context.getDynSimuAlgorithm();
            assertTrue(algo.getAclfAlgorithm().loadflow());algo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            algo.setSimuStepSec(step);algo.setTotalSimuTimeSec(.2);algo.setSimuOutputHandler(new StateMonitor());
            var model=assertInstanceOf(RegccModel.class,((DStabGen)net.getBus("Bus1").getContributeGen("1")).getDynamicGenDevice());
            var actual=new LinkedHashMap<Long,List<double[]>>();
            Runnable capture=()->{
                double[] x=new double[17];System.arraycopy(model.getStateSnapshot(),0,x,0,9);
                var voltage=net.getBus("Bus1").getVoltage();var gen=model.getParentGen();
                var current=((Complex)model.getOutputObject()).subtract(voltage.divide(gen.getPosGenZ().multiply(gen.getZMultiFactor())));
                var power=voltage.multiply(current.conjugate()).multiply(net.getBaseMva()/gen.getMvaBase());
                x[9]=power.getReal();x[10]=power.getImaginary();
                for(int bus=1;bus<=3;bus++){x[9+2*bus]=net.getBus("Bus"+bus).getVoltageMag();x[10+2*bus]=Math.toDegrees(net.getBus("Bus"+bus).getVoltageAng());}
                actual.computeIfAbsent(Math.round(algo.getSimuTime()/step),ignored->new ArrayList<>()).add(x);
            };
            var solver=new DefaultDynamicSimulationSolver(algo){
                @Override protected void rebaseDynamicModelInputs(DynamicSimuMethod method)throws DStabSimuException{super.rebaseDynamicModelInputs(method);capture.run();}
            };
            // Record the pre-event side before the event is applied; the rebase records the post-event side.
            solver.setEventBoundaryObserver((time,left)->{if(left)capture.run();});
            algo.setSolver(solver);
            if(profile.get("fault").getAsBoolean())net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",net,
                    SimpleFaultCode.GROUND_3P,new Complex(0.,100./(230.*230./100.)),null,.05,.05),"Fault");
            assertTrue(algo.initialization());capture.run();double[] initial=model.getStateSnapshot();
            while(algo.getSimuTime()<.2-step/2){
                assertTrue(algo.solveDEqnStep(true));capture.run();
                // Separate no-event run calls also record the shared boundary twice.
                if(!profile.get("fault").getAsBoolean() && (Math.abs(algo.getSimuTime()-.05)<step/4
                        || Math.abs(algo.getSimuTime()-.10)<step/4)){
                    if(profile.has("commands")&&!profile.get("commands").isJsonNull()){
                        var commands=profile.getAsJsonArray(algo.getSimuTime()<.075?"commands":"initial_commands");
                        model.setCommands(commands.get(0).getAsDouble(),commands.get(1).getAsDouble());
                    }
                    capture.run();
                }
            }
            var used=new HashMap<Long,Integer>();double[] maximum=new double[17],times=new double[17];
            for(var expected:reference){
                double t=expected.get("time_s");if(t<0){for(int i=0;i<9;i++)assertEquals(expected.get("STATE"+i),initial[i],1e-5,name+" init "+i);continue;}
                long index=Math.round(t/step);int ordinal=used.merge(index,1,Integer::sum)-1;
                assertTrue(actual.containsKey(index),name+" missing time "+t);assertTrue(ordinal<actual.get(index).size(),name+" missing event side "+t);
                double[] observed=actual.get(index).get(ordinal),target=new double[17];
                for(int i=0;i<9;i++)target[i]=expected.get("STATE"+i);target[9]=expected.get("P_PU");target[10]=expected.get("Q_PU");
                for(int bus=1;bus<=3;bus++){target[9+2*bus]=expected.get("V"+bus);target[10+2*bus]=expected.get("A"+bus+"_DEG");}
                for(int i=0;i<17;i++){double error=Math.abs(observed[i]-target[i]);if(error>maximum[i]){maximum[i]=error;times[i]=t;}}
            }
            System.out.println(name+" maximum state0..8,P,Q,V1,A1,V2,A2,V3,A3 "+Arrays.toString(maximum));
            System.out.println(name+" maximum times "+Arrays.toString(times));
            boolean qualified=profile.get("acceptance").getAsString().equals("bounded-trajectory");
            System.out.println(name+" acceptance="+profile.get("acceptance").getAsString());
            // Mixed integration schemes differ most at the first point after a discontinuity.
            // The fixed-Te half-step pair establishes the order of that boundary error.
            double factor=step/.0005;
            double[] limits={.0007,.000055,.003,5e-6,.00022,.00055,.003,3e-6,.00012,.00065,.0085,.0012,.016,.00045,.0065,.000075,.0003};
            if(qualified) {
                for(int i=0;i<17;i++)if(maximum[i]>=limits[i]*factor)
                    failures.add(name+" channel "+i+" error "+maximum[i]+" >= "+limits[i]*factor);
            } else {
                // A diagnostic is never promoted by this test: retain and print every error.
                for(double error:maximum)assertTrue(Double.isFinite(error));
                if(CONFIG.bool("requireAllTrajectories"))for(int i=0;i<17;i++)
                    if(maximum[i]>=limits[i]*factor)failures.add(name+" unqualified channel "+i+" error "+maximum[i]);
            }
        }
        assertTrue(failures.isEmpty(),String.join("\n",failures));
    }
}
