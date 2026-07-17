package org.interpss.core.dstab.cml.block;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;

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
	
	@Test
	public void testException() {
		assertThrows(InterpssRuntimeException.class, () -> {
			WashoutControlBlock block = new WashoutControlBlock(1.0, 0.1);
			assertTrue(block.initStateY0(1.0));
		});
	}
}
