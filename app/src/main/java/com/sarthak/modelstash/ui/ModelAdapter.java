package com.sarthak.modelstash.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.ModelKit;

import java.io.File;
import java.util.Objects;

/**
 * Model cards for both tabs. Pass R.layout.item_model_carousel (dashboard)
 * or R.layout.item_model_grid (catalogue); both use the same view ids.
 */
public class ModelAdapter extends ListAdapter<ModelKit, ModelAdapter.ViewHolder> {

    public interface OnModelClickListener {
        /** photo is the card's image, used for the shared-element animation into the detail screen. */
        void onModelClick(ModelKit model, ImageView photo);
    }

    private static final DiffUtil.ItemCallback<ModelKit> DIFF = new DiffUtil.ItemCallback<ModelKit>() {
        @Override
        public boolean areItemsTheSame(@NonNull ModelKit a, @NonNull ModelKit b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ModelKit a, @NonNull ModelKit b) {
            return a.name.equals(b.name)
                    && Objects.equals(a.scale, b.scale)
                    && Objects.equals(a.brand, b.brand)
                    && Objects.equals(a.photoPath, b.photoPath)
                    && a.createdAt == b.createdAt;
        }
    };

    @LayoutRes
    private final int itemLayout;
    private final OnModelClickListener listener;

    public ModelAdapter(@LayoutRes int itemLayout, OnModelClickListener listener) {
        super(DIFF);
        this.itemLayout = itemLayout;
        this.listener = listener;
    }

    /** "1:48 · Tamiya", "1:48", "Tamiya" or "". */
    public static String scaleAndBrand(ModelKit model) {
        if (model.scale != null && model.brand != null) {
            return model.scale + " · " + model.brand;
        }
        if (model.scale != null) {
            return model.scale;
        }
        return model.brand != null ? model.brand : "";
    }

    /** The same name on the card's photo and the detail screen's photo links the two for the animation. */
    public static String photoTransitionName(long modelId) {
        return "model_photo_" + modelId;
    }

    /** Loads the photo with a quick cross-fade, or shows the placeholder icon. */
    public static void loadPhoto(ImageView target, ModelKit model) {
        File photo = model.photoPath != null ? new File(model.photoPath) : null;
        Glide.with(target)
                .load(photo)
                .placeholder(R.drawable.ic_image)
                .fallback(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(180))
                .into(target);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(itemLayout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModelKit model = getItem(position);
        holder.name.setText(model.name);
        String meta = scaleAndBrand(model);
        holder.meta.setText(meta);
        holder.meta.setVisibility(meta.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        ViewCompat.setTransitionName(holder.photo, photoTransitionName(model.id));
        loadPhoto(holder.photo, model);
        holder.itemView.setOnClickListener(v -> listener.onModelClick(model, holder.photo));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView name;
        final TextView meta;

        ViewHolder(View itemView) {
            super(itemView);
            photo = itemView.findViewById(R.id.image_photo);
            name = itemView.findViewById(R.id.text_name);
            meta = itemView.findViewById(R.id.text_meta);
        }
    }
}