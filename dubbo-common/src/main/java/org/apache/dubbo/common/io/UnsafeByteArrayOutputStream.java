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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * UnsafeByteArrayOutputStream.
 */
// 非线程安全的字节数组输出流实现
public class UnsafeByteArrayOutputStream extends OutputStream {
    // 内部字节数组缓冲区
    protected byte[] mBuffer;

    // 当前写入位置
    protected int mCount;

    // 默认构造函数，初始缓冲区大小为32字节
    public UnsafeByteArrayOutputStream() {
        this(32);
    }

    // 指定初始大小的构造函数
    public UnsafeByteArrayOutputStream(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        }
        mBuffer = new byte[size];
    }

    // 写入单个字节
    @Override
    public void write(int b) {
        int newcount = mCount + 1;
        // 检查并扩展缓冲区大小
        if (newcount > mBuffer.length) {
            mBuffer = Bytes.copyOf(mBuffer, Math.max(mBuffer.length << 1, newcount));
        }
        mBuffer[mCount] = (byte) b;
        mCount = newcount;
    }

    // 写入字节数组
    @Override
    public void write(byte[] b, int off, int len) {
        // 参数校验
        if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return;
        }
        int newcount = mCount + len;
        // 检查并扩展缓冲区大小
        if (newcount > mBuffer.length) {
            mBuffer = Bytes.copyOf(mBuffer, Math.max(mBuffer.length << 1, newcount));
        }
        // 拷贝数据到缓冲区
        System.arraycopy(b, off, mBuffer, mCount, len);
        mCount = newcount;
    }

    // 获取当前数据大小
    public int size() {
        return mCount;
    }

    // 重置输出流(不清空缓冲区，只是重置计数器)
    public void reset() {
        mCount = 0;
    }

    // 获取包含当前数据的字节数组副本
    public byte[] toByteArray() {
        return Bytes.copyOf(mBuffer, mCount);
    }

    // 将数据包装为ByteBuffer
    public ByteBuffer toByteBuffer() {
        return ByteBuffer.wrap(mBuffer, 0, mCount);
    }

    // 将数据写入到另一个输出流
    public void writeTo(OutputStream out) throws IOException {
        out.write(mBuffer, 0, mCount);
    }

    // 将数据转换为字符串(使用平台默认编码)
    @Override
    public String toString() {
        return new String(mBuffer, 0, mCount);
    }

    // 将数据转换为字符串(使用指定编码)
    public String toString(String charset) throws UnsupportedEncodingException {
        return new String(mBuffer, 0, mCount, charset);
    }

    // 关闭输出流(空实现)
    @Override
    public void close() throws IOException {
    }
}
