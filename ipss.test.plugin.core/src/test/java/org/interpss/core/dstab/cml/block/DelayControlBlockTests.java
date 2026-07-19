package org.interpss.core.dstab.cml.block;

import static com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType.Limit;
import static com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType.NonWindup;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType;

import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;

public class DelayControlBlockTests {
	@Test
	public void noLimitTestCase() {
		DelayControlBlock block = new DelayControlBlock(1.0, 0.1);
		
		assertTrue(block.initStateY0(1.0));
		assertTrue(Math.abs(block.getStateX()-1.0) < 0.0001);
		assertTrue(Math.abs(block.getU0()-1.0) < 0.0001);
		
		double u = 1.0, dt = 0.01;
		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);
		
		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);

		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);
		
		assertTrue(Math.abs(block.getStateX()-1.0) < 0.0001);

		/* 
		 * u = 2.0, x(0) = 1.0, K = 1.0, dt = 0.01, T = 0.1
		 * dXdt1 = (Ku-x(0))/T = [1.0*2.0 - 1.0]/0.1 = 10.0
		 * X(1) = x(0) + dXdt*dt = 1.0 + 10.0 * 0.01 = 1.1
		 * dXdt2 = [1.0*2.0 - 1.1]/0.1 = 9.0
		 * X1 = x(0) + 0.5*(dXdt1+dXdt2)*dt = 1.0 + 0.5 * (10.0 + 9.0) * 0.01 = 1.095
		 */
		u = 2.0;
		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);
		assertTrue(Math.abs(block.getStateX()-1.095) < 0.0001);

		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getStateX()-2.0) < 0.0001);

		u = -1.0;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getStateX()+1.0) < 0.0001);
	}

	@Test
	public void limitTestCase() {
		DelayControlBlock block = new DelayControlBlock(StaticBlockType.Limit, 1.0, 0.1, 5.0, -5.0);
		
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
		DelayControlBlock block = new DelayControlBlock(StaticBlockType.NonWindup, 1.0, 0.1, 5.0, -5.0);
		
		assertTrue(!block.initStateY0(6.0));
		assertTrue(!block.initStateY0(-6.0));

		assertTrue(block.initStateY0(0.0));

		double u = 6.0, dt = 0.01;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		System.out.println(block.getStateX());
		assertTrue(Math.abs(block.getStateX()-5.0) < 0.1);
		assertTrue(Math.abs(block.getY()-5.0) < 0.1);

		u = -6.0;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		System.out.println(block.getStateX());
		assertTrue(Math.abs(block.getStateX()+5.0) < 0.1);
		assertTrue(Math.abs(block.getY()+5.0) < 0.1);
	}
}
