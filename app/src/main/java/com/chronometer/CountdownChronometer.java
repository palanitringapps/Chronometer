package com.chronometer;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Chronometer;

import java.lang.ref.WeakReference;
import java.util.Formatter;
import java.util.IllegalFormatException;
import java.util.Locale;

public class CountdownChronometer extends Chronometer {
    private static final String TAG = "CountdownChronometer";

    private static final String FAST_FORMAT_DHHMMSS = "%1$02d:%2$02d:%3$02d:%4$02d";
    private static final String FAST_FORMAT_HMMSS = "%1$02d:%2$02d:%3$02d";
    private static final String FAST_FORMAT_MMSS = "%1$02d:%2$02d";
    private static final String FAST_FORMAT_SS = "%2$02d";
    private static final char TIME_PADDING = '0';
    private static final char TIME_SEPARATOR = ':';
    private static final int TICK_HANDLER_MESSAGE = 3;

    private long base;
    private boolean visible;
    private boolean started;
    private static boolean running;
    private boolean logged;
    private String format;
    private Formatter formatter;
    private Locale formatterLocale;
    private Object[] formatterArgs = new Object[1];
    private StringBuilder formatBuilder;
    private OnChronometerTickListener onChronometerTickListener;
    private OnChronometerTickListener onCountdownCompleteListener;
    private StringBuilder recycle = new StringBuilder(8);

    private String chronoFormat;

    private MyHandler myHandler;

    /**
     * Initialize this CountdownChronometer object.
     */
    public CountdownChronometer(Context context) {
        this(context, null, 0, 0);
        myHandler = new MyHandler(this);
    }

    /**
     * Initialize this CountdownChronometer object.
     *
     * @param base Use the {@link SystemClock#elapsedRealtime} time base.
     */
    public CountdownChronometer(Context context, long base) {
        this(context, null, 0, base);
    }

    /**
     * Initialize with standard view layout information.
     */
    public CountdownChronometer(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
        myHandler = new MyHandler(this);
    }

    /**
     * Initialize with standard view layout information and style.
     *
     * @param base Use the {@link SystemClock#elapsedRealtime} time base.
     */
    public CountdownChronometer(Context context, AttributeSet attrs,
                                int defStyle, long base) {
        super(context, attrs, defStyle);
        myHandler = new MyHandler(this);
        init(base);
    }

    private void init(long base) {
        this.base = base;
        updateText(System.currentTimeMillis());
    }

    /**
     * Set the time that the count-down timer is in reference to.
     *
     * @param base Use the {@link SystemClock#elapsedRealtime} time base.
     */
    @Override
    public void setBase(long base) {
        this.base = base;
        dispatchChronometerTick();
        updateText(System.currentTimeMillis());
    }

    /**
     * Return the base time.
     */
    @Override
    public long getBase() {
        return base;
    }

    /**
     * Sets the format string used for display.  The CountdownChronometer will display
     * this string, with the first "%s" replaced by the current timer value in
     * "MM:SS", "H:MM:SS" or "D:HH:MM:SS" form.
     * <p>
     * If the format string is null, or if you never call setFormat(), the
     * Chronometer will simply display the timer value in "MM:SS", "H:MM:SS" or "D:HH:MM:SS"
     * form.
     *
     * @param format the format string.
     */
    @Override
    public void setFormat(String format) {
        this.format = format;
        if (format != null && formatBuilder == null) {
            formatBuilder = new StringBuilder(format.length() * 2);
        }
    }

    /**
     * Returns the current format string as set through {@link #setFormat}.
     */
    @Override
    public String getFormat() {
        return format;
    }

    /**
     * Sets a custom format string used for the timer value.
     * <p>
     * Example: "%1$02d days, %2$02d hours, %3$02d minutes and %4$02d seconds remaining"
     */
    public void setCustomChronoFormat(String chronoFormat) {
        this.chronoFormat = chronoFormat;
    }

    /**
     * Returns the current format string as set through {@link #setCustomChronoFormat}.
     */
    public String getCustomChronoFormat() {
        return chronoFormat;
    }

    /**
     * Sets the listener to be called when the chronometer changes.
     *
     * @param listener The listener.
     */
    @Override
    public void setOnChronometerTickListener(OnChronometerTickListener listener) {
        onChronometerTickListener = listener;
    }

    /**
     * @return The listener (may be null) that is listening for chronometer change
     * events.
     */
    @Override
    public OnChronometerTickListener getOnChronometerTickListener() {
        return onChronometerTickListener;
    }

    /**
     * Sets the listener to be called when the countdown is complete.
     *
     * @param listener The listener.
     */
    public void setOnCompleteListener(OnChronometerTickListener listener) {
        onCountdownCompleteListener = listener;
    }

    /**
     * @return The listener (may be null) that is listening for countdown complete
     * event.
     */
    public OnChronometerTickListener getOnCompleteListener() {
        return onCountdownCompleteListener;
    }

    /**
     * Start counting down.  This does not affect the base as set from {@link #setBase}, just
     * the view display.
     * <p>
     * CountdownChronometer works by regularly scheduling messages to the handler, even when the
     * Widget is not visible.  To make sure resource leaks do not occur, the user should
     * make sure that each start() call has a reciprocal call to {@link #stop}.
     */
    @Override
    public void start() {
        started = true;
        updateRunning();
    }

    /**
     * Stop counting down.  This does not affect the base as set from {@link #setBase}, just
     * the view display.
     * <p>
     * This stops the messages to the handler, effectively releasing resources that would
     * be held as the chronometer is running, via {@link #start}.
     */
    @Override
    public void stop() {
        started = false;
        updateRunning();
    }

    /**
     * The same as calling {@link #start} or {@link #stop}.
     */
    public void setStarted(boolean started) {
        this.started = started;
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        visible = false;
        updateRunning();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        visible = visibility == VISIBLE;
        updateRunning();
    }

    private synchronized boolean updateText(long now) {
        long seconds = base - now;
        seconds /= 1000;
        boolean stillRunning = true;
        if (seconds <= 0) {
            stillRunning = false;
            seconds = 0;
        }
        String text = formatRemainingTime(recycle, seconds);

        if (format != null) {
            Locale loc = Locale.getDefault();
            if (formatter == null || !loc.equals(formatterLocale)) {
                formatterLocale = loc;
                formatter = new Formatter(formatBuilder, loc);
            }
            formatBuilder.setLength(0);
            formatterArgs[0] = text;
            try {
                formatter.format(format, formatterArgs);
                text = formatBuilder.toString();
            } catch (IllegalFormatException ex) {
                if (!logged) {
                    Log.w(TAG, "Illegal format string: " + format);
                    logged = true;
                }
            }
        }
        setText(text);
        return stillRunning;
    }

    private void updateRunning() {
        boolean running = visible && started;
        if (running != CountdownChronometer.running) {
            if (running) {
                if (updateText(System.currentTimeMillis())) {
                    dispatchChronometerTick();
                    myHandler.sendMessageDelayed(
                            Message.obtain(myHandler, TICK_HANDLER_MESSAGE), 1000);
                } else {
                    running = false;
                    myHandler.removeMessages(TICK_HANDLER_MESSAGE);
                }
            } else {
                myHandler.removeMessages(TICK_HANDLER_MESSAGE);
            }
            CountdownChronometer.running = running;
        }
    }

    private static class MyHandler extends Handler {

        private final WeakReference<CountdownChronometer> chronometerWeakReference;

        MyHandler(CountdownChronometer CountdownChronometer) {
            chronometerWeakReference = new WeakReference<>(CountdownChronometer);
        }

        public void handleMessage(Message m) {
            CountdownChronometer CountdownChronometer = chronometerWeakReference.get();

            if (CountdownChronometer != null && running) {
                if (CountdownChronometer.updateText(System.currentTimeMillis())) {
                    CountdownChronometer.dispatchChronometerTick();
                    sendMessageDelayed(Message.obtain(this, TICK_HANDLER_MESSAGE), 1000);
                } else {
                    CountdownChronometer.dispatchCountdownCompleteEvent();
                    CountdownChronometer.stop();
                }

            }
        }
    }


    void dispatchChronometerTick() {
        if (onChronometerTickListener != null) {
            onChronometerTickListener.onChronometerTick(this);
        }
    }

    void dispatchCountdownCompleteEvent() {
        if (onCountdownCompleteListener != null) {
            onCountdownCompleteListener.onChronometerTick(this);
        }
    }

    /**
     * Formats remaining time in the form "MM:SS", "H:MM:SS" or "D:HH:MM:SS".
     *
     * @param recycle        {@link StringBuilder} to recycle, if possible
     * @param elapsedSeconds the remaining time in seconds.
     */
    private String formatRemainingTime(StringBuilder recycle,
                                       long elapsedSeconds) {

        long days = 0;
        long hours = 0;
        long minutes = 0;
        long seconds;

        if (elapsedSeconds >= 86400) {
            days = elapsedSeconds / 86400;
            elapsedSeconds -= days * 86400;
        }
        if (elapsedSeconds >= 3600) {
            hours = elapsedSeconds / 3600;
            elapsedSeconds -= hours * 3600;
        }
        if (elapsedSeconds >= 60) {
            minutes = elapsedSeconds / 60;
            elapsedSeconds -= minutes * 60;
        }
        seconds = elapsedSeconds;

        if (chronoFormat != null) {
            return formatRemainingTime(recycle, chronoFormat, days, hours,
                    minutes, seconds);
        } else if (days > 0) {
            return formatRemainingTime(recycle, FAST_FORMAT_DHHMMSS, days,
                    hours, minutes, seconds);
        } else if (hours > 0) {
            return formatRemainingTime(recycle, FAST_FORMAT_HMMSS, hours,
                    minutes, seconds);
        } else {
            return formatRemainingTime(recycle, FAST_FORMAT_SS, minutes,
                    seconds);
        }
    }

    private static String formatRemainingTime(StringBuilder recycle,
                                              String format, long days, long hours, long minutes, long seconds) {
        if (FAST_FORMAT_DHHMMSS.equals(format)) {
            StringBuilder sb = recycle;
            if (sb == null) {
                sb = new StringBuilder(8);
            } else {
                sb.setLength(0);
            }
            sb.append(days);
            sb.append(TIME_SEPARATOR);
            if (hours < 10) {
                sb.append(TIME_PADDING);
            } else {
                sb.append(toDigitChar(hours / 10));
            }
            sb.append(toDigitChar(hours % 10));
            sb.append(TIME_SEPARATOR);
            if (minutes < 10) {
                sb.append(TIME_PADDING);
            } else {
                sb.append(toDigitChar(minutes / 10));
            }
            sb.append(toDigitChar(minutes % 10));
            sb.append(TIME_SEPARATOR);
            if (seconds < 10) {
                sb.append(TIME_PADDING);
            } else {
                sb.append(toDigitChar(seconds / 10));
            }
            sb.append(toDigitChar(seconds % 10));
            return sb.toString();
        } else {
            return String.format(format, days, hours, minutes, seconds);
        }
    }

    private static String formatRemainingTime(StringBuilder recycle,
                                              String format, long hours, long minutes, long seconds) {
        if (FAST_FORMAT_HMMSS.equals(format)) {
            StringBuilder sb = recycle;
            if (sb == null) {
                sb = new StringBuilder(8);
            } else {
                sb.setLength(0);
            }
            sb.append(hours);
            sb.append(TIME_SEPARATOR);
            if (minutes < 10) {
                sb.append(TIME_PADDING);
            } else {
                sb.append(toDigitChar(minutes / 10));
            }
            sb.append(toDigitChar(minutes % 10));
            sb.append(TIME_SEPARATOR);
            if (seconds < 10) {
                sb.append(TIME_PADDING);
            } else {
                sb.append(toDigitChar(seconds / 10));
            }
            sb.append(toDigitChar(seconds % 10));
            return sb.toString();
        } else {
            return String.format(format, hours, minutes, seconds);
        }
    }

    private static String formatRemainingTime(StringBuilder recycle,
                                              String format, long minutes, long seconds) {
        if (FAST_FORMAT_MMSS.equals(format)) {
            StringBuilder sb = recycle;
            if (sb == null) {
                sb = new StringBuilder(8);
            } else {
                sb.setLength(0);
            }
            if (minutes < 10) {
                sb.append(TIME_PADDING);
            } else {
                sb.append(toDigitChar(minutes / 10));
            }
            sb.append(toDigitChar(minutes % 10));
            sb.append(TIME_SEPARATOR);
            if (seconds < 10) {
                sb.append(TIME_PADDING);
            } else {
                sb.append(toDigitChar(seconds / 10));
            }
            sb.append(toDigitChar(seconds % 10));
            return sb.toString();
        } else {
            return String.format(format, minutes, seconds);
        }
    }

    private static char toDigitChar(long digit) {
        return (char) (digit + '0');
    }

}
