 /*
  * @(#)AnnotateParserTests.java   
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
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.dstab.cml.controller;

import static org.junit.Assert.assertTrue;

import org.interpss.core.dstab.DStabTestSetupBase;
import org.interpss.core.dstab.cml.controller.util.DStabTestUtilFunc;
import org.interpss.core.dstab.cml.controller.util.TestAnnotateExciter;
import org.junit.Test;

import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.controller.annotate.util.AnControllerHelper;
import com.interpss.dstab.controller.block.ICMLFunction;
import com.interpss.dstab.controller.cml.block.DelayControlBlock;
import com.interpss.dstab.controller.wrapper.ControlBlockFieldAnWrapper;
import com.interpss.dstab.controller.wrapper.FunctionFieldAnWrapper;
import com.interpss.dstab.controller.wrapper.StaticBlockFieldAnWrapper;
import com.interpss.dstab.datatype.CMLVarEnum;
import com.interpss.dstab.mach.Machine;

public class AnnotateExciterParserTests extends DStabTestSetupBase {
	private ControlBlockFieldAnWrapper cfield;
	private StaticBlockFieldAnWrapper sfield;
	private FunctionFieldAnWrapper<?> field;

	@Test
	public void exciterTest1() throws Exception {
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
		assertTrue(exc.getDoubleField("k") == 50.0);
		
    	exc.setMachine(machine);
    	
    	
    	//System.out.println("Annotate Controller Init Called");
	    exc.parseAnnotation();
		
		System.out.println(exc.toString());
	}
	
	//@Test
	public void exciterTest2() throws Exception {
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
		assertTrue(exc.getDoubleField("k") == 50.0);
		
		assertTrue(exc.initStates(bus, machine)); 
		
		//System.out.println(exc.toString());
		assertTrue(AnControllerHelper.getBlockFieldWrapper("delayBlock", exc.getFieldWrapperList()) != null);

		DelayControlBlock block = (DelayControlBlock)(AnControllerHelper.getBlock("delayBlock", exc.getFieldWrapperList()));
		assertTrue(block.getK() == 50.0);
		assertTrue(block.getT() == 0.05);
		assertTrue(block.getLimit().getMax() == 10.0);
		assertTrue(block.getLimit().getMin() == 0.0);

		cfield = (ControlBlockFieldAnWrapper)(AnControllerHelper.getBlockFieldWrapper("delayBlock", exc.getFieldWrapperList()));
		assertTrue(cfield.getInitOrder() == 1);
		assertTrue(cfield.hasInput(CMLVarEnum.ControllerRefPoint));
		assertTrue(!cfield.hasOutput(CMLVarEnum.ControllerRefPoint));
		assertTrue(cfield.getInputExp().getRecList().length == 3);
		assertTrue(cfield.getInputExp().hasVarType(CMLVarEnum.ControllerRefPoint));
		assertTrue(cfield.getInputExp().hasVarType(CMLVarEnum.PssVs));
		assertTrue(cfield.getInputExp().hasVarType(CMLVarEnum.MachVt));

		assertTrue(cfield.getParameters().length == 5);

		assertTrue(cfield.getY0Exp().getRecList().length == 1);
		assertTrue(cfield.getY0Exp().hasVarType(CMLVarEnum.MachEfd));
		
		assertTrue(AnControllerHelper.getBlockFieldWrapper("seFunc", exc.getFieldWrapperList()) != null);

//		SeFunction seFunc = (SeFunction)(AnCntlUtilFunc.getBlock("seFunc", exc.getFieldList()));
//		assertTrue(seFunc.g.getSe1_0() == 50.0);
//		assertTrue(seFunc.getSe0_75() == 1.0);

		field = (FunctionFieldAnWrapper<ICMLFunction>)(AnControllerHelper.getBlockFieldWrapper("seFunc", exc.getFieldWrapperList()));
		assertTrue(field.getInputs().length == 3);
		assertTrue(field.getParameters().length == 2);

		assertTrue(AnControllerHelper.getBlockFieldWrapper("seFunc1", exc.getFieldWrapperList()) != null);
		field = (FunctionFieldAnWrapper<ICMLFunction>)(AnControllerHelper.getBlockFieldWrapper("seFunc1", exc.getFieldWrapperList()));
		assertTrue(field.getInputs().length == 3);
	}
}
