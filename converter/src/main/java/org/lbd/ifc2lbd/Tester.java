package org.lbd.ifc2lbd;

import com.openifctools.guidcompressor.GuidCompressor;

public class Tester {
public static void main(String[] args) {
	String compressed_guid = GuidCompressor.compressGuidString("ad8339ce-4378-4e54-b917-ed305724ca9b");
	System.out.println(compressed_guid);
}
}
