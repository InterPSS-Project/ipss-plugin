package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.interpss.core.dstab.reference.EventSideTrajectory;
import org.interpss.core.dstab.reference.EventSideTrajectory.Side;
import org.junit.jupiter.api.Test;

public class EventSideTrajectorySmibTest {
    private static Map<String,Double> row(double time,double value){return new LinkedHashMap<>(Map.of("time_s",time,"value",value));}
    private static List<Map<String,Double>> history(){return List.of(row(0,0),row(1,10),row(1,20),row(2,30));}

    @Test void duplicateTimestampSelectsTheRequestedSide() {
        var trace=new EventSideTrajectory(history());
        assertEquals(10,trace.sample(1,Side.BEFORE).get("value"),0);
        assertEquals(20,trace.sample(1,Side.AFTER).get("value"),0);
    }
    @Test void interpolationNeverCrossesTheJump() {
        var trace=new EventSideTrajectory(history());
        assertEquals(5,trace.sample(.5,Side.AFTER).get("value"),0);
        assertEquals(25,trace.sample(1.5,Side.BEFORE).get("value"),0);
        assertEquals(0,trace.sample(0,Side.BEFORE).get("value"),0);
        assertEquals(30,trace.sample(2,Side.AFTER).get("value"),0);
    }
    @Test void samplingRejectsExtrapolationRatherThanClamping() {
        var trace=new EventSideTrajectory(history());
        for(double time:new double[]{-.1,2.1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->trace.sample(time,Side.AFTER));
        assertThrows(NullPointerException.class,()->trace.sample(1,null));
    }
    @Test void historyAndReturnedSamplesAreImmutableSnapshots() {
        var first=row(0,0);var last=row(1,1);var input=new ArrayList<>(List.of(first,last));
        var trace=new EventSideTrajectory(input);first.put("value",99.);input.clear();
        assertEquals(0,trace.sample(0,Side.AFTER).get("value"),0);
        assertThrows(UnsupportedOperationException.class,()->trace.sample(0,Side.BEFORE).put("value",1.));
        assertThrows(UnsupportedOperationException.class,()->trace.sample(.5,Side.AFTER).clear());
    }
    @Test void invalidHistoriesCannotSilentlyLoseOrReorderRows() {
        assertThrows(IllegalArgumentException.class,()->new EventSideTrajectory(List.of()));
        assertThrows(IllegalArgumentException.class,()->new EventSideTrajectory(List.of(row(1,1),row(0,0))));
        assertThrows(IllegalArgumentException.class,()->new EventSideTrajectory(List.of(row(0,Double.NaN))));
        assertThrows(IllegalArgumentException.class,()->new EventSideTrajectory(List.of(row(0,0),Map.of("time_s",1.))));
        assertThrows(IllegalArgumentException.class,()->new EventSideTrajectory(List.of(Map.of("value",1.))));
        assertThrows(IllegalArgumentException.class,()->new EventSideTrajectory(List.of(row(0,0),row(0,1),row(0,2))));
    }
}
