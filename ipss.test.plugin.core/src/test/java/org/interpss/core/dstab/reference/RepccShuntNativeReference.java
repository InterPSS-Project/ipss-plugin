package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.RepccData;
import org.interpss.fadapter.psse.dyr.PsseDyrRecordReader;

/** Native shunt profiles. Lockout datasets retain a documented source/tool discrepancy. */
public final class RepccShuntNativeReference {
    private RepccShuntNativeReference() {}
    public record Profile(String name,double step,int samples,int statusSamples,String csvSha256,String statusSha256,String inputSha256,boolean lockoutDiscrepancy) {
        private byte[] resource(String suffix) throws IOException {
            try(var stream=RepccShuntNativeReference.class.getResourceAsStream("/repcc-shunts-native/"+name+"."+suffix+".b64")) {
                if(stream==null)throw new IllegalStateException("Missing shunt reference "+name);
                return Base64.getMimeDecoder().decode(stream.readAllBytes());
            }
        }
        private String csv(String suffix,String hash) {
            try(var stream=new GZIPInputStream(new ByteArrayInputStream(resource(suffix)))) {
                String text=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(text).equals(hash))throw new IllegalStateException("Changed shunt reference "+name);
                return text;
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
        public String body(){return csv("csv",csvSha256);}
        public String status(){return csv("status",statusSha256);}
        public String input(){
            try{
                String text=new String(resource("dyr"),StandardCharsets.UTF_8);
                if(!WtgibffraNativeReference.digest(text).equals(inputSha256))throw new IllegalStateException("Changed shunt DYR");return text;
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
        public RepccData parameters(){
            try{
                var r=PsseDyrRecordReader.read(new StringReader(input()),name).stream().filter(row->row.sourceModelName().equals("REPCC")).findFirst().orElseThrow();
                var shunts=new ArrayList<RepccData.Shunt>();for(int i=0;i<10;i++)shunts.add(new RepccData.Shunt(r.intParameter(13+2*i),r.parameter(14+2*i)));
                double[] c=new double[57];for(int i=0;i<57;i++)c[i]=r.doubleParameter(35+i);
                return new RepccData(r.intParameter(0),new RepccData.Branch(r.intParameter(1),r.intParameter(2),r.parameter(3)),r.intParameter(4),r.intParameter(5),r.intParameter(6),r.intParameter(7),r.intParameter(8),r.intParameter(9),new RepccData.Branch(r.intParameter(10),r.intParameter(11),r.parameter(12)),shunts,r.intParameter(33),r.intParameter(34),c);
            } catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("negative-slow-ten-coarse",0.0005,1202,601,"09dbf554bc7b3fe50cf0762636e61ad7c454f3c733a0364b74f687b0535cfb39","337a6c07c129abef4b8770da9d75530a5517d19575d6e33192b97b20ca76ed30","c4aa31d995640566d54621677aeca7ef61cee06896a84f32b5b9a149f081cd32",false),
        new Profile("negative-slow-ten-fine",0.00025,2402,1201,"f68280f67055b557509d478c58fbe58c5832ff6a16248fb6feb7c748d00c6bfb","765ad6f895f6ad4a64ca1cf997e187a2345cdea67c25097f0fc9931366fa4626","c4aa31d995640566d54621677aeca7ef61cee06896a84f32b5b9a149f081cd32",false),
        new Profile("positive-slow-ten-coarse",0.0005,1202,601,"989d2aa475ba1210c5e9d8900f8c9e1ac6bee275666824b4ae4bb9e4ff04162e","71bfe500bce55ee310984127d14f84b2737b64ab370ea8c3c0d2d6f23864ef6d","7f224c1cb40a90d1f71419c075878057a1f67dcb5fd75f377e5c9e3099e47e5f",false),
        new Profile("positive-slow-ten-fine",0.00025,2402,1201,"2ecce6bf99f8590b19287fa6d4e412d919b5b821d7214288e262fdd32a19ce51","83579714edd7eafdee29313920cf68550ce847a88a838230f89b27ebf50640a7","7f224c1cb40a90d1f71419c075878057a1f67dcb5fd75f377e5c9e3099e47e5f",false),
        new Profile("negative-fast-ten-coarse",0.0005,1202,601,"4d8e4349b97eaa235991c865ba70f77496cbc5082370246f3c36aecdc747446d","8f1eb1289696ad4f4543f2fbf9dc968cd9b794bf3e086e345c2c1bb2d78676c9","f5973e3325a6996698fb1318b5a6c6239ec52fc2c7835384b339adeb45a1d318",false),
        new Profile("negative-fast-ten-fine",0.00025,2402,1201,"8b861587900b1f40350343ebfb367ad2e8a12ce46f1b9e143ad6e15e65f9431e","66ed816ebd5a889d0b99c71e45522b5c4ef64014f10b1ec71c45e439308a90d9","f5973e3325a6996698fb1318b5a6c6239ec52fc2c7835384b339adeb45a1d318",false),
        new Profile("positive-fast-ten-coarse",0.0005,1202,601,"5c74b0d5bd9c21a77e1906d00ff3d6c27c74d0ea739e7fa871d86e60828a5864","4f946dec1ee5436380fd00f9c908e5c780161911113303277cbb0de566d3c993","2e55b2d65eee087377a5f3a3beac291994f1b3fc62d7d324197e58b6da28f0ae",false),
        new Profile("positive-fast-ten-fine",0.00025,2402,1201,"a61902b8b9e72a1715f9a603e64fce23a6a8e4ad519a602a8fabc1fca743266c","8cf41c03d1b5b16ea75236d0eb3b30b46b37bf4a53e9e8f8aac5d4b55abd6c0e","2e55b2d65eee087377a5f3a3beac291994f1b3fc62d7d324197e58b6da28f0ae",false),
        new Profile("lockout-ten-coarse",0.0005,1202,601,"83aadf1b41d1a69584e2d283d17485edc7b4d2f84475cc5f692fbfd52f7b5b1e","34704c6a25a5746e311cd89b606d7d365ea6d6e3de9b88d2f7a24f757eb1edaf","67e4376f51d2153d4d013c75e69e875c582c34c0aa5b62408e488905631e5b78",true),
        new Profile("lockout-ten-fine",0.00025,2402,1201,"b23791bde1b91b2eef111bee96fa6a22ee850e2558389b70743c30e7951a2ff9","bb70c46b81310896d1b8fc8063410e4740b6447fdb14f6a434fd6fcfbe820aa5","67e4376f51d2153d4d013c75e69e875c582c34c0aa5b62408e488905631e5b78",true)
    );}
}
