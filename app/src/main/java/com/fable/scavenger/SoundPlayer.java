package com.fable.scavenger;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

public class SoundPlayer {
    private static SoundPool soundPool;

    private AudioAttributes audioAttributes;
    final int SOUND_POOL_MAX = 1;

    private static int dingSound;

    public SoundPlayer(Context context){

        audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        soundPool = new SoundPool.Builder()
                .setAudioAttributes(audioAttributes)
                .setMaxStreams(SOUND_POOL_MAX)
                .build();

        dingSound = soundPool.load(context, R.raw.ding, 1);
    }

    public void playDingSound(){
        soundPool.play(dingSound, 1.0f, 1.0f, 1, 0, 1.0f);
    }
}
