package org.interpss.core.dstab.cml.block;

import static com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType.Limit;
import static com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType.NonWindup;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;

public class IntegrationControlBlockTests {
	@Test
	public void nolimitTestCase() {
		IntegrationControlBlock block = new IntegrationControlBlock(1.0);
		
		assertTrue(block.initStateY0(1.0));
		assertTrue(Math.abs(block.getStateX()-1.0) < 0.0001);
		assertTrue(Math.abs(block.getU0()-0.0) < 0.0001);
		
		double u = 0.0, dt = 0.01;
		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);
		
		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);

		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);
		
		assertTrue(Math.abs(block.getStateX()-1.0) < 0.0001);

		/* 
		 * u = 2.0, x(0) = 1.0, K = 1.0, dt = 0.01
		 * dXdt1 = Ku = 2.0
		 * X(1) = x(0) + dXdt*dt = 1.0 + 2.0 * 0.01 = 1.02
		 * dXdt2 = 2.0
		 * X1 = x(0) + 0.5*(dXdt1+dXdt2)*dt = 1.0 + 0.5 * (2.0 + 2.0) * 0.01 = 1.02
		 */
		u = 2.0;
		block.eulerStep1(u, dt);
		block.eulerStep2(u, dt);
		assertTrue(Math.abs(block.getStateX()-1.02) < 0.0001);

		for (int i = 0; i < 999; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}

		u = -2.0;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getStateX()-1.0) < 0.0001);
	}

	@Test
	public void limitTestCase() {
		IntegrationControlBlock block = new IntegrationControlBlock(Limit, 1.0, 5.0, -5.0);
		
		assertTrue(!block.initStateY0(6.0));
		assertTrue(!block.initStateY0(-6.0));

		assertTrue(block.initStateY0(0.0));

		double u = 1.0, dt = 0.01;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getY()-5.0) < 0.0001);

		u = -2.0;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getY()+5.0) < 0.0001);
	}

	@Test
	public void nonWindupTestCase() {
		IntegrationControlBlock block = new IntegrationControlBlock(NonWindup, 1.0, 5.0, -5.0);
		
		assertTrue(!block.initStateY0(6.0));
		assertTrue(!block.initStateY0(-6.0));

		assertTrue(block.initStateY0(0.0));

		double u = 1.0, dt = 0.01;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getStateX()-5.0) < 0.0001);
		assertTrue(Math.abs(block.getY()-5.0) < 0.0001);

		u = -2.0;
		for (int i = 0; i < 1000; i++) {
			block.eulerStep1(u, dt);
			block.eulerStep2(u, dt);
		}
		assertTrue(Math.abs(block.getStateX()+5.0) < 0.0001);
		assertTrue(Math.abs(block.getY()+5.0) < 0.0001);
	}
}
