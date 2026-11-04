package com.sarthak.modelstash.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.data.ModelPhoto;
import com.sarthak.modelstash.data.ModelRepository;
import com.sarthak.modelstash.util.BuildStatus;
import com.sarthak.modelstash.util.Haptics;
import com.sarthak.modelstash.util.InsetsHelper;
import com.sarthak.modelstash.util.PhotoLabel;

import java.io.File;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * One model: a swipeable photo carousel that collapses into the toolbar, details
 * below, Edit as a floating button and Delete in the toolbar.
 *
 * The screen wipes in over the list like every other screen (see the theme's
 * windowAnimationStyle). image_photo under the carousel shows the cover, so the
 * header is never blank while the carousel's first page loads.
 */
public class ModelDetailActivity extends AppCompatActivity {

    private static final String EXTRA_MODEL_ID = "model_id";

    private ModelRepository repository;
    private ModelKit current;
    private boolean deleting;

    private ViewPager2 pager;
    private PhotoPagerAdapter pagerAdapter;
    private View photoOverlay;
    private TextView photoBadge;
    private LinearLayout dots;
    private List<ModelPhoto> photos = Collections.emptyList();

    /**
     * Opens the detail screen. sharedPhoto is no longer used (the screen wipes in
     * instead of growing out of the card's photo); it stays so the callers in the
     * Catalogue, Home, Wishlist and Brands screens need no change.
     */
    public static void start(Activity from, long modelId, @Nullable ImageView sharedPhoto) {
        from.startActivity(new Intent(from, ModelDetailActivity.class).putExtra(EXTRA_MODEL_ID, modelId));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_model_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        CollapsingToolbarLayout collapsing = findViewById(R.id.collapsing);
        ImageView photo = findViewById(R.id.image_photo);
        TextView name = findViewById(R.id.text_name);
        Chip scaleChip = findViewById(R.id.chip_scale);
        Chip brandChip = findViewById(R.id.chip_brand);
        Chip numberChip = findViewById(R.id.chip_number);
        Chip statusChip = findViewById(R.id.chip_status);
        TextView added = findViewById(R.id.text_added);
        View descriptionCard = findViewById(R.id.card_description);
        TextView description = findViewById(R.id.text_description);
        View scalematesButton = findViewById(R.id.button_scalemates);
        MaterialButton moveButton = findViewById(R.id.button_move_to_stash);
        ExtendedFloatingActionButton editButton = findViewById(R.id.fab_edit);
        InsetsHelper.liftAboveNavigationBar(editButton);
        InsetsHelper.padForNavigationBar(findViewById(R.id.scroll));


        pager = findViewById(R.id.pager_photos);
        photoOverlay = findViewById(R.id.photo_overlay);
        photoBadge = findViewById(R.id.text_photo_badge);
        dots = findViewById(R.id.dots_photos);
        pagerAdapter = new PhotoPagerAdapter();
        pager.setAdapter(pagerAdapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                showPhotoInfo(position);
            }
        });

        repository = ModelRepository.get(this);
        long id = getIntent().getLongExtra(EXTRA_MODEL_ID, 0);

        editButton.setOnClickListener(v -> {
            if (current != null) {
                startActivity(new Intent(this, AddEditModelActivity.class)
                        .putExtra(AddEditModelActivity.EXTRA_MODEL_ID, current.id));
            }
        });

        // Observing (not loading once) means the screen refreshes after you edit the model.
        repository.observe(id).observe(this, model -> {
            if (model == null) {
                finish(); // deleted
                return;
            }
            current = model;
            collapsing.setTitle(model.name); // shows in the toolbar once the photo is scrolled away
            loadHeaderPhoto(photo, model);
            name.setText(model.name);

            scaleChip.setText(model.scale);
            scaleChip.setVisibility(model.scale != null ? View.VISIBLE : View.GONE);
            brandChip.setText(model.brand);
            brandChip.setVisibility(model.brand != null ? View.VISIBLE : View.GONE);

            // Owned kits carry a build status; wishlist kits don't.
            statusChip.setVisibility(model.wishlist ? View.GONE : View.VISIBLE);
            statusChip.setText(BuildStatus.label(model.buildStatus));
            statusChip.setOnClickListener(v -> chooseStatus(model));

            boolean hasNumber = model.kitNumber != null;
            numberChip.setText(hasNumber ? getString(R.string.kit_number_chip, model.kitNumber) : "");
            numberChip.setVisibility(hasNumber ? View.VISIBLE : View.GONE);

            added.setText(getString(R.string.added_on,
                    DateFormat.getDateInstance(DateFormat.LONG).format(new Date(model.createdAt))));

            description.setText(model.description);
            descriptionCard.setVisibility(model.description != null ? View.VISIBLE : View.GONE);

            // Only shown when you have added a link yourself; the app never looks one up.
            scalematesButton.setVisibility(model.sourceUrl != null ? View.VISIBLE : View.GONE);
            scalematesButton.setOnClickListener(v -> openLink(model.sourceUrl));

            // Only wishlist kits can be "bought"; the button disappears once they are.
            moveButton.setVisibility(model.wishlist ? View.VISIBLE : View.GONE);
            moveButton.setOnClickListener(v -> moveToStash(model));
        });

        // All photos, in order. Refreshes by itself after you add, label or reorder them.
        repository.observePhotos(id).observe(this, list -> {
            photos = list;
            pagerAdapter.submitList(list, this::showCarousel);
        });
    }

    // ---- Photo carousel ----

    /** Shows the carousel, its badge and dots once there are photos. */
    private void showCarousel() {
        boolean show = !photos.isEmpty();
        pager.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        photoOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        buildDots(photos.size());
        showPhotoInfo(pager.getCurrentItem());
    }

    /** One dot per photo; hidden when there is only one. */
    private void buildDots(int count) {
        dots.removeAllViews();
        dots.setVisibility(count > 1 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.bg_photo_dot);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(6), dp(6));
            params.setMarginStart(i == 0 ? 0 : dp(5));
            dots.addView(dot, params);
        }
    }

    /**
     * The badge: "Box Art · 2 of 4", just "2 of 4" for an unlabelled photo, just the
     * label when there is one photo, and nothing at all for a single unlabelled one.
     * The matching dot is drawn long.
     */
    private void showPhotoInfo(int position) {
        if (photos.isEmpty()) {
            return;
        }
        int at = Math.max(0, Math.min(position, photos.size() - 1));
        int label = photos.get(at).label;
        boolean labelled = PhotoLabel.isSet(label);
        boolean several = photos.size() > 1;
        String counter = getString(R.string.photo_count, at + 1, photos.size());
        String text;
        if (labelled && several) {
            text = getString(R.string.photo_counter, PhotoLabel.text(this, label), at + 1, photos.size());
        } else if (labelled) {
            text = PhotoLabel.text(this, label);
        } else {
            text = several ? counter : null;
        }
        photoBadge.setText(text);
        photoBadge.setVisibility(text != null ? View.VISIBLE : View.INVISIBLE);
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            boolean selected = i == at;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) dot.getLayoutParams();
            params.width = dp(selected ? 18 : 6);
            dot.setLayoutParams(params);
            dot.setAlpha(selected ? 1f : 0.4f);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---- Header photo ----

    /** The cover, under the carousel: shows while the first page loads, and the placeholder when there are no photos. */
    private void loadHeaderPhoto(ImageView target, ModelKit model) {
        File file = model.photoPath != null ? new File(model.photoPath) : null;
        Glide.with(this)
                .load(file)
                .centerCrop()
                .fallback(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .into(target);
    }

    // ---- Status, menu, delete, links ----

    /** Tapping the status chip: pick a stage and it's saved straight away, no Edit screen. */
    private void chooseStatus(ModelKit model) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.change_status)
                .setSingleChoiceItems(BuildStatus.labels(this), BuildStatus.clamp(model.buildStatus),
                        (dialog, which) -> {
                            dialog.dismiss();
                            if (which == model.buildStatus) {
                                return; // tapped the stage it is already on
                            }
                            model.buildStatus = which;
                            repository.save(model, () -> Snackbar.make(findViewById(R.id.scroll),
                                    getString(R.string.status_saved, getString(BuildStatus.label(which))),
                                    Snackbar.LENGTH_SHORT).show());
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_delete && current != null) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish(); // wipes away to the right, like the back button
        return true;
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.delete_title, current.name))
                .setMessage(R.string.delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (!deleting) {
                        deleting = true;
                        Haptics.confirm(findViewById(R.id.root));
                        repository.delete(current, this::finish);
                    }
                })
                .show();
    }

    /** Marks a wanted kit as owned, so it moves from the Wishlist to the Catalogue. */
    private void moveToStash(ModelKit model) {
        Haptics.confirm(findViewById(R.id.root));
        repository.moveToStash(model, () ->
                Snackbar.make(findViewById(R.id.root), R.string.moved_to_stash, Snackbar.LENGTH_LONG).show());
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) {
            // No browser installed - nothing sensible to do.
        }
    }
}
