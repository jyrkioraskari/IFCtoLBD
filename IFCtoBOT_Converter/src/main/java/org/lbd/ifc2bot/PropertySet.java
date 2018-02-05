package org.lbd.ifc2bot;

import java.util.HashMap;
import java.util.Map;

public class PropertySet {
    final Map<String,String> map=new HashMap<>();
	public PropertySet() {
	}
	
	public Map<String, String> getMap() {
		return map;
	}
	
	public void put(String key,String value) {
		map.put(key,value);
	}
     
}
