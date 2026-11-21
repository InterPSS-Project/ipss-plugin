package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Complete immutable zero-time and disabled-feedback histories and independent live-state endpoints. */
public final class Esst1aBypassReference {
    private Esst1aBypassReference() {}
    private static String resource(String name,int parts,boolean zipped,String digest) {
        try {
            var encoded=new ByteArrayOutputStream();
            for(int index=0;index<parts;index++)try(var stream=Esst1aBypassReference.class.getResourceAsStream("/esst1a-bypass/"+name+"."+index+".b64")) {
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
    public record Profile(String name,double step,double nativeStep,double end,int samples,double tau,double nativeKf,double increment,double liveFinalWashout,double liveFinalTime,String csvSha256,int csvParts,String inputSha256,int inputParts,String networkSha256,int networkParts) {
        public String body(){return resource(name+".csv",csvParts,true,csvSha256);}
        public String input(){return resource(name+".dyr",inputParts,false,inputSha256);}
        public String network(){return resource(name+".raw",networkParts,false,networkSha256);}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("zero-transducer-coarse",0.0005,0.0005000000237487257,0.5,1005,1.0,0.10000000149011612,0.02,0.1251995712518692,0.4999953508377075,"6dad678151ed70ee3ddaa1b4777c15db90313e3471c6cce0453debc1db774fc7",1,"f05dc374b34ae849b049a3b472fbf86bcaeda22de5b58e9e821cdb5f64d0fcef",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("zero-transducer-fine",0.00025,0.0002500000118743628,0.5,2005,1.0,0.10000000149011612,0.02,0.125202476978302,0.5000093579292297,"cea24039f100ddcc947c543e7429d65a5949fd8ad9bd2358e4c19a6ca9c35de9",2,"f05dc374b34ae849b049a3b472fbf86bcaeda22de5b58e9e821cdb5f64d0fcef",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("zero-regulator-coarse",0.0005,0.0005000000237487257,0.5,1005,1.0,0.10000000149011612,0.02,0.12534333765506744,0.4999953508377075,"ebbd8fad92551d250e3a7091ee3f6a07313eceed401b8cbd1ca68d9b78b268fb",1,"cbc1aa3f6c2b7ce3822dffadd6b790b9643939f58997db33be2a61dd844bc42b",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("zero-regulator-fine",0.00025,0.0002500000118743628,0.5,2005,1.0,0.10000000149011612,0.02,0.1253412961959839,0.5000093579292297,"9e5c018d496ddb80c4909ed1dcf04dd68a15649ad6402a9fcce45517be57922b",2,"cbc1aa3f6c2b7ce3822dffadd6b790b9643939f58997db33be2a61dd844bc42b",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("first-bypass-coarse",0.0005,0.0005000000237487257,0.5,1005,1.0,0.10000000149011612,0.02,0.12639746069908142,0.4999953508377075,"a285f8782c5bb8fd6c673f69c43f908eb464f1c2ad57409c141e33dde9de7b86",1,"711905b707ebe1daa3214da44bdb26a4e4247934ac0809d53f36a6e9b9389460",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("first-bypass-fine",0.00025,0.0002500000118743628,0.5,2005,1.0,0.10000000149011612,0.02,0.12640249729156494,0.5000093579292297,"1f6f1288d901fe72f1f144b84606b749df8d5fc06cf2d6672a2099110c5107e4",2,"711905b707ebe1daa3214da44bdb26a4e4247934ac0809d53f36a6e9b9389460",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("second-bypass-coarse",0.0005,0.0005000000237487257,0.5,1005,1.0,0.10000000149011612,0.02,0.12602363526821136,0.4999953508377075,"0101861b4a446c75c10fd05c9f4cc84c7d2405df193717f7754db4ad104fe95c",1,"300cc01cbaac3aff17e2aba4047231364d0c4f4a004373e0bf9197e567663a9e",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("second-bypass-fine",0.00025,0.0002500000118743628,0.5,2005,1.0,0.10000000149011612,0.02,0.126028910279274,0.5000093579292297,"3370a59950e9094124b03ab8449be9d84b5f3697885897b506c246fe7fbb6f4e",2,"300cc01cbaac3aff17e2aba4047231364d0c4f4a004373e0bf9197e567663a9e",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("zero-feedback-coarse",0.0005,0.0005000000237487257,0.5,1005,1.0,0.0,0.02,0.0,0.4999953508377075,"7f74406b6880427704dfdf625cf200e166367c9632404dd7d46655dc5464cbde",1,"802896290d9bb7a057c6ad65e2ab5423228156aa37ee4c8266c579642793abb2",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("zero-feedback-fine",0.00025,0.0002500000118743628,0.5,2005,1.0,0.0,0.02,0.0,0.5000093579292297,"573da9949e8f393c0a3dcd2bfa4ea5dfe7d0e27f42b9f3263b6d7d429383cbbb",2,"802896290d9bb7a057c6ad65e2ab5423228156aa37ee4c8266c579642793abb2",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("all-bypass-coarse",0.0005,0.0005000000237487257,0.5,1005,1.0,0.0,0.02,0.0,0.4999953508377075,"e421450d4cce70c83772a2dea50ca43d1aa548377e492cce1649e110af021cde",1,"81d89b0901a50b1d9ac81f95f49ca8a6b21b6eb62a087d281c5641ff105181d8",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1),
        new Profile("all-bypass-fine",0.00025,0.0002500000118743628,0.5,2005,1.0,0.0,0.02,0.0,0.5000093579292297,"3d981c3c747368a382440ccc39ccf1fcb53f57e7d07fb1355939d88af9d41ec8",2,"81d89b0901a50b1d9ac81f95f49ca8a6b21b6eb62a087d281c5641ff105181d8",1,"30edb29f02cbc597fcba616000a95e56df5ca1daf8177fc12ceba85cfa0b5c5c",1)
    );}
}
