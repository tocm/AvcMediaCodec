package com.andy.mymediacodec.audio;

import android.media.AudioManager;
import android.media.AudioTrack;

import static android.media.AudioTrack.MODE_STREAM;

/**
 * Created by Andy.chen on 2017/8/4.
 */

public class AudioTrackPcmPlayer {
    private AudioTrack mAudioTrack;
    private int sampleRate;

    public AudioTrackPcmPlayer(int sampleRateInHz, int channelConfig, int audioFormat,
                               int bufferSizeInBytes) {
        if(mAudioTrack == null) {
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, MODE_STREAM);
        }
    }

    public void startPlayer() {
        if(mAudioTrack != null) {
            mAudioTrack.play();
        }
    }

    public void stopPlayer() {
        if(mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
        }
    }

    public void pushFrame(byte[] buffer) {
        if(buffer == null) {
            return;
        }
        if(mAudioTrack != null) {
            try {
                mAudioTrack.write(buffer,0,buffer.length);
                mAudioTrack.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
