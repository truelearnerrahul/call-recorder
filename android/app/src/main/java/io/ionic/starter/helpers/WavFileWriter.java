package io.ionic.starter.helpers;

import java.io.File;
import java.io.RandomAccessFile;

public class WavFileWriter {
    public static void writeWavHeader(File file, int totalAudioLen, int sampleRate,
                                      int channels, int bitsPerSample) throws Exception {
        int totalDataLen = totalAudioLen + 36;
        int byteRate = sampleRate * channels * bitsPerSample / 8;

        byte[] header = new byte[]{
                'R','I','F','F',
                (byte)(totalDataLen & 0xff),
                (byte)((totalDataLen >> 8) & 0xff),
                (byte)((totalDataLen >> 16) & 0xff),
                (byte)((totalDataLen >> 24) & 0xff),
                'W','A','V','E',
                'f','m','t',' ',
                16,0,0,0,1,0,
                (byte)channels,0,
                (byte)(sampleRate & 0xff),
                (byte)((sampleRate >> 8) & 0xff),
                (byte)((sampleRate >> 16) & 0xff),
                (byte)((sampleRate >> 24) & 0xff),
                (byte)(byteRate & 0xff),
                (byte)((byteRate >> 8) & 0xff),
                (byte)((byteRate >> 16) & 0xff),
                (byte)((byteRate >> 24) & 0xff),
                (byte)((channels * bitsPerSample) / 8),0,
                (byte)bitsPerSample,0,
                'd','a','t','a',
                (byte)(totalAudioLen & 0xff),
                (byte)((totalAudioLen >> 8) & 0xff),
                (byte)((totalAudioLen >> 16) & 0xff),
                (byte)((totalAudioLen >> 24) & 0xff)
        };

        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        raf.seek(0);
        raf.write(header, 0, 44);
        raf.close();
    }
}
