package org.interpss.core.dstab.reference;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.interpss.dstab.renewable.Wttqa1Data;

/** Exact cold-paired controller histories; native plant-reference inputs are not torque outputs. */
public final class WttqaNativeReference {
    private WttqaNativeReference() {}
    private static String resource(String path,boolean zipped,String expected) {
        try(var stream=WttqaNativeReference.class.getResourceAsStream("/wttqa-native/"+path+".b64")) {
            if(stream==null)throw new IllegalStateException("Missing WTTQA1 fixture: "+path);
            byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=input.readAllBytes();}
            String text=new String(bytes,StandardCharsets.UTF_8).replace("\r\n","\n").replace('\r','\n');
            if(!WtgibffraNativeReference.digest(text).equals(expected))throw new IllegalStateException("Changed WTTQA1 fixture: "+path);
            return text;
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
    public record Profile(String name,String mode,double step,int samples,String csvSha,String dyrSha) {
        public String body(){return resource(name+".csv",true,csvSha);}
        public String input(){return resource(name+".dyr",false,dyrSha);}
        public Wttqa1Data data(){return mode.equals("curve")
            ?new Wttqa1Data(0,2,1,.02,.1,2,0,.1,.8,.2,.9,.4,1,.8,1.1,100)
            :new Wttqa1Data(mode.equals("power")?1:0,2,1,.02,.1,2,0,0,0,0,0,0,0,0,0,100);}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("flat-coarse","flat",0.0005,1005,"7fe0303a9aaa319298b8db09780f4ef0a35397dbada8ce85e169a2f72fd52d4d","7736c105b0780e5bbd97b44f21a9d5b3312dad088059a83cbe0dcf78824fdcd0"),
        new Profile("flat-fine","flat",0.00025,2005,"d2e71392001fd869bcf2b8fa46c53a50de1e32ab36c225016d4ed950185af174","7736c105b0780e5bbd97b44f21a9d5b3312dad088059a83cbe0dcf78824fdcd0"),
        new Profile("speed-coarse","speed",0.0005,1005,"d432ef3067994749e69f4d5dc752286979b6249b4bca605178c6f2bffe36c0d5","7736c105b0780e5bbd97b44f21a9d5b3312dad088059a83cbe0dcf78824fdcd0"),
        new Profile("speed-fine","speed",0.00025,2005,"b3f1780af5656e6c39420cd40dc5a14e81e6df06a0e18586bef9df40b25ed6b4","7736c105b0780e5bbd97b44f21a9d5b3312dad088059a83cbe0dcf78824fdcd0"),
        new Profile("power-coarse","power",0.0005,1005,"fcde30522ee32ff5fdad15f395dcd3c5d83c69fc0038029189294e5a3d05a1da","c7519a178c0851c2e4a40d3d082a3fb023162dc5f88540fe84e9116c39cd4dfd"),
        new Profile("power-fine","power",0.00025,2005,"cdbe2ca71874d54c69e29cffae81dcb1a9e85eb5ce9cba120b1743ecd36780b2","c7519a178c0851c2e4a40d3d082a3fb023162dc5f88540fe84e9116c39cd4dfd"),
        new Profile("curve-coarse","curve",0.0005,1005,"ae7d5ec86eb8f3669cda1afde897fbdcccf74d71630a36512ba2cf0f6fd5fd4e","b6bd777925d25a93fe7ead9e4f682a944d9ce4040ff4b2cf9fd8d987fb7df0b1"),
        new Profile("curve-fine","curve",0.00025,2005,"e3e481b03ad7fab33bf329b6ddeff2a620787f059df184aecd154627415a8583","b6bd777925d25a93fe7ead9e4f682a944d9ce4040ff4b2cf9fd8d987fb7df0b1"),
        new Profile("dip-coarse","dip",0.0005,1005,"bc1934023ed953304d8d996f24eb748195015a25dc44451b3c36269d84ddcb83","7736c105b0780e5bbd97b44f21a9d5b3312dad088059a83cbe0dcf78824fdcd0"),
        new Profile("dip-fine","dip",0.00025,2005,"81c99620dc61af4bde05c406d8029b49c8d4662eab44bf0cb5a9432af60949cb","7736c105b0780e5bbd97b44f21a9d5b3312dad088059a83cbe0dcf78824fdcd0")
    );}
    public static String network(){return resource("network.raw",false,"8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35");}
}
