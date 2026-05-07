package com.musicplayer.app.models;

import android.net.Uri;
import java.io.Serializable;

public class Song implements Serializable {
    private long id;
    private String title;
    private String artist;
    private String album;
    private String path;
    private long duration;
    private long albumId;
    private String albumArtUri;
    private String folderPath;
    private String lyrics;        // manually added lyrics
    private String language;      // detected language code e.g. "en", "es", "fr"

    public Song() {}

    public Song(long id, String title, String artist, String album,
                String path, long duration, long albumId) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.path = path;
        this.duration = duration;
        this.albumId = albumId;
        this.albumArtUri = "content://media/external/audio/albumart/" + albumId;
        this.folderPath = path.substring(0, path.lastIndexOf('/'));
    }

    // Getters
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getPath() { return path; }
    public long getDuration() { return duration; }
    public long getAlbumId() { return albumId; }
    public String getAlbumArtUri() { return albumArtUri; }
    public String getFolderPath() { return folderPath; }
    public String getLyrics() { return lyrics; }
    public String getLanguage() { return language; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setArtist(String artist) { this.artist = artist; }
    public void setAlbum(String album) { this.album = album; }
    public void setPath(String path) { this.path = path; }
    public void setDuration(long duration) { this.duration = duration; }
    public void setAlbumId(long albumId) { this.albumId = albumId; }
    public void setLyrics(String lyrics) { this.lyrics = lyrics; }
    public void setLanguage(String language) { this.language = language; }

    public String getFormattedDuration() {
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds %= 60;
        minutes %= 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    public Uri getUri() {
        return Uri.parse("file://" + path);
    }

    @Override
    public String toString() {
        return title + " - " + artist;
    }
}
