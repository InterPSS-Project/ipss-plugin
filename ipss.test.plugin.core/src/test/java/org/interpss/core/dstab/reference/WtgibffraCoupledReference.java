package org.interpss.core.dstab.reference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
/** Cold-paired native 36.7 governed-generator SMIB, 50 ms frequency filter.
 * All raw channel tokens retained at checkpoints; no trace resampling/fitting.
 * Tick excludes duplicate post-event samples, which retain explicit sides. */
public final class WtgibffraCoupledReference {
    private WtgibffraCoupledReference() {}
    public record Transition(int tick,double rawTime,int mode) {}
    public record Profile(String name,double step,int lastTick,int checkpoints,String rawSha256,String bodySha256,List<Transition> transitions) {
        public Profile {transitions=List.copyOf(transitions);}
        public String body() {
            try(var resource=WtgibffraCoupledReference.class.getResourceAsStream("/wtgibffra-kernel/coupled-"+name+".b64")) {
                if(resource==null)throw new IllegalStateException("Missing coupled WTGIBFFRA reference");
                try(var zip=new GZIPInputStream(new ByteArrayInputStream(Base64.getMimeDecoder().decode(resource.readAllBytes())))) {
                    String text=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
                    if(!WtgibffraNativeReference.digest(text).equals(bodySha256))throw new IllegalStateException("Changed coupled WTGIBFFRA reference");
                    return text;
                }
            }catch(IOException ex){throw new UncheckedIOException(ex);}
        }
    }
    public static List<Profile> profiles(){return List.of(
new Profile("coarse",0.0005,5001,582,"b308cccdca7c10fa5372e549d8e5a95c095e20141b1cedf6d038060c2f5208a0","296d280f9d294627741362c0fe51bddfdc2f6a743185371a252c94eeeedd4d31",List.of(new Transition(0,0.0,-1),new Transition(531,0.265498369932,1),new Transition(731,0.365497082472,2),new Transition(932,0.465995788574,3),new Transition(1132,0.56599843502,4),new Transition(3532,1.76596319675,-2),new Transition(3933,1.96644866467,-1),new Transition(3934,1.96694862843,1),new Transition(4134,2.06694149971,2),new Transition(4335,2.16743421555,3),new Transition(4535,2.26742696762,4))),
new Profile("fine",0.00025,10000,579,"23874fdbeb23af4c99a4e8f36ae9e4b212927fb189c5a8f4adc0e6f0926c0414","27b5fa7f149dcca3de593d69d159d793cfd10fb81600b605ac2bb1de758e777d",List.of(new Transition(0,0.0,-1),new Transition(1061,0.265248388052,1),new Transition(1461,0.365253061056,2),new Transition(1861,0.46525773406,3),new Transition(2261,0.565254628658,4),new Transition(7061,1.76516771317,-2),new Transition(7862,1.9654031992,-1),new Transition(7863,1.96565318108,1),new Transition(8263,2.06567716599,2),new Transition(8663,2.16571760178,3),new Transition(9063,2.26575803757,4))));}
}
