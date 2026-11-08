package edu.videoquiz;
import edu.videoquiz.core.Validation;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimestampTests {
    private double parse(String value) {return Validation.timestamp(JsonParser.parseString("{\"t\":"+value+"}").getAsJsonObject(),"t");}
    @Test void parsesSecondsAndTimecodes() {
        assertEquals(75.5,parse("75.5"));assertEquals(75.5,parse("\"01:15.5\""));
        assertEquals(3675.25,parse("\"01:01:15.25\""));assertEquals(75,parse("\"75\""));
    }
    @Test void rejectsAmbiguousInvalidTimestamps() {
        for(String value:new String[]{"-1","\"1:60\"","\"1:99:00\"","\"NaN\"","\"01:02:03:04\"","null","true"})
            assertThrows(IllegalArgumentException.class,()->parse(value));
    }
    @Test void rejectsUnknownDurationAndOutOfBoundsRatherThanClamping() {
        var obj=JsonParser.parseString("{\"points\":[{\"start\":\"00:05\",\"end\":\"00:10\",\"summary\":\"Fact\",\"evidence\":\"Evidence\"}]}").getAsJsonObject();
        for(double d:new double[]{0,Double.NaN,-1,9,1801})assertThrows(IllegalArgumentException.class,()->Validation.points(obj,d));
        assertEquals(10,Validation.points(obj,10).get(0).end());
    }
}
