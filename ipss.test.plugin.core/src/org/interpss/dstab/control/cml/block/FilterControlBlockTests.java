 /*
  * @(#)FilterControlBlockTests.java   
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

import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.Limit;
import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.NonWindup;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FilterControlBlockTests {
	@Test
	public void noLimitTestCase() {
		FilterControlBlock block = new FilterControlBlock(1.0, 0.1, 1.0);
		
		assertTrue(block.initStateY0(1.0));
		assertTrue(Math.abs(block.getStateX()-0.9) < 0.0001);
		assertTrue(Math.abs(block.getU0()-1.0) < 0.0001);
		
		double u = 1.0, dt = 0.01;
		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);
		
		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);

		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);
		
		assertTrue(Math.abs(block.getStateX()-0.9) < 0.0001);
		assertTrue(Math.abs(block.getY()-1.0) < 0.0001);

		/* 
		 * u = 2.0, x(0) = 0.9, K = 1.0, dt = 0.01, T1 = 0.1, T2=1.0
		 * dXdt1 = (K(1-T1/T2)u-x(0))/T2 = [1.0(1.0-0.1/1.0)*2.0 - 1.0]/1.0 = 0.9
		 * X(1) = x(0) + dXdt*dt = 0.9 + 0.9 * 0.01 = 0.909
		 * dXdt2 = [1.0(1.0-0.1/1.0)*2.0 - 0.909]/1.0 = 0.981
		 * X1 = x(0) + 0.5*(dXdt1+dXdt2)*dt = 0.9 + 0.5 * (0.9 + 0.891) * 0.01 = 0.908955
		 */
		u = 2.0;
		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);
		assertTrue(Math.abs(block.getStateX()-0.908955) < 0.0001);

		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getY()-2.0) < 0.001);

		u = -1.0;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getY()+1.0) < 0.001);
	}

	@Test
	public void limitTestCase() {
		FilterControlBlock block = new FilterControlBlock(Limit, 1.0, 0.1, 1.0, 5.0, -5.0);
		
		assertTrue(!block.initStateY0(6.0));
		assertTrue(!block.initStateY0(-6.0));

		assertTrue(block.initStateY0(0.0));

		double u = 6.0, dt = 0.01;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getY()-5.0) < 0.0001);

		u = -6.0;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getY()+5.0) < 0.0001);
	}

	@Test
	public void nonWindupTestCase() {
		FilterControlBlock block = new FilterControlBlock(NonWindup, 1.0, 0.1, 1.0, 5.0, -5.0);
		
		assertTrue(!block.initStateY0(6.0));
		assertTrue(!block.initStateY0(-6.0));

		assertTrue(block.initStateY0(0.0));

		double u = 6.0, dt = 0.01;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs((block.getStateX()+block.getK()*(block.getT1()/block.getT2())*u)-5.0) < 0.0001);
		assertTrue(Math.abs(block.getY()-5.0) < 0.0001);

		u = -6.0;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs((block.getStateX()+block.getK()*(block.getT1()/block.getT2())*u)+5.0) < 0.0001);
		assertTrue(Math.abs(block.getY()+5.0) < 0.0001);
	}
}
