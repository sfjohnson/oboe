package com.google.oboe.samples.rhythmgame;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

public class GameView extends View {
    private static final String TAG = "GameView";

    private native boolean native_isSongDoneLoading();

    public class BeatNotation {
        int startTime;
        int duration;
        int option;

        public BeatNotation(int startTime, int duration, int option) {
            this.startTime = startTime;
            this.duration = duration;
            this.option = option;
        }
    }

    public class Note {
        int columnNumber; // 0-4
        Float tapTimeSec; // 3.2 means that the note is expected to be tapped at 3.2s
        boolean hasBeenTapped = false;

        public Note(int columnNumber, Float tapTimeSec) {
            this.columnNumber = columnNumber;
            this.tapTimeSec = tapTimeSec;
        }
    }

    private final int MUSIC_NUM_BEATS = 336;
    private final int MUSIC_BEATS_PER_MINUTE = 105;

    private int[][] SIXTEENTH_NOTES_BEAT_OPTIONS = {
            {}, // No notes
            {0, 2, 3, 5, 6, 8, 10, 12, 14},
            {0, 4, 8, 12}, // Quarter note per beat
            {0, 2, 3, 5, 6, 8, 10, 11, 13, 14},
            {0, 2, 3, 8, 12},
            {0, 8}, // Two half notes
    };

    private BeatNotation[] BEAT_PER_COUNT = {
            new BeatNotation(0, 16, 0), // Start time, duration in beats, option SIXTEENTH_NOTES_BEAT_OPTIONS
            new BeatNotation(16, 32, 2), // Start at beat 16 and choose option 2 for 32 beats
            new BeatNotation(48, 32, 3),
            new BeatNotation(80, 32, 1),
            new BeatNotation(112, 32, 4),
            new BeatNotation(144, 32, 1),
            new BeatNotation(176, 64, 3),
            new BeatNotation(240, 32, 1),
            new BeatNotation(272, 32, 2),
            new BeatNotation(306, 32, 5)
    };

    private int NUM_VERTICAL_SECTIONS = 5;
    private float BOTTOM_BAR_RELATIVE_POSITION = .8f; // Near the bottom of the screen
    private float DISTANCE_BETWEEN_SECTIONS = .16f;
    private float TIME_NOTE_APPEARS_ON_SCREEN_SEC = 1.5f; // Note appears on screen 1.5 sec

    private int[] COLUMN_COLORS = {
            Color.rgb(223, 227, 152),
            Color.rgb(152, 227, 178),
            Color.rgb(152, 183, 227),
            Color.rgb(212, 152, 227),
            Color.rgb(227, 152, 165)
    };

    private int mScreenHeight = 0;
    private int mScreenWidth = 0;
    private Paint mWhiteCirclePaint = new Paint();
    private Paint mWhiteLinePaint = new Paint();
    private ArrayList<Paint> mColorPaints = new ArrayList<Paint>();
    private Paint mClickCircleInnerPaint = new Paint();
    private Paint mClickCircleBorderPaint = new Paint();
    private float mBottomBarHeight;
    private boolean mWasScreenLengthAndWidthFound = false;
    private ArrayList<Note> mNotes = new ArrayList<Note>();
    private Random mRng = new Random();
    private Date mStartDate;

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setupPaints();
        setupBoards();
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mScreenHeight = getHeight(); //height is ready
                mScreenWidth = getWidth();
                mBottomBarHeight = mScreenHeight * BOTTOM_BAR_RELATIVE_POSITION;
                mWasScreenLengthAndWidthFound = true;
                mStartDate = new Date();
                invalidate();
                //mScreenWidth = getWidth();
            }
        });
    }

    private void setupPaints() {
        mWhiteLinePaint.setColor(Color.WHITE);
        mWhiteLinePaint.setStrokeWidth(5);
        mWhiteCirclePaint.setColor(Color.WHITE);

        mClickCircleInnerPaint.setColor(Color.RED);
        mClickCircleBorderPaint.setStyle(Paint.Style.STROKE);
        mClickCircleBorderPaint.setStrokeWidth(5);
        mClickCircleBorderPaint.setColor(Color.BLACK);

        for (int i = 0; i < NUM_VERTICAL_SECTIONS; i++) {
            Paint curPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            curPaint.setStyle(Paint.Style.FILL);
            curPaint.setColor(COLUMN_COLORS[i]);
            curPaint.setAlpha(200);
            mColorPaints.add(curPaint);
        }
    }

    private void setupBoards() {
        for (int i = 0; i < BEAT_PER_COUNT.length; i++) {
            int numBeatsUsed = 0;
            BeatNotation curBeatNotation = BEAT_PER_COUNT[i];
            while (numBeatsUsed < curBeatNotation.duration) {
                for (int j = 0; j < SIXTEENTH_NOTES_BEAT_OPTIONS[curBeatNotation.option].length; j++) {
                    // Get which beat contains the note.
                    float beat = curBeatNotation.startTime + numBeatsUsed
                            + SIXTEENTH_NOTES_BEAT_OPTIONS[curBeatNotation.option][j] / 4.0f;
                    // Now, convert this to a time from the tempo.
                    float targetTime = beat * 60 / MUSIC_BEATS_PER_MINUTE;
                    mNotes.add(new Note(mRng.nextInt(NUM_VERTICAL_SECTIONS), targetTime));
                }
                numBeatsUsed += 4; // 4 beats
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mWasScreenLengthAndWidthFound) {
            for (int i = 0; i < NUM_VERTICAL_SECTIONS; i++) {
                float xRelPos = (1.0f - DISTANCE_BETWEEN_SECTIONS * (NUM_VERTICAL_SECTIONS - 1)) / 2
                                    + i * DISTANCE_BETWEEN_SECTIONS;
                // draw the colored boxes
                canvas.drawRect((xRelPos - DISTANCE_BETWEEN_SECTIONS / 2) * mScreenWidth,
                        0,
                        (xRelPos + DISTANCE_BETWEEN_SECTIONS / 2) * mScreenWidth,
                        mBottomBarHeight,
                        mColorPaints.get(i));
                // draw the white circles in the bottom
                canvas.drawCircle(xRelPos * mScreenWidth,
                        mBottomBarHeight,
                        20,
                        mWhiteCirclePaint);
                // draw the white lines
                canvas.drawLine(xRelPos * mScreenWidth,
                        0,
                        xRelPos * mScreenWidth,
                        mBottomBarHeight,
                        mWhiteLinePaint);
            }
            // bottom white line
            canvas.drawLine(0,
                    mBottomBarHeight,
                    mScreenWidth,
                    mBottomBarHeight,
                    mWhiteLinePaint);

            Date curDate = new Date();
            // getTime returns in ms
            float curTimeInTrackSec = (curDate.getTime() - mStartDate.getTime()) / 1000.0f;
            for (int i = 0; i < mNotes.size(); i++) {
                Note curNote = mNotes.get(i);
                float noteStartTime = curNote.tapTimeSec
                        - TIME_NOTE_APPEARS_ON_SCREEN_SEC * BOTTOM_BAR_RELATIVE_POSITION;
                float noteEndTime = curNote.tapTimeSec
                        + TIME_NOTE_APPEARS_ON_SCREEN_SEC * (1 - BOTTOM_BAR_RELATIVE_POSITION);
                if ((curTimeInTrackSec > noteStartTime) && (curTimeInTrackSec < noteEndTime)) {
                    float xRelPos = (1.0f - DISTANCE_BETWEEN_SECTIONS * (NUM_VERTICAL_SECTIONS - 1)) / 2
                            + curNote.columnNumber * DISTANCE_BETWEEN_SECTIONS;
                    float yRelPos = (curTimeInTrackSec - noteStartTime) / TIME_NOTE_APPEARS_ON_SCREEN_SEC;
                    // draw the red circles
                    canvas.drawCircle(xRelPos * mScreenWidth,
                            yRelPos * mScreenHeight,
                            20,
                            mClickCircleInnerPaint);
                    // draw the black outline of the red circles
                    canvas.drawCircle(xRelPos * mScreenWidth,
                            yRelPos * mScreenHeight,
                            20,
                            mClickCircleBorderPaint);
                }
            }
            invalidate();
        }
    }
}
