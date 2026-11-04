package com.sarthak.modelstash.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.util.Haptics;
import com.sarthak.modelstash.util.PhotoDraft;
import com.sarthak.modelstash.util.PhotoLabel;

import java.io.File;

/**
 * The row of photo tiles on the add/edit screen: one tile per photo in the draft,
 * then an "Add photo" tile while there is room. Tap a tile for its menu; hold and
 * drag to reorder (see dragHelper). The activity changes the draft and then calls
 * one of the photo… methods below, so each change animates on its own (a tile
 * fading in, sliding out of the way, moving to the front) instead of the whole
 * row being redrawn at once.
 */
public class PhotoStripAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        /** A photo tile was tapped; anchor is the tile, for the popup menu. */
        void onPhotoClick(int position, View anchor);

        void onAddClick();
    }

    private static final int TYPE_PHOTO = 0;
    private static final int TYPE_ADD = 1;

    private final PhotoDraft draft;
    private final Listener listener;

    public PhotoStripAdapter(PhotoDraft draft, Listener listener) {
        this.draft = draft;
        this.listener = listener;
    }

    @Override
    public int getItemCount() {
        return draft.size() + (draft.isFull() ? 0 : 1);
    }

    @Override
    public int getItemViewType(int position) {
        return position < draft.size() ? TYPE_PHOTO : TYPE_ADD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_ADD) {
            return new AddHolder(inflater.inflate(R.layout.item_photo_add, parent, false));
        }
        return new PhotoHolder(inflater.inflate(R.layout.item_photo_thumb, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddHolder) {
            AddHolder add = (AddHolder) holder;
            add.left.setText(add.itemView.getContext()
                    .getString(R.string.photos_left, draft.remaining()));
            add.itemView.setOnClickListener(v -> listener.onAddClick());
            return;
        }
        PhotoHolder photo = (PhotoHolder) holder;
        PhotoDraft.Item item = draft.get(position);
        boolean labelled = PhotoLabel.isSet(item.label);
        String label = PhotoLabel.text(photo.itemView.getContext(), item.label);
        // Unlabelled photos invite a tap; labelled ones show their label.
        photo.label.setText(labelled ? label : photo.itemView.getContext().getString(R.string.photo_label_add));
        photo.label.setAlpha(labelled ? 1f : 0.75f);
        photo.cover.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        photo.image.setContentDescription(labelled
                ? photo.itemView.getContext().getString(R.string.photo_description, label)
                : photo.itemView.getContext().getString(R.string.model_photo));
        Glide.with(photo.image)
                .load(new File(item.path))
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .into(photo.image);
        photo.itemView.setOnClickListener(v -> {
            int at = photo.getBindingAdapterPosition();
            if (at != RecyclerView.NO_POSITION) {
                listener.onPhotoClick(at, v);
            }
        });
    }

    // ---- Animated updates: call these right after changing the draft ----

    /** A photo was appended at the end of the draft. */
    public void photoAdded() {
        int index = draft.size() - 1;
        if (draft.isFull()) {
            notifyItemChanged(index);          // the "Add photo" tile turns into the sixth photo
        } else {
            notifyItemInserted(index);         // a tile fades in just before "Add photo"
            notifyItemChanged(index + 1);      // which now shows one less left
        }
    }

    /** The photo at index was removed; wasFull = the draft held six photos before. */
    public void photoRemoved(int index, boolean wasFull) {
        notifyItemRemoved(index);
        if (wasFull) {
            notifyItemInserted(draft.size());  // room again: "Add photo" comes back at the end
        } else {
            notifyItemChanged(draft.size());   // "Add photo" shows one more left
        }
        if (index == 0 && !draft.isEmpty()) {
            notifyItemChanged(0);              // the next photo takes over the Cover badge
        }
    }

    public void photoChanged(int index) {
        notifyItemChanged(index);
    }

    /** The photo at from was moved to the front. */
    public void photoMadeCover(int from) {
        notifyItemMoved(from, 0);
        notifyItemChanged(0);                  // gains the Cover badge
        notifyItemChanged(1);                  // the old cover loses it
    }

    /** Hold a photo and drag it sideways to reorder; the "Add photo" tile stays put at the end. */
    public ItemTouchHelper dragHelper() {
        return new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                if (!(vh instanceof PhotoHolder)) {
                    return 0;
                }
                return makeMovementFlags(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                if (!(to instanceof PhotoHolder)) {
                    return false;
                }
                int a = from.getBindingAdapterPosition();
                int b = to.getBindingAdapterPosition();
                draft.move(a, b);
                notifyItemMoved(a, b);
                return true;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder vh, int actionState) {
                super.onSelectedChanged(vh, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                    Haptics.tick(vh.itemView);
                    vh.itemView.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                vh.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                // Redraw every tile so the Cover badge follows whichever photo is now first.
                notifyItemRangeChanged(0, draft.size());
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                // No swiping: removing is in the tile's menu, where it can't happen by accident.
            }
        });
    }

    static class PhotoHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView label;
        final TextView cover;

        PhotoHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.image_thumb);
            label = itemView.findViewById(R.id.text_label);
            cover = itemView.findViewById(R.id.text_cover);
        }
    }

    static class AddHolder extends RecyclerView.ViewHolder {
        final TextView left;

        AddHolder(View itemView) {
            super(itemView);
            left = itemView.findViewById(R.id.text_left);
        }
    }
}
