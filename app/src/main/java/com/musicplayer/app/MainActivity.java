package com.musicplayer.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.musicplayer.app.adapters.SongAdapter;
import com.musicplayer.app.models.Song;
import com.musicplayer.app.services.MusicService;
import com.musicplayer.app.utils.MusicLibrary;
import com.musicplayer.app.utils.PreferenceManager;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SongAdapter.OnSongClickListener {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int NOTIFICATION_PERMISSION_CODE = 101;

    // Views
    private RecyclerView recyclerView;
    private SongAdapter songAdapter;
    private EditText searchBar;
    private View miniPlayer;
    private TextView miniPlayerTitle, miniPlayerArtist;
    private ImageView miniPlayerArt, miniPlayerPlayPause;
    private BottomNavigationView bottomNav;

    // Service
    private MusicService musicService;
    private boolean serviceConnected = false;

    // Data
    private List<Song> allSongs = new ArrayList<>();
    private PreferenceManager prefManager;
    private MusicLibrary musicLibrary;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            serviceConnected = true;
            updateMiniPlayer();
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
            switch (action) {
                case MusicService.BROADCAST_SONG_CHANGED:
                case MusicService.BROADCAST_PLAYBACK_STATE:
                    updateMiniPlayer();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefManager = new PreferenceManager(this);
        // Apply theme
        AppCompatDelegate.setDefaultNightMode(
                prefManager.isDarkTheme()
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        musicLibrary = MusicLibrary.getInstance(this);

        initViews();
        setupBottomNav();
        checkPermissions();
        startAndBindService();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        searchBar = findViewById(R.id.searchBar);
        miniPlayer = findViewById(R.id.miniPlayer);
        miniPlayerTitle = findViewById(R.id.miniPlayerTitle);
        miniPlayerArtist = findViewById(R.id.miniPlayerArtist);
        miniPlayerArt = findViewById(R.id.miniPlayerArt);
        miniPlayerPlayPause = findViewById(R.id.miniPlayerPlayPause);
        bottomNav = findViewById(R.id.bottomNav);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSongs(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        miniPlayer.setOnClickListener(v -> {
            Intent intent = new Intent(this, NowPlayingActivity.class);
            startActivity(intent);
        });

        miniPlayerPlayPause.setOnClickListener(v -> {
            if (serviceConnected) {
                musicService.togglePlayPause();
                updateMiniPlayer();
            }
        });
    }

    private void setupBottomNav() {
        if (bottomNav == null) return;
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_songs) {
                loadAllSongs();
                return true;
            } else if (id == R.id.nav_folders) {
                startActivity(new Intent(this, FolderBrowserActivity.class));
                return true;
            } else if (id == R.id.nav_playlists) {
                startActivity(new Intent(this, PlaylistActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void checkPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            loadAllSongs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAllSongs();
            } else {
                Toast.makeText(this, "Storage permission required to play music", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadAllSongs() {
        new Thread(() -> {
            musicLibrary.scanLibrary();
            allSongs = musicLibrary.getAllSongs();
            runOnUiThread(() -> {
                if (songAdapter == null) {
                    songAdapter = new SongAdapter(this, allSongs, this);
                    recyclerView.setAdapter(songAdapter);
                } else {
                    songAdapter.updateSongs(allSongs);
                }

                if (allSongs.isEmpty()) {
                    Toast.makeText(this, "No music found on device", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void filterSongs(String query) {
        if (songAdapter == null) return;
        if (query.isEmpty()) {
            songAdapter.updateSongs(allSongs);
        } else {
            songAdapter.updateSongs(musicLibrary.searchSongs(query));
        }
    }

    @Override
    public void onSongClick(Song song, int position) {
        if (!serviceConnected) return;
        musicService.setPlaylist(allSongs, position);

        // Open Now Playing
        startActivity(new Intent(this, NowPlayingActivity.class));
    }

    private void updateMiniPlayer() {
        if (!serviceConnected) return;
        Song song = musicService.getCurrentSong();
        if (song == null) {
            miniPlayer.setVisibility(View.GONE);
            return;
        }

        miniPlayer.setVisibility(View.VISIBLE);
        miniPlayerTitle.setText(song.getTitle());
        miniPlayerArtist.setText(song.getArtist());
        miniPlayerPlayPause.setImageResource(
                musicService.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);

        Glide.with(this)
                .load(song.getAlbumArtUri())
                .error(R.drawable.default_album_art)
                .centerCrop()
                .into(miniPlayerArt);
    }

    private void startAndBindService() {
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
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceConnected) {
            unbindService(serviceConnection);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_shuffle_all) {
            if (!allSongs.isEmpty() && serviceConnected) {
                musicService.setPlaylist(allSongs, 0);
                if (!musicService.isShuffle()) musicService.toggleShuffle();
                startActivity(new Intent(this, NowPlayingActivity.class));
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
