package com.musicplayer.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.musicplayer.app.models.Playlist;
import com.musicplayer.app.models.Song;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PreferenceManager {
    private static final String PREF_NAME = "MusicPlayerPrefs";
    private SharedPreferences prefs;
    private Gson gson;

    // Keys
    private static final String KEY_SHUFFLE = "shuffle";
    private static final String KEY_REPEAT = "repeat_mode";
    private static final String KEY_THEME = "theme_dark";
    private static final String KEY_EQ_BANDS = "eq_bands";
    private static final String KEY_EQ_ENABLED = "eq_enabled";
    private static final String KEY_PLAYLISTS = "playlists";
    private static final String KEY_LAST_SONG_PATH = "last_song_path";
    private static final String KEY_LAST_POSITION = "last_position";
    private static final String KEY_SONG_LYRICS = "lyrics_";
    private static final String KEY_SLEEP_TIMER_DURATION = "sleep_timer_duration";

    public PreferenceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    // ─── Playback settings ────────────────────────────────────────────────────
    public boolean isShuffle() { return prefs.getBoolean(KEY_SHUFFLE, false); }
    public void setShuffle(boolean shuffle) { prefs.edit().putBoolean(KEY_SHUFFLE, shuffle).apply(); }

    public int getRepeatMode() { return prefs.getInt(KEY_REPEAT, 0); }
    public void setRepeatMode(int mode) { prefs.edit().putInt(KEY_REPEAT, mode).apply(); }

    // ─── Theme ────────────────────────────────────────────────────────────────
    public boolean isDarkTheme() { return prefs.getBoolean(KEY_THEME, true); }
    public void setDarkTheme(boolean dark) { prefs.edit().putBoolean(KEY_THEME, dark).apply(); }

    // ─── Equalizer ────────────────────────────────────────────────────────────
    public short[] getEqualizerBands() {
        String json = prefs.getString(KEY_EQ_BANDS, null);
        if (json == null) return null;
        Type type = new TypeToken<short[]>(){}.getType();
        return gson.fromJson(json, type);
    }

    public void saveEqualizerBands(short[] bands) {
        prefs.edit().putString(KEY_EQ_BANDS, gson.toJson(bands)).apply();
    }

    public boolean isEqualizerEnabled() { return prefs.getBoolean(KEY_EQ_ENABLED, false); }
    public void setEqualizerEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply(); }

    // ─── Playlists ────────────────────────────────────────────────────────────
    public List<Playlist> getPlaylists() {
        String json = prefs.getString(KEY_PLAYLISTS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<Playlist>>(){}.getType();
        List<Playlist> playlists = gson.fromJson(json, type);
        return playlists != null ? playlists : new ArrayList<>();
    }

    public void savePlaylists(List<Playlist> playlists) {
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(playlists)).apply();
    }

    // ─── Last played ──────────────────────────────────────────────────────────
    public String getLastSongPath() { return prefs.getString(KEY_LAST_SONG_PATH, null); }
    public void setLastSongPath(String path) { prefs.edit().putString(KEY_LAST_SONG_PATH, path).apply(); }

    public int getLastPosition() { return prefs.getInt(KEY_LAST_POSITION, 0); }
    public void setLastPosition(int position) { prefs.edit().putInt(KEY_LAST_POSITION, position).apply(); }

    // ─── Lyrics ───────────────────────────────────────────────────────────────
    public String getLyrics(String songPath) {
        return prefs.getString(KEY_SONG_LYRICS + songPath.hashCode(), null);
    }

    public void saveLyrics(String songPath, String lyrics) {
        prefs.edit().putString(KEY_SONG_LYRICS + songPath.hashCode(), lyrics).apply();
    }

    // ─── Sleep timer ──────────────────────────────────────────────────────────
    public long getSleepTimerDuration() { return prefs.getLong(KEY_SLEEP_TIMER_DURATION, 30 * 60 * 1000L); }
    public void setSleepTimerDuration(long duration) { prefs.edit().putLong(KEY_SLEEP_TIMER_DURATION, duration).apply(); }
}
