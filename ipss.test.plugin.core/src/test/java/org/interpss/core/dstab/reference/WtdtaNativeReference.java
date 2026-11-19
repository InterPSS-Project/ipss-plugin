package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.Wtdta1Data;

/** Four-state native mechanical references, retaining every raw history/event sample. */
public final class WtdtaNativeReference {
    private WtdtaNativeReference() {}
    private static String resource(String path,boolean zipped,String expected) {
        try(var stream=WtdtaNativeReference.class.getResourceAsStream("/wtdta-native/"+path+".b64")) {
            if(stream==null)throw new IllegalStateException("Missing native drive-train fixture: "+path);
            byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=input.readAllBytes();}
            String text=new String(bytes,StandardCharsets.UTF_8).replace("\r\n","\n").replace('\r','\n');
            if(!WtgibffraNativeReference.digest(text).equals(expected))throw new IllegalStateException("Changed native drive-train fixture: "+path);
            return text;
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
    public record Profile(String name,double step,int samples,int nominalHz,double h,double damp,double fraction,double torsionalHz,double shaftDamping,String csvSha,String dyrSha,String rawSha) {
        public String body(){return resource(name+".csv",true,csvSha);}
        public String input(){return resource(name+".dyr",false,dyrSha);}
        public String network(){return resource("network-"+nominalHz+".raw",false,rawSha);}
        public Wtdta1Data data(){return new Wtdta1Data(h,damp,fraction,torsionalHz,shaftDamping);}
        public boolean hasFault(){return !name.startsWith("flat-")&&!name.startsWith("curve-flat-");}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("flat-coarse",0.0005,1005,60,5.0,0.1,0.8,1.5,0.2,"7bd2a149c4041e2520f7e8103f71eaf2f1f6d98e676fe56babeeeedda58e391c","a61944dd5c5102acba29ca066e3847808bb899ba76916729380b1aa40a4ce830","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("flat-fine",0.00025,2005,60,5.0,0.1,0.8,1.5,0.2,"bb8ffdd204a0b7c8ca4893c397ff6f1b48d050db9004019f360d6293f10a8b74","a61944dd5c5102acba29ca066e3847808bb899ba76916729380b1aa40a4ce830","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("fault-coarse",0.0005,1005,60,5.0,0.1,0.8,1.5,0.2,"45565f7afa40fc16fd59e2427b4cb8803e42cfc6611f97bb30554630fb0e77f6","a61944dd5c5102acba29ca066e3847808bb899ba76916729380b1aa40a4ce830","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("fault-fine",0.00025,2005,60,5.0,0.1,0.8,1.5,0.2,"83f5970e81b8db5651a2181a38fdb9939fdacfb52be74093e43ffe4742cf214a","a61944dd5c5102acba29ca066e3847808bb899ba76916729380b1aa40a4ce830","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("curve-coarse",0.0005,1005,60,5.0,0.1,0.8,1.5,0.2,"c607dfd36f81453252b9b69f5aa9463362fbd66c186488aa898dc6b48d248908","eb4b0b43f4ec271fb268e1d632d60002ff3d84670b4c3ce652217cdf0110972d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("curve-fine",0.00025,2005,60,5.0,0.1,0.8,1.5,0.2,"55e5f03f44b63de06b3fb32c0b8181c8e2759cd6effb7d9d2430912e9b92df34","eb4b0b43f4ec271fb268e1d632d60002ff3d84670b4c3ce652217cdf0110972d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("curve-flat-coarse",0.0005,1005,60,5.0,0.1,0.8,1.5,0.2,"5ad4c0b96439a3ef721321b2a50d10a983ce9afe2b2e19520538b05dd61c8056","eb4b0b43f4ec271fb268e1d632d60002ff3d84670b4c3ce652217cdf0110972d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("curve-flat-fine",0.00025,2005,60,5.0,0.1,0.8,1.5,0.2,"8e46dec307774039123361a3d057d4f25db1f92828cb6cbacb5cce2364e188c4","eb4b0b43f4ec271fb268e1d632d60002ff3d84670b4c3ce652217cdf0110972d","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("single-coarse",0.0005,1005,60,5.0,0.1,0.0,1.5,0.2,"e5041e2b4dce53ab69663c3c69e70d0ab05b76e0ad4daf786b811a863c23ba0e","e4db246265702b8b8cd826c640fb1dfcab6c34e586fae0751b0d933cf27ebe83","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("single-fine",0.00025,2005,60,5.0,0.1,0.0,1.5,0.2,"fd8a8ecfd48f6d75414aa4bf9337639eb35857c91c05d4556cb20aaa56c8e9c7","e4db246265702b8b8cd826c640fb1dfcab6c34e586fae0751b0d933cf27ebe83","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("single-curve-coarse",0.0005,1005,60,5.0,0.1,0.0,1.5,0.2,"099d154545a94495bb1f434c113c83ed902944fa86281ba4088ba7b14bfdee7b","9b699f17be33d8c3479b794baa693b9111f23047781e055b1af8cc50c01ffd87","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("single-curve-fine",0.00025,2005,60,5.0,0.1,0.0,1.5,0.2,"a9998cd01809285ce464c67f886ae231b7e14457ee9baeff27a0bd08d40193c3","9b699f17be33d8c3479b794baa693b9111f23047781e055b1af8cc50c01ffd87","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("frequency-coarse",0.0005,1005,60,5.0,0.1,0.8,3.0,0.2,"0beea9cacd468ea20d3635803afafedb5e63572f7aa03b00cf3ffc275b5adf74","397044963a686c733539c54cd3ef85bfce5c2895e5229560a503ca61231ab053","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("frequency-fine",0.00025,2005,60,5.0,0.1,0.8,3.0,0.2,"352ff18ce220a174ed7b15cf5b620a08bb8d7b62216abbee3548fb95995125a5","397044963a686c733539c54cd3ef85bfce5c2895e5229560a503ca61231ab053","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("grid50-coarse",0.0005,1005,50,5.0,0.1,0.8,1.5,0.2,"184ec99a11908309ac2ea1e1cc9ef3d7f0afc8d34a4e5083dfa78313a5b62923","a61944dd5c5102acba29ca066e3847808bb899ba76916729380b1aa40a4ce830","a1ab93fd1ee8af10844dba593fd9441aeacaa2bb0760ff47f24dffb56b13e08d"),
        new Profile("grid50-fine",0.00025,2005,50,5.0,0.1,0.8,1.5,0.2,"860aa93e3204b90e975c7b0b8aeb681bcf924484d5aa0f2f1463976a44f80039","a61944dd5c5102acba29ca066e3847808bb899ba76916729380b1aa40a4ce830","a1ab93fd1ee8af10844dba593fd9441aeacaa2bb0760ff47f24dffb56b13e08d"),
        new Profile("undamped-coarse",0.0005,1005,60,5.0,0.0,0.8,1.5,0.2,"222dbdaca3203fa9c62d4f9efa6e5813c4696e4beeb406a02a127c27ff695e35","bf994e07bdbcc270a4b1457345f8e6cb5dc82c8e9591fe979b014e3f6e6ffa39","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("undamped-fine",0.00025,2005,60,5.0,0.0,0.8,1.5,0.2,"3b480fd7c1481a952dffec067968cd1b24901f0492dbcd85a5ca0e0dd2ecfbf0","bf994e07bdbcc270a4b1457345f8e6cb5dc82c8e9591fe979b014e3f6e6ffa39","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35")
    );}
}
