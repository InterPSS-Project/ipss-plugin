package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Complete immutable controlled-washout histories and independent live-state endpoints. */
public final class Esst1aWashoutReference {
    private Esst1aWashoutReference() {}
    private static String resource(String name,int parts,boolean zipped,String digest) {
        try {
            var encoded=new ByteArrayOutputStream();
            for(int index=0;index<parts;index++)try(var stream=Esst1aWashoutReference.class.getResourceAsStream("/esst1a-washout/"+name+"."+index+".b64")) {
                if(stream==null)throw new IllegalStateException("Missing washout reference: "+name+" part "+index);
                encoded.writeBytes(stream.readAllBytes());
            }
            byte[] bytes=Base64.getMimeDecoder().decode(encoded.toByteArray());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=NativeFloatCsvCodec.decode(input.readAllBytes()).getBytes(StandardCharsets.UTF_8);}
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(!WtgibffraNativeReference.digest(text).equals(digest))throw new IllegalStateException("Changed washout reference: "+name);
            return text;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public record Profile(String name,double step,double nativeStep,double end,int samples,double tau,double nativeKf,double increment,double liveFinalWashout,String csvSha256,int csvParts,String inputSha256,int inputParts,String networkSha256,int networkParts) {
        public String body(){return resource(name+".csv",csvParts,true,csvSha256);}
        public String input(){return resource(name+".dyr",inputParts,false,inputSha256);}
        public String network(){return resource(name+".raw",networkParts,false,networkSha256);}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("tau-half-coarse",0.0005,0.0005000000237487257,3.0,6006,0.5,0.10000000149011612,0.2,0.19976790249347687,"546ebf0ff6c7ef6cd7798220cd2e6b8ca092447718d73d955f820efe5505d3a3",4,"97ba112441e0a6f01b0b8df5cd1b07eaa2542de05423ffd099931f6dbc204470",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("tau-half-fine",0.00025,0.0002500000118743628,3.0,12004,0.5,0.10000000149011612,0.2,0.19975049793720245,"bb675b7ab16faf89b28cef163cec85857afdbff9266972f6642fc6ec0ae42614",8,"97ba112441e0a6f01b0b8df5cd1b07eaa2542de05423ffd099931f6dbc204470",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("tau-one-coarse",0.0005,0.0005000000237487257,3.0,6006,1.0,0.10000000149011612,0.2,0.19572311639785767,"8bd33421c4ec61538a4bcda0ea296629111df26eec519a4b47f7b4fe5fe40286",4,"3dfa47357c6e48a374d71cdf8b521896b2ea7b69539d4dbe1e271ce48bfb30d2",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("tau-one-fine",0.00025,0.0002500000118743628,3.0,12004,1.0,0.10000000149011612,0.2,0.19571466743946075,"d07b98893e7c197dbe98e00618c2120d85d8c9241ca56a49bdb4d0848eaa12f4",8,"3dfa47357c6e48a374d71cdf8b521896b2ea7b69539d4dbe1e271ce48bfb30d2",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("tau-two-coarse",0.0005,0.0005000000237487257,3.0,6006,2.0,0.10000000149011612,0.2,0.1815420687198639,"bb200839cda9f2fe7388a54cffacedf382394eac8a2cd470f7f3f9a11ba91aee",4,"3445fda67d86df68be8a89e8e10c4dbfeddbeeb659c036582baaffdc0715a8f8",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("tau-two-fine",0.00025,0.0002500000118743628,3.0,12004,2.0,0.10000000149011612,0.2,0.18153968453407288,"f0e1f75f372cb979f6c69546601a8bfa5c7db9b78907f886277dd82db1e76a08",8,"3445fda67d86df68be8a89e8e10c4dbfeddbeeb659c036582baaffdc0715a8f8",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1)
    );}
}
