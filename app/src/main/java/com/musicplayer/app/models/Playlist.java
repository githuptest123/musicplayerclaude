package com.musicplayer.app.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Playlist implements Serializable {
    private long id;
    private String name;
    private List<Song> songs;
    private long dateCreated;
    private long dateModified;

    public Playlist(long id, String name) {
        this.id = id;
        this.name = name;
        this.songs = new ArrayList<>();
        this.dateCreated = System.currentTimeMillis();
        this.dateModified = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public List<Song> getSongs() { return songs; }
    public long getDateCreated() { return dateCreated; }
    public long getDateModified() { return dateModified; }

    public void setName(String name) {
        this.name = name;
        this.dateModified = System.currentTimeMillis();
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
        this.dateModified = System.currentTimeMillis();
    }

    public void addSong(Song song) {
        if (!songs.contains(song)) {
            songs.add(song);
            dateModified = System.currentTimeMillis();
        }
    }

    public void removeSong(Song song) {
        songs.remove(song);
        dateModified = System.currentTimeMillis();
    }

    public int getSongCount() { return songs.size(); }
}
