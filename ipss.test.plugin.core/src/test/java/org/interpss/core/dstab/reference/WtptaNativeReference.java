package org.interpss.core.dstab.reference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.interpss.dstab.renewable.Wtpta1Data;

/** Native pitch states and boundary inputs with complete history/event rows. */
public final class WtptaNativeReference {
    private WtptaNativeReference() {}
    private static String resource(String path,boolean zipped,String expected) {
        try(var stream=WtptaNativeReference.class.getResourceAsStream("/wtpta-native/"+path+".b64")) {
            if(stream==null)throw new IllegalStateException("Missing pitch fixture: "+path);
            byte[] bytes=Base64.getMimeDecoder().decode(stream.readAllBytes());
            if(zipped)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))){bytes=input.readAllBytes();}
            String text=new String(bytes,StandardCharsets.UTF_8).replace("\r\n","\n").replace('\r','\n');
            if(!WtgibffraNativeReference.digest(text).equals(expected))throw new IllegalStateException("Changed pitch fixture: "+path);
            return text;
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
    public record Profile(String name,double step,int samples,double initialPitch,double[] parameters,String csvSha,String dyrSha,String rawSha) {
        public Profile { parameters=parameters.clone(); }
        @Override public double[] parameters(){return parameters.clone();}
        public String body(){return resource(name+".csv",true,csvSha);}
        public String input(){return resource(name+".dyr",false,dyrSha);}
        public String network(){return resource("network.raw",false,rawSha);}
        public Wtpta1Data data(){return new Wtpta1Data(parameters[0],parameters[1],parameters[2],parameters[3],parameters[4],parameters[5],parameters[6],parameters[7],parameters[8],parameters[9]);}
        public boolean hasOrderDriver(){return name.startsWith("order-");}
        public boolean hasFault(){return !hasOrderDriver()&&!name.startsWith("flat-");}
    }
    public static List<Profile> profiles(){return List.of(
        new Profile("flat-coarse",0.0005,1005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,10.0,-10.0},"1327a486fdd7cc10da2689f4dd4759180a2b252dfdb360799087569be1c96238","0695a8729b94edefc2a42ae390828c7646453625605c293c573afece0a360887","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("flat-fine",0.00025,2005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,10.0,-10.0},"e5b68176632dc4fe4cb5ddc1fa105dddc01b1dcc5ad493e09e784641d1541889","0695a8729b94edefc2a42ae390828c7646453625605c293c573afece0a360887","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("fault-coarse",0.0005,1005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,10.0,-10.0},"45b03bb4cadfaef53670c192de74215b00cbda423cc26494b786fa318f83596a","0695a8729b94edefc2a42ae390828c7646453625605c293c573afece0a360887","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("fault-fine",0.00025,2005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,10.0,-10.0},"57faa416bfd8217caf888165eb5c14270cc6a9952a90a9ba66e698b448453d27","0695a8729b94edefc2a42ae390828c7646453625605c293c573afece0a360887","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("order-upper-coarse",0.0005,1005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,2.005,0.0,10.0,-10.0},"142fd8ed807edcf2cb3bda61e3c9caa430e5313aba8483bbec68cee64ac52548","73d3985e20b9e8818262feb89054767b702e9e933d612f9ed222091aba0784a7","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("order-upper-fine",0.00025,2005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,2.005,0.0,10.0,-10.0},"e21c1c1d447615d775707cc309123a5d694d412930a24a40d1a6af8960ff5611","73d3985e20b9e8818262feb89054767b702e9e933d612f9ed222091aba0784a7","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("order-lower-coarse",0.0005,1005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,10.0,-10.0},"2a5d11efcb7bd06f9d7e60475442d242dc6727d5dc2c730f488a5389eb61407f","0695a8729b94edefc2a42ae390828c7646453625605c293c573afece0a360887","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("order-lower-fine",0.00025,2005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,10.0,-10.0},"c465c73c589da2ff74d14a404f2701bb329e9a26e8ca97a1afa4b7590f785954","0695a8729b94edefc2a42ae390828c7646453625605c293c573afece0a360887","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("upper-coarse",0.0005,1005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,2.005,0.0,10.0,-10.0},"45b03bb4cadfaef53670c192de74215b00cbda423cc26494b786fa318f83596a","73d3985e20b9e8818262feb89054767b702e9e933d612f9ed222091aba0784a7","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("upper-fine",0.00025,2005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,2.005,0.0,10.0,-10.0},"57faa416bfd8217caf888165eb5c14270cc6a9952a90a9ba66e698b448453d27","73d3985e20b9e8818262feb89054767b702e9e933d612f9ed222091aba0784a7","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("lower-coarse",0.0005,1005,0.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,10.0,-10.0},"cac03d76c71e4d8b186fb3487c95098dd4f41098c83ef20ee20b0403d5c0665c","37c9b2327a2779204e0aec2657dba02c692de831f5ae04909437f0c6964dd232","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("lower-fine",0.00025,2005,0.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,10.0,-10.0},"f6f97be2a04251d1de83dc5492001a3f691294afb85586c03860b4385377d0ca","37c9b2327a2779204e0aec2657dba02c692de831f5ae04909437f0c6964dd232","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("rate-coarse",0.0005,1005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,0.01,-0.01},"e12a0b6fb50617aed99fe3375d349d677bcf81c974c50fb0e7f0cb4a7eb3ad8b","dd1e349b797645616a9f43d56ee4e23d6150d97623f6e007bb46a9008feb607a","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("rate-fine",0.00025,2005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,0.01,-0.01},"fdbea380dae8697fbe452dbd518995ea767d826713a08a7d30dd76e13c8bcf01","dd1e349b797645616a9f43d56ee4e23d6150d97623f6e007bb46a9008feb607a","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("moving-coarse",0.0005,1005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,10.0,-10.0},"bb270826b4e7f86e2a65fc5bb5a603aad0d61a0e3764b739f89b2955b8d757de","74c8818a594b0ad9c15f4a0eafc2cfe6121b05531b7b9dfa84711a82bbc01c98","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("moving-fine",0.00025,2005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.3,27.0,0.0,10.0,-10.0},"44a60beced8234bc854e5b5aa03c02986cad1d0c3899ca6710df95241a644824","74c8818a594b0ad9c15f4a0eafc2cfe6121b05531b7b9dfa84711a82bbc01c98","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("zero-coarse",0.0005,1005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.0,27.0,0.0,10.0,-10.0},"9b0ab2f1c7cf1f58dbcb2ecfc721c177e81c722d260c91be89fab7f3e9721931","9697f47a1ea94ad1dba11f39982cc60aa6373ad45e94a88922a547c008f4f804","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("zero-fine",0.00025,2005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.0,27.0,0.0,10.0,-10.0},"a7d9817dbcc89f2f8fdaa0b81915f5c848eb20951367da6cd26742c33cca0d29","9697f47a1ea94ad1dba11f39982cc60aa6373ad45e94a88922a547c008f4f804","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("zero-rate-coarse",0.0005,1005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.0,27.0,0.0,0.01,-0.01},"9b0ab2f1c7cf1f58dbcb2ecfc721c177e81c722d260c91be89fab7f3e9721931","0bf5198c6f79b69d2c47f6f215b32c5dafd087981d8a80536d089e12e89de80e","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35"),
        new Profile("zero-rate-fine",0.00025,2005,2.0,new double[]{29.0,177.0,23.72,2.93,0.25,0.0,27.0,0.0,0.01,-0.01},"a7d9817dbcc89f2f8fdaa0b81915f5c848eb20951367da6cd26742c33cca0d29","0bf5198c6f79b69d2c47f6f215b32c5dafd087981d8a80536d089e12e89de80e","8332f09083835c5696163929a3814d75b5c378214c7b0ab1d5ce4ff8ba870e35")
    );}
}
