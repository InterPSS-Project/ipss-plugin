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
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Regcau2Data;
import org.interpss.dstab.renewable.RenewableElectricalController;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonParser;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.DStabSimuException;

/** Independent fixed-command fault-current profiles, including both event sides. */
class Regcau2ReferenceTest extends CorePluginTestSetup {
    private static byte[] resource(String name) throws Exception {
        try(var input=Regcau2ReferenceTest.class.getResourceAsStream("/regcau2-impedance/"+name)) {
            assertNotNull(input,name);return input.readAllBytes();
        }
    }
    private static byte[] decode(String name) throws Exception {
        return Base64.getMimeDecoder().decode(resource(name));
    }
    private static List<Map<String,Double>> rows(String name,String sha) throws Exception {
        byte[] body;
        try(var input=new GZIPInputStream(new ByteArrayInputStream(decode(name+".csv.gz.b64")))) {body=input.readAllBytes();}
        assertEquals(sha,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
        var lines=new String(body,StandardCharsets.UTF_8).lines().toList();String[] names=lines.getFirst().split(",");
        var result=new ArrayList<Map<String,Double>>();
        for(String line:lines.subList(1,lines.size())) {
            String[] values=line.split(",");var row=new LinkedHashMap<String,Double>();
            for(int i=0;i<names.length;i++){double value=Double.parseDouble(values[i]);assertTrue(Double.isFinite(value));row.put(names[i],value);}
            result.add(row);
        }
        return result;
    }

    @Test void activeRecoveryLimitRetainsItsVoltageCoordinate() throws Exception {
        var profile=JsonParser.parseString(new String(resource("active-rate.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        var reference=rows("active-rate",profile.get("sha256").getAsString());
        assertEquals(profile.get("samples").getAsInt(),reference.size());
        int checked=0;
        for(int i=1;i<reference.size();i++) {
            var left=reference.get(i-1);var right=reference.get(i);
            double t=right.get("time_s"),dt=t-left.get("time_s");
            if(t>.06&&t<.09&&dt>0.) {
                double rate=(right.get("STATE1")-left.get("STATE1"))/dt;
                double quantization=2.*Math.ulp(left.get("STATE1").floatValue())/dt;
                assertEquals(.1,rate,quantization);checked++;
            }
        }
        assertTrue(checked>40);
        for(double q:new double[]{.2,-.2}) {
            var builder=DStabBuilderTestFixture.createBuilder();
            var data=new Regca1Data(0,.02,999.,.9,.4,1.22,10.,.8,.4,-99.,.02,0.,.1,-.1,.8);
            var model=builder.addRegcau2("Bus1","1",new Regcau2Data(data,.2));
            model.getParentGen().setGen(new Complex(.7,q));
            double[] command={0.};
            model.setActiveElectricalController(new RenewableElectricalController() {
                @Override public void initialize(double p,double iq,double v){command[0]=-iq/v;}
                @Override public void step(double dt,double p,double iq,double v,double f){ }
                @Override public double getIpcmd(){return .7;}
                @Override public double getIqcmd(){return command[0];}
                @Override public Repca1Model getPlantController(){return null;}
                @Override public void setPlantController(Repca1Model plant){ }
            });
            assertTrue(model.initStates(model.getDStabBus()));
            double before=model.getIqRegulatorState();command[0]=q>0?-2.:2.;
            assertTrue(model.nextStep(.0005,DynamicSimuMethod.MODIFIED_EULER,0));
            assertEquals(before+Math.copySign(.00005,q),model.getIqRegulatorState(),1e-12);
            assertEquals(Math.copySign(.1,q),model.getStateDerivativeSnapshot().rates().get("Reactive Voltage Regulator"),1e-12);
        }
    }

    @Test void allRecordedFaultCurrentSamplesAndCoupledEndpointsRetainXe(@TempDir Path directory) throws Exception {
        var profiles=JsonParser.parseString(new String(resource("profiles.json"),StandardCharsets.UTF_8)).getAsJsonArray();
        for(var item:profiles) {
            var profile=item.getAsJsonObject();String name=profile.get("name").getAsString();double step=profile.get("step_s").getAsDouble();
            var reference=rows(name,profile.get("sha256").getAsString());assertEquals(profile.get("samples").getAsInt(),reference.size());
            Path raw=directory.resolve(name+".raw"),dyr=directory.resolve(name+".dyr");
            Files.write(raw,decode("network.raw.b64"));Files.write(dyr,decode(name+".dyr.b64"));
            var context=new PSSEMultiFileLoader().loadDStab(raw.toString(),dyr.toString());
            var network=context.getDStabilityNet();var algorithm=context.getDynSimuAlgorithm();
            assertTrue(algorithm.getAclfAlgorithm().loadflow());
            algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);algorithm.setSimuStepSec(step);algorithm.setTotalSimuTimeSec(.2);
            algorithm.setSimuOutputHandler(new StateMonitor());
            var generator=(DStabGen)network.getBus("Bus1").getContributeGen("1");
            var model=assertInstanceOf(Regca1Model.class,generator.getDynamicGenDevice());
            assertEquals("REGCAU2",model.getModelName());assertEquals(profile.get("xe").getAsDouble(),model.getEffectiveReactance());
            var sourceBefore=generator.getPosGenZ();
            var actual=new LinkedHashMap<Long,List<double[]>>();
            Runnable capture=()->{
                double v=network.getBus("Bus1").getVoltageMag();
                actual.computeIfAbsent(Math.round(algorithm.getSimuTime()/step),ignored->new ArrayList<>())
                        .add(new double[]{v,model.getIp()*v,model.getIq()*v,model.getIpRegulatorState(),model.getIqRegulatorState()});
            };
            algorithm.setSolver(new DefaultDynamicSimulationSolver(algorithm) {
                @Override protected void rebaseDynamicModelInputs(DynamicSimuMethod method) throws DStabSimuException {
                    super.rebaseDynamicModelInputs(method);capture.run();
                }
            });
            double faultPu=100./(230.*230./100.);
            network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus2",network,
                    SimpleFaultCode.GROUND_3P,new Complex(0.,faultPu),null,.05,.05),"Fault");
            assertTrue(algorithm.initialization());capture.run();
            double initialIp=model.getIpRegulatorState(),initialEq=model.getIqRegulatorState();
            while(algorithm.getSimuTime()<.2-step/2.) {assertTrue(algorithm.solveDEqnStep(true));capture.run();}
            assertEquals(sourceBefore,generator.getPosGenZ());assertFalse(model.supportsUncompensatedNetworkSolve());
            double[] maximum=new double[5];var consumed=new HashMap<Long,Integer>();
            for(var expected:reference) {
                double time=expected.get("time_s");long index=Math.round(time/step);
                if(time<0) {
                    assertEquals(initialIp,expected.get("STATE0"),1e-6);assertEquals(initialEq,expected.get("STATE1"),1e-6);continue;
                }
                int ordinal=consumed.merge(index,1,Integer::sum)-1;
                assertTrue(actual.containsKey(index),name+" missing endpoint "+index);
                assertTrue(ordinal<actual.get(index).size(),name+" missing event side "+index+"/"+ordinal);
                double[] observed=actual.get(index).get(ordinal);
                double[] target={expected.get("V1"),expected.get("P_PU"),expected.get("Q_PU"),expected.get("STATE0"),expected.get("STATE1")};
                for(int i=0;i<maximum.length;i++)maximum[i]=Math.max(maximum[i],Math.abs(observed[i]-target[i]));
            }
            System.out.println(name+" maximum V/P/Q/IP/Eq differences "+Arrays.toString(maximum));
            for(int i=0;i<3;i++)assertTrue(maximum[i]<1e-3,name+" V/P/Q error "+maximum[i]);
            assertTrue(maximum[3]<1e-6);assertTrue(maximum[4]<1e-6);
        }
    }
}
