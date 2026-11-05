package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.Arrays;
import org.interpss.dstab.hvdc.Chvdc2Parameters;
import org.interpss.dstab.hvdc.Chvdc2Parameters.Constant;
import org.interpss.dstab.hvdc.Chvdc2Parameters.Dialect;
import org.interpss.fadapter.psse.dyr.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Source-schema checks only; a valid record is not evidence of a qualified dynamic model. */
public class Chvdc2ParametersTest {
    public static double[] constants() {
        return new double[]{50,150,90,5,.0005,50,150,110,.0005,.01,5,.005,.01,.005,.02,-.05,
                .95,.01,10,-10,.3,1.1,.4,.8,.05,.02,.1,.01,3500,500,10,.9,.035,.5,-.5,
                1.05,.85,.25,90,10,.85,.1,.9,.1,1500,500,500,0};
    }
    public static String text(boolean legacy) {
        return "'Link, O''Brien/West' "+(legacy ? "'USRDCL' 'CHVDC2U1' 18 1 3 48 18 24 " : "'CHVDC2' ")
                +"0 1 1 "+String.join(" ",Arrays.stream(constants()).mapToObj(Double::toString).toList())+" /";
    }
    private static PsseDyrRecord read(String text) throws Exception {
        return PsseDyrRecordReader.read(new StringReader(text),"synthetic.dyr").getFirst();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void exactNamedSourcePreservesAllSlotsAndDialect(boolean legacy) throws Exception {
        var record=read(text(legacy));var data=Chvdc2RecordMapper.prepare(record);
        assertTrue(record.hasNamedTarget());assertEquals(0,record.busNumber());
        assertEquals("Link, O'Brien/West",data.linkName());
        assertEquals(legacy?Dialect.CHVDC2U1:Dialect.CHVDC2,data.dialect());
        assertEquals(data.dialect().name(),record.canonicalModelName());
        assertEquals(legacy?57:51,record.parameterCount());
        assertArrayEquals(constants(),data.copySourceConstants());
        assertFalse(data.acVoltageCurrentLimitEnabled());assertTrue(data.rectifierMinimumAngleLogicEnabled());
        assertTrue(data.lineDynamicsEnabled());assertEquals(48,Constant.values().length);
        for(var c:Constant.values())assertEquals(constants()[c.ordinal()],data.value(c),0,c.name());
    }
    @Test void arraysAndSourceIdentityCannotBeChangedAfterDecoding() {
        var flags=new int[]{1,0,1};var values=constants();
        var p=new Chvdc2Parameters(" link ",Dialect.CHVDC2U1,flags,values);
        Arrays.fill(flags,0);Arrays.fill(values,0);
        Arrays.fill(p.copySourceFlags(),0);Arrays.fill(p.copySourceConstants(),0);
        assertArrayEquals(constants(),p.copySourceConstants());assertArrayEquals(new int[]{1,0,1},p.copySourceFlags());
        assertEquals("link",p.linkName());assertEquals(Dialect.CHVDC2U1,p.dialect());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void adjacentRecordsQuotedNamesCommentsAndLineLocationsSurvive(boolean legacy) throws Exception {
        String input="// comment\n1 'GENCLS' '1' 3 0 / "+text(legacy).replace("0 1 1 ","0 1\n1 ")
                +" 'trailing words' are not a record\n"+text(!legacy)+" 2 'GENCLS' '2' 4 0 /\n";
        var list=PsseDyrRecordReader.read(new StringReader(input),"synthetic.dyr");
        assertEquals(4,list.size());assertEquals(2,list.get(1).startLine());assertEquals(3,list.get(1).endLine());
        assertEquals(4,list.get(2).startLine());assertEquals(2,list.get(3).busNumber());
        Chvdc2RecordMapper.prepare(list.get(1));Chvdc2RecordMapper.prepare(list.get(2));
        var unknown=read("'Other Link' 'USRDCL' 'OTHER_DC' 18 1 0 0 0 0 /");
        assertTrue(unknown.hasNamedTarget());assertEquals("OTHER_DC",unknown.sourceModelName());
        assertThrows(IllegalArgumentException.class,()->Chvdc2RecordMapper.prepare(unknown));
        assertFalse(read("0 'GENCLS' '1' 3 0 /").hasNamedTarget());
    }
    @Test void legacyAllocationFieldsAreStrictIntegersAndExact() throws Exception {
        var original=read(text(true));
        for(int i=0;i<6;i++)for(String replacement:new String[]{"0","18.0","NaN"}){
            var fields=new java.util.ArrayList<>(original.parameters());fields.set(i,replacement);
            var bad=read("'Link' 'USRDCL' 'CHVDC2U1' "+String.join(" ",fields)+" /");
            assertLocatedFailure(bad);
        }
        assertThrows(IllegalArgumentException.class,()->Chvdc2RecordMapper.prepare(read(text(true).replace("'CHVDC2U1'","'CHVDC2'"))));
        assertThrows(IllegalArgumentException.class,()->Chvdc2RecordMapper.prepare(read("1 'CHVDC2' '1' 0 0 0 /")));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void cardinalityFlagsAndEveryNonfiniteConstantAreRejected(boolean legacy) throws Exception {
        var original=read(text(legacy));int offset=legacy?6:0;
        String header=legacy?"'link' 'USRDCL' 'CHVDC2U1' ":"'link' 'CHVDC2' ";
        var fields=new java.util.ArrayList<>(original.parameters());fields.removeLast();
        assertLocatedFailure(read(header+String.join(" ",fields)+" /"));
        fields=new java.util.ArrayList<>(original.parameters());fields.add("0");
        assertLocatedFailure(read(header+String.join(" ",fields)+" /"));
        for(int i=0;i<3;i++)for(String invalid:new String[]{"2","-1","0.0"}){
            fields=new java.util.ArrayList<>(original.parameters());fields.set(offset+i,invalid);
            assertLocatedFailure(read(header+String.join(" ",fields)+" /"));
        }
        for(int i=0;i<48;i++)for(String invalid:new String[]{"NaN","Infinity","-Infinity","bad"}){
            fields=new java.util.ArrayList<>(original.parameters());fields.set(offset+3+i,invalid);
            assertLocatedFailure(read(header+String.join(" ",fields)+" /"));
        }
    }
    @Test void physicalBoundsAndZeroSentinelsAreValidatedWithoutCorrection() {
        for(int index:new int[]{1,4,6,8,10,20,21,22,23,24,26,28,29,37,39}){
            var data=constants();data[index]=0;
            assertThrows(IllegalArgumentException.class,()->new Chvdc2Parameters("link",Dialect.CHVDC2,new int[3],data));
        }
        for(int index:new int[]{11,13,17,25,27,32,41,44,45,46,47}){
            var data=constants();data[index]=-1;
            assertThrows(IllegalArgumentException.class,()->new Chvdc2Parameters("link",Dialect.CHVDC2,new int[3],data));
            data[index]=0;
            assertEquals(0,new Chvdc2Parameters("link",Dialect.CHVDC2,new int[3],data).copySourceConstants()[index]);
        }
        var data=constants();data[30]=-1;
        assertEquals(-1,new Chvdc2Parameters("link",Dialect.CHVDC2,new int[3],data).value(Constant.GAMMA_CF_DEG));
        for(int[] pair:new int[][]{{3,2},{15,14},{19,18},{34,33},{36,35},{20,21},{22,23}}){
            var reversed=constants();reversed[pair[0]]=reversed[pair[1]]+1;
            assertThrows(IllegalArgumentException.class,()->new Chvdc2Parameters("link",Dialect.CHVDC2,new int[3],reversed));
        }
    }
    private static void assertLocatedFailure(PsseDyrRecord record) {
        assertTrue(assertThrows(IllegalArgumentException.class,()->Chvdc2RecordMapper.prepare(record)).getMessage().contains("synthetic.dyr:1"));
    }
}
