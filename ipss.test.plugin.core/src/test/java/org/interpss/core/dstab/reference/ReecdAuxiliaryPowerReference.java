package org.interpss.core.dstab.reference;
import java.util.List;
/** Active-path native Paux evidence; inactive native PI/filter storage is retained, not fitted.
 * Inputs: terminal V, auxiliary power. Checkpoints: raw row,time,seven native
 * STATEs,Ipcmd,Iqcmd,Paux,V1,V2,V3,P,Q. Native positive-Q command is normalized
 * only at the InterPSS observation boundary, whose Iqcmd interface is negative-Q.
 */
public final class ReecdAuxiliaryPowerReference {
    private ReecdAuxiliaryPowerReference() {}
    public record Profile(String name,String kind,double step,double initialP,double initialQ,double initialV,
            int samples,List<Integer> postRows,String rawSha256,String inputsSha256,String checkpointsSha256,
            String inputs,String checkpoints){
        public Profile{postRows=List.copyOf(postRows);}
        public boolean isPost(int row){return postRows.contains(row);}
        public int tick(int row){return row-(int)postRows.stream().filter(p->row>=p).count();}
        public void verify(){verify(inputs,checkpoints);}
        public void verify(String in,String out){
            if(!inputsSha256.equals(WtgwgoaNativeReference.digest(in))||!checkpointsSha256.equals(WtgwgoaNativeReference.digest(out)))
                throw new IllegalArgumentException("Changed native REECD Paux reference: "+name);
        }
    }
    public static List<Profile> profiles(){return List.of(ReecdAuxiliaryPowerPart0.profile(),ReecdAuxiliaryPowerPart1.profile(),
            ReecdAuxiliaryPowerPart2.profile(),ReecdAuxiliaryPowerPart3.profile());}
}
