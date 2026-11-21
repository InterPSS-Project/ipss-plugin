package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import com.interpss.dstab.mach.model.*;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Native selector/cardinality contract; typed three-way saturation remains separate. */
public class GenqecNativeSchemaTest extends org.interpss.CorePluginTestSetup {
    private static final String CONSTANTS="6.4 .032 .68 .043 4.4 .07 2.05 1.74 .34 .49 .089 .12 .05 .05 .24 .035";
    private static GenqecMachine parse(Path file,String text)throws Exception{
        var builder=DStabBuilderTestFixture.createBuilder();Files.writeString(file,text+" /\n");
        var parser=new PSSEDStabDirectParser(builder).setStrictImport(true);parser.parseDynFile(file.toString());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        return (GenqecMachine)builder.getDStabNetwork().getMachine("Bus1-mach1");
    }
    @Test void standardRecordHasOneSelectorAndSixteenConstants(@TempDir Path dir)throws Exception{
        assertEquals(17,DynamicModelCatalog.find("GENQEC").orElseThrow().parameterCount());
        for(int selector:new int[]{0,1,2,-1}){
            var machine=parse(dir.resolve("standard"+selector+".dyr"),"1 'GENQEC' '1' "+selector+" "+CONSTANTS);
            var data=machine.getGenqecData();assertEquals(selector==0?0:1,data.satFunc());assertEquals(.035,data.kw());assertEquals(0,data.accel());
            assertEquals(6.4,data.tdop());assertEquals(.032,data.tdopp());assertEquals(.68,data.tqop());assertEquals(.043,data.tqopp());assertEquals(4.4,data.h());
        }
    }
    @Test void wrappedNonzeroSelectorsAlwaysUseNativeScaledQuadratic(@TempDir Path dir)throws Exception{
        for(String alias:new String[]{"GENQECU","GENQEJU"})for(int selector:new int[]{0,1,2,-1}){
            var machine=parse(dir.resolve(alias+selector+".dyr"),"1 'USRMDL' '1' '"+alias+"' 1 1 1 16 6 1 "+selector+" "+CONSTANTS);
            assertEquals(selector==0?0:1,machine.getGenqecData().satFunc());
            assertEquals(.05,machine.getSatruationFactor(1),1e-12);assertEquals(.24,machine.getSatruationFactor(1.2),1e-12);
            if(machine instanceof GenqejMachine qej)assertEquals(.035,qej.getGenqejData().kis());else assertEquals(.035,machine.getGenqecData().kw());
        }
    }
    @Test void legacyFlatInterchangeIsNotAnExactNativeRecord(@TempDir Path dir){
        for(String name:new String[]{"GENQEC","GENQEJ"})assertThrows(Exception.class,()->parse(dir.resolve(name+".dyr"),"1 '"+name+"' '1' 0 "+CONSTANTS+" 1"));
    }
    @Test void badCardinalityAndFractionalSelectorAreRejected(@TempDir Path dir){
        for(String parameters:new String[]{"1 "+CONSTANTS+" .3","0.5 "+CONSTANTS,"1 6.4 .032"})
            assertThrows(Exception.class,()->parse(dir.resolve("bad"+parameters.length()+".dyr"),"1 'GENQEC' '1' "+parameters));
    }
    @Test void typedQuadraticSelectorIsNotReinterpretedAsNative(){
        var data=new GenqecData(4.4,.07,0,2.05,1.74,.34,.49,.089,.12,.05,6.4,.68,.032,.043,.05,.24,0,0,0,.035,2);
        var model=new GenqecMachine(data);assertEquals(2,data.satFunc());
        double alpha=Math.sqrt(.24/.05),threshold=(alpha-1.2)/(alpha-1),coefficient=.05/Math.pow(1-threshold,2);
        assertEquals(coefficient*Math.pow(1.1-threshold,2),model.getSatruationFactor(1.1),1e-12);
    }
}
