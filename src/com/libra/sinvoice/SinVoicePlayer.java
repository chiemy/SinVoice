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

import java.util.ArrayList;
import java.util.List;

import android.media.AudioFormat;
import android.text.TextUtils;

import com.libra.sinvoice.Buffer.BufferData;

/**
 * 发送端<br>
 * <p>负责：数据组合、编码（加密）、发声</p>
 * <p>基本流程：<br>
 * Encoder类交由SinGenerator类对文字进行编码处理，处理完成后放入Buffer的队列中。<br>
 * 然后PcmPlayer从Buffer队列中取出处理结果，进行音频播放</p>
 */
public class SinVoicePlayer implements Encoder.Listener, Encoder.Callback, PcmPlayer.Listener, PcmPlayer.Callback {
    private final static String TAG = "SinVoicePlayer";

    private final static int STATE_START = 1;
    private final static int STATE_STOP = 2;
    private final static int STATE_PENDING = 3;

    private final static int DEFAULT_GEN_DURATION = 100;

    private String mCodeBook;
    private List<Integer> mCodes = new ArrayList<Integer>();

    private Encoder mEncoder;
    private PcmPlayer mPlayer;
    private Buffer mBuffer;

    private int mState;
    private Listener mListener;
    private Thread mPlayThread;
    private Thread mEncodeThread;

    public static interface Listener {
        void onPlayStart();

        void onPlayEnd();
    }

    public SinVoicePlayer() {
        this(Common.DEFAULT_CODE_BOOK);
    }

    /**
     * 
     * @param codeBook 码本，码本中文字对应的频率，见{@link Encoder#CODE_FREQUENCY}
     */
    public SinVoicePlayer(String codeBook) {
        this(codeBook, Common.DEFAULT_SAMPLE_RATE, Common.DEFAULT_BUFFER_SIZE, Common.DEFAULT_BUFFER_COUNT);
    }

    /**
     * 
     * @param codeBook 码本，码本中文字对应的频率，见{@link Encoder#CODE_FREQUENCY}
     * @param sampleRate
     * @param bufferSize
     * @param buffCount
     */
    public SinVoicePlayer(String codeBook, int sampleRate, int bufferSize, int buffCount) {
        mState = STATE_STOP;
        mBuffer = new Buffer(buffCount, bufferSize);

        mEncoder = new Encoder(this, sampleRate, SinGenerator.BITS_16, bufferSize);
        mEncoder.setListener(this);
        mPlayer = new PcmPlayer(this, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        mPlayer.setListener(this);

        setCodeBook(codeBook);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setCodeBook(String codeBook) {
        if (!TextUtils.isEmpty(codeBook) && codeBook.length() < Encoder.getMaxCodeCount() - 1) {
            mCodeBook = codeBook;
        }
    }

    /**
     * 将文字转换
     * @param text
     * @return 是否转换成功
     */
    private boolean convertTextToCodes(String text) {
        boolean ret = true;

        if (!TextUtils.isEmpty(text)) {
            mCodes.clear();
            // 声音开始标志
            mCodes.add(Common.START_TOKEN);
            int len = text.length();
            for (int i = 0; i < len; ++i) {
                char ch = text.charAt(i);
                // 找到字符在码本中的位置
                int index = mCodeBook.indexOf(ch);
                if (index > -1) {
                	//将字符在码本中的位置存到集合中
                    mCodes.add(index + 1);
                } else {
                    ret = false;
                    LogHelper.d(TAG, "invalidate char:" + ch);
                    break;
                }
            }
            if (ret) {
            	// 声音结束标志
                mCodes.add(Common.STOP_TOKEN);
            }
        } else {
            ret = false;
        }

        return ret;
    }

    /**
     * 
     * @param text
     */
    public void play(final String text) {
        play(text, false, 0);
    }

    /**
     * 将文字转换为声音播放<br>
     * PcmPlayer不断的从Buffer队列中取处理结果进行播放<br>
     * Encoder不断的将处理结果，放到Buffer队列中<br>
     * @param text 发送的文字
     * @param repeat 是否重复
     * @param muteInterval 两次声音的间隔
     */
    public void play(final String text, final boolean repeat, final int muteInterval) {
        if (STATE_STOP == mState && null != mCodeBook && convertTextToCodes(text)) {
            mState = STATE_PENDING;

            mPlayThread = new Thread() {
                @Override
                public void run() {
                    mPlayer.start();
                }
            };
            if (null != mPlayThread) {
                mPlayThread.start();
            }

            mEncodeThread = new Thread() {
                @Override
                public void run() {
                    do {
                        LogHelper.d(TAG, "encode start");
                        mEncoder.encode(mCodes, DEFAULT_GEN_DURATION, muteInterval);
                        LogHelper.d(TAG, "encode end");

                        mEncoder.stop();
                    } while (repeat && STATE_PENDING != mState);
                    stopPlayer();
                }
            };
            if (null != mEncodeThread) {
                mEncodeThread.start();
            }

            LogHelper.d(TAG, "play");
            mState = STATE_START;
        }
    }
    /**
     * 停止编码
     */
    public void stop() {
        if (STATE_START == mState) {
            mState = STATE_PENDING;

            LogHelper.d(TAG, "force stop start");
            mEncoder.stop();
            if (null != mEncodeThread) {
                try {
                    mEncodeThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    mEncodeThread = null;
                }
            }

            LogHelper.d(TAG, "force stop end");
        }
    }
    /**
     * 停止播放
     */
    private void stopPlayer() {
        if (mEncoder.isStoped()) {
            mPlayer.stop();
        }

        // put end buffer
        mBuffer.putFull(BufferData.getEmptyBuffer());

        if (null != mPlayThread) {
            try {
                mPlayThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mPlayThread = null;
            }
        }

        mBuffer.reset();
        mState = STATE_STOP;
    }

    @Override
    public void onStartEncode() {
        LogHelper.d(TAG, "onStartGen");
    }

    @Override
    public void freeEncodeBuffer(BufferData buffer) {
        if (null != buffer) {
            mBuffer.putFull(buffer);
        }
    }

    @Override
    public BufferData getEncodeBuffer() {
        return mBuffer.getEmpty();
    }

    @Override
    public void onEndEncode() {
    }

    @Override
    public BufferData getPlayBuffer() {
        return mBuffer.getFull();
    }

    @Override
    public void freePlayData(BufferData data) {
        mBuffer.putEmpty(data);
    }

    @Override
    public void onPlayStart() {
        if (null != mListener) {
            mListener.onPlayStart();
        }
    }

    @Override
    public void onPlayStop() {
        if (null != mListener) {
            mListener.onPlayEnd();
        }
    }

}
