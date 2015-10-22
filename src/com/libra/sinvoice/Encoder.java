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

import java.util.List;

import com.libra.sinvoice.Buffer.BufferData;

/**
 * 编码类
 * 对声音频率进行编码，编码成字节数组
 */
public class Encoder implements SinGenerator.Listener, SinGenerator.Callback {
    private final static String TAG = "Encoder";
    private final static int STATE_ENCODING = 1;
    private final static int STATE_STOPED = 2;

    // index 0, 1, 2, 3, 4, 5, 6
    // sampling point Count 31, 28, 25, 22, 19, 15, 10
    // sampling point Count = 采样点数量？？
    
    // 首尾分别为开始和结束音对应的频率，其他为码本中对应位置的字符的频率（如1575对应的是码本中第0个字符，以此类推）
    private final static int[] CODE_FREQUENCY = { 1422, 1575, 1764, 2004, 2321, 2940, 4410 };
    private int mState;

    private SinGenerator mSinGenerator;
    private Listener mListener;
    private Callback mCallback;

    public static interface Listener {
        void onStartEncode();

        void onEndEncode();
    }

    public static interface Callback {
    	/**
    	 * 编码完成，放入消费队列中
    	 * @param buffer 编码完成的BufferData
    	 */
        void freeEncodeBuffer(BufferData buffer);
        /**
         * 从生产队列中获取，进行编码
         * @return 
         */
        BufferData getEncodeBuffer();
    }

    /**
     * 
     * @param callback
     * @param sampleRate 采用率？
     * @param bits
     * @param bufferSize 
     */
    public Encoder(Callback callback, int sampleRate, int bits, int bufferSize) {
        mCallback = callback;
        mState = STATE_STOPED;
        mSinGenerator = new SinGenerator(this, sampleRate, bits, bufferSize);
        mSinGenerator.setListener(this);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public final static int getMaxCodeCount() {
        return CODE_FREQUENCY.length;
    }

    public final boolean isStoped() {
        return (STATE_STOPED == mState);
    }

    // content of input from 0 to (CODE_FREQUENCY.length-1)
    /**
     * 对codes进行编码，生成声音信息，每段声音没有间隔
     * @see {@link #encode(List, int, int)}
     * @param codes codes 消息中每个字符在码本中的位置的集合（包含起始、结尾标识）
     * @param duration 每个音持续的时间
     */
    public void encode(List<Integer> codes, int duration) {
        encode(codes, duration, 0);
    }

    /**
     * 对codes进行编码，生成声音信息，放到消费队列中，以提供给PcmPlayer进行播放（PcmPlayer从队列主动中取）
     * @param codes 消息中每个字符在码本中的位置的集合（包含起始、结尾标识）
     * @param duration 每个音持续的时间
     * @param muteInterval 两段声音的间隔
     */
    public void encode(List<Integer> codes, int duration, int muteInterval) {
        if (STATE_STOPED == mState) {
            mState = STATE_ENCODING;

            if (null != mListener) {
                mListener.onStartEncode();
            }

            mSinGenerator.start();
            for (int index : codes) {
                if (STATE_ENCODING == mState) {
                    LogHelper.d(TAG, "encode:" + index);
                    if (index >= 0 && index < CODE_FREQUENCY.length) {
                    	// 获取位置对应的频率，编码成字节数组
                        mSinGenerator.gen(CODE_FREQUENCY[index], duration);
                    } else {
                        LogHelper.e(TAG, "code index error");
                    }
                } else {
                    LogHelper.d(TAG, "encode force stop");
                    break;
                }
            }
            // 两条消息间隔部分（静音）
            if (STATE_ENCODING == mState) {
            	// 第一参数为0，即频率为0，所以没有声音
                mSinGenerator.gen(0, muteInterval);
            } else {
                LogHelper.d(TAG, "encode force stop");
            }
            stop();

            if (null != mListener) {
                mListener.onEndEncode();
            }
        }
    }

    public void stop() {
        if (STATE_ENCODING == mState) {
            mState = STATE_STOPED;

            mSinGenerator.stop();
        }
    }

    @Override
    public void onStartGen() {
        LogHelper.d(TAG, "start gen codes");
    }

    @Override
    public void onStopGen() {
        LogHelper.d(TAG, "end gen codes");
    }

    @Override
    public BufferData getGenBuffer() {
        if (null != mCallback) {
            return mCallback.getEncodeBuffer();
        }
        return null;
    }

    @Override
    public void freeGenBuffer(BufferData buffer) {
        if (null != mCallback) {
            mCallback.freeEncodeBuffer(buffer);
        }
    }
}
