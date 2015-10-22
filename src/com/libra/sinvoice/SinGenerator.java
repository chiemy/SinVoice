/*
 * Copyright (C) 2013 gujicheng
 * 
 * Licensed under the GPL License Version 2.0;
 * you may not use this file except in compliance with the License.
 * 
 * If you have any question, please contact me.
 * 
 *************************************************************************
 **                   Author information                                **
 *************************************************************************
 ** Email: gujicheng197@126.com                                         **
 ** QQ   : 29600731                                                     **
 ** Weibo: http://weibo.com/gujicheng197                                **
 *************************************************************************
 */
package com.libra.sinvoice;

import com.libra.sinvoice.Buffer.BufferData;
import com.libra.sinvoice.LogHelper;

/**
 * 正弦音波生成<br>
 * 纯音是一种固定频率的声波，也就是正弦声波了。那么要实现Andoird播放纯音，那么首先就应该绘制出正弦波来。<br>
 * <a href="http://blog.csdn.net/cshichao/article/details/8646913">参考</a>
 */
public class SinGenerator {
    private static final String TAG = "SinGenerator";

    private static final int STATE_START = 1;
    private static final int STATE_STOP = 2;

    public static final int BITS_8 = 128;
    public static final int BITS_16 = 32768;
    
    public static final int SAMPLE_RATE_8 = 8000;
    public static final int SAMPLE_RATE_11 = 11250;
    public static final int SAMPLE_RATE_16 = 16000;

    public static final int UNIT_ACCURACY_1 = 4;
    public static final int UNIT_ACCURACY_2 = 8;

    private int mState;
    private int mSampleRate;
    private int mBits;
    private int mDuration;
    private int mGenRate;

    private static final int DEFAULT_BITS = BITS_8;
    private static final int DEFAULT_SAMPLE_RATE = SAMPLE_RATE_8;
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    private int mFilledSize;
    private int mBufferSize;
    private Listener mListener;
    private Callback mCallback;

    public static interface Listener {
    	/**
    	 * 开始生成
    	 */
        void onStartGen();

        /**
         * 生产结束
         */
        void onStopGen();
    }

    public static interface Callback {
        BufferData getGenBuffer();

        void freeGenBuffer(BufferData buffer);
    }

    public SinGenerator(Callback callback) {
        this(callback, DEFAULT_SAMPLE_RATE, DEFAULT_BITS, DEFAULT_BUFFER_SIZE);
    }

    public SinGenerator(Callback callback, int sampleRate, int bits, int bufferSize) {
        mCallback = callback;

        mBufferSize = bufferSize;
        mSampleRate = sampleRate;
        mBits = bits;
        mDuration = 0;

        mFilledSize = 0;
        mState = STATE_STOP;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void stop() {
        if (STATE_START == mState) {
            mState = STATE_STOP;
        }
    }

    public void start() {
        if (STATE_STOP == mState) {
            mState = STATE_START;
        }
    }

    /**
     * 
     * @param genRate 生成音波的频率
     * @param duration 时长
     */
    public void gen(int genRate, int duration) {
        if (STATE_START == mState) {
            mGenRate = genRate;
            mDuration = duration;

            if (null != mListener) {
                mListener.onStartGen();
            }

            // 正弦波峰？
            int n = mBits / 2;
            // 采样点个数？frame? mSampleRate单位时间为秒，所以要除以1000。
            int totalCount = (mDuration * mSampleRate) / 1000;
            
            // 采集mGenRate频率的声音所需要的时间 = mGenRate / (double) mSampleRate?
            double per = (mGenRate / (double) mSampleRate) * 2 * Math.PI;
            double d = 0;

            LogHelper.d(TAG, "genRate:" + genRate);
            if (null != mCallback) {
                mFilledSize = 0;
                // 从生产队列中取出
                BufferData buffer = mCallback.getGenBuffer();
                if (null != buffer) {
                    for (int i = 0; i < totalCount; ++i) {
                        if (STATE_START == mState) {
                        	// 采样点x轴坐标 = Math.sin(d)
                        	// 获取采样点的振幅，没有负值，所以+128
                            int out = (int) (Math.sin(d) * n) + 128;

                            if (mFilledSize >= mBufferSize - 1) {
                                // free buffer
                                buffer.setFilledSize(mFilledSize);
                                // 放到消费队列中
                                mCallback.freeGenBuffer(buffer);

                                mFilledSize = 0;
                                buffer = mCallback.getGenBuffer();
                                if (null == buffer) {
                                    LogHelper.e(TAG, "get null buffer");
                                    break;
                                }
                            }

                            // 0xff二进制 0000 0000 1111 1111
                            // & 0xff 操作，舍弃了任意数的高八位(byte就是8位，不用&0xff也一样，直接强转byte也一样？)
                            buffer.mData[mFilledSize++] = (byte) (out & 0xff);
                            if (BITS_16 == mBits) {
                            	// 保留高8位
                                buffer.mData[mFilledSize++] = (byte) ((out >> 8) & 0xff);
                            }

                            d += per;
                        } else {
                            LogHelper.d(TAG, "sin gen force stop");
                            break;
                        }
                    }
                } else {
                    LogHelper.e(TAG, "get null buffer");
                }

                if (null != buffer) {
                    buffer.setFilledSize(mFilledSize);
                    // 放到消费队列中
                    mCallback.freeGenBuffer(buffer);
                }
                mFilledSize = 0;

                if (null != mListener) {
                    mListener.onStopGen();
                }
            }
        }
    }
}
