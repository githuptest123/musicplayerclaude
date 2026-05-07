package com.musicplayer.app;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.palette.graphics.Palette;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.musicplayer.app.models.Song;
import com.musicplayer.app.services.MusicService;
import com.musicplayer.app.utils.LyricsManager;
import com.musicplayer.app.utils.PreferenceManager;
import android.os.SystemClock;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;

public class NowPlayingActivity extends AppCompatActivity {

    // Views
    private ImageView albumArt;
    private TextView songTitle, songArtist, songAlbum;
    private SeekBar seekBar;
    private TextView currentTime, totalTime;
    private ImageButton btnPlayPause, btnNext, btnPrev;
    private ImageButton btnSkipForward, btnSkipBackward;
    private ImageButton btnShuffle, btnRepeat;
    private ImageButton btnLyrics, btnEqualizer, btnSleepTimer;
    private ImageButton btnBack;
    private View lyricsPanel;
    private TextView lyricsText;
    private ProgressBar lyricsLoading;
    private ScrollView lyricsScroll;

    // Service
    private MusicService musicService;
    private boolean serviceConnected = false;

    // Skip hold support
    private Handler skipHandler = new Handler(Looper.getMainLooper());
    private boolean isSkipHolding = false;
    private static final long SKIP_HOLD_DELAY = 400L;   // delay before continuous skip starts
    private static final long SKIP_HOLD_INTERVAL = 300L; // interval when held

    // Seek bar tracking
    private boolean isSeeking = false;

    private LyricsManager lyricsManager;
    private PreferenceManager prefManager;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MusicBinder) service).getService();
            serviceConnected = true;
            updateUI();
            startSeekBarUpdater();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceConnected = false;
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            if (action.equals(MusicService.BROADCAST_SONG_CHANGED)) {
                updateUI();
            } else if (action.equals(MusicService.BROADCAST_PLAYBACK_STATE)) {
                updatePlaybackButtons();
            } else if (action.equals(MusicService.BROADCAST_PROGRESS)) {
                if (!isSeeking) {
                    int pos = intent.getIntExtra("position", 0);
                    int dur = intent.getIntExtra("duration", 0);
                    updateSeekBar(pos, dur);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_now_playing);

        lyricsManager = new LyricsManager(this);
        prefManager = new PreferenceManager(this);

        initViews();
        bindMusicService();
    }

    private void initViews() {
        albumArt = findViewById(R.id.albumArt);
        songTitle = findViewById(R.id.songTitle);
        songArtist = findViewById(R.id.songArtist);
        songAlbum = findViewById(R.id.songAlbum);
        seekBar = findViewById(R.id.seekBar);
        currentTime = findViewById(R.id.currentTime);
        totalTime = findViewById(R.id.totalTime);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        btnSkipForward = findViewById(R.id.btnSkipForward);
        btnSkipBackward = findViewById(R.id.btnSkipBackward);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnLyrics = findViewById(R.id.btnLyrics);
        btnEqualizer = findViewById(R.id.btnEqualizer);
        btnSleepTimer = findViewById(R.id.btnSleepTimer);
        btnBack = findViewById(R.id.btnBack);
        lyricsPanel = findViewById(R.id.lyricsPanel);
        lyricsText = findViewById(R.id.lyricsText);
        lyricsLoading = findViewById(R.id.lyricsLoading);
        lyricsScroll = findViewById(R.id.lyricsScroll);

        setupButtons();
        setupSeekBar();
        setupSwipeGesture();
    }

    private void setupButtons() {
        btnBack.setOnClickListener(v -> finish());
        btnPlayPause.setOnClickListener(v -> { if (serviceConnected) musicService.togglePlayPause(); });
        btnNext.setOnClickListener(v -> { if (serviceConnected) musicService.nextSong(); });
        btnPrev.setOnClickListener(v -> { if (serviceConnected) musicService.previousSong(); });

        // ── Skip forward 10s ──────────────────────────────────────────────────
        btnSkipForward.setOnClickListener(v -> {
            if (serviceConnected) musicService.seekForward(10000);
        });
        btnSkipForward.setOnLongClickListener(v -> {
            startContinuousSkip(true);
            return true;
        });
        btnSkipForward.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP ||
                event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopContinuousSkip();
            }
            return false;
        });

        // ── Skip backward 10s ─────────────────────────────────────────────────
        btnSkipBackward.setOnClickListener(v -> {
            if (serviceConnected) musicService.seekBackward(10000);
        });
        btnSkipBackward.setOnLongClickListener(v -> {
            startContinuousSkip(false);
            return true;
        });
        btnSkipBackward.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP ||
                event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopContinuousSkip();
            }
            return false;
        });

        // ── Shuffle / Repeat ──────────────────────────────────────────────────
        btnShuffle.setOnClickListener(v -> { if (serviceConnected) musicService.toggleShuffle(); updatePlaybackButtons(); });
        btnRepeat.setOnClickListener(v -> { if (serviceConnected) musicService.cycleRepeatMode(); updatePlaybackButtons(); });

        // ── Lyrics ────────────────────────────────────────────────────────────
        btnLyrics.setOnClickListener(v -> toggleLyricsPanel());

        // ── Equalizer ─────────────────────────────────────────────────────────
        btnEqualizer.setOnClickListener(v -> startActivity(new Intent(this, EqualizerActivity.class)));

        // ── Sleep Timer ───────────────────────────────────────────────────────
        btnSleepTimer.setOnClickListener(v -> showSleepTimerDialog());
    }

    // ─── Continuous skip (hold) ───────────────────────────────────────────────

    private void startContinuousSkip(boolean forward) {
        isSkipHolding = true;
        skipHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isSkipHolding && serviceConnected) {
                    if (forward) musicService.seekForward(10000);
                    else musicService.seekBackward(10000);
                    skipHandler.postDelayed(this, SKIP_HOLD_INTERVAL);
                }
            }
        }, SKIP_HOLD_DELAY);
    }

    private void stopContinuousSkip() {
        isSkipHolding = false;
        skipHandler.removeCallbacksAndMessages(null);
    }

    // ─── SeekBar ──────────────────────────────────────────────────────────────

    private void setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeeking = false;
                if (serviceConnected) {
                    musicService.seekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void updateSeekBar(int position, int duration) {
        seekBar.setMax(duration);
        seekBar.setProgress(position);
        currentTime.setText(formatTime(position));
        totalTime.setText(formatTime(duration));
    }

    private void startSeekBarUpdater() {
        // Progress comes via broadcast from service, nothing extra needed
    }

    // ─── Swipe to change song ─────────────────────────────────────────────────

    private void setupSwipeGesture() {
        GestureDetector gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    private static final int SWIPE_MIN_DISTANCE = 120;
                    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                          float velocityX, float velocityY) {
                        if (e1 == null || e2 == null) return false;
                        float dx = e2.getX() - e1.getX();
                        float dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) > Math.abs(dy) &&
                                Math.abs(dx) > SWIPE_MIN_DISTANCE &&
                                Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                            if (dx > 0) {
                                if (serviceConnected) musicService.previousSong();
                            } else {
                                if (serviceConnected) musicService.nextSong();
                            }
                            return true;
                        }
                        return false;
                    }
                });

        albumArt.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    // ─── Lyrics panel ─────────────────────────────────────────────────────────

    private boolean lyricsVisible = false;

    private void toggleLyricsPanel() {
        lyricsVisible = !lyricsVisible;
        if (lyricsVisible) {
            lyricsPanel.setVisibility(View.VISIBLE);
            lyricsPanel.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up));
            loadLyrics();
        } else {
            lyricsPanel.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_down));
            lyricsPanel.setVisibility(View.GONE);
        }
    }

    private void loadLyrics() {
        if (!serviceConnected) return;
        Song song = musicService.getCurrentSong();
        if (song == null) return;

        lyricsLoading.setVisibility(View.VISIBLE);
        lyricsText.setVisibility(View.GONE);

        lyricsManager.getLyrics(song, false, new LyricsManager.LyricsCallback() {
            @Override
            public void onLyricsReady(String lyrics, String detectedLanguage) {
                lyricsLoading.setVisibility(View.GONE);
                lyricsText.setVisibility(View.VISIBLE);
                lyricsText.setText(lyrics);
            }

            @Override
            public void onError(String error) {
                lyricsLoading.setVisibility(View.GONE);
                lyricsText.setVisibility(View.VISIBLE);
                lyricsText.setText(error + "\n\nTap the edit button to add lyrics manually.");
                showAddLyricsOption(song);
            }

            @Override
            public void onLanguageDetected(String languageCode, Runnable proceedInEnglish, Runnable proceedOriginal) {
                // Song is not in English - ask user
                new MaterialAlertDialogBuilder(NowPlayingActivity.this)
                        .setTitle("Language Detected")
                        .setMessage("This song appears to be in " + getLanguageName(languageCode) + ". Would you like the lyrics translated to English?")
                        .setPositiveButton("Yes, translate to English", (d, w) -> {
                            lyricsLoading.setVisibility(View.VISIBLE);
                            lyricsText.setVisibility(View.GONE);
                            proceedInEnglish.run();
                        })
                        .setNegativeButton("Keep original language", (d, w) -> {
                            lyricsLoading.setVisibility(View.VISIBLE);
                            lyricsText.setVisibility(View.GONE);
                            proceedOriginal.run();
                        })
                        .show();
            }
        });
    }

    private void showAddLyricsOption(Song song) {
        // Show an "Add lyrics" button within lyrics panel
        View addBtn = lyricsPanel.findViewById(R.id.btnAddLyrics);
        if (addBtn != null) {
            addBtn.setVisibility(View.VISIBLE);
            addBtn.setOnClickListener(v -> showManualLyricsDialog(song));
        }
    }

    private void showManualLyricsDialog(Song song) {
        EditText input = new EditText(this);
        input.setHint("Paste lyrics here...");
        input.setMinLines(8);
        input.setGravity(android.view.Gravity.TOP);
        // Pre-fill if lyrics already exist
        String existing = lyricsManager.getSavedLyrics(song.getPath());
        if (existing != null) input.setText(existing);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Add Lyrics for " + song.getTitle())
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String lyrics = input.getText().toString().trim();
                    if (!lyrics.isEmpty()) {
                        lyricsManager.saveLyricsManually(song.getPath(), lyrics);
                        lyricsText.setText(lyrics);
                        lyricsText.setVisibility(View.VISIBLE);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Sleep Timer Dialog ───────────────────────────────────────────────────

    private void showSleepTimerDialog() {
        String[] options = {
                "5 minutes", "10 minutes", "15 minutes", "20 minutes",
                "30 minutes", "45 minutes", "1 hour", "Cancel timer"
        };
        long[] durations = {
                5 * 60 * 1000L, 10 * 60 * 1000L, 15 * 60 * 1000L, 20 * 60 * 1000L,
                30 * 60 * 1000L, 45 * 60 * 1000L, 60 * 60 * 1000L, -1L
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle("Sleep Timer")
                .setItems(options, (dialog, which) -> {
                    if (!serviceConnected) return;
                    if (durations[which] == -1L) {
                        musicService.cancelSleepTimer();
                        Toast.makeText(this, "Sleep timer cancelled", Toast.LENGTH_SHORT).show();
                    } else {
                        musicService.setSleepTimer(durations[which]);
                        Toast.makeText(this, "Sleep timer set for " + options[which], Toast.LENGTH_SHORT).show();
                        btnSleepTimer.setColorFilter(getColor(R.color.accent));
                    }
                })
                .show();
    }

    // ─── UI Updates ───────────────────────────────────────────────────────────

    private void updateUI() {
        if (!serviceConnected) return;
        Song song = musicService.getCurrentSong();
        if (song == null) return;

        songTitle.setText(song.getTitle());
        songArtist.setText(song.getArtist());
        songAlbum.setText(song.getAlbum());
        totalTime.setText(song.getFormattedDuration());

        // Album art with palette-based background
        Glide.with(this)
                .load(song.getAlbumArtUri())
                .error(R.drawable.default_album_art)
                .centerCrop()
                .into(albumArt);

        // Generate palette from album art in background
        new Thread(() -> {
            Bitmap bitmap = loadAlbumArtBitmap(song);
            if (bitmap != null) {
                Palette.from(bitmap).generate(palette -> {
                    if (palette == null) return;
                    int vibrant = palette.getVibrantColor(0);
                    if (vibrant != 0) {
                        runOnUiThread(() -> {
                            // Use palette color for accent elements
                            // (in a real implementation you'd update the background/accent)
                        });
                    }
                });
            }
        }).start();

        updatePlaybackButtons();

        // If lyrics panel is visible, reload
        if (lyricsVisible) {
            loadLyrics();
        }
    }

    private void updatePlaybackButtons() {
        if (!serviceConnected) return;
        btnPlayPause.setImageResource(
                musicService.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

        // Shuffle button tint
        btnShuffle.setAlpha(musicService.isShuffle() ? 1.0f : 0.4f);

        // Repeat button icon
        switch (musicService.getRepeatMode()) {
            case MusicService.REPEAT_NONE:
                btnRepeat.setImageResource(R.drawable.ic_repeat);
                btnRepeat.setAlpha(0.4f);
                break;
            case MusicService.REPEAT_ALL:
                btnRepeat.setImageResource(R.drawable.ic_repeat);
                btnRepeat.setAlpha(1.0f);
                break;
            case MusicService.REPEAT_ONE:
                btnRepeat.setImageResource(R.drawable.ic_repeat_one);
                btnRepeat.setAlpha(1.0f);
                break;
        }
    }

    private Bitmap loadAlbumArtBitmap(Song song) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(song.getPath());
            byte[] art = retriever.getEmbeddedPicture();
            retriever.release();
            if (art != null) return BitmapFactory.decodeByteArray(art, 0, art.length);
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / 1000) / 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private String getLanguageName(String code) {
        switch (code.toLowerCase()) {
            case "es": return "Spanish";
            case "fr": return "French";
            case "de": return "German";
            case "pt": return "Portuguese";
            case "it": return "Italian";
            case "ja": return "Japanese";
            case "ko": return "Korean";
            case "zh": return "Chinese";
            case "ar": return "Arabic";
            case "ru": return "Russian";
            case "hi": return "Hindi";
            default: return code.toUpperCase();
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    private void bindMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        startForegroundService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(MusicService.BROADCAST_SONG_CHANGED);
        filter.addAction(MusicService.BROADCAST_PLAYBACK_STATE);
        filter.addAction(MusicService.BROADCAST_PROGRESS);
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(receiver);
        stopContinuousSkip();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceConnected) unbindService(serviceConnection);
    }
}
