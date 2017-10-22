package com.andy.mymediacodec.audio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.andy.mymediacodec.audio.decoder.AACDecoder;
import com.andy.mymediacodec.utils.AudioUtils;

/**
 * Created by Andy.chen on 2017/8/4.
 */

public class AudioTrackPcmPlayer {
    private AudioTrack mAudioTrack;

    public AudioTrackPcmPlayer() {
        if(mAudioTrack == null) {
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, AACDecoder.AudioCfg.sampleRete, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                    AudioUtils.getAudioBufferSize(), AudioTrack.MODE_STREAM);
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

    public void pushFrame(byte[] buffer, int offset, int size) {
        if(buffer == null) {
            return;
        }
        if(mAudioTrack != null) {
            try {
                mAudioTrack.write(buffer,offset,size);
                mAudioTrack.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
