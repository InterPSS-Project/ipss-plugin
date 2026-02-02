package sample.contingency;


import org.interpss.plugin.pssl.plugin.IpssAdapter;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.aclf.AclfNetworkState;

/**
 * Extended Parallel Contingency Analysis Test with ACTIVSg25k bus system.
 * This test demonstrates large-scale contingency analysis using parallel processing.
 */
public class JSonCopy25kTest {
    
    public static void main(String[] args) throws InterpssException {
        // Initialize InterPSS
       // IpssCorePlugin.init();
       // IpssLogger.getLogger().setLevel(Level.INFO);
        
        System.out.println("=== Large-Scale Parallel Contingency Analysis Test ===");
        System.out.println("Testing with ACTIVSg25k bus system (25,000+ buses)");
        System.out.println("============================================================");
        
        // Load the ACTIVSg25k network
        long loadStartTime = System.currentTimeMillis();
        System.out.println("Loading ACTIVSg25k network...");
        
        //String filename = "ipss-plugin/ipss.test.plugin.core/testData/psse/v33/ACTIVSg25k.RAW";
        String filename = "testData/psse/v33/ACTIVSg25k.RAW";

        AclfNetwork net = IpssAdapter.importAclfNet(filename)
                .setFormat(PSSE)
                .setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
                .load()
                .getImportedObj();
        
        long loadEndTime = System.currentTimeMillis();
        System.out.println("Network loaded successfully in " + (loadEndTime - loadStartTime)*0.001 + " s");
        
        loadStartTime = System.currentTimeMillis();
        AclfNetworkState clonedNetBean = new AclfNetworkState(net);
        for (int i = 0; i < 100; i++) {
        	AclfNetwork copyNet = AclfNetworkState.create(clonedNetBean);
        }
        loadEndTime = System.currentTimeMillis();
        System.out.println("Network json copy(1) " + (loadEndTime - loadStartTime)*0.001 + " s");
        
        loadStartTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
        	AclfNetwork copyNet = net.jsonCopy();
        }
        loadEndTime = System.currentTimeMillis();
        System.out.println("Network json copy(2) " + (loadEndTime - loadStartTime)*0.001 + " s");
    }
}
