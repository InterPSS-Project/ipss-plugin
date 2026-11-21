package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Import acceptance is separate from the scoped trajectory evidence for these models. */
public class ReactiveDeviceDyrImportTest extends CorePluginTestSetup {
    @Test void t3AliasRetainsStrictImportAcceptance(@TempDir Path folder)throws Exception {
        var context=new PSSEMultiFileLoader().loadDStab("testData/psse/v35/PSSE_5Bus_svsmo1t2_synthetic.raw");
        var text=java.nio.file.Files.readString(Path.of("testData/psse/v35/PSSE_5Bus_svsmo1t2_synthetic.dyr"));
        assertTrue(text.contains("SVSMO1T2"));
        // T3 has an explicit switched-shunt ID; T2 selects the first switched shunt.
        var dyr=folder.resolve("alias.dyr");java.nio.file.Files.writeString(dyr,text.replace("'SVSMO1T2' 4","'SVSMO1T3' '1' 4"));
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(context.getDStabilityNet())).setStrictImport(true);
        parser.parseDynFile(dyr.toString());assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }
    @ParameterizedTest
    @CsvSource({"testData/adpter/psse/v33/SMIB/SMIB_v33_csvgn5.raw,testData/adpter/psse/v33/SMIB/SMIB_v33_csvgn5.dyr",
        "testData/psse/v35/PSSE_5Bus_svsmo1t2_synthetic.raw,testData/psse/v35/PSSE_5Bus_svsmo1t2_synthetic.dyr",
        "testData/psse/v33/SVSMO3T2/smib.raw,testData/psse/v33/SVSMO3T2/smib.dyr"})
    void validNativeRecordsAreAcceptedByStrictImport(String raw,String dyr)throws Exception {
        var context=new PSSEMultiFileLoader().loadDStab(Path.of(raw).toString());
        var parser=new PSSEDStabDirectParser(new DStabNetworkBuilder(context.getDStabilityNet())).setStrictImport(true);
        parser.parseDynFile(dyr);assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertTrue(parser.getLastImportReport().attachedCountsByModel().size()>0);
    }
}
