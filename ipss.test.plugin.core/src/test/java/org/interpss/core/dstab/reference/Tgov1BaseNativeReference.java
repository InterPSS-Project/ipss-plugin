package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Cold-paired native machine-base/damping profiles with all raw event and history rows. */
public final class Tgov1BaseNativeReference {
    private Tgov1BaseNativeReference() {}
    private static String resource(String path,boolean zipped,String expected) {
        try(var stream=Tgov1BaseNativeReference.class.getResourceAsStream("/tgov1-base-native/"+path+".b64")) {
            if(stream==null)throw new IllegalStateException("Missing TGOV1 base fixture: "+path);
            byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=input.readAllBytes();}
            String text=new String(bytes,StandardCharsets.UTF_8).replace("\r\n","\n").replace('\r','\n');
            if(!WtgibffraNativeReference.digest(text).equals(expected))throw new IllegalStateException("Changed TGOV1 base fixture: "+path);
            return text;
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
    public record Profile(String name,double rating,double damping,double step,int samples,double apply,double release,String csvSha,String rawSha,String dyrSha) {
        public String body(){return resource(name+".csv",true,csvSha);}
        public String network(){return resource(name+".raw",false,rawSha);}
        public String input(){return resource(name+".dyr",false,dyrSha);}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("50-zero-coarse",50,0.0,0.0005,1005,0.050000011920928955,0.19999919831752777,"7b41188e7227e437e9bd6e2478b04a5a02be4485127b14d09e59112d34128115","14898c871f8e35b1e3b6d476f37d43a2a91653c31457b33d0de1a72d503ff4e4","58776c6e48a274f3ac717eb72995d91b36ae42baee1e256208f24cfe77a3baf0"),
        new Profile("50-zero-fine",50,0.0,0.00025,2005,0.05000005662441254,0.19999831914901733,"f8ba90eedd04a0eba069f3c894a210e4eb527e92c9dc620253be08df07c5cf5f","14898c871f8e35b1e3b6d476f37d43a2a91653c31457b33d0de1a72d503ff4e4","58776c6e48a274f3ac717eb72995d91b36ae42baee1e256208f24cfe77a3baf0"),
        new Profile("50-zero-fine-tight",50,0.0,0.00025,2005,0.05000005662441254,0.19999831914901733,"a62381fe4fa9e6672c2077505f1c8bb6b002b1f37fe2f9cd4d262b90a4fa540a","14898c871f8e35b1e3b6d476f37d43a2a91653c31457b33d0de1a72d503ff4e4","58776c6e48a274f3ac717eb72995d91b36ae42baee1e256208f24cfe77a3baf0"),
        new Profile("50-damped-coarse",50,0.7,0.0005,1005,0.050000011920928955,0.19999919831752777,"ced16be90510da4caacca66c5720156c3278cd28c0a7937f368d3617f122e0b7","14898c871f8e35b1e3b6d476f37d43a2a91653c31457b33d0de1a72d503ff4e4","4f38bc4a832f650160b395b1987fd188e805bc8d5ebe04923df4b0690684d52c"),
        new Profile("50-damped-fine",50,0.7,0.00025,2005,0.05000005662441254,0.19999831914901733,"f506ff891cefe184518be0177c8422ec490cdf4bc4614ec83c6747d5119365dd","14898c871f8e35b1e3b6d476f37d43a2a91653c31457b33d0de1a72d503ff4e4","4f38bc4a832f650160b395b1987fd188e805bc8d5ebe04923df4b0690684d52c"),
        new Profile("200-zero-coarse",200,0.0,0.0005,1005,0.050000011920928955,0.19999919831752777,"ca7a62f8e1c94f53ffd655706fc0cd283eacafa0ea790320b899fef75d2fb177","7774c280a98fba8ae1e2c5b713f73c78d8dc9cf2ec5be133e7168c849c2c5ccb","58776c6e48a274f3ac717eb72995d91b36ae42baee1e256208f24cfe77a3baf0"),
        new Profile("200-zero-fine",200,0.0,0.00025,2005,0.05000005662441254,0.19999831914901733,"2cd83346eca2acece24a0c3acab1c6cf6c4d664eecb4fce6691190a64a2a435f","7774c280a98fba8ae1e2c5b713f73c78d8dc9cf2ec5be133e7168c849c2c5ccb","58776c6e48a274f3ac717eb72995d91b36ae42baee1e256208f24cfe77a3baf0"),
        new Profile("200-zero-fine-tight",200,0.0,0.00025,2005,0.05000005662441254,0.19999831914901733,"35cc2051bb9d0863813e9fecf80f666d84c3c750cbc53b87c32f254a6ba4cd2d","7774c280a98fba8ae1e2c5b713f73c78d8dc9cf2ec5be133e7168c849c2c5ccb","58776c6e48a274f3ac717eb72995d91b36ae42baee1e256208f24cfe77a3baf0"),
        new Profile("200-damped-coarse",200,0.7,0.0005,1005,0.050000011920928955,0.19999919831752777,"8dd9ce2f12d9d7629139bfa5c8a8c968bafed93c89567a22eab695501539198a","7774c280a98fba8ae1e2c5b713f73c78d8dc9cf2ec5be133e7168c849c2c5ccb","4f38bc4a832f650160b395b1987fd188e805bc8d5ebe04923df4b0690684d52c"),
        new Profile("200-damped-fine",200,0.7,0.00025,2005,0.05000005662441254,0.19999831914901733,"73a4c81cb292527aad226dc1b1279c78fa3150726fc6775dc8e8edfa6d353907","7774c280a98fba8ae1e2c5b713f73c78d8dc9cf2ec5be133e7168c849c2c5ccb","4f38bc4a832f650160b395b1987fd188e805bc8d5ebe04923df4b0690684d52c")
    );}
}
