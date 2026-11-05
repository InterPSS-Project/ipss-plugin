package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.dstab.hvdc.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Physical resistance loss and signed bridge identities, independent of controller integration. */
public class Cdc6tConverterResistanceTest {
 @ParameterizedTest @ValueSource(booleans={false,true})
 void finiteResistancePreservesPositiveLossAndAngleCoordinates(boolean rectifier) {
  int n=2;double r=.5,x=13.,voltage=230000.,ratio=.92,tap=.94,base=1e8,current=1000.,angle=.3;
  double v0=3*Math.sqrt(2)/Math.PI*n*voltage*ratio/tap,k=3.0/Math.PI*n*x,loss=2*n*r,sign=rectifier?1:-1;
  var c=new LccConverterBoundary(rectifier,n,voltage,ratio,tap,r,x,base);
  double dc=v0*Math.cos(angle)-(k+sign*loss)*current;
  assertEquals(dc,c.maximumDcVoltage(1,current,angle),1e-10);
  double[] a=new double[3];c.evaluateDcVoltage(1,dc,current,a,0);
  assertEquals(angle,a[2],1e-14);assertEquals(sign*dc*current/base+loss*current*current/base,a[0],1e-14);
  assertTrue(a[1]>0);assertEquals(loss*current*current/base,a[0]-sign*dc*current/base,1e-14);
  double overlapEnd=Math.acos(Math.cos(angle)-Math.sqrt(2)*current*x/(voltage*ratio/tap));
  double firing=rectifier?angle:Math.PI-overlapEnd;
  double[] f=new double[4];c.evaluateFiringAngle(1,current,firing,f,0);
  assertEquals(dc,f[0],1e-9);assertEquals(a[0],f[1],1e-14);assertEquals(a[1],f[2],1e-13);
  assertEquals(firing,c.firingAngleForDcVoltage(1,dc,current),1e-14);
  assertEquals(dc,c.dcVoltageAtFiringAngle(1,current,firing),1e-9);
 }
 @Test void zeroResistanceOverloadRetainsExactEstablishedArithmetic() {
  for(boolean rectifier:new boolean[]{false,true}) {
   var old=new LccConverterBoundary(rectifier,2,230000,.92,.94,13.,1e8);
   var explicit=new LccConverterBoundary(rectifier,2,230000,.92,.94,0.,13.,1e8);
   for(double current:new double[]{0,10,1000})for(double v:new double[]{.8,1.,1.2}) {
    double dc=old.maximumDcVoltage(v,current,.3);double[] a=new double[4],b=new double[4];
    assertEquals(dc,explicit.maximumDcVoltage(v,current,.3),0.);
    old.evaluateDcVoltage(v,dc,current,a,0);explicit.evaluateDcVoltage(v,dc,current,b,0);assertArrayEquals(a,b);
    old.evaluateFiringAngle(v,current,.3,a,0);explicit.evaluateFiringAngle(v,current,.3,b,0);assertArrayEquals(a,b);
   }
  }
 }
 @Test void conductingLinkIncludesBothConverterLossesAndActualCeilings() {
  double i=1000,rr=2.,ri=3.,line=5.;
  var r=new Cdc6tConverterBoundary(true,2,230000,.9263,.9375,rr,13.5939,1e8);
  var v=new Cdc6tConverterBoundary(false,2,230000,.9097,.94375,ri,13.3174,1e8);
  var link=new Cdc6tConductingLink(r,v,line,.1,.3);double[] out=new double[8];
  link.evaluate(1,1,i,500000,out,0);
  assertEquals(line*i,out[6]-out[7],1e-9);
  assertEquals((line+4*rr+4*ri)*i*i/1e8,out[0]+out[3],1e-14);
  link.evaluate(.8,1,i,500000,out,0);
  assertEquals(.1,out[2],1e-13,"Rectifier capability constrains physical DC voltage");
  assertEquals((line+4*rr+4*ri)*i*i/1e8,out[0]+out[3],1e-14);
 }
 @Test void invalidResistanceAndOverflowDoNotPublishPartialPower() {
  assertThrows(IllegalArgumentException.class,()->new LccConverterBoundary(true,2,230000,1,1,-1,13,1e8));
  assertThrows(IllegalArgumentException.class,()->new LccConverterBoundary(true,2,230000,1,1,Double.NaN,13,1e8));
  assertThrows(IllegalArgumentException.class,()->new LccConverterBoundary(true,2,230000,1,1,Double.MAX_VALUE,13,1e8));
  var c=new LccConverterBoundary(true,2,230000,.92,.94,.5,13,1e8);double[] out={7,8,9};
  assertThrows(IllegalArgumentException.class,()->c.evaluateDcVoltage(.01,500000,1000,out,0));assertArrayEquals(new double[]{7,8,9},out);
 }
}
