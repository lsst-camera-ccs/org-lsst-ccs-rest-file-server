package org.lsst.ccs.web.rest.file.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility methods for computing and formatting entity tags (ETags) used in
 * HTTP caching.
 *
 * @author tonyj
 */
class ETagHelper {

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

    /**
     * Computes an MD5 based ETag for the supplied serializable object.
     *
     * @param object the object for which the tag should be generated
     * @return a hex encoded representation of the object's digest
     */
    public static String computeEtag(Serializable object) {

        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");

            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream out = new ObjectOutputStream(bos)) {
                out.writeObject(object);
                out.flush();
                messageDigest.update(bos.toByteArray());
            }

            byte[] digest = messageDigest.digest();
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException | IOException x) {
            throw new RuntimeException("Error computing etag", x);
        }
    }

    /**
     * Converts the supplied byte array to a hexadecimal string.
     *
     * @param bytes the bytes to convert
     * @return the hexadecimal representation
     */
    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }
}
