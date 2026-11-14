package org.interpss.core.dstab;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.interpss.core.dstab.reference.HplntdReferenceLimitNativeReference;
import com.interpss.dstab.renewable.HplntdKernel;

/** Only source-conforming Q/PF references are trajectory acceptance profiles. */
public class HplntdReferenceLimitNativeSmibTest {
    @Test void compareImportedQUpperAndPfLowerLimitResponses(@TempDir Path dir)throws Exception {
        HplntdSelectorNativeSmibTest.compareImportedProfiles(HplntdReferenceLimitNativeReference.acceptedProfiles(),
                p->HplntdReferenceLimitNativeReference.coupledBounds(p.name()),dir,"hplntd-reference-limit-coupled",(p,g)->{
                    var r=g.kernel().references();g.kernel().setReferences(new HplntdKernel.References(r.voltage(),
                            r.reactive()+(p.name().contains("q-upper")?2:0),r.frequency(),r.active(),
                            r.powerFactor()-(p.name().contains("pf-lower")?.5:0),r.voltageAux(),r.activeAux()));
                });
    }
}
