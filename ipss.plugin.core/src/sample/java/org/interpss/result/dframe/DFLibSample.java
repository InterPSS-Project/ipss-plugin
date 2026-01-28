package org.interpss.result.dframe;

import static org.dflib.Exp.$int;
import static org.dflib.Exp.$str;

import org.dflib.DataFrame;
import org.dflib.Extractor;
import org.dflib.Printers;
import org.dflib.builder.DataFrameAppender;
import org.dflib.builder.DataFrameArrayAppender;
import org.dflib.json.Json;

public class DFLibSample {
    public static void main(String[] args) {
    	sample1();
    	sample2();
    	sample3();
    }

    private static record MyDataRec(String id, int value) {}
    
    public static void sample3() {
        // Define how to pull data from the object into columns
        DataFrameAppender<MyDataRec> appender = DataFrame
            .byRow(
                Extractor.$col(MyDataRec::id),   // Becomes Series<String>
                Extractor.$int(MyDataRec::value) // Becomes IntSeries (Primitive)
            )
            .columnNames("ID", "Value")
            .appender();
        
        appender.append(new MyDataRec("A", 100));
        appender.append(new MyDataRec("B", 200));
        appender.append(new MyDataRec("C", 300));
        
        DataFrame df = appender.toDataFrame();

        // 3. This will now work WITHOUT ClassCastException
        // Because Extractor.$int created a primitive IntSeries internally
        String[] ids = $str("ID").eval(df)
                         .toArray(new String[] {});
        
        int[] values = $int("Value").eval(df)
                .castAsInt()
                .toIntArray();

        for(int v : values) {
            System.out.println("Value: " + v);
        }
    }
    
    public static void sample2() {
    	
    	// 1. Define the schema and create the appender
    	DataFrameArrayAppender appender = DataFrame
    	    .byArrayRow("name", "age", "salary")
    	    .appender();

    	// 2. Append rows (varargs or Object[])
    	appender.append("Joe", 18, 50000.0);
    	appender.append("Andrus", 49, 120000.0);
    	appender.append("Joan", 32, 95000.0);

    	// 3. Create the final DataFrame
    	DataFrame df = appender.toDataFrame();    	
    	
        System.out.println("\n--- Original Data1 ---");
        System.out.println(df);
        
        System.out.println("\n--- Original Data2 ---");
        DataFrame dfSorted = df.sort($int("age").desc());
        System.out.println(dfSorted);
    }
    
    public static void sample1() {
        // 1. Create a DataFrame by "folding" data row-by-row
        DataFrame df = DataFrame.foldByRow("Name", "Department", "Salary")
                .of(
                    "Alice", "Engineering", 95000,
                    "Bob", "HR", 60000,
                    "Charlie", "Engineering", 110000,
                    "David", "Marketing", 75000
                );

        // 2. Perform Transformations
        DataFrame processedDf = df
            // Filter: Find people in Engineering earning more than 100k
            .rows($str("Department").eq("Engineering")
                  .and($int("Salary").gt(100000)))
            .select()
            
            // Add Column: Calculate a 10% bonus
            .cols("Bonus").select($int("Salary").castAsDouble().mul(0.10));

        // 3. Print the result as a pretty table
        System.out.println("--- Original Data ---");
        //System.out.println(Printers.tabular.toString(df));
        Printers.tabular.print(df);

        System.out.println("\n--- Processed Data (High Earners in Eng) ---");
        //System.out.println(Printers.tabular.toString(processedDf));
        Printers.tabular.print(processedDf);
        
        // 4. Export to JSON String
        // This creates a standard JSON array of objects
        String json = Json.saver().saveToString(processedDf);

        System.out.println("\n--- Processed Data Json ---");
        System.out.println(json);
    }
}
