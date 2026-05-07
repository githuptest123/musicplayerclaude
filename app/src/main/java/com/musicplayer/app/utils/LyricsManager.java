package com.musicplayer.app.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.musicplayer.app.models.Song;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages lyrics for songs:
 * 1. Checks for manually added lyrics (stored in prefs)
 * 2. Checks for .lrc or .txt files alongside the audio file
 * 3. Uses OpenAI Whisper API for transcription
 */
public class LyricsManager {

    private static final String TAG = "LyricsManager";
    private Context context;
    private PreferenceManager prefManager;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface LyricsCallback {
        void onLyricsReady(String lyrics, String detectedLanguage);
        void onError(String error);
        void onLanguageDetected(String languageCode, Runnable proceedInEnglish, Runnable proceedOriginal);
    }

    public LyricsManager(Context context) {
        this.context = context;
        this.prefManager = new PreferenceManager(context);
    }

    /**
     * Load lyrics for a song. Priority:
     * 1. Manually saved lyrics in preferences
     * 2. Sidecar .lrc/.txt file
     * 3. Whisper transcription (if API key set)
     */
    public void getLyrics(Song song, boolean translateToEnglish, LyricsCallback callback) {
        executor.execute(() -> {
            // 1. Check manually saved lyrics
            String savedLyrics = prefManager.getLyrics(song.getPath());
            if (savedLyrics != null && !savedLyrics.isEmpty()) {
                mainHandler.post(() -> callback.onLyricsReady(savedLyrics, "manual"));
                return;
            }

            // 2. Check for sidecar file (.lrc or .txt)
            String sidecarLyrics = checkSidecarFile(song.getPath());
            if (sidecarLyrics != null) {
                mainHandler.post(() -> callback.onLyricsReady(sidecarLyrics, "file"));
                return;
            }

            // 3. Whisper transcription
            String apiKey = getWhisperApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                mainHandler.post(() -> callback.onError("No lyrics found. Add lyrics manually or set up an OpenAI API key in Settings for auto-transcription."));
                return;
            }

            transcribeWithWhisper(song, apiKey, translateToEnglish, callback);
        });
    }

    private String checkSidecarFile(String audioPath) {
        // Try .lrc file
        String lrcPath = audioPath.replaceAll("\\.[^.]+$", ".lrc");
        File lrcFile = new File(lrcPath);
        if (lrcFile.exists()) {
            return readFile(lrcFile);
        }

        // Try .txt file
        String txtPath = audioPath.replaceAll("\\.[^.]+$", ".txt");
        File txtFile = new File(txtPath);
        if (txtFile.exists()) {
            return readFile(txtFile);
        }

        return null;
    }

    private String readFile(File file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private void transcribeWithWhisper(Song song, String apiKey, boolean translateToEnglish, LyricsCallback callback) {
        try {
            File audioFile = new File(song.getPath());
            if (!audioFile.exists()) {
                mainHandler.post(() -> callback.onError("Audio file not found"));
                return;
            }

            // File size check (Whisper limit is 25MB)
            if (audioFile.length() > 25 * 1024 * 1024) {
                mainHandler.post(() -> callback.onError("File too large for auto-transcription (max 25MB). Please add lyrics manually."));
                return;
            }

            // Use Whisper API
            // Endpoint: /v1/audio/transcriptions for transcription
            //           /v1/audio/translations for English translation
            String endpoint = translateToEnglish
                    ? "https://api.openai.com/v1/audio/translations"
                    : "https://api.openai.com/v1/audio/transcriptions";

            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000); // transcription can take time

            try (OutputStream os = conn.getOutputStream()) {
                // Model field
                writeFormField(os, boundary, "model", "whisper-1");
                // Response format
                writeFormField(os, boundary, "response_format", "verbose_json");
                // File field
                writeFileField(os, boundary, "file", audioFile, getMimeType(song.getPath()));
                // Close boundary
                os.write(("\r\n--" + boundary + "--\r\n").getBytes());
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                String text = json.getString("text");
                String detectedLang = json.optString("language", "unknown");

                // If language is not English and user hasn't chosen yet, ask
                if (!translateToEnglish && !"english".equalsIgnoreCase(detectedLang) && !"en".equalsIgnoreCase(detectedLang)) {
                    mainHandler.post(() -> callback.onLanguageDetected(
                            detectedLang,
                            () -> transcribeWithWhisper(song, apiKey, true, new LyricsCallback() {
                                @Override public void onLyricsReady(String lyrics, String lang) { callback.onLyricsReady(lyrics, "en"); }
                                @Override public void onError(String error) { callback.onError(error); }
                                @Override public void onLanguageDetected(String l, Runnable e, Runnable o) {}
                            }),
                            () -> callback.onLyricsReady(text, detectedLang)
                    ));
                } else {
                    String finalLyrics = text;
                    String finalLang = translateToEnglish ? "en" : detectedLang;
                    // Save to prefs for future use
                    prefManager.saveLyrics(song.getPath(), finalLyrics);
                    mainHandler.post(() -> callback.onLyricsReady(finalLyrics, finalLang));
                }
            } else {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) errorResponse.append(line);
                errorReader.close();
                String errorMsg = "Transcription failed: HTTP " + responseCode + " - " + errorResponse;
                Log.e(TAG, errorMsg);
                mainHandler.post(() -> callback.onError(errorMsg));
            }

        } catch (Exception e) {
            Log.e(TAG, "Transcription error: " + e.getMessage());
            mainHandler.post(() -> callback.onError("Transcription error: " + e.getMessage()));
        }
    }

    private void writeFormField(OutputStream os, String boundary, String name, String value) throws IOException {
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes());
        os.write((value + "\r\n").getBytes());
    }

    private void writeFileField(OutputStream os, String boundary, String name, File file, String mimeType) throws IOException {
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + file.getName() + "\"\r\n").getBytes());
        os.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes());

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        fis.close();
    }

    private String getMimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".opus")) return "audio/opus";
        return "audio/mpeg";
    }

    public void saveLyricsManually(String songPath, String lyrics) {
        prefManager.saveLyrics(songPath, lyrics);
    }

    public String getSavedLyrics(String songPath) {
        return prefManager.getLyrics(songPath);
    }

    private String getWhisperApiKey() {
        // Stored in SharedPreferences by the user in Settings
        return new PreferenceManager(context).getPlaylists() != null ?
                android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                        .getString("openai_api_key", "") : "";
    }
}
