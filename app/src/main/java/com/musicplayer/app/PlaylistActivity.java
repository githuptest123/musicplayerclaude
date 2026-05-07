package com.musicplayer.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.musicplayer.app.models.Playlist;
import com.musicplayer.app.models.Song;
import com.musicplayer.app.services.MusicService;
import com.musicplayer.app.utils.PreferenceManager;
import java.util.List;

public class PlaylistActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FloatingActionButton fabAdd;
    private List<Playlist> playlists;
    private PreferenceManager prefManager;
    private MusicService musicService;
    private boolean serviceConnected = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MusicBinder) service).getService();
            serviceConnected = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { serviceConnected = false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);

        prefManager = new PreferenceManager(this);
        playlists = prefManager.getPlaylists();

        recyclerView = findViewById(R.id.recyclerView);
        fabAdd = findViewById(R.id.fabAdd);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        loadPlaylists();

        fabAdd.setOnClickListener(v -> showCreatePlaylistDialog());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Playlists");
        }

        bindService(new Intent(this, MusicService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void loadPlaylists() {
        PlaylistsAdapter adapter = new PlaylistsAdapter();
        recyclerView.setAdapter(adapter);
    }

    private void showCreatePlaylistDialog() {
        EditText input = new EditText(this);
        input.setHint("Playlist name");

        new MaterialAlertDialogBuilder(this)
                .setTitle("New Playlist")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        Playlist playlist = new Playlist(System.currentTimeMillis(), name);
                        playlists.add(playlist);
                        prefManager.savePlaylists(playlists);
                        loadPlaylists();
                        Toast.makeText(this, "Playlist created", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceConnected) unbindService(serviceConnection);
    }

    class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.VH> {

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_playlist, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Playlist pl = playlists.get(position);
            holder.name.setText(pl.getName());
            holder.count.setText(pl.getSongCount() + " songs");

            holder.itemView.setOnClickListener(v -> {
                if (serviceConnected && !pl.getSongs().isEmpty()) {
                    musicService.setPlaylist(pl.getSongs(), 0);
                    startActivity(new Intent(PlaylistActivity.this, NowPlayingActivity.class));
                } else {
                    Toast.makeText(PlaylistActivity.this, "Playlist is empty", Toast.LENGTH_SHORT).show();
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(PlaylistActivity.this)
                        .setTitle(pl.getName())
                        .setItems(new String[]{"Delete playlist"}, (d, w) -> {
                            playlists.remove(position);
                            prefManager.savePlaylists(playlists);
                            notifyItemRemoved(position);
                        })
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() { return playlists.size(); }

        class VH extends RecyclerView.ViewHolder {
            android.widget.TextView name, count;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.playlistName);
                count = v.findViewById(R.id.playlistCount);
            }
        }
    }
}
