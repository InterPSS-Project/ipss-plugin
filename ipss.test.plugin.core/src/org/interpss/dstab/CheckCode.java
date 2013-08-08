package org.interpss.dstab;

/*
InterPSS implementation of the IEEE 1968 Type-1 excitation system
    Copyright www.interpss.org 2007
*/

import java.lang.reflect.Field;

import org.interpss.dstab.control.cml.block.DelayControlBlock;
import org.interpss.dstab.control.cml.block.WashoutControlBlock;
import org.interpss.dstab.control.cml.func.SeFunction;

import com.interpss.dstab.controller.AnnotateExciter;
import com.interpss.dstab.controller.annotate.AnController;
import com.interpss.dstab.controller.annotate.AnControllerField;
import com.interpss.dstab.controller.annotate.AnFunctionField;
import com.interpss.dstab.datatype.CMLFieldEnum;

@AnController(
input="this.refPoint - mach.vt + pss.vs - this.washoutBlock.y",
output="this.delayBlock.y",
refPoint="this.kaDelayBlock.u - pss.vs + mach.vt + this.washoutBlock.y",
display= { "str.kaDelayBlock, this.kaDelayBlock.y",
          "str.seFunc, this.seFunc.y",  
          "str.delayBlock, this.delayBlock.y",
          "str.washoutBlock, this.washoutBlock.y" 
        }
)
    
// do not modify this tag line

public class CheckCode extends AnnotateExciter {  // do not modify this tag line        

public double ka = 50.0, ta = 0.06, vrmax = 2.0, vrmin = -0.9;
@AnControllerField(
  type= CMLFieldEnum.ControlBlock,
  input="this.refPoint + pss.vs - mach.vt - this.washoutBlock.y",
  parameter={"type.NonWindup", "this.ka", "this.ta", "this.vrmax", "this.vrmin"},
  y0="this.delayBlock.u + this.seFunc.y"	)
DelayControlBlock kaDelayBlock;

public double ke1 = 1.0 /* ke1 = 1/Ke  */, te_ke = 0.46 /* te_ke = Te/Ke */;
@AnControllerField(
  type= CMLFieldEnum.ControlBlock,
  input="this.kaDelayBlock.y - this.seFunc.y",
  parameter={"type.NoLimit", "this.ke1", "this.te_ke"},
  y0="mach.efd"	)
DelayControlBlock delayBlock;

public double e1 = 3.1, se_e1 = 0.33, e2 = 2.3, se_e2 = 0.1;
@AnFunctionField(
  input= {"this.delayBlock.y"},
  parameter={"this.e1", "this.se_e1", "this.e2", "this.se_e2"}	)
SeFunction seFunc;

public double kf = 0.1, tf = 1.0, k = kf/tf;
@AnControllerField(
  type= CMLFieldEnum.ControlBlock,
  input="this.delayBlock.y",
  parameter={"type.NoLimit", "this.k", "this.tf"},
  feedback = true	)
WashoutControlBlock washoutBlock;

public AnController getAnController() {
return (AnController)getClass().getAnnotation(AnController.class);  }
public Field getField(String fieldName) throws Exception {
return getClass().getField(fieldName);   }
public Object getFieldObject(Field field) throws Exception {
return field.get(this);    }
}  // do not modify this tag line   
