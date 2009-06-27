package net.luminis.liq.client.repository.impl;

import java.io.IOException;
import java.io.InputStream;

public class AdminTestUtil {

    public static byte[] copy(InputStream in) throws IOException {
        byte[] result = new byte[in.available()];
        in.read(result);
        return result;
    }

    public static boolean byteArraysEqual(byte[] left, byte[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < right.length; i++) {
            if (left[i] != right[i]) {
                return false;
            }
        }
        return true;
    }

    public static byte[] copy(byte[] input) {
        byte[] result = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            result[i] = input[i];
        }
        return result;
    }


}
