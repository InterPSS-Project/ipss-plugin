package org.interpss.core.dstab.cml.block;

import static com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType.Limit;
import static com.interpss.dstab.controller.cml.field.ICMLStaticBlock.StaticBlockType.NonWindup;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;

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
