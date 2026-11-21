package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Exact native generator inputs and complete raw histories, not complete-model parity. */
public final class GenqecSchemaNativeReference {
    private GenqecSchemaNativeReference() {}
    private static String resource(String name,boolean zipped,String digest){
        try(var stream=GenqecSchemaNativeReference.class.getResourceAsStream("/genqec-schema-native/"+name+".b64")){
            if(stream==null)throw new IllegalStateException("Missing generator schema reference: "+name);
            byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=input.readAllBytes();}
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(!WtgibffraNativeReference.digest(text).equals(digest))throw new IllegalStateException("Changed generator schema reference: "+name);
            return text;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public record Profile(String name,double step,int samples,int selector,String csvSha256,String inputSha256){
        public String body(){return resource(name+".csv",true,csvSha256);}
        public String input(){return resource(name+".dyr",false,inputSha256);}
        public String network(){return resource("network.raw",false,"986c6b3c8201ff0c4581a60e48af3438a14c692c12e4f8b966f4f2c710daa02b");}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("standard-0-coarse",0.0005,605,0,"e1caa38d4be64e588d9c81817abe9007a89925b945d77b66813f40386db2f257","a0eacb6566ed97a6773f64375a0e00de614f371bf552bf941a3a390d9285459b"),
        new Profile("standard-0-fine",0.00025,1205,0,"a67605a96790787bdd5af4ffd7411ce2b355d717ce65884cbe07ca886ef63739","a0eacb6566ed97a6773f64375a0e00de614f371bf552bf941a3a390d9285459b"),
        new Profile("standard-1-coarse",0.0005,605,1,"9bd4b8f7ed5d63541984ecbdc947a615006add4b82b0ac226f58d45d02b8e57d","cb0c169a3031fae440a7c8a32361f53f98a8877b8492affe9a26f85d4ad0d803"),
        new Profile("standard-1-fine",0.00025,1205,1,"aab3f67ab0ec30845dd06c91f0ee9a40c2c5c28c6222829a1d94836552fb98c8","cb0c169a3031fae440a7c8a32361f53f98a8877b8492affe9a26f85d4ad0d803"),
        new Profile("standard-2-coarse",0.0005,605,2,"9bd4b8f7ed5d63541984ecbdc947a615006add4b82b0ac226f58d45d02b8e57d","dd839cf51326770d60aeedb7a3fd68882726d32165de1a5bc0724888a9d94a78"),
        new Profile("standard-2-fine",0.00025,1205,2,"aab3f67ab0ec30845dd06c91f0ee9a40c2c5c28c6222829a1d94836552fb98c8","dd839cf51326770d60aeedb7a3fd68882726d32165de1a5bc0724888a9d94a78"),
        new Profile("standard-negative-coarse",0.0005,605,-1,"9bd4b8f7ed5d63541984ecbdc947a615006add4b82b0ac226f58d45d02b8e57d","5ddbf7cd561462d9ea36599f2e0c52737a18321c10fde333d451e13fbc22de43"),
        new Profile("standard-negative-fine",0.00025,1205,-1,"aab3f67ab0ec30845dd06c91f0ee9a40c2c5c28c6222829a1d94836552fb98c8","5ddbf7cd561462d9ea36599f2e0c52737a18321c10fde333d451e13fbc22de43"),
        new Profile("wrapper-1-coarse",0.0005,605,1,"0bd93475df4d683ba82c03c600d61a28fec48706123d88c816ae91d78a601114","63f80d63ef39f2909170e7ad2fdf8311d4adcb2acdfc90a135aeac032fc5b5aa"),
        new Profile("wrapper-1-fine",0.00025,1205,1,"6916da7b06b6acbe4491a785fd99a8bf52358437dfd448faa9035eed861cc64d","63f80d63ef39f2909170e7ad2fdf8311d4adcb2acdfc90a135aeac032fc5b5aa")
    );}
}
