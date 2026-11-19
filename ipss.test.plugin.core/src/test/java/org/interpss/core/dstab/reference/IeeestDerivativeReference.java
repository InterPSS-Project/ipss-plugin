package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Complete derivative-input histories, including native voltage memory and held derivative. */
public final class IeeestDerivativeReference {
    private IeeestDerivativeReference() {}
    private static String resource(String name,int parts,boolean zipped,String digest) {
        try {
            var encoded=new ByteArrayOutputStream();
            for(int index=0;index<parts;index++)try(var stream=IeeestDerivativeReference.class.getResourceAsStream("/ieeest-derivative/"+name+"."+index+".b64")) {
                if(stream==null)throw new IllegalStateException("Missing input reference: "+name+" part "+index);
                encoded.writeBytes(stream.readAllBytes());
            }
            byte[] bytes=Base64.getMimeDecoder().decode(encoded.toByteArray());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=NativeFloatCsvCodec.decode(input.readAllBytes()).getBytes(StandardCharsets.UTF_8);}
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(!WtgibffraNativeReference.digest(text).equals(digest))throw new IllegalStateException("Changed input reference: "+name);
            return text;
        }catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    public record Profile(String name,double step,double nativeStep,double end,int samples,int mode,boolean fault,String csvSha256,int csvParts,String inputSha256,int inputParts,String networkSha256,int networkParts) {
        public String body(){return resource(name+".csv",csvParts,true,csvSha256);}
        public String input(){return resource(name+".dyr",inputParts,false,inputSha256);}
        public String network(){return resource(name+".raw",networkParts,false,networkSha256);}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("local-flat-coarse",0.0005,0.0005000000237487257,0.5,1005,6,false,"eb851502c2c926e766941af95c5ce3abe50375fe84478259f7f339d5abc8c925",1,"e9b626d52533d95b2cc9136956d25ddab9cb31575ec6af25ca256b81959da670",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("local-flat-fine",0.00025,0.0002500000118743628,0.5,2005,6,false,"f0e551834571add37a7c8648fe9b13371cf18f83eac36672802df413e75e4cfd",1,"e9b626d52533d95b2cc9136956d25ddab9cb31575ec6af25ca256b81959da670",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("local-fault-coarse",0.0005,0.0005000000237487257,0.5,1005,6,true,"dc187d6877fb3ee1bed6bda003a64d666542a41daab6dbd913b5ab343467b7cb",2,"e9b626d52533d95b2cc9136956d25ddab9cb31575ec6af25ca256b81959da670",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("local-fault-fine",0.00025,0.0002500000118743628,0.5,2005,6,true,"21f8e3bffab5789abb611f3efff3edcfdc2bb35b4ec4c830a3850f21c0941baf",3,"e9b626d52533d95b2cc9136956d25ddab9cb31575ec6af25ca256b81959da670",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("local-reference-coarse",0.0005,0.0005000000237487257,0.5,1005,6,false,"10621577492bd3d233c0d70817060cbc5688b07d11136d7055278ab77d2ca501",1,"e9b626d52533d95b2cc9136956d25ddab9cb31575ec6af25ca256b81959da670",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("local-reference-fine",0.00025,0.0002500000118743628,0.5,2005,6,false,"e49d7fc3e873f56f0a4f2e1587f1b525fccf9a7c1415a8299b0a25e78aa9a3eb",2,"e9b626d52533d95b2cc9136956d25ddab9cb31575ec6af25ca256b81959da670",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("remote-flat-coarse",0.0005,0.0005000000237487257,0.5,1005,6,false,"eb851502c2c926e766941af95c5ce3abe50375fe84478259f7f339d5abc8c925",1,"777705e6b42fd9398f7674067ca6f938934c1d9df774e56376ce26328be27c67",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("remote-flat-fine",0.00025,0.0002500000118743628,0.5,2005,6,false,"f0e551834571add37a7c8648fe9b13371cf18f83eac36672802df413e75e4cfd",1,"777705e6b42fd9398f7674067ca6f938934c1d9df774e56376ce26328be27c67",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("remote-fault-coarse",0.0005,0.0005000000237487257,0.5,1005,6,true,"6b9050ac05f16a7e83b9a49e3dbd8cc178abdc1750155430bcc614406e9c4f32",2,"777705e6b42fd9398f7674067ca6f938934c1d9df774e56376ce26328be27c67",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("remote-fault-fine",0.00025,0.0002500000118743628,0.5,2005,6,true,"4603365c90f02ddf069bfce5af38439596b650eec6afe98de0f3595890e6d04f",3,"777705e6b42fd9398f7674067ca6f938934c1d9df774e56376ce26328be27c67",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("remote-reference-coarse",0.0005,0.0005000000237487257,0.5,1005,6,false,"40443e29e973a549976a537122d33cf13e65b9cf41f8e3ed5cdc426774bb4e80",1,"777705e6b42fd9398f7674067ca6f938934c1d9df774e56376ce26328be27c67",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("remote-reference-fine",0.00025,0.0002500000118743628,0.5,2005,6,false,"5cf2b210f6248eba1a5bd225194fa5ec813c77977bec9c2a8f504e0f93677fc5",2,"777705e6b42fd9398f7674067ca6f938934c1d9df774e56376ce26328be27c67",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1)
    );}
}
