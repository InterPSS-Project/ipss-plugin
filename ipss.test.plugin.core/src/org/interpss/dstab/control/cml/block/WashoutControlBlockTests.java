 /*
  * @(#)WashoutControlBlockTests.java   
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

package org.interpss.dstab.control.cml.block;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.interpss.common.exp.InterpssRuntimeException;

public class WashoutControlBlockTests {
	@Test
	public void testCase() {
		WashoutControlBlock block = new WashoutControlBlock(1.0, 0.1);
		
		assertTrue(block.initStateY0(0.0));
		assertTrue(Math.abs(block.getStateX()+0.0) < 0.0001);
		assertTrue(Math.abs(block.getU0()-0.0) < 0.0001);
		
		double u = 0.0, dt = 0.01;
		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);
		
		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);

		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);
		
		assertTrue(Math.abs(block.getStateX()+0.0) < 0.0001);
		assertTrue(Math.abs(block.getY()) < 0.0001);

		u = 2.0;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getStateX()-2.0) < 0.0001);
		assertTrue(Math.abs(block.getY()) < 0.0001);
	}
	
	@Test(expected=InterpssRuntimeException.class)
	public void testException() {
		WashoutControlBlock block = new WashoutControlBlock(1.0, 0.1);
		assertTrue(block.initStateY0(1.0));
	}
}
