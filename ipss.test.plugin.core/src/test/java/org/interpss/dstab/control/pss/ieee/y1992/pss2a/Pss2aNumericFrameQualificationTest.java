package org.interpss.dstab.control.pss.ieee.y1992.pss2a;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import com.interpss.dstab.controller.cml.field.ICMLControlBlock;
import com.interpss.dstab.controller.cml.wrapper.ControlBlockFieldAnWrapper;

public class Pss2aNumericFrameQualificationTest {
    @Test void customWrapperObservationsRequireOriginalCascadeProtocol() throws Exception {
        var plans=new ArrayList<Pss2aDirectEvaluation>();var logs=List.of(new ArrayList<List<Double>>(),new ArrayList<List<Double>>());
        for(int i=0;i<2;i++){
            var p=Pss2aNumericFrameTest.plan(i==1,3,1,.05).model;
            var fields=p.customBlock1.getFieldWrapperList();int index=0;
            while(!fields.get(index).getFieldName().equals("washoutBlock1"))index++;
            var original=(ControlBlockFieldAnWrapper)fields.get(index);var log=logs.get(i);
            var replacement=new ControlBlockFieldAnWrapper("washoutBlock1"){
                @Override public ICMLControlBlock getField(){
                    var values=new ArrayList<Double>();
                    for(var child:p.filterNthBlock.getIntegrationBlocks())values.add(child.getU());
                    log.add(values);return super.getField();
                }
            };
            replacement.setField(original.getField());replacement.setInitOrder(original.getInitOrder());fields.set(index,replacement);
            plans.add(Pss2aNumericFrameTest.construct(p,i==1));log.clear();
        }
        for(int k=0;k<20;k++){
            for(var p:plans){p.model.input1Signal=.01*k;p.model.input2Signal=-.02*k;}
            Pss2aNumericFrameTest.advancePair(plans.get(0),plans.get(1),true,.001);
            Pss2aNumericFrameTest.advancePair(plans.get(0),plans.get(1),false,.001);
        }
        assertEquals(logs.get(0),logs.get(1));assertFalse(logs.get(0).isEmpty());
        assertNull(plans.get(1).numericFrame,"Custom wrapper getters must retain the original protocol");
    }
    @Test void ordinaryModelAdaptersObserveMatchingStateAtTheirExistingBoundaries() throws Exception {
        var a=Pss2aNumericFrameTest.plan(false,3,1,.05);var b=Pss2aNumericFrameTest.plan(true,3,1,.05);
        var logs=List.of(new ArrayList<String>(),new ArrayList<String>());int index=0;
        for(var p:List.of(a,b)){
            var log=logs.get(index++);var adapter=new AdapterImpl(){@Override public void notifyChanged(Notification n){
                if(n.getEventType()!=Notification.REMOVING_ADAPTER)log.add(n.getFeatureID(null)+":"+n.getOldValue()+":"+n.getNewValue());
            }};
            p.model.eAdapters().add(adapter);p.model.customBlock1.eAdapters().add(adapter);p.model.customBlock2.eAdapters().add(adapter);
        }
        for(int k=0;k<20;k++){
            for(var p:List.of(a,b)){p.model.input1Signal=.01*k;p.model.setRefPoint(k*.002);}
            Pss2aNumericFrameTest.advancePair(a,b,true,.001);Pss2aNumericFrameTest.advancePair(a,b,false,.001);
        }
        assertEquals(logs.get(0),logs.get(1));assertEquals(0,b.numericStages);
    }
}
