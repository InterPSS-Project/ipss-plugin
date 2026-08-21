package org.interpss.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(AclfNetJsonComparator.class);

	// predicate to filter out certain fields from being printed
	private Predicate<String> outFilter = path -> true;
	
	// flag to indicate if there are differences
	private boolean isDifferent = false;
	
	// case description
	private String desc = "";

	private double numericTolerance = 1.0e-12;
	
	/**
	 * Default constructor.
	 * 
	 * @param desc case description
	 */
	public AclfNetJsonComparator(String desc) {
		this.desc = desc;
	}

	public AclfNetJsonComparator(String desc, double numericTolerance) {
		this.desc = desc;
		this.numericTolerance = numericTolerance;
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

	public AclfNetJsonComparator(String desc, Predicate<String> outFilter, double numericTolerance) {
		this.desc = desc;
		this.outFilter = outFilter;
		this.numericTolerance = numericTolerance;
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
    	this.isDifferent = false;
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
    	this.isDifferent = false;
        JsonElement obj1 = JsonParser.parseString(str1);
        JsonElement obj2 = JsonParser.parseString(str2);
        
        System.out.println(this.desc + " comparing JSON objects:");
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
    	 this.isDifferent = false;
    	 JsonElement obj1 = JsonParser.parseString(new AclfNetworkState(aclfNet).toString());
    	 JsonElement obj2 = JsonParser.parseReader(new FileReader(file2));
		 
         // Change to log
         log.info(this.desc + " comparing JSON objects: " + aclfNet.getName() + " vs " + file2.getPath());
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
    	 this.isDifferent = false;
    	 JsonElement obj1 = JsonParser.parseReader(new FileReader(file1));
    	 JsonElement obj2 = JsonParser.parseReader(new FileReader(file2));
		 
		 // Change to log
		 log.info(this.desc + " comparing JSON objects: " + file1.getPath() + " vs " + file2.getPath());
		 comparePrettyPrint("", obj1, obj2);
		 
		 return !isDifferent;
    }
      
    private void comparePrettyPrint(String path, JsonElement obj1, JsonElement obj2) {
    	if (!outFilter.test(path)) {
    		return;
    	}
        if (obj1.isJsonObject() && obj2.isJsonObject()) {
            compareJsonObjects(path, obj1.getAsJsonObject(), obj2.getAsJsonObject());
        }
        else if (obj1.isJsonArray() && obj2.isJsonArray()) {
            compareJsonArrays(path, obj1.getAsJsonArray(), obj2.getAsJsonArray());
        }
        else if (obj1.isJsonPrimitive() && obj2.isJsonPrimitive()
                && obj1.getAsJsonPrimitive().isNumber() && obj2.getAsJsonPrimitive().isNumber()
                && Math.abs(obj1.getAsDouble() - obj2.getAsDouble()) <= this.numericTolerance) {
            return;
        }
        else if (!obj1.equals(obj2)) {
        	if (outFilter.test(path)) {
        		if (!path.contains("timeStamp")) {
        			isDifferent = true;
	        		log.warn("Value mismatch at " + path + ": " +
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
                if (!isInactiveSwitchedShuntSavedBInit(path, key, obj1, obj2)) {
                    comparePrettyPrint(currentPath, obj1.get(key), obj2.get(key));
                }
            } else {
            	if (outFilter.test(currentPath)) {
	            	isDifferent = true;
	                log.warn("Path missing in second JSON: " + currentPath);
            	}
            }
        }

        for (String key : obj2.keySet()) {
            String currentPath = path.endsWith("/") ? path + key : path + "/" + key;
            if (!obj1.has(key)) {
            	if (outFilter.test(currentPath)) {
	            	isDifferent = true;
	                log.warn("Path missing in first JSON: " + currentPath);
            	}
            }
        }
    }

    private boolean isInactiveSwitchedShuntSavedBInit(String path, String key,
            JsonObject obj1, JsonObject obj2) {
        if (!path.contains("/switchedShuntAry[") || !"bInit".equals(key)) {
            return false;
        }
        return hasFalseStatus(obj1) && hasFalseStatus(obj2);
    }

    private boolean hasFalseStatus(JsonObject obj) {
        JsonElement status = obj.get("status");
        return status != null && status.isJsonPrimitive()
                && status.getAsJsonPrimitive().isBoolean()
                && !status.getAsBoolean();
    }

    private void compareJsonArrays(String path, JsonArray array1, JsonArray array2) {
        int size1 = array1.size();
        int size2 = array2.size();
        
        if (size1 != size2) {
        	isDifferent = true;
            log.warn("Array size mismatch at " + path + ": " + 
                             size1 + " != " + size2);
        }

        // Check if arrays contain objects with stable keys
        if (array1.size() > 0 && array2.size() > 0 && 
            array1.get(0).isJsonObject() && array2.get(0).isJsonObject() &&
            array1.get(0).getAsJsonObject().has("id") && 
            array2.get(0).getAsJsonObject().has("id")) {
            
            compareArraysByKeyMapping(path, array1, array2, "id");
        } else if (array1.size() > 0 && array2.size() > 0 && 
            array1.get(0).isJsonObject() && array2.get(0).isJsonObject() &&
            array1.get(0).getAsJsonObject().has("number") && 
            array2.get(0).getAsJsonObject().has("number")) {
            
            compareArraysByKeyMapping(path, array1, array2, "number");
        } else {
            compareUnsortedArrays(path, array1, array2);
        }
    }

    private void compareArraysByKeyMapping(String path, JsonArray array1, JsonArray array2,
    		String keyName) {
        // Create maps for stable-key lookup
        Map<String, JsonElement> map1 = new HashMap<>();
        Map<String, JsonElement> map2 = new HashMap<>();
        
        // Populate maps with debugging
        for (int i = 0; i < array1.size(); i++) {
            JsonElement elem = array1.get(i);
            if (elem.isJsonObject() && elem.getAsJsonObject().has(keyName)) {
                String key = elem.getAsJsonObject().get(keyName).getAsString();
                if (map1.containsKey(key)) {
                    log.warn("WARNING: Duplicate " + keyName + " in first array at " + path + ": " + key);
                }
                map1.put(key, elem);
            }
        }
        
        for (int i = 0; i < array2.size(); i++) {
            JsonElement elem = array2.get(i);
            if (elem.isJsonObject() && elem.getAsJsonObject().has(keyName)) {
                String key = elem.getAsJsonObject().get(keyName).getAsString();
                if (map2.containsKey(key)) {
                    log.warn("WARNING: Duplicate " + keyName + " in second array at " + path + ": " + key);
                }
                map2.put(key, elem);
            }
        }
        
        // Debug: Show first few IDs from each array
        if (map1.size() > 0 && map2.size() > 0) {
            //System.out.println("DEBUG " + path + " - First array has " + map1.size() + " elements, Second array has " + map2.size() + " elements");
            if (map1.size() != map2.size()) {
                log.debug("DEBUG " + path + " - Size mismatch detected!");
            }
        }
        
        // Compare common elements
        Set<String> commonIds = new HashSet<>(map1.keySet());
        commonIds.retainAll(map2.keySet());
        
        Set<String> onlyInFirst = new HashSet<>(map1.keySet());
        onlyInFirst.removeAll(map2.keySet());
        
        Set<String> onlyInSecond = new HashSet<>(map2.keySet());
        onlyInSecond.removeAll(map1.keySet());
        
        // Report missing elements first
        if (!onlyInFirst.isEmpty()) {
            isDifferent = true;
            log.warn("Elements only in first array at " + path + ": " + onlyInFirst.size() + " elements");
            for (String id : onlyInFirst) {
                log.warn("  Missing from second: " + id);
            }
        }
        
        if (!onlyInSecond.isEmpty()) {
            isDifferent = true;
            log.warn("Elements only in second array at " + path + ": " + onlyInSecond.size() + " elements");
            for (String id : onlyInSecond) {
                log.warn("  Missing from first: " + id);
            }
        }
        
        // Compare common elements
        for (String id : commonIds) {
            String currentPath = path + "[" + id + "]";
            comparePrettyPrint(currentPath, map1.get(id), map2.get(id));
        }
    }

    private void compareUnsortedArrays(String path, JsonArray array1, JsonArray array2) {
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
        		JsonObject item1 = array1.get(i).getAsJsonObject();
        		JsonObject item2 = array2.get(i).getAsJsonObject();
        		if (item1.has("id") && item2.has("id")) {
        			// Assuming the objects in the arrays are model objects with an id field
	            	String id1 = item1.get("id").getAsString();
	            	String id2 = item2.get("id").getAsString();
	            	if (id1.equals(id2)) {
		                String currentPath = path + "[" + id1 + "]";
		                comparePrettyPrint(currentPath, array1.get(i), array2.get(i));
	            	}
	            	else {
						// If the id mis-matches, we can compare the objects directly
						System.out.println("ID mismatch at " + path + "[" + i + "]: " +
								"\nFirst:  " + id1 +
								"\nSecond: " + id2);
					}
        		}
        		else {
	                String currentPath = path + "[" + i + "]";
	                comparePrettyPrint(currentPath, array1.get(i), array2.get(i));
        		}
			}
        	else {
        		/* for handling the case where the array elements are not model objects,
        		 * for example:
        		 * 	
        		 * "refBusIdSet": ["xxx.500.254"],
        		 */
        		String currentPath = path + "[" + i + "]";
                comparePrettyPrint(currentPath, array1.get(i), array2.get(i));
        	}
        }
    }

}
