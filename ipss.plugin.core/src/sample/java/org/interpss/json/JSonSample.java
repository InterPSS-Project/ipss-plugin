package org.interpss.json;

import org.interpss.util.NetJsonComparator;

public class JSonSample {
	static String str1 = "{\r\n"
			+ "  \"customer\": {\r\n"
			+ "    \"id\": \"44521\",\r\n"
			+ "    \"fullName\": \"Emily Jenkins\",\r\n"
			+ "    \"age\": 27,\r\n"
			+ "    \"consumption_info\": {\r\n"
			+ "      \"fav_product\": \"Coke\",\r\n"
			+ "      \"last_buy\": \"2012-04-23\"\r\n"
			+ "    }\r\n"
			+ "  }\r\n"
			+ "}";
	
	static String str2 = "{\r\n"
			+ "  \"customer\": {\r\n"
			+ "    \"fullName\": \"Emily Jenkins\",\r\n"
			+ "    \"id\": \"44521\",\r\n"
			+ "    \"age\": 27,\r\n"
			+ "    \"consumption_info\": {\r\n"
			+ "      \"last_buy\": \"2012-04-23\",\r\n"
			+ "      \"fav_product\": \"Coke1\"\r\n"
			+ "   }\r\n"
			+ "  }\r\n"
			+ "}";
	
	public static void main(String[] args) {
		NetJsonComparator.compareJson(str1, str2);

	}

}
