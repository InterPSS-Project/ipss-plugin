 /*
  * @(#)AnnotationExciterTests.java   
  *
  * Copyright (C) 2006 www.interpss.org
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author Mike Zhou
  * @Version 1.0
  * @Date 06/01/2018
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.dstab.cml.controller;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.interpss.core.dstab.DStabTestSetupBase;
import org.interpss.core.dstab.cml.controller.util.DStabTestUtilFunc;
import org.interpss.core.dstab.cml.controller.util.TestAnnotateExciter;
import org.junit.Test;

import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType;
import com.interpss.dstab.controller.cml.field.adapt.CMLFunctionAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.func.SeFunction;
import com.interpss.dstab.controller.cml.wrapper.BlockFieldAnWrapper;
import com.interpss.dstab.controller.cml.wrapper.FunctionFieldAnWrapper;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.datatype.CMLVarEnum;
import com.interpss.dstab.datatype.ExpCalculator;
import com.interpss.dstab.mach.Machine;

public class AnnotationExciterTests extends DStabTestSetupBase {
	/* 
	 * Part-0 : Testing annotation filed and parameters
	 * ================================================ 
	 */
	@Test
	public void parameterTest() throws Exception {
		TestAnnotateExciter exe = new TestAnnotateExciter();
		/*
		public double k = 50.0, t = 0.05, vmax = 10.0, vmin = 0.0;
			 */
		assertTrue("", exe.getDoubleField("k") == 50.0);
		assertTrue("", exe.getDoubleField("t") == 0.05);
		assertTrue("", exe.getDoubleField("vmax") == 10.0);
		assertTrue("", exe.getDoubleField("vmin") == 0.0);
	}
	
	@Test
	public void classAnnotationTest() {
		TestAnnotateExciter exe = new TestAnnotateExciter();
		/*
		@AnController(
        	input="pss.vs - mach.vt",
        	output="this.delayBlock.y",
        	refPoint="this.delayBlock.u0 - pss.vs + mach.vt",
        	display= {"str.Efd, this.output", "str.ExciterState, this.delayBlock.state"})		 
        	*/
		
		assertTrue("", exe.getAnController().input().equals("pss.vs - mach.vt"));
		assertTrue("", exe.getAnController().output().equals("this.delayBlock.y"));
		assertTrue("", exe.getAnController().refPoint().equals("this.delayBlock.u0 - pss.vs + mach.vt"));
		assertTrue("", exe.getAnController().display()[0].equals("str.Efd, this.output"));
		assertTrue("", exe.getAnController().display()[1].equals("str.ExciterState, this.delayBlock.state"));
	}
	
	@Test
	public void fieldAnnotationTest() throws Exception {
		TestAnnotateExciter exe = new TestAnnotateExciter();
		/*
    	@AnControllerField(
            type= CMLFieldEnum.ControlBlock,
            input="this.refPoint + pss.vs - mach.vt",
            parameter={"type.Limit", "this.k", "this.t", "this.vmax", "this.vmin"},
            y0="mach.efd"	)
    	public DelayControlBlock delayBlock;		 
    	*/
		Field field = exe.getField("delayBlock");
		AnControllerField anField = field.getAnnotation(AnControllerField.class);
		assertTrue("", anField.type() == CMLFieldEnum.ControlBlock);
		assertTrue("", anField.input().equals("this.refPoint + pss.vs - mach.vt"));
		assertTrue("", anField.parameter()[0].equals("type.Limit"));
		assertTrue("", anField.parameter()[1].equals("this.k"));
		assertTrue("", anField.y0().equals("mach.efd"));
	}
	
	@Test
	public void funcFieldAnnotationTest() throws Exception {
		TestAnnotateExciter exe = new TestAnnotateExciter();
		/*
    	@AnFunctionField(
            input={"this.refPoint", "pss.vs", "mach.vt"})		 
            */
		Field field = exe.getField("seFunc1");
		AnFunctionField funcField = field.getAnnotation(AnFunctionField.class);
		assertTrue("", funcField.input()[0].equals("this.refPoint"));
		assertTrue("", funcField.input()[1].equals("pss.vs"));
	}
	
	/*
	 * Part-1 : Testing parsing annotation info
	 * ======================================== 
	 */	
	
	@Test
	public void parseControllerAnnotatoinInfoTest() throws Exception {
		BaseDStabNetwork<?,?> net = DStabTestUtilFunc.createTestNetwork();
		DStabBus bus = (DStabBus)net.getDStabBus("BusId");
		Machine machine = bus.getMachine();
		/*
		public double k = 50.0, t = 0.05, vmax = 10.0, vmin = 0.0;
    		@AnControllerField(
        	type= "type.ControlBlock",
        	input="this.refPoint + pss.vs - mach.vt",
        	parameter={"type.Limit", "this.k", "this.t", "this.vmax", "this.vmin"},
        	y0="mach.efd"	)
		DelayControlBlock delayBlock;
		 */
		TestAnnotateExciter exc = new TestAnnotateExciter();
		
    	exc.setMachine(machine);
    	
    	
    	//System.out.println("Annotate Controller Init Called");
	    exc.parseAnnotation();
		
		// System.out.println(exc.toString());
/*
		@AnController(
        	input="pss.vs - mach.vt",
        	output="this.delayBlock.y",
        	refPoint="this.delayBlock.u0 - pss.vs + mach.vt",
        	display= {"str.Efd, this.output", "str.ExciterState, this.delayBlock.state"})	
        	
Controller: TestAnnotateExciter
Input: Expression: pss.vs - mach.vt
symbol: pss.vs, opt: Add, type: not mapped
symbol: mach.vt, opt: Sub, type: not mapped

Output: Expression: this.delayBlock.y
symbol: this.delayBlock.y, opt: Add, type: not mapped

RefPoint: Expression: this.delayBlock.u0 - pss.vs + mach.vt
symbol: this.delayBlock.u0, opt: Add, type: not mapped
symbol: pss.vs, opt: Sub, type: not mapped
symbol: mach.vt, opt: Add, type: not mapped

Is child controller : false

Display: {
  [Display String, name, varStr, varType: str.Efd, this.output, str.Efd, this.output, null]
  [Display String, name, varStr, varType: str.ExciterState, this.delayBlock.state, str.ExciterState, this.delayBlock.state, null]
}
 */
	    /*
		Input: Expression: pss.vs - mach.vt
		symbol: pss.vs, opt: Add, type: not mapped
		symbol: mach.vt, opt: Sub, type: not mapped
		*/
	    assertTrue("", exc.getInputExp().getRecList().length == 2);

	    assertTrue("", exc.getInputExp().getRecList()[0].getSymbolStr().equals("pss.vs"));
	    assertTrue("", exc.getInputExp().getRecList()[0].opt == ExpCalculator.Opt.Add);
	    
	    assertTrue("", exc.getInputExp().getRecList()[1].getSymbolStr().equals("mach.vt"));
	    assertTrue("", exc.getInputExp().getRecList()[1].opt == ExpCalculator.Opt.Sub);
	    /*
		Output: Expression: this.delayBlock.y
		symbol: this.delayBlock.y, opt: Add, type: not mapped
	     */
	    assertTrue("", exc.getOutputExp().getRecList().length == 1);
	    
	    assertTrue("", exc.getOutputExp().getRecList()[0].getSymbolStr().equals("this.delayBlock.y"));
	    assertTrue("", exc.getOutputExp().getRecList()[0].opt == ExpCalculator.Opt.Add);
	    /*
		RefPoint: Expression: this.delayBlock.u0 - pss.vs + mach.vt
		symbol: this.delayBlock.u0, opt: Add, type: not mapped
		symbol: pss.vs, opt: Sub, type: not mapped
		symbol: mach.vt, opt: Add, type: not mapped
		*/
	    assertTrue("", exc.getRefPointExp().getRecList().length == 3);
	    
	    assertTrue("", exc.getRefPointExp().getRecList()[0].getSymbolStr().equals("this.delayBlock.u0"));
	    assertTrue("", exc.getRefPointExp().getRecList()[0].opt == ExpCalculator.Opt.Add);
	    
	    assertTrue("", exc.getRefPointExp().getRecList()[2].getSymbolStr().equals("mach.vt"));
	    assertTrue("", exc.getRefPointExp().getRecList()[2].opt == ExpCalculator.Opt.Add);
	    /*
		Is child controller : false
	     */
	    assertTrue("", !exc.isChildController());
	    /*
		Display: {
  			[Display String, name, varStr, varType: str.Efd, this.output, str.Efd, this.output, null]
  			[Display String, name, varStr, varType: str.ExciterState, this.delayBlock.state, str.ExciterState, this.delayBlock.state, null]
		}
	     */
	    assertTrue("", exc.getDisplayRecList().length == 2);
	    
	    assertTrue("", exc.getDisplayRecList()[0].getDisplayName().equals("str.Efd"));
	    assertTrue("", exc.getDisplayRecList()[0].getSymbolStr().equals("this.output"));

	    assertTrue("", exc.getDisplayRecList()[1].getDisplayName().equals("str.ExciterState"));
	    assertTrue("", exc.getDisplayRecList()[1].getSymbolStr().equals("this.delayBlock.state"));
	}
	
	@Test
	public void parseDelayBlockInfoTest() throws Exception {
		BaseDStabNetwork<?,?> net = DStabTestUtilFunc.createTestNetwork();
		DStabBus bus = (DStabBus)net.getDStabBus("BusId");
		Machine machine = bus.getMachine();
		/*
		public double k = 50.0, t = 0.05, vmax = 10.0, vmin = 0.0;
    		@AnControllerField(
        	type= "type.ControlBlock",
        	input="this.refPoint + pss.vs - mach.vt",
        	parameter={"type.Limit", "this.k", "this.t", "this.vmax", "this.vmin"},
        	y0="mach.efd"	)
		DelayControlBlock delayBlock;
		 */
		TestAnnotateExciter exc = new TestAnnotateExciter();
		
    	exc.setMachine(machine);
    	
    	
    	//System.out.println("Annotate Controller Init Called");
	    exc.parseAnnotation();
		
		//System.out.println(exc.toString());
		/*
Field name : delayBlock
   parameters {type.Limit, this.k, this.t, this.vmax, this.vmin, }, 
init order : 0}, 
input {
Expression: this.refPoint + pss.vs - mach.vt
symbol: this.refPoint, opt: Add, type: not mapped
symbol: pss.vs, opt: Add, type: not mapped
symbol: mach.vt, opt: Sub, type: not mapped
}, 
y0 {
Expression: mach.efd
symbol: mach.efd, opt: Add, type: not mapped
}
controlBlock {
type, k, t, limit: Limit, 50.0, 0.05, ( 10.0, 0.0 )
}, 
		 */
	    assertTrue("", exc.getFieldWrapperList().size() == 3);
	    BlockFieldAnWrapper<?> wrapper = (BlockFieldAnWrapper<?>)exc.getFieldWrapperList().get(0);
	    
		/*
			init order : 0}, 
		*/
	    assertTrue("", wrapper.getInitOrder() == 0);
	    /*
		input {
			Expression: this.refPoint + pss.vs - mach.vt
			symbol: this.refPoint, opt: Add, type: not mapped
			symbol: pss.vs, opt: Add, type: not mapped
			symbol: mach.vt, opt: Sub, type: not mapped
		}, 
		*/
	    assertTrue("", wrapper.getInputExp().getRecList().length == 3);
	    
	    /*
		y0 {
			Expression: mach.efd
			symbol: mach.efd, opt: Add, type: not mapped
		}
		*/
	    assertTrue("", wrapper.getY0Exp().getRecList().length == 1);

	    /*
		controlBlock {
			type, k, t, limit: Limit, 50.0, 0.05, ( 10.0, 0.0 )
		}, 
		 */	
	    DelayControlBlock block = (DelayControlBlock)wrapper.getField();
	    assertTrue("", block.getType() == StaticBlockType.Limit);
	    assertTrue("", block.getK() == 50.0);
	    assertTrue("", block.getT() == 0.05);
	    assertTrue("", block.getLimit().getMax() == 10.0);
	    assertTrue("", block.getLimit().getMin() == 0.0);
	}
	
	@Test
	public void parseSeFuncInfoTest() throws Exception {
		BaseDStabNetwork<?,?> net = DStabTestUtilFunc.createTestNetwork();
		DStabBus bus = (DStabBus)net.getDStabBus("BusId");
		Machine machine = bus.getMachine();
		/*
		public double e1 = 50.0, se_e1 = 1.0, e2 = 50.0, se_e2 = 1.0;
    	@AnFunctionField(
            parameter=	{"this.e1", "this.se_e1", "this.e2", "this.se_e2"},
            input={"this.refPoint", "pss.vs", "mach.vt"})
    	public SeFunction seFunc;
		 */
		TestAnnotateExciter exc = new TestAnnotateExciter();
		
    	exc.setMachine(machine);
    	
    	
    	//System.out.println("Annotate Controller Init Called");
	    exc.parseAnnotation();
		
		//System.out.println(exc.toString());
/*
Field name : seFunc
   parameters {this.e1, this.se_e1, this.e2, this.se_e2, }, 

   Input strings : this.refPoint, pss.vs, mach.vt, 
   Funciton Block {E1, Se(E1), E2, Se(E2): 1.0, 1.0, 1.0, 1.0A, B: 0.0, 0.0}, 
*/
	    FunctionFieldAnWrapper<?> wrapper = (FunctionFieldAnWrapper<?>)exc.getFieldWrapperList().get(1);

	    /*
	       Input strings : this.refPoint, pss.vs, mach.vt, 
	       */
	    assertTrue("", wrapper.getInputs().length == 3);

	    assertTrue("", wrapper.getInputs()[0].equals("this.refPoint"));
	    assertTrue("", wrapper.getInputs()[1].equals("pss.vs"));
	    assertTrue("", wrapper.getInputs()[2].equals("mach.vt"));
	    /*
	       Funciton Block {E1, Se(E1), E2, Se(E2): 1.0, 1.0, 1.0, 1.0A, B: 0.0, 0.0}, 
	    */
	    SeFunction func = (SeFunction)wrapper.getField();

	    assertTrue("", func.getVarRecList().size() == 0);  // the var record has not been mapped yet.
	}
	
	@Test
	public void parseCustomFuncInfoTest() throws Exception {
		BaseDStabNetwork<?,?> net = DStabTestUtilFunc.createTestNetwork();
		DStabBus bus = (DStabBus)net.getDStabBus("BusId");
		Machine machine = bus.getMachine();
		/*
    	@AnFunctionField(
            input={"this.refPoint", "pss.vs", "mach.vt"})
    	public ICMLFunction seFunc1 = new CMLFunctionAdapter() {
    		public double eval(double[] dAry)  {
    			return 0.0;
    		}
    	}; 
		 */
		TestAnnotateExciter exc = new TestAnnotateExciter();
		
    	exc.setMachine(machine);
    	
    	
    	//System.out.println("Annotate Controller Init Called");
	    exc.parseAnnotation();
		
		//System.out.println(exc.toString());
/*
Field name : seFunc1
   parameters {}, 

   Input strings : this.refPoint, pss.vs, mach.vt, 
   Function Block {org.interpss.core.dstab.cml.controller.util.TestAnnotateExciter$1@5c33f1a9}, 
 */
	    FunctionFieldAnWrapper<?> wrapper = (FunctionFieldAnWrapper<?>)exc.getFieldWrapperList().get(2);
	    /*
	       Input strings : this.refPoint, pss.vs, mach.vt,
	    */
	    assertTrue("", wrapper.getInputs().length == 3);

	    assertTrue("", wrapper.getInputs()[0].equals("this.refPoint"));
	    assertTrue("", wrapper.getInputs()[1].equals("pss.vs"));
	    assertTrue("", wrapper.getInputs()[2].equals("mach.vt"));	    
	    /*
	       Function Block {org.interpss.core.dstab.cml.controller.util.TestAnnotateExciter$1@5c33f1a9}, 
	     */
	    assertTrue("", wrapper.getField() instanceof CMLFunctionAdapter);
	}	
	
	/*
	 * Part-2 : Testing mapSymbolStr2Type() function
	 * ======================================== 
	 */	
	@Test
	public void mapControllerAnnotatoinInfoTest() throws Exception {
		BaseDStabNetwork<?,?> net = DStabTestUtilFunc.createTestNetwork();
		DStabBus bus = (DStabBus)net.getDStabBus("BusId");
		Machine machine = bus.getMachine();
		/*
		public double k = 50.0, t = 0.05, vmax = 10.0, vmin = 0.0;
    		@AnControllerField(
        	type= "type.ControlBlock",
        	input="this.refPoint + pss.vs - mach.vt",
        	parameter={"type.Limit", "this.k", "this.t", "this.vmax", "this.vmin"},
        	y0="mach.efd"	)
		DelayControlBlock delayBlock;
		 */
		TestAnnotateExciter exc = new TestAnnotateExciter();
		
    	exc.setMachine(machine);
    	
    	
    	//System.out.println("Annotate Controller Init Called");
	    exc.parseAnnotation();
	    exc.mapSymbolStr2Type();
		
		//System.out.println(exc.toString());
/*
Controller: TestAnnotateExciter
Input: Expression: pss.vs - mach.vt
  symbol: pss.vs, opt: Add, type: PssVs
  symbol: mach.vt, opt: Sub, type: MachVt

Output: Expression: this.delayBlock.y
  symbol: this.delayBlock.y, opt: Add, type: ControllerFieldOutput

RefPoint: Expression: this.delayBlock.u0 - pss.vs + mach.vt
  symbol: this.delayBlock.u0, opt: Add, type: ControllerFieldInput
  symbol: pss.vs, opt: Sub, type: PssVs
  symbol: mach.vt, opt: Add, type: MachVt
*/
	    assertTrue("", exc.getInputExp().getRecList()[0].getSymbolType() == CMLVarEnum.PssVs);
	    assertTrue("", exc.getInputExp().getRecList()[1].getSymbolType() == CMLVarEnum.MachVt);

	    assertTrue("", exc.getOutputExp().getRecList()[0].getSymbolType() == CMLVarEnum.ControllerFieldOutput);

	    assertTrue("", exc.getRefPointExp().getRecList()[0].getSymbolType() == CMLVarEnum.ControllerFieldInput);
	    assertTrue("", exc.getRefPointExp().getRecList()[1].getSymbolType() == CMLVarEnum.PssVs);
	    assertTrue("", exc.getRefPointExp().getRecList()[2].getSymbolType() == CMLVarEnum.MachVt);
	}
	
	@Test
	public void mapDelayBlockInfoTest() throws Exception {
		BaseDStabNetwork<?,?> net = DStabTestUtilFunc.createTestNetwork();
		DStabBus bus = (DStabBus)net.getDStabBus("BusId");
		Machine machine = bus.getMachine();
		/*
		public double k = 50.0, t = 0.05, vmax = 10.0, vmin = 0.0;
    		@AnControllerField(
        	type= "type.ControlBlock",
        	input="this.refPoint + pss.vs - mach.vt",
        	parameter={"type.Limit", "this.k", "this.t", "this.vmax", "this.vmin"},
        	y0="mach.efd"	)
		DelayControlBlock delayBlock;
		 */
		TestAnnotateExciter exc = new TestAnnotateExciter();
		
    	exc.setMachine(machine);
    	
    	
    	//System.out.println("Annotate Controller Init Called");
	    exc.parseAnnotation();
	    exc.mapSymbolStr2Type();
		
		//System.out.println(exc.toString());
		/*
input {
	Expression: this.refPoint + pss.vs - mach.vt
	symbol: this.refPoint, opt: Add, type: ControllerRefPoint
	symbol: pss.vs, opt: Add, type: PssVs
	symbol: mach.vt, opt: Sub, type: MachVt
}, 
y0 {
	Expression: mach.efd
	symbol: mach.efd, opt: Add, type: MachEfd
}
		 */
	    BlockFieldAnWrapper<?> wrapper = (BlockFieldAnWrapper<?>)exc.getFieldWrapperList().get(0);
	    
	    assertTrue("", wrapper.getInputExp().getRecList()[0].getSymbolType() == CMLVarEnum.ControllerRefPoint);
	    assertTrue("", wrapper.getInputExp().getRecList()[1].getSymbolType() == CMLVarEnum.PssVs);
	    assertTrue("", wrapper.getInputExp().getRecList()[2].getSymbolType() == CMLVarEnum.MachVt);
	    
	    assertTrue("", wrapper.getY0Exp().getRecList()[0].getSymbolType() == CMLVarEnum.MachEfd);
	}
	
	@Test
	public void mapSeFuncInfoTest() throws Exception {
		BaseDStabNetwork<?,?> net = DStabTestUtilFunc.createTestNetwork();
		DStabBus bus = (DStabBus)net.getDStabBus("BusId");
		Machine machine = bus.getMachine();
		/*
		public double e1 = 50.0, se_e1 = 1.0, e2 = 50.0, se_e2 = 1.0;
    	@AnFunctionField(
            parameter=	{"this.e1", "this.se_e1", "this.e2", "this.se_e2"},
            input={"this.refPoint", "pss.vs", "mach.vt"})
    	public SeFunction seFunc;
		 */
		TestAnnotateExciter exc = new TestAnnotateExciter();
		
    	exc.setMachine(machine);
    	
    	
    	//System.out.println("Annotate Controller Init Called");
	    exc.parseAnnotation();
	    exc.mapSymbolStr2Type();
		
		//System.out.println(exc.toString());
/*
Field name : seFunc
   parameters {this.e1, this.se_e1, this.e2, this.se_e2, }, 

   Input strings : this.refPoint, pss.vs, mach.vt, 
   Function Block {E1, Se(E1), E2, Se(E2): 1.0, 1.0, 1.0, 1.0A, B: 0.0, 0.0}, 
*/
	    FunctionFieldAnWrapper<?> wrapper = (FunctionFieldAnWrapper<?>)exc.getFieldWrapperList().get(1);
	    SeFunction func = (SeFunction)wrapper.getField();

	    assertTrue("", func.getVarRecList().size() == 3);  // the var record has not been mapped yet.

	    assertTrue("", func.getVarRecList().get(0).getSymbolStr().equals("this.refPoint"));
	    assertTrue("", func.getVarRecList().get(0).getSymbolType() == CMLVarEnum.ControllerRefPoint);

	    assertTrue("", func.getVarRecList().get(1).getSymbolStr().equals("pss.vs"));
	    assertTrue("", func.getVarRecList().get(1).getSymbolType() == CMLVarEnum.PssVs);

	    assertTrue("", func.getVarRecList().get(2).getSymbolStr().equals("mach.vt"));
	    assertTrue("", func.getVarRecList().get(2).getSymbolType() == CMLVarEnum.MachVt);
	}
	
	/*
	 * Part-3 : init order
	 * =================== 
	 */	
	@Test
	public void initOrderTest() throws Exception {
		BaseDStabNetwork<?,?> net = DStabTestUtilFunc.createTestNetwork();
		DStabBus bus = (DStabBus)net.getDStabBus("BusId");
		Machine machine = bus.getMachine();
		/*
		public double k = 50.0, t = 0.05, vmax = 10.0, vmin = 0.0;
    		@AnControllerField(
        	type= "type.ControlBlock",
        	input="this.refPoint + pss.vs - mach.vt",
        	parameter={"type.Limit", "this.k", "this.t", "this.vmax", "this.vmin"},
        	y0="mach.efd"	)
		DelayControlBlock delayBlock;
		 */
		TestAnnotateExciter exc = new TestAnnotateExciter();
		
    	exc.setMachine(machine);
    	
    	
    	//System.out.println("Annotate Controller Init Called");
		exc.initStates(bus, machine); 
		
		//System.out.println(exc.toString());
		/*
Field name : delayBlock
   parameters {type.Limit, this.k, this.t, this.vmax, this.vmin, }, 
init order : 1},  
		 */
	    BlockFieldAnWrapper<?> wrapper = (BlockFieldAnWrapper<?>)exc.getFieldWrapperList().get(0);
	    assertTrue("", wrapper.getInitOrder() == 1);
	}	
}
