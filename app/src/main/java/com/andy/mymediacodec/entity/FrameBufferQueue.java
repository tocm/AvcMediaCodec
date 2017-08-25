package com.andy.mymediacodec.entity;

import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;

import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

/**
 * Created by Andy.chen on 2017/6/27.
 */

public class FrameBufferQueue {
    private final static String TAG = FrameBufferQueue.class.getSimpleName();
    private final static int DECODER_QUEUE_BUF_SIZE = 10;
    private ArrayBlockingQueue<FrameEntity> mFrameQueue;

    public FrameBufferQueue() {
        mFrameQueue = new ArrayBlockingQueue<FrameEntity>(DECODER_QUEUE_BUF_SIZE);
    }

    public synchronized void pushFrameData(FrameEntity frameEntity) {
        if (mFrameQueue.size() >= DECODER_QUEUE_BUF_SIZE) {
            Log.d(TAG,"==== drop frame ======  id = "+frameEntity.getId());
            FrameEntity checkKeyFrameEntity = mFrameQueue.poll();
            //Avoid drop the I-KEY-FRAME
            if(checkKeyFrameEntity != null && checkKeyFrameEntity.getFrameType() == BUFFER_FLAG_KEY_FRAME) {
                Log.d(TAG,"==== not drop I frame ======  id= "+frameEntity.getId());
                mFrameQueue.clear();
                mFrameQueue.offer(checkKeyFrameEntity);
            }
        }
        mFrameQueue.offer(frameEntity);
        Log.d(TAG,"offer queue id= "+frameEntity.getId() +", size =" + mFrameQueue.size()+", frame size = "+frameEntity.getBuf().length);
    }
    public synchronized void clearQueue() {
        if(mFrameQueue != null) {
            Log.d(TAG,"clear queue");
            mFrameQueue.clear();
        }
    }

    public synchronized FrameEntity pollFrameData() {
        FrameEntity frame = null;
        int queueSize = mFrameQueue.size();
        Log.d(TAG,"current queue size = "+ queueSize);
        if(queueSize > 0) {
            frame = mFrameQueue.poll();
            Log.d(TAG,"after queue poll ,size = "+ mFrameQueue.size());
        }
        return frame;
    }

    public synchronized boolean isEmptyQueue() {
        return mFrameQueue.isEmpty();
    }


    public void destroy() {
        if(mFrameQueue != null) {
            mFrameQueue.clear();
            mFrameQueue = null;
        }
    }

}
