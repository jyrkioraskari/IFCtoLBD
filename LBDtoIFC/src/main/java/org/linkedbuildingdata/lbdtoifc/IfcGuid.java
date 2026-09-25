package org.linkedbuildingdata.lbdtoifc;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.regex.Pattern;

final class IfcGuid {
    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$".toCharArray();
    private static final Pattern VALID = Pattern.compile("[0-3][0-9A-Za-z_$]{21}");

    private IfcGuid() {}

    static String preserveOrCreate(String candidate, String stableKey) {
        return isValid(candidate)
                ? candidate
                : fromUuid(deterministicUuid(stableKey));
    }

    static boolean isValid(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches();
    }

    static String forRelationship(String kind, String... keys) {
        return fromUuid(deterministicUuid(kind + "\u0000" + String.join("\u0000", keys)));
    }

    private static UUID deterministicUuid(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x50);
            bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("The JVM does not provide SHA-256", impossible);
        }
    }

    private static String fromUuid(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits());
        BigInteger value = new BigInteger(1, buffer.array());
        char[] encoded = new char[22];
        for (int index = encoded.length - 1; index >= 0; index--) {
            encoded[index] = ALPHABET[value.and(BigInteger.valueOf(63)).intValue()];
            value = value.shiftRight(6);
        }
        return new String(encoded);
    }
}
