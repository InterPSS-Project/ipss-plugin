 /*
  * @(#)PiecewiseAlgoTestSuite.java   
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
  * @Date 11/15/2016
  * 
  *   Revision History
  *   ================
  *
  */

package test.piecewise;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	IEEE14TestSubAreaSearch.class,
	IEEE14TestAclfNetPiesewise.class,
	IEEE14TestAclfSubNetBuild.class,
	
	Acsc5BusTestSubAreaNet.class,
	Acsc5BusTesPiecewiseAlgo.class,
	
	IEEE9BusTestDStabSubAreaNet.class,
})
public class PiecewiseAlgoTestSuite {
}
