package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.interpss.core.dstab.reference.ReeceLocalPiNativeReference;
import org.interpss.core.dstab.reference.ReeceBranchNativeReference;
import org.interpss.core.dstab.reference.WtgibffraNativeReference;
import org.interpss.dstab.renewable.Reece1Model;
import org.junit.jupiter.api.Test;

/** Full runtime replay for one QFLAG=2/PEFLAG=1 branch, not complete acceptance. */
public class ReeceRuntimeNativePartialConformanceTest {
    @Test void nativeVoltageCascadeAndPowerFactorLagBranchesUseTerminalInputsOnly() {
        Map<String,double[]> coarseByBranch=new HashMap<>();
        for(var profile:ReeceBranchNativeReference.profiles()) {
            var rows=profile.body().lines().map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
            assertEquals(profile.samples(),rows.size());double[] initial=rows.getFirst();
            var m=new Reece1Model(profile.parameters());m.initialize(initial[1],initial[2],initial[3]);
            double[] maximum=new double[10],at=new double[10];int posts=0;
            for(int i=0;i<rows.size();i++) {
                double[] now=rows.get(i);m.setReferenceSteps(now[4],now[5]);
                if(i==0||now[0]==rows.get(i-1)[0]){m.step(0,now[1],now[2],now[3],1);if(i>0)posts++;}
                else {
                    double[] before=rows.get(i-1);m.setReferenceSteps(before[4],before[5]);m.step(profile.step(),before[1],before[2],before[3],1,0);
                    m.setReferenceSteps(now[4],now[5]);m.step(profile.step(),now[1],now[2],now[3],1,1);
                }
                var state=m.getNamedStates();String[] names={"Vmeas","Pmeas","PIQ","PIV","Q_V","Pord","Vcomp","PIP"};
                for(int c=0;c<10;c++) {
                    double actual=c<8?state.get(names[c]):c==8?m.getIpcmd():nativeCallbackIq(m,now,i==0?now:rows.get(i-1));
                    double error=Math.abs(actual-now[6+c]);if(error>maximum[c]){maximum[c]=error;at[c]=now[0];}
                }
            }
            assertEquals(1,posts);System.out.println("REECE native branch "+profile.name()+" max="+Arrays.toString(maximum)+" times="+Arrays.toString(at));
            boolean limit=profile.name().startsWith("limit-");
            // Retain the ramp-rate budget, then tighten each channel against
            // the independently measured two-step-size envelope (~30% margin).
            double[] bounds=branchBounds(profile.name(),profile.step()==.0005);
            for(int c=0;c<10;c++)assertTrue(maximum[c]<Math.min(bounds[c],limit?3*99*profile.step():1),profile.name()+" channel "+c+": "+maximum[c]);
            String branch=profile.name().replace("-coarse","").replace("-fine","");
            if(profile.step()==.0005)coarseByBranch.put(branch,maximum);
            else for(int c:limit?new int[]{2,5,7,8,9}:new int[]{5,8})assertTrue(maximum[c]<.75*coarseByBranch.get(branch)[c],profile.name()+" convergence channel "+c);
        }
    }
    /** Native VAR14 is published before the newly exported terminal-Q update.
     * Refresh that algebraic boundary only; integration retains both endpoints. */
    private static double nativeCallbackIq(Reece1Model m,double[] now,double[] previous) {
        var state=m.getNamedStates();double pref=m.getPreparedPowerReference();
        m.step(0,now[1],previous[2],now[3],1);double iq=-m.getIqcmd();
        assertEquals(state,m.getNamedStates(),"Callback observation cannot alter a dynamic state");
        assertEquals(pref,m.getPreparedPowerReference(),0);
        m.step(0,now[1],now[2],now[3],1);assertEquals(state,m.getNamedStates());return iq;
    }
    @Test void nativeReactiveCallbackUsesPreviousTerminalQAndCurrentLimitFeedback() {
        for(var p:ReeceBranchNativeReference.profiles())if(p.name().startsWith("limit-lower")) {
            var rows=p.body().lines().map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
            double initialQ=rows.getFirst()[2],oldMaximum=0,sameMaximum=0;
            double imax=(float)p.parameters().value(org.interpss.dstab.renewable.Reece1Data.Parameter.IMAX);
            for(int i=1;i<rows.size();i++) {
                double[] now=rows.get(i),previous=rows.get(i-1);double target=Math.max(-1,Math.min(1,initialQ+now[5]));
                double limit=Math.sqrt(Math.max(0,imax*imax-previous[14]*previous[14]));
                double old=Math.max(-limit,Math.min(limit,now[8]+2*(target-previous[2])));
                double same=Math.max(-limit,Math.min(limit,now[8]+2*(target-now[2])));
                oldMaximum=Math.max(oldMaximum,Math.abs(old-now[15]));sameMaximum=Math.max(sameMaximum,Math.abs(same-now[15]));
            }
            assertTrue(oldMaximum<3e-7,p.name()+" callback identity residual "+oldMaximum);
            assertTrue(sameMaximum>.01,"Profile must discriminate the terminal measurement publication stage");
        }
    }
    private static double[] branchBounds(String name,boolean coarse) {
        double e=1e-12;
        if(name.startsWith("voltage-"))return coarse
                ?new double[]{3.1e-6,e,e,3.7e-5,e,2.32e-4,4.8e-6,e,2.30e-4,4.55e-5}
                :new double[]{6.2e-6,e,e,5.4e-5,e,9.81e-5,9.3e-6,e,9.71e-5,7.18e-5};
        if(name.startsWith("reactive-"))return coarse
                ?new double[]{3.1e-6,e,5.02e-5,6.52e-5,e,2.32e-4,4.8e-6,e,2.30e-4,1.06e-4}
                :new double[]{6.2e-6,e,2.14e-4,3e-4,e,9.81e-5,9.3e-6,e,9.71e-5,5.15e-4};
        if(name.startsWith("lag-"))return coarse
                ?new double[]{3.1e-6,2.45e-6,e,e,3.3e-7,2.32e-4,e,e,2.30e-4,3.3e-7}
                :new double[]{6.2e-6,1.52e-6,e,e,3.16e-7,9.81e-5,e,e,9.71e-5,3.16e-7};
        if(name.startsWith("limit-upper-"))return coarse
                ?new double[]{2.93e-6,5.9e-5,.00287,e,e,.01006,e,.00246,.00996,.00363}
                :new double[]{5.92e-6,1.47e-5,.00149,e,e,.00530,e,.00127,.00525,.00174};
        if(name.startsWith("limit-lower-"))return coarse
                ?new double[]{1.03e-4,8.64e-5,8.11e-4,e,e,.01634,e,.00246,.01632,.00186}
                :new double[]{2.63e-5,2.14e-5,4.16e-4,e,e,.00848,e,.00127,.00847,.000962};
        throw new IllegalArgumentException("Undeclared native REECE branch "+name);
    }
    @Test void frozenBranchBundleAndUpperIntegralSaturationAreIndependentlyRecorded() {
        var bundle=new StringBuilder();
        for(var p:ReeceBranchNativeReference.profiles()) {
            String body=p.body();bundle.append(p.rawSha256()).append('|').append(p.repeatRawSha256()).append('|').append(p.decodedSha256()).append('\n');
            if(p.name().startsWith("limit-upper")) {
                double[] last=Arrays.stream(body.lines().toList().getLast().split(",")).mapToDouble(Double::parseDouble).toArray();
                assertEquals(1,last[13],1e-7,"Native power integral reaches Pmax, not Pmax minus proportional contribution");
                assertEquals(Math.sqrt(1.3*1.3-last[14]*last[14]),last[8],3e-7,"Native local Q integral reaches the moving current limit");
            }
        }
        assertEquals(10,ReeceBranchNativeReference.profiles().size());
        assertEquals("aec01acb562034de98843a912086baea6d30f012b23cb1c1542dec50ab547784",WtgibffraNativeReference.digest(bundle.toString()));
    }
    @Test void nativeTerminalMeasurementsDriveControllerWithoutOraclePreparedSignals() throws Exception {
        double[] coarse=null;
        for(var profile:ReeceLocalPiNativeReference.profiles()) {
            String body=read(profile.name());
            var rows=body.lines().map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
            assertEquals(profile.samples(),rows.size());
            var m=new Reece1Model(profile.parameters());m.initialize(profile.initialP(),profile.initialQ(),profile.initialV());
            double[] maximum=new double[8],at=new double[8];int posts=0;
            for(int i=0;i<rows.size();i++) {
                double[] now=rows.get(i);m.setReferenceSteps(now[4],now[5]);
                if(i==0||now[0]==rows.get(i-1)[0]) {m.step(0,now[1],now[2],now[3],1);if(i>0)posts++;}
                else {
                    double[] before=rows.get(i-1);m.setReferenceSteps(before[4],before[5]);
                    m.step(profile.step(),before[1],before[2],before[3],1,0);
                    m.setReferenceSteps(now[4],now[5]);m.step(profile.step(),now[1],now[2],now[3],1,1);
                }
                var x=m.getNamedStates();
                double[] actual={x.get("Vmeas"),x.get("Pmeas"),x.get("PIQ"),x.get("Pord"),x.get("PIP"),m.getIpcmd(),-m.getIqcmd(),m.getPreparedPowerReference()};
                int[] oracle={6,7,8,11,13,14,15,16};
                for(int c=0;c<8;c++){double error=Math.abs(actual[c]-now[oracle[c]]);if(error>maximum[c]){maximum[c]=error;at[c]=now[0];}}
            }
            assertEquals(1,posts);System.out.println("REECE runtime replay "+profile.name()+" errors="+Arrays.toString(maximum)+" times="+Arrays.toString(at));
            // The seven integrated/output channels retain every event side.
            // Prepared Pref is a separate, explicitly UNACCEPTED native
            // zero-time callback difference, not an eighth parity channel.
            // The single-precision native voltage-filter floor grows with the
            // number of steps; it is not used as a convergence channel.
            double[] bounds=profile.step()==.0005?new double[]{3e-6,5e-6,1.6e-5,4.6e-4,4.8e-5,4.6e-4,1.6e-5}
                    :new double[]{5.8e-6,1.25e-6,8e-6,1.95e-4,2e-5,1.95e-4,8e-6};
            for(int c=0;c<7;c++)assertTrue(maximum[c]<bounds[c],"channel "+c+" error "+maximum[c]);
            assertTrue(maximum[7]>.02,"Preserve the known native event-post reference discrepancy");
            assertEquals(.27,m.getPreparedPowerReference(),3e-8);
            if(coarse==null)coarse=maximum;
            else for(int c:new int[]{2,3,4,5,6})assertTrue(maximum[c]<.85*coarse[c],"step-halving channel "+c);
        }
    }
    private static String read(String name) throws Exception {
        try(var stream=ReeceRuntimeNativePartialConformanceTest.class.getResourceAsStream("/reece-runtime/"+name+".b64")) {
            assertNotNull(stream);
            try(var zip=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(stream.readAllBytes())))) {
                String body=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
                String expected=name.equals("native-coarse")?"0df5512c15e63189ad6541cd69b1d06ef9555309a7312a3df67647b94ffc85fd":"f1ba51f3f411bc40e73dfbe0eef704e18eb17803c7c6c8a98e960cccd86b7f6e";
                assertEquals(expected,WtgibffraNativeReference.digest(body));return body;
            }
        }
    }
}
