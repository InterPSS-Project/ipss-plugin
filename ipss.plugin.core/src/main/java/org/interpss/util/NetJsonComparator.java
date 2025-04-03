package org.interpss.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.aclf.AclfNetworkState;

public class NetJsonComparator {
    public static void compareJson(AclfNetwork aclfNet1, AclfNetwork aclfNet2) {
        JsonElement obj1 = JsonParser.parseString(new AclfNetworkState(aclfNet1).toString());
        JsonElement obj2 = JsonParser.parseString(new AclfNetworkState(aclfNet2).toString());
        
        System.out.println("Comparing JSON objects:");
        comparePrettyPrint("", obj1, obj2);
    }
    
    public static void compareJson(String str1, String str2) {
        JsonElement obj1 = JsonParser.parseString(str1);
        JsonElement obj2 = JsonParser.parseString(str2);
        
        System.out.println("Comparing JSON objects:");
        comparePrettyPrint("", obj1, obj2);
    }
      
    private static void comparePrettyPrint(String path, JsonElement obj1, JsonElement obj2) {
        if (obj1.isJsonObject() && obj2.isJsonObject()) {
            compareJsonObjects(path, obj1.getAsJsonObject(), obj2.getAsJsonObject());
        }
        else if (obj1.isJsonArray() && obj2.isJsonArray()) {
            compareJsonArrays(path, obj1.getAsJsonArray(), obj2.getAsJsonArray());
        }
        else if (!obj1.equals(obj2)) {
            System.out.println("Value mismatch at " + path + ": " +
                             "\nFirst:  " + obj1 + 
                             "\nSecond: " + obj2);
        }
    }

    private static void compareJsonObjects(String path, JsonObject obj1, JsonObject obj2) {
        for (String key : obj1.keySet()) {
            String currentPath = path.endsWith("/") ? path + key : path + "/" + key;
            if (obj2.has(key)) {
                comparePrettyPrint(currentPath, obj1.get(key), obj2.get(key));
            } else {
                System.out.println("Path missing in second JSON: " + currentPath);
            }
        }

        for (String key : obj2.keySet()) {
            String currentPath = path.endsWith("/") ? path + key : path + "/" + key;
            if (!obj1.has(key)) {
                System.out.println("Path missing in first JSON: " + currentPath);
            }
        }
    }

    private static void compareJsonArrays(String path, JsonArray array1, JsonArray array2) {
        int size1 = array1.size();
        int size2 = array2.size();
        
        if (size1 != size2) {
            System.out.println("Array size mismatch at " + path + ": " + 
                             size1 + " != " + size2);
        }

        int minSize = Math.min(size1, size2);
        for (int i = 0; i < minSize; i++) {
        	String id = array1.get(i).getAsJsonObject().get("id").getAsString();
            String currentPath = path + "[" + id + "]";
            comparePrettyPrint(currentPath, array1.get(i), array2.get(i));
        }

        if (size1 > size2) {
            for (int i = size2; i < size1; i++) {
                System.out.println("Extra element in first array at " + path + "[" + i + "]: " + 
                                 "\nValue: " + array1.get(i));
            }
        } else if (size2 > size1) {
            for (int i = size1; i < size2; i++) {
                System.out.println("Extra element in second array at " + path + "[" + i + "]: " + 
                                 "\nValue: " + array2.get(i));
            }
        }
    }
}
