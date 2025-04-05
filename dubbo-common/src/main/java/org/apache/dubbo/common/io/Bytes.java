/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.io;

import org.apache.dubbo.common.utils.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * CodecUtils.
 */

// 字节操作工具类，提供各种字节转换和编码解码功能
public class Bytes {
    // 默认Base64编码字符集
    private static final String C64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

    // Base16和Base64编码字符数组
    private static final char[] BASE16 = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'},
                              BASE64 = C64.toCharArray();

    // 位掩码常量
    private static final int MASK4 = 0x0f, MASK6 = 0x3f, MASK8 = 0xff;

    // Base64解码表缓存
    private static final Map<Integer, byte[]> DECODE_TABLE_MAP = new ConcurrentHashMap<Integer, byte[]>();

    // 线程本地MD5摘要对象
    private static ThreadLocal<MessageDigest> MD = new ThreadLocal<MessageDigest>();

    // 私有构造函数，防止实例化
    private Bytes() {
    }

    /**
     * 复制字节数组
     * @param src 源数组
     * @param length 新数组长度
     * @return 新字节数组
     */
    public static byte[] copyOf(byte[] src, int length) {
        byte[] dest = new byte[length];
        System.arraycopy(src, 0, dest, 0, Math.min(src.length, length));
        return dest;
    }

    /**
     * short转字节数组
     * @param v short值
     * @return 字节数组
     */
    public static byte[] short2bytes(short v) {
        byte[] ret = {0, 0};
        short2bytes(v, ret);
        return ret;
    }

    /**
     * short转字节数组
     * @param v short值
     * @param b 目标字节数组
     */
    public static void short2bytes(short v, byte[] b) {
        short2bytes(v, b, 0);
    }

    /**
     * short转字节数组
     * @param v short值
     * @param b 目标字节数组
     * @param off 起始偏移量
     */
    public static void short2bytes(short v, byte[] b, int off) {
        b[off + 1] = (byte) v;
        b[off + 0] = (byte) (v >>> 8);
    }

    /**
     * int转字节数组
     * @param v int值
     * @return 字节数组
     */
    public static byte[] int2bytes(int v) {
        byte[] ret = {0, 0, 0, 0};
        int2bytes(v, ret);
        return ret;
    }

    /**
     * int转字节数组
     * @param v int值
     * @param b 目标字节数组
     */
    public static void int2bytes(int v, byte[] b) {
        int2bytes(v, b, 0);
    }

    /**
     * int转字节数组
     * @param v int值
     * @param b 目标字节数组
     * @param off 起始偏移量
     */
    public static void int2bytes(int v, byte[] b, int off) {
        b[off + 3] = (byte) v;
        b[off + 2] = (byte) (v >>> 8);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 0] = (byte) (v >>> 24);
    }

    /**
     * float转字节数组
     * @param v float值
     * @return 字节数组
     */
    public static byte[] float2bytes(float v) {
        byte[] ret = {0, 0, 0, 0};
        float2bytes(v, ret);
        return ret;
    }

    /**
     * float转字节数组
     * @param v float值
     * @param b 目标字节数组
     */
    public static void float2bytes(float v, byte[] b) {
        float2bytes(v, b, 0);
    }

    /**
     * float转字节数组
     * @param v float值
     * @param b 目标字节数组
     * @param off 起始偏移量
     */
    public static void float2bytes(float v, byte[] b, int off) {
        int i = Float.floatToIntBits(v);
        b[off + 3] = (byte) i;
        b[off + 2] = (byte) (i >>> 8);
        b[off + 1] = (byte) (i >>> 16);
        b[off + 0] = (byte) (i >>> 24);
    }

    /**
     * long转字节数组
     * @param v long值
     * @return 字节数组
     */
    public static byte[] long2bytes(long v) {
        byte[] ret = {0, 0, 0, 0, 0, 0, 0, 0};
        long2bytes(v, ret);
        return ret;
    }

    /**
     * long转字节数组
     * @param v long值
     * @param b 目标字节数组
     */
    public static void long2bytes(long v, byte[] b) {
        long2bytes(v, b, 0);
    }

    /**
     * long转字节数组
     * @param v long值
     * @param b 目标字节数组
     * @param off 起始偏移量
     */
    public static void long2bytes(long v, byte[] b, int off) {
        b[off + 7] = (byte) v;
        b[off + 6] = (byte) (v >>> 8);
        b[off + 5] = (byte) (v >>> 16);
        b[off + 4] = (byte) (v >>> 24);
        b[off + 3] = (byte) (v >>> 32);
        b[off + 2] = (byte) (v >>> 40);
        b[off + 1] = (byte) (v >>> 48);
        b[off + 0] = (byte) (v >>> 56);
    }

    /**
     * double转字节数组
     * @param v double值
     * @return 字节数组
     */
    public static byte[] double2bytes(double v) {
        byte[] ret = {0, 0, 0, 0, 0, 0, 0, 0};
        double2bytes(v, ret);
        return ret;
    }

    /**
     * double转字节数组
     * @param v double值
     * @param b 目标字节数组
     */
    public static void double2bytes(double v, byte[] b) {
        double2bytes(v, b, 0);
    }

    /**
     * double转字节数组
     * @param v double值
     * @param b 目标字节数组
     * @param off 起始偏移量
     */
    public static void double2bytes(double v, byte[] b, int off) {
        long j = Double.doubleToLongBits(v);
        b[off + 7] = (byte) j;
        b[off + 6] = (byte) (j >>> 8);
        b[off + 5] = (byte) (j >>> 16);
        b[off + 4] = (byte) (j >>> 24);
        b[off + 3] = (byte) (j >>> 32);
        b[off + 2] = (byte) (j >>> 40);
        b[off + 1] = (byte) (j >>> 48);
        b[off + 0] = (byte) (j >>> 56);
    }

    /**
     * 字节数组转short
     * @param b 字节数组
     * @return short值
     */
    public static short bytes2short(byte[] b) {
        return bytes2short(b, 0);
    }

    /**
     * 字节数组转short
     * @param b 字节数组
     * @param off 起始偏移量
     * @return short值
     */
    public static short bytes2short(byte[] b, int off) {
        return (short) (((b[off + 1] & 0xFF) << 0) +
                ((b[off + 0]) << 8));
    }

    /**
     * 字节数组转int
     * @param b 字节数组
     * @return int值
     */
    public static int bytes2int(byte[] b) {
        return bytes2int(b, 0);
    }

    /**
     * 字节数组转int
     * @param b 字节数组
     * @param off 起始偏移量
     * @return int值
     */
    public static int bytes2int(byte[] b, int off) {
        return ((b[off + 3] & 0xFF) << 0) +
                ((b[off + 2] & 0xFF) << 8) +
                ((b[off + 1] & 0xFF) << 16) +
                ((b[off + 0]) << 24);
    }

    /**
     * 字节数组转float
     * @param b 字节数组
     * @return float值
     */
    public static float bytes2float(byte[] b) {
        return bytes2float(b, 0);
    }

    /**
     * 字节数组转float
     * @param b 字节数组
     * @param off 起始偏移量
     * @return float值
     */
    public static float bytes2float(byte[] b, int off) {
        int i = ((b[off + 3] & 0xFF) << 0) +
                ((b[off + 2] & 0xFF) << 8) +
                ((b[off + 1] & 0xFF) << 16) +
                ((b[off + 0]) << 24);
        return Float.intBitsToFloat(i);
    }

    /**
     * 字节数组转long
     * @param b 字节数组
     * @return long值
     */
    public static long bytes2long(byte[] b) {
        return bytes2long(b, 0);
    }

    /**
     * 字节数组转long
     * @param b 字节数组
     * @param off 起始偏移量
     * @return long值
     */
    public static long bytes2long(byte[] b, int off) {
        return ((b[off + 7] & 0xFFL) << 0) +
                ((b[off + 6] & 0xFFL) << 8) +
                ((b[off + 5] & 0xFFL) << 16) +
                ((b[off + 4] & 0xFFL) << 24) +
                ((b[off + 3] & 0xFFL) << 32) +
                ((b[off + 2] & 0xFFL) << 40) +
                ((b[off + 1] & 0xFFL) << 48) +
                (((long) b[off + 0]) << 56);
    }

    /**
     * 字节数组转double
     * @param b 字节数组
     * @return double值
     */
    public static double bytes2double(byte[] b) {
        return bytes2double(b, 0);
    }

    /**
     * 字节数组转double
     * @param b 字节数组
     * @param off 起始偏移量
     * @return double值
     */
    public static double bytes2double(byte[] b, int off) {
        long j = ((b[off + 7] & 0xFFL) << 0) +
                ((b[off + 6] & 0xFFL) << 8) +
                ((b[off + 5] & 0xFFL) << 16) +
                ((b[off + 4] & 0xFFL) << 24) +
                ((b[off + 3] & 0xFFL) << 32) +
                ((b[off + 2] & 0xFFL) << 40) +
                ((b[off + 1] & 0xFFL) << 48) +
                (((long) b[off + 0]) << 56);
        return Double.longBitsToDouble(j);
    }

    /**
     * 字节数组转16进制字符串
     * @param bs 字节数组
     * @return 16进制字符串
     */
    public static String bytes2hex(byte[] bs) {
        return bytes2hex(bs, 0, bs.length);
    }

    /**
     * 字节数组转16进制字符串
     * @param bs 字节数组
     * @param off 起始偏移量
     * @param len 长度
     * @return 16进制字符串
     */
    public static String bytes2hex(byte[] bs, int off, int len) {
        if (off < 0) {
            throw new IndexOutOfBoundsException("bytes2hex: offset < 0, offset is " + off);
        }
        if (len < 0) {
            throw new IndexOutOfBoundsException("bytes2hex: length < 0, length is " + len);
        }
        if (off + len > bs.length) {
            throw new IndexOutOfBoundsException("bytes2hex: offset + length > array length.");
        }

        byte b;
        int r = off, w = 0;
        char[] cs = new char[len * 2];
        for (int i = 0; i < len; i++) {
            b = bs[r++];
            cs[w++] = BASE16[b >> 4 & MASK4];
            cs[w++] = BASE16[b & MASK4];
        }
        return new String(cs);
    }

    /**
     * 16进制字符串转字节数组
     * @param str 16进制字符串
     * @return 字节数组
     */
    public static byte[] hex2bytes(String str) {
        return hex2bytes(str, 0, str.length());
    }

    /**
     * 16进制字符串转字节数组
     * @param str 16进制字符串
     * @param off 起始偏移量
     * @param len 长度
     * @return 字节数组
     */
    public static byte[] hex2bytes(final String str, final int off, int len) {
        if ((len & 1) == 1) {
            throw new IllegalArgumentException("hex2bytes: ( len & 1 ) == 1.");
        }

        if (off < 0) {
            throw new IndexOutOfBoundsException("hex2bytes: offset < 0, offset is " + off);
        }
        if (len < 0) {
            throw new IndexOutOfBoundsException("hex2bytes: length < 0, length is " + len);
        }
        if (off + len > str.length()) {
            throw new IndexOutOfBoundsException("hex2bytes: offset + length > array length.");
        }

        int num = len / 2, r = off, w = 0;
        byte[] b = new byte[num];
        for (int i = 0; i < num; i++) {
            b[w++] = (byte) (hex(str.charAt(r++)) << 4 | hex(str.charAt(r++)));
        }
        return b;
    }

    /**
     * 字节数组转Base64字符串
     * @param b 字节数组
     * @return Base64字符串
     */
    public static String bytes2base64(byte[] b) {
        return bytes2base64(b, 0, b.length, BASE64);
    }

    /**
     * 字节数组转Base64字符串
     * @param b 字节数组
     * @param offset 起始偏移量
     * @param length 长度
     * @return Base64字符串
     */
    public static String bytes2base64(byte[] b, int offset, int length) {
        return bytes2base64(b, offset, length, BASE64);
    }

    /**
     * 字节数组转Base64字符串
     * @param b 字节数组
     * @param code Base64编码字符集
     * @return Base64字符串
     */
    public static String bytes2base64(byte[] b, String code) {
        return bytes2base64(b, 0, b.length, code);
    }

    /**
     * 字节数组转Base64字符串
     * @param b 字节数组
     * @param code Base64编码字符集
     * @return Base64字符串
     */
    public static String bytes2base64(byte[] b, int offset, int length, String code) {
        if (code.length() < 64) {
            throw new IllegalArgumentException("Base64 code length < 64.");
        }

        return bytes2base64(b, offset, length, code.toCharArray());
    }

    /**
     * 字节数组转Base64字符串
     * @param b 字节数组
     * @param code Base64编码字符数组
     * @return Base64字符串
     */
    public static String bytes2base64(byte[] b, char[] code) {
        return bytes2base64(b, 0, b.length, code);
    }

    /**
     * 字节数组转Base64字符串
     * @param bs 字节数组
     * @param off 起始偏移量
     * @param len 长度
     * @param code Base64编码字符数组
     * @return Base64字符串
     */
    public static String bytes2base64(final byte[] bs, final int off, final int len, final char[] code) {
        if (off < 0) {
            throw new IndexOutOfBoundsException("bytes2base64: offset < 0, offset is " + off);
        }
        if (len < 0) {
            throw new IndexOutOfBoundsException("bytes2base64: length < 0, length is " + len);
        }
        if (off + len > bs.length) {
            throw new IndexOutOfBoundsException("bytes2base64: offset + length > array length.");
        }

        if (code.length < 64) {
            throw new IllegalArgumentException("Base64 code length < 64.");
        }

        boolean pad = code.length > 64; // 是否有填充字符
        int num = len / 3, rem = len % 3, r = off, w = 0;
        char[] cs = new char[num * 4 + (rem == 0 ? 0 : pad ? 4 : rem + 1)];

        for (int i = 0; i < num; i++) {
            int b1 = bs[r++] & MASK8, b2 = bs[r++] & MASK8, b3 = bs[r++] & MASK8;

            cs[w++] = code[b1 >> 2];
            cs[w++] = code[(b1 << 4) & MASK6 | (b2 >> 4)];
            cs[w++] = code[(b2 << 2) & MASK6 | (b3 >> 6)];
            cs[w++] = code[b3 & MASK6];
        }

        if (rem == 1) {
            int b1 = bs[r++] & MASK8;
            cs[w++] = code[b1 >> 2];
            cs[w++] = code[(b1 << 4) & MASK6];
            if (pad) {
                cs[w++] = code[64];
                cs[w++] = code[64];
            }
        } else if (rem == 2) {
            int b1 = bs[r++] & MASK8, b2 = bs[r++] & MASK8;
            cs[w++] = code[b1 >> 2];
            cs[w++] = code[(b1 << 4) & MASK6 | (b2 >> 4)];
            cs[w++] = code[(b2 << 2) & MASK6];
            if (pad) {
                cs[w++] = code[64];
            }
        }
        return new String(cs);
    }

    /**
     * Base64字符串转字节数组
     * @param str Base64字符串
     * @return 字节数组
     */
    public static byte[] base642bytes(String str) {
        return base642bytes(str, 0, str.length());
    }

    /**
     * Base64字符串转字节数组
     * @param str Base64字符串
     * @param offset 起始偏移量
     * @param length 长度
     * @return 字节数组
     */
    public static byte[] base642bytes(String str, int offset, int length) {
        return base642bytes(str, offset, length, C64);
    }

    /**
     * Base64字符串转字节数组
     * @param str Base64字符串
     * @param code Base64编码字符集
     * @return 字节数组
     */
    public static byte[] base642bytes(String str, String code) {
        return base642bytes(str, 0, str.length(), code);
    }

    /**
     * Base64字符串转字节数组
     * @param str Base64字符串
     * @param off 起始偏移量
     * @param len 长度
     * @param code Base64编码字符集
     * @return 字节数组
     */
    public static byte[] base642bytes(final String str, final int off, final int len, final String code) {
        if (off < 0) {
            throw new IndexOutOfBoundsException("base642bytes: offset < 0, offset is " + off);
        }
        if (len < 0) {
            throw new IndexOutOfBoundsException("base642bytes: length < 0, length is " + len);
        }
        if (len == 0) {
            return new byte[0];
        }
        if (off + len > str.length()) {
            throw new IndexOutOfBoundsException("base642bytes: offset + length > string length.");
        }

        if (code.length() < 64) {
            throw new IllegalArgumentException("Base64 code length < 64.");
        }

        int rem = len % 4;
        if (rem == 1) {
            throw new IllegalArgumentException("base642bytes: base64 string length % 4 == 1.");
        }

        int num = len / 4, size = num * 3;
        if (code.length() > 64) {
            if (rem != 0) {
                throw new IllegalArgumentException("base642bytes: base64 string length error.");
            }

            char pc = code.charAt(64);
            if (str.charAt(off + len - 2) == pc) {
                size -= 2;
                --num;
                rem = 2;
            } else if (str.charAt(off + len - 1) == pc) {
                size--;
                --num;
                rem = 3;
            }
        } else {
            if (rem == 2) {
                size++;
            } else if (rem == 3) {
                size += 2;
            }
        }

        int r = off, w = 0;
        byte[] b = new byte[size], t = decodeTable(code);
        for (int i = 0; i < num; i++) {
            int c1 = t[str.charAt(r++)], c2 = t[str.charAt(r++)];
            int c3 = t[str.charAt(r++)], c4 = t[str.charAt(r++)];

            b[w++] = (byte) ((c1 << 2) | (c2 >> 4));
            b[w++] = (byte) ((c2 << 4) | (c3 >> 2));
            b[w++] = (byte) ((c3 << 6) | c4);
        }

        if (rem == 2) {
            int c1 = t[str.charAt(r++)], c2 = t[str.charAt(r++)];

            b[w++] = (byte) ((c1 << 2) | (c2 >> 4));
        } else if (rem == 3) {
            int c1 = t[str.charAt(r++)], c2 = t[str.charAt(r++)], c3 = t[str.charAt(r++)];

            b[w++] = (byte) ((c1 << 2) | (c2 >> 4));
            b[w++] = (byte) ((c2 << 4) | (c3 >> 2));
        }
        return b;
    }

    /**
     * Base64字符串转字节数组
     * @param str Base64字符串
     * @param code Base64编码字符数组
     * @return 字节数组
     */
    public static byte[] base642bytes(String str, char[] code) {
        return base642bytes(str, 0, str.length(), code);
    }

    /**
     * Base64字符串转字节数组
     * @param str Base64字符串
     * @param off 起始偏移量
     * @param len 长度
     * @param code Base64编码字符数组
     * @return 字节数组
     */
    public static byte[] base642bytes(final String str, final int off, final int len, final char[] code) {
        if (off < 0) {
            throw new IndexOutOfBoundsException("base642bytes: offset < 0, offset is " + off);
        }
        if (len < 0) {
            throw new IndexOutOfBoundsException("base642bytes: length < 0, length is " + len);
        }
        if (len == 0) {
            return new byte[0];
        }
        if (off + len > str.length()) {
            throw new IndexOutOfBoundsException("base642bytes: offset + length > string length.");
        }

        if (code.length < 64) {
            throw new IllegalArgumentException("Base64 code length < 64.");
        }

        int rem = len % 4;
        if (rem == 1) {
            throw new IllegalArgumentException("base642bytes: base64 string length % 4 == 1.");
        }

        int num = len / 4, size = num * 3;
        if (code.length > 64) {
            if (rem != 0) {
                throw new IllegalArgumentException("base642bytes: base64 string length error.");
            }

            char pc = code[64];
            if (str.charAt(off + len - 2) == pc) {
                size -= 2;
                --num;
                rem = 2;
            } else if (str.charAt(off + len - 1) == pc) {
                size--;
                --num;
                rem = 3;
            }
        } else {
            if (rem == 2) {
                size++;
            } else if (rem == 3) {
                size += 2;
            }
        }

        int r = off, w = 0;
        byte[] b = new byte[size];
        for (int i = 0; i < num; i++) {
            int c1 = indexOf(code, str.charAt(r++)), c2 = indexOf(code, str.charAt(r++));
            int c3 = indexOf(code, str.charAt(r++)), c4 = indexOf(code, str.charAt(r++));

            b[w++] = (byte) ((c1 << 2) | (c2 >> 4));
            b[w++] = (byte) ((c2 << 4) | (c3 >> 2));
            b[w++] = (byte) ((c3 << 6) | c4);
        }

        if (rem == 2) {
            int c1 = indexOf(code, str.charAt(r++)), c2 = indexOf(code, str.charAt(r++));

            b[w++] = (byte) ((c1 << 2) | (c2 >> 4));
        } else if (rem == 3) {
            int c1 = indexOf(code, str.charAt(r++)), c2 = indexOf(code, str.charAt(r++)), c3 = indexOf(code, str.charAt(r++));

            b[w++] = (byte) ((c1 << 2) | (c2 >> 4));
            b[w++] = (byte) ((c2 << 4) | (c3 >> 2));
        }
        return b;
    }

    /**
     * 压缩字节数组
     * @param bytes 源字节数组
     * @return 压缩后的字节数组
     * @throws IOException
     */
    public static byte[] zip(byte[] bytes) throws IOException {
        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
        OutputStream os = new DeflaterOutputStream(bos);
        try {
            os.write(bytes);
        } finally {
            os.close();
            bos.close();
        }
        return bos.toByteArray();
    }

    /**
     * 解压缩字节数组
     * @param bytes 压缩后的字节数组
     * @return 解压后的字节数组
     * @throws IOException
     */
    public static byte[] unzip(byte[] bytes) throws IOException {
        UnsafeByteArrayInputStream bis = new UnsafeByteArrayInputStream(bytes);
        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
        InputStream is = new InflaterInputStream(bis);
        try {
            IOUtils.write(is, bos);
            return bos.toByteArray();
        } finally {
            is.close();
            bis.close();
            bos.close();
        }
    }

    /**
     * 获取字符串的MD5值
     * @param str 输入字符串
     * @return MD5字节数组
     */
    public static byte[] getMD5(String str) {
        return getMD5(str.getBytes());
    }

    /**
     * 获取字节数组的MD5值
     * @param source 源字节数组
     * @return MD5字节数组
     */
    public static byte[] getMD5(byte[] source) {
        MessageDigest md = getMessageDigest();
        return md.digest(source);
    }

    /**
     * 获取文件的MD5值
     * @param file 文件
     * @return MD5字节数组
     */
    public static byte[] getMD5(File file) throws IOException {
        InputStream is = new FileInputStream(file);
        try {
            return getMD5(is);
        } finally {
            is.close();
        }
    }

    /**
     * 获取输入流的MD5值
     * @param is 输入流
     * @return MD5字节数组
     */
    public static byte[] getMD5(InputStream is) throws IOException {
        return getMD5(is, 1024 * 8);
    }

    // 16进制字符转数值
    private static byte hex(char c) {
        if (c <= '9') {
            return (byte) (c - '0');
        }
        if (c >= 'a' && c <= 'f') {
            return (byte) (c - 'a' + 10);
        }
        if (c >= 'A' && c <= 'F') {
            return (byte) (c - 'A' + 10);
        }
        throw new IllegalArgumentException("hex string format error [" + c + "].");
    }

    // 查找字符在字符数组中的位置
    private static int indexOf(char[] cs, char c) {
        for (int i = 0, len = cs.length; i < len; i++) {
            if (cs[i] == c) {
                return i;
            }
        }
        return -1;
    }

    // 生成Base64解码表
    private static byte[] decodeTable(String code) {
        int hash = code.hashCode();
        byte[] ret = DECODE_TABLE_MAP.get(hash);
        if (ret == null) {
            if (code.length() < 64) {
                throw new IllegalArgumentException("Base64 code length < 64.");
            }
            // 创建新的解码表
            ret = new byte[128];
            for (int i = 0; i < 128; i++) // 初始化表
            {
                ret[i] = -1;
            }
            for (int i = 0; i < 64; i++) {
                ret[code.charAt(i)] = (byte) i;
            }
            DECODE_TABLE_MAP.put(hash, ret);
        }
        return ret;
    }

    // 从输入流获取MD5值
    private static byte[] getMD5(InputStream is, int bs) throws IOException {
        MessageDigest md = getMessageDigest();
        byte[] buf = new byte[bs];
        while (is.available() > 0) {
            int read, total = 0;
            do {
                if ((read = is.read(buf, total, bs - total)) <= 0) {
                    break;
                }
                total += read;
            }
            while (total < bs);
            md.update(buf);
        }
        return md.digest();
    }

    // 获取线程本地MD5摘要对象
    private static MessageDigest getMessageDigest() {
        MessageDigest ret = MD.get();
        if (ret == null) {
            try {
                ret = MessageDigest.getInstance("MD5");
                MD.set(ret);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        return ret;
    }
}

