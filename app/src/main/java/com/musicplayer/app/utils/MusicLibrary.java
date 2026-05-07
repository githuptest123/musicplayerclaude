package com.musicplayer.app.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import com.musicplayer.app.models.Song;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MusicLibrary {

    private static MusicLibrary instance;
    private Context context;
    private List<Song> allSongs = new ArrayList<>();
    private Map<String, List<Song>> folderMap = new HashMap<>();

    private MusicLibrary(Context context) {
        this.context = context.getApplicationContext();
    }

    public static MusicLibrary getInstance(Context context) {
        if (instance == null) {
            instance = new MusicLibrary(context);
        }
        return instance;
    }

    public void scanLibrary() {
        allSongs.clear();
        folderMap.clear();

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.IS_MUSIC
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
                MediaStore.Audio.Media.DURATION + " > 30000"; // > 30 seconds

        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        try (Cursor cursor = context.getContentResolver().query(
                uri, projection, selection, null, sortOrder)) {

            if (cursor != null && cursor.moveToFirst()) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

                do {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    String album = cursor.getString(albumCol);
                    String path = cursor.getString(dataCol);
                    long duration = cursor.getLong(durationCol);
                    long albumId = cursor.getLong(albumIdCol);

                    if (title == null) title = "Unknown";
                    if (artist == null) artist = "Unknown Artist";
                    if (album == null) album = "Unknown Album";

                    if (path != null && new File(path).exists()) {
                        Song song = new Song(id, title, artist, album, path, duration, albumId);
                        allSongs.add(song);

                        // Group by folder
                        String folder = path.substring(0, path.lastIndexOf('/'));
                        if (!folderMap.containsKey(folder)) {
                            folderMap.put(folder, new ArrayList<>());
                        }
                        folderMap.get(folder).add(song);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void scanFolder(String folderPath) {
        // Scan a specific folder for audio files
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) return;

        List<Song> folderSongs = new ArrayList<>();
        scanFolderRecursive(folder, folderSongs);
        folderMap.put(folderPath, folderSongs);
        for (Song s : folderSongs) {
            if (!allSongs.contains(s)) allSongs.add(s);
        }
    }

    private void scanFolderRecursive(File folder, List<Song> songs) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanFolderRecursive(file, songs);
            } else if (isAudioFile(file.getName())) {
                Song song = createSongFromFile(file);
                if (song != null) songs.add(song);
            }
        }
    }

    private boolean isAudioFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".flac") ||
                lower.endsWith(".aac") || lower.endsWith(".ogg") ||
                lower.endsWith(".wav") || lower.endsWith(".m4a") ||
                lower.endsWith(".opus") || lower.endsWith(".wma");
    }

    private Song createSongFromFile(File file) {
        try {
            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
            retriever.setDataSource(file.getAbsolutePath());

            String title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            retriever.release();

            if (title == null) title = file.getName().replaceAll("\\.[^.]+$", "");
            if (artist == null) artist = "Unknown Artist";
            if (album == null) album = "Unknown Album";
            long duration = durationStr != null ? Long.parseLong(durationStr) : 0;

            return new Song(file.hashCode(), title, artist, album,
                    file.getAbsolutePath(), duration, 0);
        } catch (Exception e) {
            return null;
        }
    }

    public List<Song> getAllSongs() { return allSongs; }
    public Map<String, List<Song>> getFolderMap() { return folderMap; }

    public List<Song> getSongsByFolder(String folderPath) {
        return folderMap.getOrDefault(folderPath, new ArrayList<>());
    }

    public List<String> getRootFolders() {
        return new ArrayList<>(folderMap.keySet());
    }

    public List<Song> searchSongs(String query) {
        List<Song> results = new ArrayList<>();
        String q = query.toLowerCase();
        for (Song song : allSongs) {
            if (song.getTitle().toLowerCase().contains(q) ||
                    song.getArtist().toLowerCase().contains(q) ||
                    song.getAlbum().toLowerCase().contains(q)) {
                results.add(song);
            }
        }
        return results;
    }
}
