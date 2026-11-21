package org.interpss.core.dstab.reference;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.interpss.dstab.renewable.WtgwgoaData;
/** Native 36.7 inputs and unmodified selected STATE/VAR observations. Mode labels
 * are inferred from recorded states/VARs, not exported native internal flags.
 * Discontinuous callback/handoff differences are retained as diagnostic evidence.
 * Input columns: terminal V, live plant output (or fixed initial reference).
 * Checkpoints: row, raw time, Vfilter, ramp1, ramp2, captured Pref, timer, stored Pref, mode.
 * Full raw channels/prehistory, scripts and cold provenance remain private.
 */
public final class WtgwgoaNativeReference {
    private WtgwgoaNativeReference() {}
    public record Transition(String mode,double rawTime) {}
    public record Profile(String name,WtgwgoaData parameters,double step,double initialVoltage,double initialReference,
            int samples,int checkpoints,List<Integer> postRows,List<Transition> transitions,
            String rawSha256,String inputsSha256,String statesSha256,String inputs,String states) {
        public Profile { postRows=List.copyOf(postRows);transitions=List.copyOf(transitions); }
        public boolean isPost(int row){return postRows.contains(row);}
        public int tick(int row){return row-(int)postRows.stream().filter(p->row>=p).count();}
        public void verify(){verify(inputs,states);}
        public void verify(String in,String out){if(!inputsSha256.equals(digest(in))||!statesSha256.equals(digest(out)))throw new IllegalArgumentException("Native WGO reference mutation: "+name);}
    }
    public static String digest(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    public static List<Profile> profiles(){return List.of(WtgwgoaNativePart0.profile(), WtgwgoaNativePart1.profile(), WtgwgoaNativePart2.profile(), WtgwgoaNativePart3.profile(), WtgwgoaNativePart4.profile(), WtgwgoaNativePart5.profile(), WtgwgoaNativePart6.profile(), WtgwgoaNativePart7.profile(), WtgwgoaNativePart8.profile(), WtgwgoaNativePart9.profile());}
}
