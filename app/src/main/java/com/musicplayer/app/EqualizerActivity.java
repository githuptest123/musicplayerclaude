package com.musicplayer.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.musicplayer.app.services.MusicService;
import com.musicplayer.app.utils.PreferenceManager;
import java.util.ArrayList;
import java.util.List;

public class EqualizerActivity extends AppCompatActivity {

    private MusicService musicService;
    private boolean serviceConnected = false;
    private PreferenceManager prefManager;
    private List<SeekBar> bandSeekBars = new ArrayList<>();
    private List<TextView> bandLabels = new ArrayList<>();

    private static final String[] PRESET_NAMES = {
            "Normal", "Classical", "Dance", "Flat", "Folk",
            "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock"
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MusicBinder) service).getService();
            serviceConnected = true;
            setupEqualizer();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceConnected = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equalizer);
        prefManager = new PreferenceManager(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Equalizer");
        }

        bindService(new Intent(this, MusicService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setupEqualizer() {
        if (!serviceConnected) return;
        Equalizer eq = musicService.getEqualizer();
        if (eq == null) return;

        short numBands = eq.getNumberOfBands();
        short[] bandLevelRange = eq.getBandLevelRange();
        int min = bandLevelRange[0];
        int max = bandLevelRange[1];

        // Band seek bars are in layout: eq_band_0, eq_band_1, ... eq_band_4
        int[] seekBarIds = {R.id.eqBand0, R.id.eqBand1, R.id.eqBand2, R.id.eqBand3, R.id.eqBand4};
        int[] labelIds = {R.id.eqLabel0, R.id.eqLabel1, R.id.eqLabel2, R.id.eqLabel3, R.id.eqLabel4};

        for (short i = 0; i < Math.min(numBands, seekBarIds.length); i++) {
            SeekBar sb = findViewById(seekBarIds[i]);
            TextView lbl = findViewById(labelIds[i]);
            if (sb == null || lbl == null) continue;

            int centerFreq = eq.getCenterFreq(i) / 1000;
            lbl.setText(centerFreq + "Hz");

            sb.setMax(max - min);
            sb.setProgress(eq.getBandLevel(i) - min);

            final short band = i;
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && eq != null) {
                        short level = (short) (progress + min);
                        eq.setBandLevel(band, level);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    saveEqualizerSettings(eq, numBands, min);
                }
            });

            bandSeekBars.add(sb);
            bandLabels.add(lbl);
        }

        // Preset buttons
        setupPresets(eq, min);
    }

    private void setupPresets(Equalizer eq, int min) {
        // Handle preset selection via spinner
        android.widget.Spinner presetSpinner = findViewById(R.id.presetSpinner);
        if (presetSpinner == null) return;

        short numPresets = eq.getNumberOfPresets();
        List<String> presets = new ArrayList<>();
        presets.add("Custom");
        for (short i = 0; i < numPresets; i++) {
            presets.add(eq.getPresetName(i));
        }

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);

        presetSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position == 0) return; // Custom
                eq.usePreset((short) (position - 1));
                updateSeekBarsFromEq(eq, min);
                saveEqualizerSettings(eq, eq.getNumberOfBands(), min);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void updateSeekBarsFromEq(Equalizer eq, int min) {
        for (int i = 0; i < bandSeekBars.size(); i++) {
            bandSeekBars.get(i).setProgress(eq.getBandLevel((short) i) - min);
        }
    }

    private void saveEqualizerSettings(Equalizer eq, short numBands, int min) {
        short[] levels = new short[numBands];
        for (short i = 0; i < numBands; i++) {
            levels[i] = eq.getBandLevel(i);
        }
        prefManager.saveEqualizerBands(levels);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceConnected) unbindService(serviceConnection);
    }
}
