package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.Repcgfmc1Data;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;

/** Immutable native hybrid-plant profiles. Foundation evidence, not a runtime. */
public final class Repcgfmc1NativeContractReference {
    private Repcgfmc1NativeContractReference() {}
    public record Profile(String name,double step,int samples,String rawSha256,String inputSha256) {
        private byte[] resource(String suffix) throws IOException {
            try(var stream=Repcgfmc1NativeContractReference.class.getResourceAsStream("/repcgfmc-native-contract/"+name+"."+suffix+".b64")) {
                if(stream==null)throw new IllegalStateException("Missing hybrid-plant reference "+name);
                return Base64.getMimeDecoder().decode(stream.readAllBytes());
            }
        }
        public String body() {
            try(var stream=new GZIPInputStream(new ByteArrayInputStream(resource("csv")))) {
                String result=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(result).equals(rawSha256))throw new IllegalStateException("Changed hybrid-plant CSV");
                return result;
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
        public String input() {
            try {
                String result=new String(resource("dyr"),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(result).equals(inputSha256))throw new IllegalStateException("Changed hybrid-plant DYR");
                return result;
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
        public Repcgfmc1Data parameters() {
            try {
                var r=PsseDyrRecordReader.read(new StringReader(input()),name).stream()
                        .filter(row->row.sourceModelName().equals("REPCGFMC1U")).findFirst().orElseThrow();
                double[] c=new double[54];for(int i=0;i<54;i++)c[i]=r.doubleParameter(13+i);
                return new Repcgfmc1Data(r.intParameter(6),r.intParameter(7),r.intParameter(8),r.parameter(9),
                        r.intParameter(10),r.intParameter(11),r.intParameter(12),c);
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("branch-flat",0.0005,605,"f1dbeccd7af1751c1c3b26a486ec75a7b7604ee3cf868739e529842788d86bed","319143fafd38c033abcbb712e3f932730dce1a5acfc425211c42f8f6d3d03d48"),
        new Profile("branch-fault-coarse",0.0005,605,"eb2315501c9c7dc81825f0d89c731e5cb69114f2c5e121a2cfa1f07e43609c71","319143fafd38c033abcbb712e3f932730dce1a5acfc425211c42f8f6d3d03d48"),
        new Profile("branch-fault-fine",0.00025,1205,"77c241189e0498e472667aa222452e311d7260fe6bdadb9df6216bc7ccd90bbd","319143fafd38c033abcbb712e3f932730dce1a5acfc425211c42f8f6d3d03d48"),
        new Profile("auto-flat",0.0005,605,"fd4da7783af4a010f5b48584aa9ccbc00c20fe85cb470c6969c12de080cc8c70","cf015caf2584ea9e273544b2826918aa36155d38acad4c36fd2e2649757c59ee"),
        new Profile("fallback-flat",0.0005,605,"3dd4f29145ab6ecf76a2f100f083a666f4989f52e02b3e71953bc0b30d92d3dc","d35f7a1a4fd611df8c7563eced6fa8059b8f77a65b47613c1c0f5c065b31a824"),
        new Profile("base-flat",0.0005,605,"e50e159bfed9514a4a7fd2717fd22b855249be779b1c8ea19ef28a1161223f58","3824285a8e54770debf42f3fc56b041430ec7692bdedfa4e758b9d161a9f1a86"),
        new Profile("kp-only-flat",0.0005,605,"d6f58d8afa5f9f82fd19834329fe8bc83bef29e7f37803c68b316ec3e035159b","573837cc26a0d3096460d53e493231c26f81b0f6621d9379b42890734866657e"),
        new Profile("terminal-flat",0.0005,605,"cccdbf93f6fb7b941ac2989f5bdd226d6433b950220e1ea9fb0d7639fd9746db","849f9b829f56f0a3db63f4c8d1e11c4d6a804650db6a48b0162997b73cbd8bcc")
    );}
}
