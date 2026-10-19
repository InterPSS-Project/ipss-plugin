package org.interpss.fadapter.psse.dyr;

import java.io.StringReader;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.interpss.dstab.renewable.*;
import static org.junit.jupiter.api.Assertions.*;

class HplntdNativeRecordsTest {
    private static double[] constants(){return new double[]{.02,1,5,0,.05,.7,0,.05,.02,1,-1,0,0,1,-1,1,.1,.02,-.0006,.0006,99,-99,2,-1,.1,20,20,1.08,.95,1,-1,99,-99,99,-99,99,-99,.8,-.8,99,-99,1,-1,.02,-.25,-.5,.3,.4,.5,.1,.06,120,.731,.02,.8,.015,-.015,173,.123,-.456,1.3};}
    private static String plant(){var fields=new ArrayList<>(List.of("2","'USRBUS'","'HPLNTDU'","504","0","35","61","13","45","8","-2","3","'C1'","0","2","1","1","0","1","-3","2","'C2'"));for(int i=0;i<10;i++){fields.add(Integer.toString(400+i));fields.add("'A"+i+"'");}fields.add("9");fields.add("0");for(double c:constants())fields.add(Double.toString(c));return String.join(" ",fields)+" /";}
    private static String unit(){return "1 'USRMDL' '1' 'REAXDU' 107 0 9 8 2 7 2 'HPLNTDU' 37 38 39 42 43 44 45 1.2 -.01 2.3 .04 1.5 -.2 .8 -.7 /";}
    private static List<String> tokens(String text){var matcher=java.util.regex.Pattern.compile("'(?:[^']|'')*'|[^\\s,]+").matcher(text.substring(0,text.lastIndexOf('/')));var tokens=new ArrayList<String>();while(matcher.find())tokens.add(matcher.group());return tokens;}
    private static String changed(String text,int field,String value){var tokens=tokens(text);tokens.set(field,value);return String.join(" ",tokens)+" /";}
    private static PsseDyrRecord record(String text){try{return PsseDyrRecordReader.read(new StringReader(text),"hybrid-fixture.dyr").getFirst();}catch(Exception e){throw new IllegalArgumentException(e);}}
    private static HplntdBindingPlan plan(String... texts){try{return HplntdNativeRecords.plan(PsseDyrRecordReader.read(new StringReader(String.join("\n",texts)),"hybrid-fixture.dyr"));}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    @Test void everyPlantAndDownstreamFieldIsDecodedWithoutReordering(){
        var p=HplntdNativeRecords.plant(record(plant()));var d=p.data();assertEquals(2,p.source().busNumber());assertEquals("*",p.source().deviceId());
        assertEquals(8,d.remoteBus());assertEquals(new RepccData.Branch(-2,3,"C1"),d.measurementBranch());assertEquals(new RepccData.Branch(-3,2,"C2"),d.switchingBranch());
        assertEquals(0,d.compensationFlag());assertEquals(2,d.referenceFlag());assertEquals(1,d.frequencyFlag());assertEquals(1,d.feedbackFlag());assertEquals(0,d.feedforwardFlag());assertEquals(1,d.switchingFlag());assertEquals(9,d.frequencyBus());assertEquals(0,d.reactiveFlag());
        for(int i=0;i<10;i++)assertEquals(new RepccData.Shunt(400+i,"A"+i),d.shunts().get(i));assertArrayEquals(constants(),d.constants(),0);d.constants()[0]=9;assertEquals(.02,d.constants()[0]);
        var u=HplntdNativeRecords.unit(record(unit()));assertEquals(1,u.source().busNumber());assertEquals("1",u.source().deviceId());assertEquals(2,u.data().plantBus());assertEquals("HPLNTDU",u.data().plantModel());assertArrayEquals(new int[]{37,38,39,42,43,44,45},u.data().relativeVariables());assertArrayEquals(new double[]{1.2,-.01,2.3,.04,1.5,-.2,.8,-.7},u.data().constants(),0);assertEquals(.001,u.data().activeTime(.0005));
    }
    @Test void allocationCardinalityWrapperAndNativeNameAreExact(){
        for(int i=3;i<9;i++){int field=i;String bad=changed(plant(),i,Integer.toString(Integer.parseInt(tokens(plant()).get(i))+1));assertThrows(IllegalArgumentException.class,()->HplntdNativeRecords.plant(record(bad)),"plant allocation "+field);}
        for(int i=4;i<10;i++){String bad=changed(unit(),i,Integer.toString(Integer.parseInt(tokens(unit()).get(i))+1));assertThrows(IllegalArgumentException.class,()->HplntdNativeRecords.unit(record(bad)));}
        for(String bad:List.of(changed(plant(),1,"'USRMDL'"),changed(plant(),2,"'HPLNTD'"),changed(plant(),0,"-2"),plant().replace(" /"," 0 /"),plant().replace("1.3 /","/")))assertThrows(IllegalArgumentException.class,()->HplntdNativeRecords.plant(record(bad)));
        for(String bad:List.of(changed(unit(),1,"'USRLOD'"),changed(unit(),3,"'REAXD'"),unit().replace(" /"," 0 /"),unit().replace("-.7 /","/")))assertThrows(IllegalArgumentException.class,()->HplntdNativeRecords.unit(record(bad)));
    }
    @Test void characterTypingFlagsAndNonfiniteValuesHaveLocatedErrors(){
        for(int field:new int[]{1,2,12,21,23}){String bad=changed(plant(),field,tokens(plant()).get(field).replace("'",""));assertThrows(IllegalArgumentException.class,()->HplntdNativeRecords.plant(record(bad)));}
        for(int field:new int[]{1,2,3,11}){String bad=changed(unit(),field,tokens(unit()).get(field).replace("'",""));assertThrows(IllegalArgumentException.class,()->HplntdNativeRecords.unit(record(bad)));}
        for(String value:new String[]{"NaN","Infinity","-.1"}){String bad=changed(plant(),44,value);var e=assertThrows(IllegalArgumentException.class,()->HplntdNativeRecords.plant(record(bad)));assertTrue(e.getMessage().contains("hybrid-fixture.dyr:1"));}
        assertThrows(IllegalArgumentException.class,()->HplntdNativeRecords.plant(record(changed(plant(),13,".5"))));
    }
    @Test void unusedShuntZeroMayBeUnquotedButActiveCharacterSlotsMayNot(){String text=plant();for(int i=0;i<10;i++){text=changed(text,22+2*i,"0");text=changed(text,23+2*i,"0");}var d=HplntdNativeRecords.plant(record(text)).data();for(var s:d.shunts())assertEquals(new RepccData.Shunt(0,"0"),s);}
    @Test void deferredLinksDoNotDependOnSourceOrderOrMutateCollections(){var a=plan(unit(),plant());var b=plan(plant(),unit());assertEquals(a.plants().keySet(),b.plants().keySet());assertEquals(HplntdBindingPlan.Status.LINKABLE,a.links().getFirst().status());assertSame(a.plants().get(2),a.links().getFirst().plant());assertEquals(1,a.downstream(2).size());assertThrows(UnsupportedOperationException.class,()->a.plants().clear());assertThrows(UnsupportedOperationException.class,()->a.links().clear());}
    @Test void duplicateOwnersAreRejectedAtomically(){var original=plan(plant(),unit());assertThrows(IllegalArgumentException.class,()->plan(plant(),plant(),unit()));assertThrows(IllegalArgumentException.class,()->plan(plant(),unit(),unit()));assertEquals(1,original.plants().size());assertEquals(1,original.links().size());}
    @Test void sourceDefinedInvalidLinksRemainExplicitlyIgnored(){assertEquals(HplntdBindingPlan.Status.IGNORED_MISSING_PLANT,plan(unit()).links().getFirst().status());assertEquals(HplntdBindingPlan.Status.IGNORED_PLANT_MODEL,plan(plant(),changed(unit(),11,"'OTHER'")).links().getFirst().status());for(int index:new int[]{0,-1,46}){var p=plan(plant(),changed(unit(),12,Integer.toString(index)));assertEquals(HplntdBindingPlan.Status.IGNORED_RELATIVE_VARIABLES,p.links().getFirst().status());assertEquals(index,p.links().getFirst().unit().data().relativeVariables()[0]);assertTrue(p.downstream(2).isEmpty());}}
    @Test void independentPlantsAndPlansDoNotShareRegistryState(){var secondPlant=changed(plant(),0,"5");var secondUnit=changed(changed(unit(),0,"4"),10,"5");var p=plan(secondUnit,plant(),unit(),secondPlant);assertEquals(Set.of(2,5),p.plants().keySet());assertEquals(1,p.downstream(2).size());assertEquals(4,p.downstream(5).getFirst().unit().source().busNumber());assertTrue(plan(unit()).plants().isEmpty());}
}
