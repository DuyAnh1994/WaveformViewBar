package com.dev.anhnd.waveformviewbar;

public final class ConvertBytesToInt {

    public static long getLE2(byte[] buffer) {
        long val = buffer[1] & 0xFF;
        val = (val << 8) + (buffer[0] & 0xFF);
        return val;
    }
}
