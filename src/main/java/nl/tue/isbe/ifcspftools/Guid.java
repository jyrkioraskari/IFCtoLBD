package nl.tue.isbe.ifcspftools;

import java.util.UUID;

public class Guid {
    long   Data1;
    int    Data2;
    int    Data3;
    char[] Data4 = new char[8];

    public UUID test() {
        return UUID.fromString(GuidHandler.getUncompressedStringFromGuid(this));
    }
}