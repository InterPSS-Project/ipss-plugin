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

import java.lang.reflect.Field;

import org.interpss.core.dstab.cml.controller.util.TestAnnotateExciter;
import org.junit.Test;

import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.datatype.CMLFieldEnum;

public class AnnotationExciterTests  {
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
}
