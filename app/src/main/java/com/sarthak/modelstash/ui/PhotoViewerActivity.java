package com.sarthak.modelstash.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.ModelPhoto;
import com.sarthak.modelstash.data.ModelRepository;
import com.sarthak.modelstash.util.InsetsHelper;
import com.sarthak.modelstash.util.PhotoLabel;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * A kit's photos full screen, on black: swipe between them, pinch or double-tap to
 * zoom in on box details, tap to hide or show the close button and caption.
 * Opened by tapping the photo on a kit's page.
 */
public class PhotoViewerActivity extends AppCompatActivity {

    private static final String EXTRA_MODEL_ID = "model_id";
    private static final String EXTRA_START = "start";
    private static final String STATE_PAGE = "page";
    private static final String STATE_CHROME = "chrome";

    private ViewPager2 pager;
    private TextView caption;
    private View chrome;
    private List<ModelPhoto> photos = Collections.emptyList();
    private int startPage;
    private boolean startApplied;

    public static void start(Activity from, long modelId, int position) {
        from.startActivity(new Intent(from, PhotoViewerActivity.class)
                .putExtra(EXTRA_MODEL_ID, modelId)
                .putExtra(EXTRA_START, position));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // White status-bar icons over the black page, in light and dark theme alike.
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_photo_viewer);

        pager = findViewById(R.id.pager_viewer);
        caption = findViewById(R.id.text_viewer_caption);
        chrome = findViewById(R.id.viewer_chrome);
        InsetsHelper.applySystemBars(chrome, true, true);
        findViewById(R.id.button_viewer_close).setOnClickListener(v -> finish());

        startPage = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_PAGE)
                : getIntent().getIntExtra(EXTRA_START, 0);
        if (savedInstanceState != null && !savedInstanceState.getBoolean(STATE_CHROME, true)) {
            chrome.setVisibility(View.GONE);
        }

        ZoomAdapter adapter = new ZoomAdapter(v -> toggleChrome());
        pager.setAdapter(adapter);
        pager.setOffscreenPageLimit(1);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showCaption(position);
                resetNeighbours(position);
            }
        });

        long modelId = getIntent().getLongExtra(EXTRA_MODEL_ID, 0);
        ModelRepository.get(this).observePhotos(modelId).observe(this, list -> {
            photos = list;
            if (list.isEmpty()) {
                finish(); // the kit (or its last photo) is gone
                return;
            }
            adapter.submitList(list, () -> {
                if (!startApplied) {
                    startApplied = true;
                    pager.setCurrentItem(Math.max(0, Math.min(startPage, list.size() - 1)), false);
                }
                showCaption(pager.getCurrentItem());
            });
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PAGE, pager.getCurrentItem());
        outState.putBoolean(STATE_CHROME, chrome.getVisibility() == View.VISIBLE);
    }

    private void toggleChrome() {
        boolean show = chrome.getVisibility() != View.VISIBLE;
        chrome.animate().cancel();
        if (show) {
            chrome.setAlpha(0f);
            chrome.setVisibility(View.VISIBLE);
            chrome.animate().alpha(1f).setDuration(150).start();
        } else {
            chrome.animate().alpha(0f).setDuration(150).withEndAction(() -> chrome.setVisibility(View.GONE)).start();
        }
    }

    /** "Box Art · 2 of 4", "2 of 4", or just the label for a single photo. */
    private void showCaption(int position) {
        if (photos.isEmpty()) {
            caption.setText("");
            return;
        }
        int at = Math.max(0, Math.min(position, photos.size() - 1));
        int label = photos.get(at).label;
        boolean several = photos.size() > 1;
        String text;
        if (PhotoLabel.isSet(label) && several) {
            text = getString(R.string.photo_counter, PhotoLabel.text(this, label), at + 1, photos.size());
        } else if (PhotoLabel.isSet(label)) {
            text = PhotoLabel.text(this, label);
        } else {
            text = several ? getString(R.string.photo_count, at + 1, photos.size()) : "";
        }
        caption.setText(text);
        caption.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** The photos either side go back to normal size, so you never swipe back into a zoomed one. */
    private void resetNeighbours(int position) {
        RecyclerView rv = (RecyclerView) pager.getChildAt(0);
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            RecyclerView.ViewHolder holder = rv.getChildViewHolder(child);
            if (holder.getBindingAdapterPosition() != position && child instanceof ZoomImageView) {
                ((ZoomImageView) child).resetZoom();
            }
        }
    }

    // ---- Pages ----

    private static final DiffUtil.ItemCallback<ModelPhoto> DIFF = new DiffUtil.ItemCallback<ModelPhoto>() {
        @Override
        public boolean areItemsTheSame(@NonNull ModelPhoto a, @NonNull ModelPhoto b) {
            return a.path.equals(b.path);
        }

        @Override
        public boolean areContentsTheSame(@NonNull ModelPhoto a, @NonNull ModelPhoto b) {
            return a.path.equals(b.path) && a.label == b.label;
        }
    };

    private static final class ZoomAdapter extends ListAdapter<ModelPhoto, ZoomAdapter.Holder> {

        private final View.OnClickListener onTap;

        ZoomAdapter(View.OnClickListener onTap) {
            super(DIFF);
            this.onTap = onTap;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ZoomImageView view = (ZoomImageView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_photo_zoom, parent, false);
            view.setOnClickListener(onTap);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ModelPhoto photo = getItem(position);
            holder.image.setContentDescription(PhotoLabel.isSet(photo.label)
                    ? holder.image.getContext().getString(R.string.photo_description,
                    PhotoLabel.text(holder.image.getContext(), photo.label))
                    : holder.image.getContext().getString(R.string.model_photo));
            // Up to 2560 px: sharp when zoomed in, without loading a 50 MP original whole.
            Glide.with(holder.image)
                    .load(new File(photo.path))
                    .override(2560, 2560)
                    .fitCenter()
                    .error(R.drawable.ic_image)
                    .into(holder.image);
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ZoomImageView image;

            Holder(ZoomImageView image) {
                super(image);
                this.image = image;
            }
        }
    }
}
