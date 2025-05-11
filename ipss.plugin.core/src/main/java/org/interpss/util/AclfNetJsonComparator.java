package org.interpss.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.aclf.AclfNetworkState;

/**
 * Utility class for comparing JSON representations of AclfNetwork objects.
 * This class provides methods to compare two AclfNetwork objects, two JSON strings,
 * or two JSON files, and prints the differences in a human-readable format.
 */
public class AclfNetJsonComparator {
	// predicate to filter out certain fields from being printed
	private Predicate<String> outFilter = path -> true;
	
	// flag to indicate if there are differences
	private boolean isDifferent = false;
	
	// case description
	private String desc = "";
	
	/**
	 * Default constructor.
	 * 
	 * @param desc case description
	 */
	public AclfNetJsonComparator(String desc) {
		this.desc = desc;
	}
	
	/**
	 * Constructor that accepts a predicate to filter out certain fields
	 * 
	 * @param desc case description
	 * @param outFilter predicate to filter out certain fields
	 */
	public AclfNetJsonComparator(String desc, Predicate<String> outFilter) {
		this.desc = desc;
		this.outFilter = outFilter;
	}
	
	/**
	 * Compares two AclfNetwork objects by converting them to JSON strings
	 * and comparing the resulting JSON objects.
	 * 
	 * @param aclfNet1
	 * @param aclfNet2
	 * @return true if the JSON representations are equal, false otherwise
	 */
    public boolean compareJson(AclfNetwork aclfNet1, AclfNetwork aclfNet2) {
        JsonElement obj1 = JsonParser.parseString(new AclfNetworkState(aclfNet1).toString());
        JsonElement obj2 = JsonParser.parseString(new AclfNetworkState(aclfNet2).toString());
        
        System.out.println(this.desc + " comparing JSON objects:");
        comparePrettyPrint("", obj1, obj2);
        
        return !isDifferent;
    }
    
    /**
	 * Compares two JSON strings and prints the differences.
	 * 
	 * @param str1
	 * @param str2
	 * @return true if the JSON representations are equal, false otherwise
	 */
    public boolean compareJson(String str1, String str2) {
        JsonElement obj1 = JsonParser.parseString(str1);
        JsonElement obj2 = JsonParser.parseString(str2);
        
        System.out.println("Comparing JSON objects:");
        comparePrettyPrint("", obj1, obj2);
        
        return !isDifferent;
    }
    
    /**
     * Compares two JSON files and prints the differences.
     * 
     * @param file1
     * @param file2
     * @return true if the JSON representations are equal, false otherwise
     * @throws JsonIOException
     * @throws JsonSyntaxException
     * @throws FileNotFoundException
     */
    public boolean compareJson(AclfNetwork aclfNet, File file2) throws JsonIOException, JsonSyntaxException, FileNotFoundException {
    	 JsonElement obj1 = JsonParser.parseString(new AclfNetworkState(aclfNet).toString());
    	 JsonElement obj2 = JsonParser.parseReader(new FileReader(file2));
		 
		 System.out.println("Comparing JSON objects: " + aclfNet.getName() + " vs " + file2.getPath());
		 comparePrettyPrint("", obj1, obj2);
		 
		 return !isDifferent;
    }
    
    /**
     * Compares two JSON files and prints the differences.
     * 
     * @param file1
     * @param file2	
     * 
     * @throws JsonIOException
     * @throws JsonSyntaxException
     * @throws FileNotFoundException
     */
    public boolean compareJson(File file1, File file2) throws JsonIOException, JsonSyntaxException, FileNotFoundException {
    	 JsonElement obj1 = JsonParser.parseReader(new FileReader(file1));
    	 JsonElement obj2 = JsonParser.parseReader(new FileReader(file2));
		 
		 System.out.println("Comparing JSON objects: " + file1.getPath() + " vs " + file2.getPath());
		 comparePrettyPrint("", obj1, obj2);
		 
		 return !isDifferent;
    }
      
    private void comparePrettyPrint(String path, JsonElement obj1, JsonElement obj2) {
        if (obj1.isJsonObject() && obj2.isJsonObject()) {
            compareJsonObjects(path, obj1.getAsJsonObject(), obj2.getAsJsonObject());
        }
        else if (obj1.isJsonArray() && obj2.isJsonArray()) {
            compareJsonArrays(path, obj1.getAsJsonArray(), obj2.getAsJsonArray());
        }
        else if (!obj1.equals(obj2)) {
        	if (outFilter.test(path)) {
        		if (!path.contains("timeStamp")) {
        			isDifferent = true;
	        		System.out.println("Value mismatch at " + path + ": " +
	                             "\nFirst:  " + obj1 + 
	                             "\nSecond: " + obj2);
        		}
        	}
        }
    }
    

    private void compareJsonObjects(String path, JsonObject obj1, JsonObject obj2) {
        for (String key : obj1.keySet()) {
            String currentPath = path.endsWith("/") ? path + key : path + "/" + key;
            if (obj2.has(key)) {
                comparePrettyPrint(currentPath, obj1.get(key), obj2.get(key));
            } else {
            	isDifferent = true;
                System.out.println("Path missing in second JSON: " + currentPath);
            }
        }

        for (String key : obj2.keySet()) {
            String currentPath = path.endsWith("/") ? path + key : path + "/" + key;
            if (!obj1.has(key)) {
            	isDifferent = true;
                System.out.println("Path missing in first JSON: " + currentPath);
            }
        }
    }

    private void compareJsonArrays(String path, JsonArray array1, JsonArray array2) {
        int size1 = array1.size();
        int size2 = array2.size();
        
        if (size1 != size2) {
        	isDifferent = true;
            System.out.println("Array size mismatch at " + path + ": " + 
                             size1 + " != " + size2);
        }

        int minSize = Math.min(size1, size2);
        for (int i = 0; i < minSize; i++) {
        	if (array1.get(i).isJsonObject() && array2.get(i).isJsonObject()) {
    			// Assuming the objects in the arrays are model objects with an id field
        		
            	String id = array1.get(i).getAsJsonObject().get("id").getAsString();
                String currentPath = path + "[" + id + "]";
                comparePrettyPrint(currentPath, array1.get(i), array2.get(i));
			}
        	else {
        		/* for handling the case where the array elements are not model objects,
        		 * for example:
        		 * 	
        		 * "refBusIdSet": ["����.����վ.500.254"],
        		 */
        		String currentPath = path + "[" + i + "]";
				if (!array1.get(i).equals(array2.get(i))) {
					if (outFilter.test(currentPath)) {
						isDifferent = true;
						System.out.println("Value mismatch at " + currentPath + ": " +
								"\nFirst:  " + array1.get(i) + 
								"\nSecond: " + array2.get(i));
					}
				}
        	}
        }

        if (size1 > size2) {
            for (int i = size2; i < size1; i++) {
            	isDifferent = true;
                System.out.println("Extra element in first array at " + path + "[" + i + "]: " + 
                                 "\nValue: " + array1.get(i));
            }
        } else if (size2 > size1) {
            for (int i = size1; i < size2; i++) {
            	isDifferent = true;
                System.out.println("Extra element in second array at " + path + "[" + i + "]: " + 
                                 "\nValue: " + array2.get(i));
            }
        }
    }
}
