package com.sarthak.modelstash.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.ModelPhoto;
import com.sarthak.modelstash.util.PhotoLabel;

import java.io.File;

/** One full-width page per photo, for the swipeable header on the detail screen. */
public class PhotoPagerAdapter extends ListAdapter<ModelPhoto, PhotoPagerAdapter.PageHolder> {

    private static final DiffUtil.ItemCallback<ModelPhoto> DIFF = new DiffUtil.ItemCallback<ModelPhoto>() {
        @Override
        public boolean areItemsTheSame(@NonNull ModelPhoto a, @NonNull ModelPhoto b) {
            return a.path.equals(b.path);
        }

        @Override
        public boolean areContentsTheSame(@NonNull ModelPhoto a, @NonNull ModelPhoto b) {
            return a.path.equals(b.path) && a.label == b.label && a.position == b.position;
        }
    };

    public PhotoPagerAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo_page, parent, false);
        return new PageHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageHolder holder, int position) {
        ModelPhoto photo = getItem(position);
        holder.image.setContentDescription(PhotoLabel.isSet(photo.label)
                ? holder.itemView.getContext().getString(R.string.photo_description,
                        PhotoLabel.text(holder.itemView.getContext(), photo.label))
                : holder.itemView.getContext().getString(R.string.model_photo));
        Glide.with(holder.image)
                .load(new File(photo.path))
                .centerCrop()
                .error(R.drawable.ic_image)
                .into(holder.image);
    }

    static class PageHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        PageHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.image_page);
        }
    }
}
