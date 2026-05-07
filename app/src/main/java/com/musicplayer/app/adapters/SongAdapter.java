package com.musicplayer.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.musicplayer.app.R;
import com.musicplayer.app.models.Song;
import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(Song song, int position);
    }

    private Context context;
    private List<Song> songs;
    private OnSongClickListener listener;
    private int currentlyPlayingIndex = -1;

    public SongAdapter(Context context, List<Song> songs, OnSongClickListener listener) {
        this.context = context;
        this.songs = new ArrayList<>(songs);
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.title.setText(song.getTitle());
        holder.artist.setText(song.getArtist());
        holder.duration.setText(song.getFormattedDuration());

        Glide.with(context)
                .load(song.getAlbumArtUri())
                .error(R.drawable.default_album_art)
                .centerCrop()
                .into(holder.albumArt);

        // Highlight currently playing
        boolean isPlaying = position == currentlyPlayingIndex;
        holder.title.setTextColor(context.getColor(isPlaying ? R.color.accent : R.color.text_primary));
        holder.playingIndicator.setVisibility(isPlaying ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSongClick(song, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    public void updateSongs(List<Song> newSongs) {
        this.songs.clear();
        this.songs.addAll(newSongs);
        notifyDataSetChanged();
    }

    public void setCurrentlyPlaying(int index) {
        int old = currentlyPlayingIndex;
        currentlyPlayingIndex = index;
        if (old >= 0) notifyItemChanged(old);
        if (index >= 0) notifyItemChanged(index);
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        ImageView albumArt;
        TextView title, artist, duration;
        View playingIndicator;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            albumArt = itemView.findViewById(R.id.albumArt);
            title = itemView.findViewById(R.id.songTitle);
            artist = itemView.findViewById(R.id.songArtist);
            duration = itemView.findViewById(R.id.songDuration);
            playingIndicator = itemView.findViewById(R.id.playingIndicator);
        }
    }
}
