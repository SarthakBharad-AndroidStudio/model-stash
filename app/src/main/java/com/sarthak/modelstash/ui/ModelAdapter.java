package com.sarthak.modelstash.ui;

import android.content.Context;
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
import com.google.android.material.card.MaterialCardView;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.util.BuildStatus;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Model cards for both tabs. Pass R.layout.item_model_carousel (dashboard)
 * or R.layout.item_model_grid (catalogue); both use the same view ids.
 *
 * Optional multi-select (enableSelection): hold a card to start selecting, then
 * tap cards to add or remove them; selected cards show a tick. The selection
 * follows the list: kits that disappear (search, filter, deleted) drop out of it.
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
                    && a.buildStatus == b.buildStatus   // the card shows the stage
                    && a.wishlist == b.wishlist
                    && a.createdAt == b.createdAt;
        }
    };

    /** Told whenever the number of selected kits changes (0 = selection over). */
    public interface SelectionListener {
        void onSelectionChanged(int count);
    }

    private static final Object PAYLOAD_SELECTION = new Object();

    @LayoutRes
    private final int itemLayout;
    private final OnModelClickListener listener;
    @androidx.annotation.Nullable
    private SelectionListener selectionListener;
    private final Set<Long> selected = new LinkedHashSet<>();

    public ModelAdapter(@LayoutRes int itemLayout, OnModelClickListener listener) {
        super(DIFF);
        this.itemLayout = itemLayout;
        this.listener = listener;
    }

    // ---- Selection ----

    /** Turns on hold-to-select for this list. */
    public void enableSelection(SelectionListener listener) {
        selectionListener = listener;
    }

    public boolean isSelecting() {
        return !selected.isEmpty();
    }

    public int selectedCount() {
        return selected.size();
    }

    public List<Long> selectedIds() {
        return new ArrayList<>(selected);
    }

    /** The selected kits, in list order. */
    public List<ModelKit> selectedKits() {
        List<ModelKit> out = new ArrayList<>();
        for (ModelKit m : getCurrentList()) {
            if (selected.contains(m.id)) {
                out.add(m);
            }
        }
        return out;
    }

    public void selectAll() {
        for (ModelKit m : getCurrentList()) {
            selected.add(m.id);
        }
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        selectionChanged();
    }

    public void clearSelection() {
        if (selected.isEmpty()) {
            return;
        }
        selected.clear();
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        selectionChanged();
    }

    /** Restores a selection (after rotation); ids no longer in the list are ignored. */
    public void setSelection(Collection<Long> ids) {
        selected.clear();
        Set<Long> present = new HashSet<>();
        for (ModelKit m : getCurrentList()) {
            present.add(m.id);
        }
        for (Long id : ids) {
            if (present.contains(id)) {
                selected.add(id);
            }
        }
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        selectionChanged();
    }

    private void toggle(ModelKit model, int position) {
        if (!selected.remove(model.id)) {
            selected.add(model.id);
        }
        notifyItemChanged(position, PAYLOAD_SELECTION);
        selectionChanged();
    }

    private void selectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selected.size());
        }
    }

    @Override
    public void onCurrentListChanged(@NonNull List<ModelKit> previous, @NonNull List<ModelKit> current) {
        if (selected.isEmpty()) {
            return;
        }
        Set<Long> present = new HashSet<>();
        for (ModelKit m : current) {
            present.add(m.id);
        }
        if (selected.retainAll(present)) {
            selectionChanged();
        }
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

    /**
     * The card's second line. The build stage is added only once it is past Unbuilt,
     * so a shelf full of untouched kits doesn't repeat the same word on every card.
     */
    public static String meta(Context context, ModelKit model) {
        String base = scaleAndBrand(model);
        if (model.wishlist || BuildStatus.clamp(model.buildStatus) == BuildStatus.UNBUILT) {
            return base;
        }
        String stage = context.getString(BuildStatus.label(model.buildStatus));
        return base.isEmpty() ? stage : base + " · " + stage;
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
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_SELECTION) && payloads.size() == 1) {
            showChecked(holder, getItem(position)); // only the tick changed: don't reload the photo
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void showChecked(ViewHolder holder, ModelKit model) {
        boolean checked = selected.contains(model.id);
        if (holder.itemView instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) holder.itemView;
            card.setCheckable(selectionListener != null);
            card.setChecked(checked);
        }
        holder.photo.setAlpha(checked ? 0.6f : 1f); // selected cards fade back a little behind the tick
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModelKit model = getItem(position);
        holder.name.setText(model.name);
        String meta = meta(holder.itemView.getContext(), model);
        holder.meta.setText(meta);
        holder.meta.setVisibility(meta.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        ViewCompat.setTransitionName(holder.photo, photoTransitionName(model.id));
        loadPhoto(holder.photo, model);
        showChecked(holder, model);
        holder.itemView.setOnClickListener(v -> {
            int at = holder.getBindingAdapterPosition();
            if (at == RecyclerView.NO_POSITION) {
                return;
            }
            if (isSelecting()) {
                toggle(getItem(at), at); // while selecting, a tap adds or removes
            } else {
                listener.onModelClick(getItem(at), holder.photo);
            }
        });
        if (selectionListener != null) {
            holder.itemView.setOnLongClickListener(v -> {
                int at = holder.getBindingAdapterPosition();
                if (at == RecyclerView.NO_POSITION) {
                    return false;
                }
                toggle(getItem(at), at);
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                return true;
            });
        } else {
            holder.itemView.setOnLongClickListener(null);
            holder.itemView.setLongClickable(false);
        }
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
