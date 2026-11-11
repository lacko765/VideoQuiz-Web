package edu.videoquiz.core;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Bounded ISO-BMFF container validation, without executing uploaded content. */
public final class Mp4 {
    private double duration;
    private boolean ftyp, video, h264, media;
    private int boxes;
    public static double inspect(Path path) throws IOException {
        Mp4 p = new Mp4();
        try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "r")) { p.walk(f, 0, f.length(), 0); }
        if (!p.ftyp || !p.video || !p.h264 || !p.media || !Double.isFinite(p.duration) || p.duration <= 0 || p.duration > 1800)
            throw new IllegalArgumentException("Use an H.264 MP4 video with a valid duration of up to 30 minutes (AAC audio recommended).");
        return p.duration;
    }
    private void walk(RandomAccessFile f, long start, long end, int depth) throws IOException {
        if (depth > 12) throw new IllegalArgumentException("Invalid MP4 nesting.");
        long pos = start;
        while (pos < end) {
            if (++boxes > 100000 || end-pos < 8) throw new IllegalArgumentException("Invalid MP4 structure.");
            f.seek(pos); long size = Integer.toUnsignedLong(f.readInt());
            byte[] tag = new byte[4]; f.readFully(tag); String type = new String(tag, java.nio.charset.StandardCharsets.US_ASCII);
            long header = 8;
            if (size == 1) { size = f.readLong(); header = 16; }
            if (size == 0) size = end-pos;
            if (size < header || size > end-pos) throw new IllegalArgumentException("Truncated or invalid MP4 box.");
            long data = pos+header, stop = pos+size;
            if (type.equals("ftyp") && stop-data>=8) ftyp = true;
            if (type.equals("mdat") && stop-data>0) media = true;
            if (type.equals("mvhd")) {
                if (size-header < 20) throw new IllegalArgumentException("Invalid MP4 duration.");
                f.seek(data); int version=f.readUnsignedByte();
                if (version != 0 && version != 1) throw new IllegalArgumentException("Unsupported MP4 header.");
                if (version == 1 && size-header < 32) throw new IllegalArgumentException("Invalid MP4 duration.");
                f.seek(data+(version == 1 ? 20 : 12)); long scale=Integer.toUnsignedLong(f.readInt());
                long ticks=version == 1 ? f.readLong() : Integer.toUnsignedLong(f.readInt());
                if (scale == 0 || ticks < 0) throw new IllegalArgumentException("Invalid MP4 duration.");
                duration = (double)ticks/scale;
            }
            if (type.equals("hdlr") && stop-data >= 12) {
                f.seek(data+8); f.readFully(tag); if (new String(tag, java.nio.charset.StandardCharsets.US_ASCII).equals("vide")) video=true;
            }
            if (type.equals("avc1") || type.equals("avc3")) h264=true;
            if (Set.of("moov","trak","mdia","minf","stbl").contains(type)) walk(f,data,stop,depth+1);
            if (type.equals("stsd")) {
                if (stop-data < 8) throw new IllegalArgumentException("Invalid MP4 sample description.");
                walk(f,data+8,stop,depth+1);
            }
            pos=stop;
        }
    }
}
