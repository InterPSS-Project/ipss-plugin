package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.interpss.dstab.renewable.Repcgfmc1Data;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;

/** Immutable native hybrid-plant profiles. Coupled import/lifecycle reference profiles; partial coverage only. */
public final class Repcgfmc1ImportNativeReference {
    private Repcgfmc1ImportNativeReference() {}
    public record Profile(String name,double step,int samples,String rawSha256,String inputSha256) {
        private byte[] resource(String suffix) throws IOException {
            try(var stream=Repcgfmc1ImportNativeReference.class.getResourceAsStream("/repcgfmc-import-native/"+name+"."+suffix+".b64")) {
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
        new Profile("flat-fine",0.00025,1205,"7677ced97c7fb397bf1f8a68c5cf9c8e665d0ef55f657976e75acfea5de024e9","319143fafd38c033abcbb712e3f932730dce1a5acfc425211c42f8f6d3d03d48"),
        new Profile("base-fine",0.00025,1205,"0402402f682d7aa4808447e1f68beb6b4eadc3dd963664c2d4492e5a01368065","3824285a8e54770debf42f3fc56b041430ec7692bdedfa4e758b9d161a9f1a86"),
        new Profile("trip-coarse",0.0005,606,"50eb8a39a7f4bc828cef2a64ddacd2a727728278fcab997bb19aaf785f352da2","319143fafd38c033abcbb712e3f932730dce1a5acfc425211c42f8f6d3d03d48"),
        new Profile("trip-fine",0.00025,1206,"947950a30f019d96495a0c65ef85b46a22c6845e04979fdbfa96659740449378","319143fafd38c033abcbb712e3f932730dce1a5acfc425211c42f8f6d3d03d48"),
        new Profile("fallback-circuit",0.0005,605,"3dd4f29145ab6ecf76a2f100f083a666f4989f52e02b3e71953bc0b30d92d3dc","38d4ba2b2bd84d3b319fc6a7ad8bc54d7d5520ac2da026f33c05927e5b44bec9")
    );}
}
