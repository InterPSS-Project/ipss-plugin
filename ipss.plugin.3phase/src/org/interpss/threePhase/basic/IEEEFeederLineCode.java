package org.interpss.threePhase.basic;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;

public class IEEEFeederLineCode {
	
	// NOTE: The data definition convention for all the following matrices is as follows:
	// Z (R +jX) in ohms per mile
	// B in Siemens per mile
	
	
	
    ////////////////////////////IEEE 34 bus ////////////////////////////
	/*
	 * Configuration 300:
	
		--------- Z & B Matrices Before Changes ---------
		
		           Z (R +jX) in ohms per mile
		 1.3368  1.3343   0.2101  0.5779   0.2130  0.5015
		                  1.3238  1.3569   0.2066  0.4591
		                                   1.3294  1.3471
		          B in micro Siemens per mile
		            5.3350   -1.5313   -0.9943
		                      5.0979   -0.6212
		                                4.8880
   */
	
	public static Complex3x3 zMtx300 = new Complex3x3(new Complex[][]{{new Complex(1.3368,  1.3343), new Complex(0.2101,0.5779), new Complex(0.2130,0.5015)}, 
                                                                      {new Complex(0.2101,0.5779), new Complex(1.3238,1.3569),   new Complex(0.2066,0.4591)},
                                                                      {new Complex(0.2130,0.5015), new Complex(0.2066,0.4591),   new Complex(1.3294,1.3471)}});


   public static Complex3x3 shuntYMtx300 = new Complex3x3(new Complex[][]{{new Complex(0.0,5.3350), new Complex(0.00,-1.5313), new Complex(0.00,-0.9943)}, 
                                                                          {new Complex(0.0,-1.5313), new Complex(0.00,5.0979), new Complex(0.0,-0.6212)},
                                                                          {new Complex(0.00,-0.6212), new Complex(0.0,-0.6212),new Complex(0.0,4.8880)}});
	
	/*
	Configuration 301:
		
	           Z (R +jX) in ohms per mile
	 1.9300  1.4115   0.2327  0.6442   0.2359  0.5691
	                  1.9157  1.4281   0.2288  0.5238
	                                   1.9219  1.4209
	          B in micro Siemens per mile
	            5.1207   -1.4364   -0.9402
	                      4.9055   -0.5951
	                                4.7154
    */
   
	
   public static Complex3x3 zMtx301 = new Complex3x3(new Complex[][]{{new Complex(1.9300,  1.4115 ), new Complex(0.2327, 0.6442),  new Complex(0.2359,  0.5691)}, 
                                                                     {new Complex(0.2327,  0.6442), new Complex(1.9157,  1.4281),  new Complex(0.2288,  0.5238)},
                                                                     {new Complex(0.2359,  0.5691), new Complex(0.2288,  0.5238),  new Complex(1.9219,  1.4209)}});


   public static Complex3x3 shuntYMtx301 = new Complex3x3(new Complex[][]{{new Complex(0.0,5.1207), new Complex(0.00,-1.4364), new Complex(0.00,-0.9402)}, 
                                                                          {new Complex(0.0,-1.4364), new Complex(0.00,4.9055), new Complex(0.0,-0.5951)},
                                                                          {new Complex(0.00,-0.9402), new Complex(0.0,-0.5951),new Complex(0.0,4.7154)}});   
	

   
	/*	
	Configuration 302:
		
	           Z (R +jX) in ohms per mile
	 2.7995  1.4855   0.0000  0.0000   0.0000  0.0000
	                  0.0000  0.0000   0.0000  0.0000
	                                   0.0000  0.0000
	          B in micro Siemens per mile
	            4.2251    0.0000    0.0000
	                      0.0000    0.0000
	                                0.0000
	*/
   
	
   public static Complex3x3 zMtx302 = new Complex3x3(new Complex[][]{{new Complex(2.7995,  1.4855 ), new Complex(0.0,0.0),  new Complex(0.0,0.0)}, 
                                                                     {new Complex(0.0,0.0), new Complex(0.0,0.0),  new Complex(0.0,0.0)},
                                                                     {new Complex(0.0,0.0), new Complex(0.0,0.0),  new Complex(0.0,0.0)}});


   public static Complex3x3 shuntYMtx302 = new Complex3x3(new Complex[][]{{new Complex(0.0,4.2251), new Complex(0.0,0.0), new Complex(0.0,0.0)}, 
                                                                          {new Complex(0.0,0.0), new Complex(0.0,0.0), new Complex(0.0,0.0)},
                                                                          {new Complex(0.0,0.0), new Complex(0.0,0.0),new Complex(0.0,0.0)}});   
	
   
   
	
	/*
	Configuration 303:
		
	           Z (R +jX) in ohms per mile
	 0.0000  0.0000   0.0000  0.0000   0.0000  0.0000
	                  2.7995  1.4855   0.0000  0.0000
	                                   0.0000  0.0000
	          B in micro Siemens per mile
	            0.0000    0.0000    0.0000
	                      4.2251    0.0000
	                                0.0000
	*/
	
   
   public static Complex3x3 zMtx303 = new Complex3x3(new Complex[][]{{new Complex(0.0,0.0 ), new Complex(0.0,0.0),  new Complex(0.0,0.0)}, 
                                                                     {new Complex(0.0,0.0), new Complex(2.7995,  1.4855),  new Complex(0.0,0.0)},
                                                                     {new Complex(0.0,0.0), new Complex(0.0,0.0),  new Complex(0.0,0.0)}});


   public static Complex3x3 shuntYMtx303 = new Complex3x3(new Complex[][]{{new Complex(0.0,0.0), new Complex(0.0,0.0), new Complex(0.0,0.0)}, 
                                                                        {new Complex(0.0,0.0), new Complex(0.0,4.2251), new Complex(0.0,0.0)},
                                                                        {new Complex(0.0,0.0), new Complex(0.0,0.0),new Complex(0.0,0.0)}});   

	/*
		Configuration 304:
			
		          Z (R +jX) in ohms per mile
		0.0000  0.0000   0.0000  0.0000   0.0000  0.0000
		                 1.9217  1.4212   0.0000  0.0000
		                                  0.0000  0.0000
		         B in micro Siemens per mile
		           0.0000    0.0000    0.0000
		                     4.3637    0.0000
		                               0.0000

	 */
	
	
   
   public static Complex3x3 zMtx304 = new Complex3x3(new Complex[][]{{new Complex(0.0,0.0 ), new Complex(0.0,0.0),  new Complex(0.0,0.0)}, 
                                                                     {new Complex(0.0,0.0), new Complex(1.9217,  1.4212),  new Complex(0.0,0.0)},
                                                                     {new Complex(0.0,0.0), new Complex(0.0,0.0),  new Complex(0.0,0.0)}});


   public static Complex3x3 shuntYMtx304 = new Complex3x3(new Complex[][]{{new Complex(0.0,0.0), new Complex(0.0,0.0), new Complex(0.0,0.0)}, 
                                                                        {new Complex(0.0,0.0), new Complex(0.0,4.2251), new Complex(0.0,0.0)},
                                                                        {new Complex(0.0,0.0), new Complex(0.0,0.0),new Complex(0.0,0.0)}});   
	
	
	
	//////////////////////////// IEEE 13 bus //////////////////////////
	
	
	
	/**
	Configuration 601:

           Z (R +jX) in ohms per mile
    0.3465  1.0179   0.1560  0.5017   0.1580  0.4236
                  0.3375  1.0478   0.1535  0.3849
                                   0.3414  1.0348
          B in micro Siemens per mile
            6.2998   -1.9958   -1.2595
                      5.9597   -0.7417
                                5.6386

	 */
	
	
	public static Complex3x3 zMtx601 = new Complex3x3(new Complex[][]{{new Complex(0.3465,1.0179), new Complex(0.1560,0.5017), new Complex(0.1580,0.4236)}, 
	                                                                  {new Complex(0.1560,0.5017), new Complex(0.3375,1.0478), new Complex(0.1535,0.3849)},
	                                                                  {new Complex(0.1580,0.4236), new Complex(0.1535,0.3849),new Complex(0.3414,1.0348)}});
	
	
	public static Complex3x3 shuntYMtx601 = new Complex3x3(new Complex[][]{{new Complex(0.0,6.2998), new Complex(0.00,-1.9958), new Complex(0.00,-1.2595)}, 
                                                                           {new Complex(0.0,-1.9958), new Complex(0.00,5.9597), new Complex(0.0,-0.7417)},
                                                                           {new Complex(0.00,-1.2595), new Complex(0.0,-0.7417),new Complex(0.0,5.6386)}});
	
   /**
   Configuration 602:

          Z (R +jX) in ohms per mile
   0.7526  1.1814   0.1580  0.4236   0.1560  0.5017
                 0.7475  1.1983   0.1535  0.3849
                                  0.7436  1.2112
         B in micro Siemens per mile
           5.6990   -1.0817   -1.6905
                     5.1795   -0.6588
                               5.4246

    */
	public static Complex3x3 zMtx602 = new Complex3x3(new Complex[][]{{new Complex(0.7526,1.1814),new Complex(0.1580,0.4236),new Complex(0.1560,0.5017)},
			                                                          {new Complex(0.1580,0.4236),new Complex(0.7475,1.1983),new Complex(0.1535,0.3849)},
			                                                          {new Complex(0.1560,0.5017),new Complex(0.1535,0.3849),new Complex(0.7436,1.2112)}});
			                                   
	
	public static Complex3x3 shuntYMtx602 = new Complex3x3(new Complex[][]{{new Complex(0.0,5.699), new Complex(0.00,-1.0817), new Complex(0.00,-1.6905)}, 
                                                                           {new Complex(0.0,-1.0817), new Complex(0.00,5.1795), new Complex(0.0,-0.6588)},
                                                                           {new Complex(0.00,-1.6905), new Complex(0.0,-0.6588),new Complex(0.0,5.4246)}});
	
	
	/*
	 * Configuration 603:

           Z (R +jX) in ohms per mile
       0.0000  0.0000   0.0000  0.0000   0.0000  0.0000
                        1.3294  1.3471   0.2066  0.4591
                                         1.3238  1.3569
          B in micro Siemens per mile
            0.0000    0.0000    0.0000
                      4.7097   -0.8999
                                4.6658

	 */
	
	public static Complex3x3 zMtx603 = new Complex3x3(new Complex[][]{{new Complex(0.0,0.0),new Complex(0.0,0.0),new Complex(0.0,0.0)},
                                                                      {new Complex(0.0,0.0),new Complex(1.3294,  1.3471),new Complex(0.2066,0.4591)},
                                                                      {new Complex(0.0,0.0),new Complex(0.2066,  0.4591),new Complex(1.3238,1.3569)}});


   public static Complex3x3 shuntYMtx603 = new Complex3x3(new Complex[][]{{new Complex(0.0,0.0), new Complex(0.0,0.0), new Complex(0.0,0.0)}, 
                                                                          {new Complex(0.0,0.0), new Complex(0.0,4.7097), new Complex(0.0,-0.8999)},
                                                                          {new Complex(0.0,0.0), new Complex(0.0,-0.8999),new Complex(0.0,4.6658)}});
	
	
	
	/*
	 * Configuration 604:

           Z (R +jX) in ohms per mile
    1.3238  1.3569   0.0000  0.0000   0.2066  0.4591
                  0.0000  0.0000   0.0000  0.0000
                                   1.3294  1.3471
          B in micro Siemens per mile
            4.6658    0.0000   -0.8999
                      0.0000    0.0000
                                4.7097

	 * 
	 */
	
   
	public static Complex3x3 zMtx604 = new Complex3x3(new Complex[][]{{new Complex(1.3238,1.3569),new Complex(0.0,0.0), new Complex(0.2066,0.4591)},
                                                                      {new Complex(0.0,0.0),     new Complex(0.0,0.0),  new Complex(0.0,  0.00)},
                                                                      {new Complex(0.0,0.0),     new Complex(0.2066,  0.4591),new Complex(1.3294,1.3471)}});


    public static Complex3x3 shuntYMtx604 = new Complex3x3(new Complex[][]{{new Complex(0.0,4.6658), new Complex(0.0,0.0), new Complex(0.0, -0.8999)}, 
                                                                           {new Complex(0.0,0.0),    new Complex(0.0,0.0), new Complex(0.0,0.0)},
                                                                           {new Complex(0.0,-0.8999),new Complex(0.0,0.0), new Complex(0.0,4.7097)}});

   
	
	/*
	 * Configuration 605:

           Z (R +jX) in ohms per mile
      0.0000  0.0000   0.0000  0.0000   0.0000  0.0000
                       0.0000  0.0000   0.0000  0.0000
                                        1.3292  1.3475
          B in micro Siemens per mile
            0.0000    0.0000    0.0000
                      0.0000    0.0000
                                4.5193

	 * 
	 * 
	 */
	
    
 	public static Complex3x3 zMtx605 = new Complex3x3(new Complex[][]{{new Complex(0.0,0.0),      new Complex(0.0,0.0),  new Complex(0.0,0.0)},
                                                                       {new Complex(0.0,0.0),     new Complex(0.0,0.0),  new Complex(0.0,  0.00)},
                                                                       {new Complex(0.0,0.0),     new Complex(0.0,0.0),new Complex( 1.3292,1.3475)}});


    public static Complex3x3 shuntYMtx605 = new Complex3x3(new Complex[][]{{new Complex(0.0,0.0), new Complex(0.0,0.0), new Complex(0.0, 0.0)}, 
                                                                           {new Complex(0.0,0.0),new Complex(0.0,0.0), new Complex(0.0, 0.0)},
                                                                           {new Complex(0.0,0.0),new Complex(0.0,0.0), new Complex(0.0, 4.5193)}});

     

	
	/*
	 * Configuration 606:

          Z (R +jX) in ohms per mile
       0.7982  0.4463   0.3192  0.0328   0.2849 -0.0143
                 0.7891  0.4041   0.3192  0.0328
                                  0.7982  0.4463
         B in micro Siemens per mile
          96.8897    0.0000    0.0000
                    96.8897    0.0000
                              96.8897

	 * 
	 */
	
	
	public static Complex3x3 zMtx606 = new Complex3x3(new Complex[][]{{new Complex(0.7982,0.4463),new Complex(0.3192,0.0328),new Complex(0.2849,-0.0143)},
                                                                      {new Complex(0.3192,0.0328),new Complex(0.7891,0.4041),new Complex(0.3192,0.0328)},
                                                                      {new Complex(0.2849,-0.0143),new Complex(0.3192,0.0328),new Complex(0.7982,0.4463 )}});

	
	
	public static Complex3x3 shuntYMtx606 = new Complex3x3(new Complex(0,96.8897),new Complex(0,96.8897),new Complex(0,96.8897));
	
	
	
	/*
	 * Configuration 607:

           Z (R +jX) in ohms per mile
    1.3425  0.5124   0.0000  0.0000   0.0000  0.0000
                     0.0000  0.0000   0.0000  0.0000
                                      0.0000  0.0000
          B in micro Siemens per mile
           88.9912    0.0000    0.0000
                      0.0000    0.0000
                                0.0000

	 */
	
 	public static Complex3x3 zMtx607 = new Complex3x3(new Complex[][]{{new Complex(1.3425,0.5124),      new Complex(0.0,0.0),  new Complex(0.0,0.0)},
                                                                      {new Complex(0.0,0.0),     new Complex(0.0,0.0),  new Complex(0.0,  0.0)},
                                                                      {new Complex(0.0,0.0),     new Complex(0.0,0.0),  new Complex( 0.0, 0.0)}});


    public static Complex3x3 shuntYMtx607 = new Complex3x3(new Complex[][]{{new Complex(0.0,88.9912), new Complex(0.0,0.0), new Complex(0.0, 0.0)}, 
                                                                           {new Complex(0.0,0.0),new Complex(0.0,0.0), new Complex(0.0, 0.0)},
                                                                           {new Complex(0.0,0.0),new Complex(0.0,0.0), new Complex(0.0, 0.0)}});

	
	
	

}
