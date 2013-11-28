 /*
  * @(#)PSSE2BAPBusComparator.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
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
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.QA.odm;

import org.ieee.odm.model.IODMModelParser;
import org.ieee.odm.schema.BusXmlType;
import org.ieee.odm.util.IODMComparator;

public class PSSE2BAPBusComparator implements IODMComparator<BusXmlType> {
	@Override
	public IODMModelParser getBaseParser() {
		return null;
	}
	
	public int compare(BusXmlType psseBus, BusXmlType bpaBus) {
		// PSSE uses bus number, BPA uses bus name as the id
		if (psseBus.getName().trim().toLowerCase()
				.equals(bpaBus.getName().trim().toLowerCase()))
			return 0;
		else
			return 1;
	}
}
