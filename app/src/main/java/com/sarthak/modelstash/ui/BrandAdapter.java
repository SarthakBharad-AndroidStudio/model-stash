package com.sarthak.modelstash.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.util.BrandGroups;
import com.sarthak.modelstash.util.Haptics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Brands screen's list: a header per brand, and the kits under it when it is
 * expanded. Everything is one flat list of rows, rebuilt whenever a header is
 * tapped, so the RecyclerView can animate the rows in and out.
 */
public class BrandAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_MODEL = 1;

    /** One line in the list: either a brand header or one of its kits. */
    private static final class Row {
        final BrandGroups.Group group; // the header's group, or the kit's group
        final ModelKit model;          // null on a header row

        Row(BrandGroups.Group group, ModelKit model) {
            this.group = group;
            this.model = model;
        }
    }

    private final ModelAdapter.OnModelClickListener listener;
    private final List<BrandGroups.Group> groups = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    /** Brands currently open, by display name, so the state survives a data refresh. */
    private final Set<String> expanded = new HashSet<>();

    public BrandAdapter(ModelAdapter.OnModelClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<BrandGroups.Group> newGroups) {
        groups.clear();
        groups.addAll(newGroups);
        rebuild();
        notifyDataSetChanged();
    }

    private void rebuild() {
        rows.clear();
        for (BrandGroups.Group g : groups) {
            rows.add(new Row(g, null));
            if (expanded.contains(keyOf(g))) {
                for (ModelKit m : g.models) {
                    rows.add(new Row(g, m));
                }
            }
        }
    }

    private static String keyOf(BrandGroups.Group group) {
        return group.brand == null ? "" : group.brand;
    }

    private void toggle(BrandGroups.Group group, int headerPosition) {
        if (headerPosition == RecyclerView.NO_POSITION) {
            return; // tapped mid-animation; the row is between positions
        }
        String key = keyOf(group);
        boolean opening = !expanded.contains(key);
        if (opening) {
            expanded.add(key);
        } else {
            expanded.remove(key);
        }
        rebuild();
        // Only the kit rows change position; the arrow is turned by the click
        // handler, so the header itself does not need rebinding.
        if (opening) {
            notifyItemRangeInserted(headerPosition + 1, group.size());
        } else {
            notifyItemRangeRemoved(headerPosition + 1, group.size());
        }
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).model == null ? TYPE_HEADER : TYPE_MODEL;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_brand_header, parent, false));
        }
        return new ModelHolder(inflater.inflate(R.layout.item_brand_model, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            HeaderHolder h = (HeaderHolder) holder;
            String name = row.group.brand != null
                    ? row.group.brand
                    : h.itemView.getContext().getString(R.string.brand_none);
            h.name.setText(name);
            h.count.setText(h.itemView.getContext().getResources()
                    .getQuantityString(R.plurals.brand_kit_count, row.group.size(), row.group.size()));
            h.arrow.setRotation(expanded.contains(keyOf(row.group)) ? 180f : 0f);
            h.itemView.setOnClickListener(v -> {
                Haptics.tick(v);
                h.arrow.animate().rotation(expanded.contains(keyOf(row.group)) ? 0f : 180f)
                        .setDuration(180).start();
                toggle(row.group, h.getBindingAdapterPosition());
            });
            return;
        }

        ModelHolder m = (ModelHolder) holder;
        ModelKit model = row.model;
        m.name.setText(model.name);
        String meta = ModelAdapter.meta(m.itemView.getContext(), model);
        m.meta.setText(meta);
        m.meta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
        ViewCompat.setTransitionName(m.photo, ModelAdapter.photoTransitionName(model.id));
        ModelAdapter.loadPhoto(m.photo, model);
        m.itemView.setOnClickListener(v -> listener.onModelClick(model, m.photo));
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView count;
        final ImageView arrow;

        HeaderHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_brand);
            count = itemView.findViewById(R.id.text_brand_count);
            arrow = itemView.findViewById(R.id.icon_expand);
        }
    }

    static class ModelHolder extends RecyclerView.ViewHolder {
        final ImageView photo;
        final TextView name;
        final TextView meta;

        ModelHolder(View itemView) {
            super(itemView);
            photo = itemView.findViewById(R.id.image_photo);
            name = itemView.findViewById(R.id.text_name);
            meta = itemView.findViewById(R.id.text_meta);
        }
    }
}
