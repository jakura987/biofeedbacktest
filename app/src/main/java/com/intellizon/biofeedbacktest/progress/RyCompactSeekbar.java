package com.intellizon.biofeedbacktest.progress;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.SeekBar;

import androidx.appcompat.widget.AppCompatSeekBar;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Lenovo on 2024/12/3.
 */

public class RyCompactSeekbar extends AppCompatSeekBar {

    @FunctionalInterface
    public interface NumberFormatter {
        String format(int value);

        NumberFormatter sDefault = String::valueOf;
    }

    private int step = 1;


    private NumberFormatter formatter = NumberFormatter.sDefault;

    private int minCompact;

    private OnSeekBarChangeListenerWrapper mWrapper;

    public RyCompactSeekbar(Context context) {
        this(context, null);
    }


    public RyCompactSeekbar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RyCompactSeekbar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setThumb(Drawable thumb) {
        super.setThumb(thumb);
        if (thumb instanceof OnSeekBarChangeListener) {
            setOnSeekBarChangeListener((OnSeekBarChangeListener) thumb);
        }
    }


    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
        invalidate();
    }

    public int getMinCompact() {
        return minCompact;
    }

    public void setMinCompact(int minCompact) {
        this.minCompact = minCompact;
        invalidate();
    }


    public NumberFormatter getFormatter() {
        return formatter;
    }

    public void setFormatter(NumberFormatter formatter) {
        this.formatter = formatter;
    }

    public void notifyRefresh() {
        if (mWrapper != null) {
            mWrapper.onProgressChanged(this, getProgress(), false);
        }
    }

    @Override
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
        if (mWrapper == null) {
            mWrapper = new OnSeekBarChangeListenerWrapper();
        }
        mWrapper.add(l);
        super.setOnSeekBarChangeListener(mWrapper);
    }

    private static class OnSeekBarChangeListenerWrapper implements OnSeekBarChangeListener {

        private final Set<OnSeekBarChangeListener> mWrapped = new HashSet<>();

        public void add(OnSeekBarChangeListener listener) {
            mWrapped.add(listener);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mWrapped.forEach(listener -> listener.onProgressChanged(seekBar, progress, fromUser));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mWrapped.forEach(listener -> listener.onStartTrackingTouch(seekBar));
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mWrapped.forEach(listener -> listener.onStopTrackingTouch(seekBar));
        }
    }
}
