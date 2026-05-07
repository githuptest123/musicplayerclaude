package com.musicplayer.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;
import com.musicplayer.app.MainActivity;
import com.musicplayer.app.NowPlayingActivity;
import com.musicplayer.app.R;
import com.musicplayer.app.models.Song;
import com.musicplayer.app.utils.MusicLibrary;
import com.musicplayer.app.utils.PreferenceManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MusicService extends Service implements
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "MusicService";
    public static final String CHANNEL_ID = "MusicPlayerChannel";
    public static final int NOTIFICATION_ID = 101;

    // Actions
    public static final String ACTION_PLAY = "com.musicplayer.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.musicplayer.ACTION_PAUSE";
    public static final String ACTION_NEXT = "com.musicplayer.ACTION_NEXT";
    public static final String ACTION_PREVIOUS = "com.musicplayer.ACTION_PREVIOUS";
    public static final String ACTION_STOP = "com.musicplayer.ACTION_STOP";
    public static final String ACTION_SEEK = "com.musicplayer.ACTION_SEEK";
    public static final String ACTION_SHUFFLE = "com.musicplayer.ACTION_SHUFFLE";
    public static final String ACTION_REPEAT = "com.musicplayer.ACTION_REPEAT";
    public static final String ACTION_SLEEP_TIMER = "com.musicplayer.ACTION_SLEEP_TIMER";

    // Broadcast actions (sent TO activities)
    public static final String BROADCAST_SONG_CHANGED = "com.musicplayer.SONG_CHANGED";
    public static final String BROADCAST_PLAYBACK_STATE = "com.musicplayer.PLAYBACK_STATE";
    public static final String BROADCAST_PROGRESS = "com.musicplayer.PROGRESS";

    // Repeat modes
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    // Binder
    private final IBinder binder = new MusicBinder();

    // Player
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;

    // State
    private List<Song> playlist = new ArrayList<>();
    private List<Song> shuffledPlaylist = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isPlaying = false;
    private boolean isShuffle = false;
    private int repeatMode = REPEAT_NONE;
    private boolean isPrepared = false;

    // Equalizer
    private Equalizer equalizer;
    private static final int EQ_PRESET_NORMAL = 0;

    // Sleep timer
    private Timer sleepTimer;
    private long sleepTimerEndTime = -1;

    // Progress broadcaster
    private Timer progressTimer;

    // Preferences
    private PreferenceManager prefManager;

    // Notification manager
    private NotificationManager notificationManager;

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefManager = new PreferenceManager(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel();
        initMediaSession();
        initMediaPlayer();
        startProgressBroadcaster();

        // Restore state
        isShuffle = prefManager.isShuffle();
        repeatMode = prefManager.getRepeatMode();
    }

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "MusicPlayer");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { resumePlayback(); }
            @Override public void onPause() { pausePlayback(); }
            @Override public void onSkipToNext() { nextSong(); }
            @Override public void onSkipToPrevious() { previousSong(); }
            @Override public void onStop() { stopSelf(); }
            @Override public void onSeekTo(long pos) { seekTo((int) pos); }
        });

        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSession, intent);
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_PLAY: resumePlayback(); break;
                    case ACTION_PAUSE: pausePlayback(); break;
                    case ACTION_NEXT: nextSong(); break;
                    case ACTION_PREVIOUS: previousSong(); break;
                    case ACTION_STOP: stopSelf(); break;
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ─── Playback control ──────────────────────────────────────────────────────

    public void setPlaylist(List<Song> songs, int startIndex) {
        playlist.clear();
        playlist.addAll(songs);
        shuffledPlaylist.clear();
        shuffledPlaylist.addAll(songs);
        if (isShuffle) {
            shufflePlaylist(startIndex);
            currentIndex = 0;
        } else {
            currentIndex = startIndex;
        }
        playCurrent();
    }

    public void playCurrent() {
        if (getCurrentList().isEmpty()) return;
        Song song = getCurrentSong();
        if (song == null) return;

        isPrepared = false;
        mediaPlayer.reset();

        try {
            mediaPlayer.setDataSource(song.getPath());
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e(TAG, "Error setting data source: " + e.getMessage());
            nextSong();
        }

        // Init equalizer on first song
        if (equalizer == null) {
            initEqualizer();
        }

        broadcastSongChanged();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        requestAudioFocus();
        mp.start();
        isPlaying = true;
        broadcastPlaybackState();
        updateNotification();
        updateMediaSessionState();
    }

    public void pausePlayback() {
        if (mediaPlayer != null && isPlaying && isPrepared) {
            mediaPlayer.pause();
            isPlaying = false;
            broadcastPlaybackState();
            updateNotification();
            updateMediaSessionState();
        }
    }

    public void resumePlayback() {
        if (mediaPlayer != null && !isPlaying && isPrepared) {
            requestAudioFocus();
            mediaPlayer.start();
            isPlaying = true;
            broadcastPlaybackState();
            updateNotification();
            updateMediaSessionState();
        }
    }

    public void togglePlayPause() {
        if (isPlaying) pausePlayback();
        else resumePlayback();
    }

    public void nextSong() {
        List<Song> list = getCurrentList();
        if (list.isEmpty()) return;

        if (repeatMode == REPEAT_ONE) {
            playCurrent();
            return;
        }

        if (currentIndex < list.size() - 1) {
            currentIndex++;
        } else if (repeatMode == REPEAT_ALL) {
            currentIndex = 0;
        } else {
            pausePlayback();
            return;
        }
        playCurrent();
    }

    public void previousSong() {
        // If more than 3 seconds in, restart. Otherwise go to previous.
        if (isPrepared && mediaPlayer.getCurrentPosition() > 3000) {
            mediaPlayer.seekTo(0);
            return;
        }
        List<Song> list = getCurrentList();
        if (list.isEmpty()) return;

        if (currentIndex > 0) {
            currentIndex--;
        } else if (repeatMode == REPEAT_ALL) {
            currentIndex = list.size() - 1;
        }
        playCurrent();
    }

    public void seekTo(int milliseconds) {
        if (isPrepared) {
            mediaPlayer.seekTo(milliseconds);
        }
    }

    public void seekForward(int milliseconds) {
        if (isPrepared) {
            int newPos = Math.min(mediaPlayer.getCurrentPosition() + milliseconds,
                    mediaPlayer.getDuration());
            mediaPlayer.seekTo(newPos);
        }
    }

    public void seekBackward(int milliseconds) {
        if (isPrepared) {
            int newPos = Math.max(mediaPlayer.getCurrentPosition() - milliseconds, 0);
            mediaPlayer.seekTo(newPos);
        }
    }

    public void toggleShuffle() {
        isShuffle = !isShuffle;
        prefManager.setShuffle(isShuffle);
        if (isShuffle) {
            Song current = getCurrentSong();
            shufflePlaylist(playlist.indexOf(current));
            currentIndex = 0;
        } else {
            Song current = getCurrentSong();
            currentIndex = playlist.indexOf(current);
        }
        broadcastPlaybackState();
    }

    public void cycleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
        prefManager.setRepeatMode(repeatMode);
        broadcastPlaybackState();
    }

    private void shufflePlaylist(int currentSongIndex) {
        shuffledPlaylist.clear();
        shuffledPlaylist.addAll(playlist);
        if (currentSongIndex >= 0 && currentSongIndex < shuffledPlaylist.size()) {
            Song current = shuffledPlaylist.remove(currentSongIndex);
            Collections.shuffle(shuffledPlaylist, new Random());
            shuffledPlaylist.add(0, current);
        } else {
            Collections.shuffle(shuffledPlaylist, new Random());
        }
    }

    // ─── Sleep Timer ──────────────────────────────────────────────────────────

    public void setSleepTimer(long durationMs) {
        cancelSleepTimer();
        sleepTimerEndTime = System.currentTimeMillis() + durationMs;
        sleepTimer = new Timer();
        sleepTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                pausePlayback();
                sleepTimerEndTime = -1;
            }
        }, durationMs);
    }

    public void cancelSleepTimer() {
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
        }
        sleepTimerEndTime = -1;
    }

    public long getSleepTimerEndTime() { return sleepTimerEndTime; }

    // ─── Equalizer ────────────────────────────────────────────────────────────

    private void initEqualizer() {
        if (mediaPlayer == null) return;
        try {
            equalizer = new Equalizer(0, mediaPlayer.getAudioSessionId());
            equalizer.setEnabled(true);

            // Restore saved EQ settings
            short[] bandLevels = prefManager.getEqualizerBands();
            if (bandLevels != null) {
                for (short i = 0; i < equalizer.getNumberOfBands(); i++) {
                    if (i < bandLevels.length) {
                        equalizer.setBandLevel(i, bandLevels[i]);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Equalizer init failed: " + e.getMessage());
        }
    }

    public Equalizer getEqualizer() { return equalizer; }

    public int getAudioSessionId() {
        return mediaPlayer != null ? mediaPlayer.getAudioSessionId() : -1;
    }

    // ─── State getters ────────────────────────────────────────────────────────

    public Song getCurrentSong() {
        List<Song> list = getCurrentList();
        if (list.isEmpty() || currentIndex >= list.size()) return null;
        return list.get(currentIndex);
    }

    public List<Song> getCurrentList() {
        return isShuffle ? shuffledPlaylist : playlist;
    }

    public List<Song> getPlaylist() { return playlist; }

    public boolean isPlaying() { return isPlaying; }
    public boolean isShuffle() { return isShuffle; }
    public int getRepeatMode() { return repeatMode; }
    public int getCurrentPosition() { return isPrepared ? mediaPlayer.getCurrentPosition() : 0; }
    public int getDuration() { return isPrepared ? mediaPlayer.getDuration() : 0; }
    public int getCurrentIndex() { return currentIndex; }
    public boolean isPrepared() { return isPrepared; }

    // ─── Audio focus ──────────────────────────────────────────────────────────

    private void requestAudioFocus() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(this)
                .build();
        audioManager.requestAudioFocus(audioFocusRequest);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (!isPlaying) resumePlayback();
                mediaPlayer.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                pausePlayback();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                pausePlayback();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                mediaPlayer.setVolume(0.3f, 0.3f);
                break;
        }
    }

    // ─── MediaPlayer callbacks ────────────────────────────────────────────────

    @Override
    public void onCompletion(MediaPlayer mp) {
        nextSong();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
        nextSong();
        return true;
    }

    // ─── Broadcasts ───────────────────────────────────────────────────────────

    private void broadcastSongChanged() {
        Intent intent = new Intent(BROADCAST_SONG_CHANGED);
        sendBroadcast(intent);
    }

    private void broadcastPlaybackState() {
        Intent intent = new Intent(BROADCAST_PLAYBACK_STATE);
        intent.putExtra("isPlaying", isPlaying);
        intent.putExtra("isShuffle", isShuffle);
        intent.putExtra("repeatMode", repeatMode);
        sendBroadcast(intent);
    }

    private void startProgressBroadcaster() {
        progressTimer = new Timer();
        progressTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isPlaying && isPrepared) {
                    Intent intent = new Intent(BROADCAST_PROGRESS);
                    intent.putExtra("position", mediaPlayer.getCurrentPosition());
                    intent.putExtra("duration", mediaPlayer.getDuration());
                    sendBroadcast(intent);
                }
            }
        }, 0, 500);
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Music player controls");
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Song song = getCurrentSong();
        if (song == null) return null;

        // Album art
        Bitmap albumArt = getAlbumArt(song);

        Intent notifIntent = new Intent(this, NowPlayingActivity.class);
        notifIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notifIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent prevIntent = buildActionIntent(ACTION_PREVIOUS, 0);
        PendingIntent playPauseIntent = buildActionIntent(isPlaying ? ACTION_PAUSE : ACTION_PLAY, 1);
        PendingIntent nextIntent = buildActionIntent(ACTION_NEXT, 2);
        PendingIntent stopIntent = buildActionIntent(ACTION_STOP, 3);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(song.getTitle())
                .setContentText(song.getArtist())
                .setSubText(song.getAlbum())
                .setSmallIcon(R.drawable.ic_music_note)
                .setLargeIcon(albumArt)
                .setContentIntent(contentIntent)
                .setDeleteIntent(stopIntent)
                .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
                .addAction(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play, "Play/Pause", playPauseIntent)
                .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }

    private PendingIntent buildActionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateNotification() {
        Notification notification = buildNotification();
        if (notification != null) {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Bitmap getAlbumArt(Song song) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(song.getPath());
            byte[] art = retriever.getEmbeddedPicture();
            retriever.release();
            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting album art: " + e.getMessage());
        }
        return BitmapFactory.decodeResource(getResources(), R.drawable.default_album_art);
    }

    private void updateMediaSessionState() {
        if (mediaSession == null) return;
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(
                        isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        isPrepared ? mediaPlayer.getCurrentPosition() : 0,
                        1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());

        Song song = getCurrentSong();
        if (song != null) {
            mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle())
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist())
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.getAlbum())
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.getDuration())
                    .build());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
        if (sleepTimer != null) sleepTimer.cancel();
        if (progressTimer != null) progressTimer.cancel();
        if (audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
    }
}
