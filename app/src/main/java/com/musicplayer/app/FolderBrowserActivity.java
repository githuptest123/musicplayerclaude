package com.musicplayer.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.musicplayer.app.adapters.SongAdapter;
import com.musicplayer.app.models.Song;
import com.musicplayer.app.services.MusicService;
import com.musicplayer.app.utils.MusicLibrary;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FolderBrowserActivity extends AppCompatActivity implements SongAdapter.OnSongClickListener {

    private RecyclerView recyclerView;
    private TextView currentPathText;
    private TextView emptyText;
    private File currentDir;
    private MusicService musicService;
    private boolean serviceConnected = false;
    private List<Song> songsInCurrentFolder = new ArrayList<>();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MusicBinder) service).getService();
            serviceConnected = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceConnected = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_browser);

        recyclerView = findViewById(R.id.recyclerView);
        currentPathText = findViewById(R.id.currentPath);
        emptyText = findViewById(R.id.emptyText);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Browse Files");
        }

        // Start at external storage root
        currentDir = Environment.getExternalStorageDirectory();
        browseFolder(currentDir);

        bindService(new Intent(this, MusicService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void browseFolder(File folder) {
        currentDir = folder;
        currentPathText.setText(folder.getAbsolutePath());

        List<FolderItem> items = new ArrayList<>();

        // Add parent folder navigation
        if (folder.getParentFile() != null && !folder.equals(Environment.getExternalStorageDirectory())) {
            items.add(new FolderItem(".. (Up)", folder.getParentFile(), true, false));
        }

        File[] files = folder.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            songsInCurrentFolder.clear();

            for (File file : files) {
                if (file.isDirectory() && !file.isHidden()) {
                    items.add(new FolderItem(file.getName(), file, false, false));
                } else if (isAudioFile(file.getName())) {
                    items.add(new FolderItem(file.getName(), file, false, true));
                }
            }
        }

        // Use a folder-specific adapter
        FolderAdapter adapter = new FolderAdapter(items);
        recyclerView.setAdapter(adapter);

        if (items.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    private boolean isAudioFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".flac") ||
                lower.endsWith(".aac") || lower.endsWith(".ogg") ||
                lower.endsWith(".wav") || lower.endsWith(".m4a") ||
                lower.endsWith(".opus") || lower.endsWith(".wma");
    }

    @Override
    public void onSongClick(Song song, int position) {
        if (serviceConnected) {
            musicService.setPlaylist(songsInCurrentFolder, position);
            startActivity(new Intent(this, NowPlayingActivity.class));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!currentDir.equals(Environment.getExternalStorageDirectory()) &&
                currentDir.getParentFile() != null) {
            browseFolder(currentDir.getParentFile());
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceConnected) unbindService(serviceConnection);
    }

    // Simple data class
    static class FolderItem {
        String name;
        File file;
        boolean isParent;
        boolean isAudio;

        FolderItem(String name, File file, boolean isParent, boolean isAudio) {
            this.name = name;
            this.file = file;
            this.isParent = isParent;
            this.isAudio = isAudio;
        }
    }

    // Simple folder adapter
    class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderVH> {
        List<FolderItem> items;

        FolderAdapter(List<FolderItem> items) { this.items = items; }

        @Override
        public FolderVH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_folder, parent, false);
            return new FolderVH(v);
        }

        @Override
        public void onBindViewHolder(FolderVH holder, int position) {
            FolderItem item = items.get(position);
            holder.name.setText(item.name);
            holder.icon.setImageResource(item.isAudio ? R.drawable.ic_music_note : R.drawable.ic_folder);

            holder.itemView.setOnClickListener(v -> {
                if (item.isAudio) {
                    // Build song list from audio files in this folder and play
                    MusicLibrary.getInstance(FolderBrowserActivity.this).scanFolder(currentDir.getAbsolutePath());
                    List<Song> songs = MusicLibrary.getInstance(FolderBrowserActivity.this)
                            .getSongsByFolder(currentDir.getAbsolutePath());

                    // Find which song was clicked
                    int songIndex = 0;
                    for (int i = 0; i < songs.size(); i++) {
                        if (songs.get(i).getPath().equals(item.file.getAbsolutePath())) {
                            songIndex = i;
                            break;
                        }
                    }
                    if (serviceConnected && !songs.isEmpty()) {
                        musicService.setPlaylist(songs, songIndex);
                        startActivity(new Intent(FolderBrowserActivity.this, NowPlayingActivity.class));
                    }
                } else {
                    browseFolder(item.isParent ? item.file : item.file);
                }
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class FolderVH extends RecyclerView.ViewHolder {
            TextView name;
            android.widget.ImageView icon;

            FolderVH(View v) {
                super(v);
                name = v.findViewById(R.id.folderName);
                icon = v.findViewById(R.id.folderIcon);
            }
        }
    }
}
