package com.sarthak.modelstash.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.view.ViewCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.data.ModelRepository;
import com.sarthak.modelstash.util.Haptics;
import com.sarthak.modelstash.util.InsetsHelper;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

/**
 * One model: a large photo that collapses into the toolbar, details below,
 * Edit as a floating button and Delete in the toolbar.
 */
public class ModelDetailActivity extends AppCompatActivity {

    private static final String EXTRA_MODEL_ID = "model_id";

    private ModelRepository repository;
    private ModelKit current;
    private boolean deleting;
    private boolean enterTransitionStarted;

    /**
     * Opens the detail screen. When sharedPhoto is given, the card's photo
     * grows smoothly into the big header photo.
     */
    public static void start(Activity from, long modelId, @Nullable ImageView sharedPhoto) {
        Intent intent = new Intent(from, ModelDetailActivity.class).putExtra(EXTRA_MODEL_ID, modelId);
        if (sharedPhoto != null && ViewCompat.getTransitionName(sharedPhoto) != null) {
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    from, sharedPhoto, ViewCompat.getTransitionName(sharedPhoto));
            from.startActivity(intent, options.toBundle());
        } else {
            from.startActivity(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_model_detail);
        // Wait for the photo before running the shared-element animation (see startEnterTransition).
        supportPostponeEnterTransition();

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
        TextView added = findViewById(R.id.text_added);
        View descriptionCard = findViewById(R.id.card_description);
        TextView description = findViewById(R.id.text_description);
        View scalematesButton = findViewById(R.id.button_scalemates);
        MaterialButton moveButton = findViewById(R.id.button_move_to_stash);
        ExtendedFloatingActionButton editButton = findViewById(R.id.fab_edit);
        InsetsHelper.liftAboveNavigationBar(editButton);
        InsetsHelper.padForNavigationBar(findViewById(R.id.scroll));

        repository = ModelRepository.get(this);
        long id = getIntent().getLongExtra(EXTRA_MODEL_ID, 0);
        ViewCompat.setTransitionName(photo, ModelAdapter.photoTransitionName(id));

        editButton.setOnClickListener(v -> {
            if (current != null) {
                startActivity(new Intent(this, AddEditModelActivity.class)
                        .putExtra(AddEditModelActivity.EXTRA_MODEL_ID, current.id));
            }
        });

        // Observing (not loading once) means the screen refreshes after you edit the model.
        repository.observe(id).observe(this, model -> {
            if (model == null) {
                startEnterTransition();
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
            boolean hasNumber = model.kitNumber != null;
            numberChip.setText(hasNumber ? getString(R.string.kit_number_chip, model.kitNumber) : "");
            numberChip.setVisibility(hasNumber ? View.VISIBLE : View.GONE);

            added.setText(getString(R.string.added_on,
                    DateFormat.getDateInstance(DateFormat.LONG).format(new Date(model.createdAt))));

            description.setText(model.description);
            descriptionCard.setVisibility(model.description != null ? View.VISIBLE : View.GONE);

            scalematesButton.setVisibility(model.sourceUrl != null ? View.VISIBLE : View.GONE);
            scalematesButton.setOnClickListener(v -> openLink(model.sourceUrl));
            // Only wishlist kits can be "bought"; the button disappears once they are.
            moveButton.setVisibility(model.wishlist ? View.VISIBLE : View.GONE);
            moveButton.setOnClickListener(v -> moveToStash(model));
        });
    }

    private void loadHeaderPhoto(ImageView target, ModelKit model) {
        File file = model.photoPath != null ? new File(model.photoPath) : null;
        Glide.with(this)
                .load(file)
                .dontAnimate() // a fade would fight the shared-element animation
                .centerCrop()
                .fallback(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object glideModel,
                                                @NonNull Target<Drawable> target, boolean isFirstResource) {
                        startEnterTransition();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object glideModel,
                                                   Target<Drawable> target, @NonNull DataSource dataSource,
                                                   boolean isFirstResource) {
                        startEnterTransition();
                        return false;
                    }
                })
                .into(target);
    }

    /** Runs the postponed shared-element animation exactly once. */
    private void startEnterTransition() {
        if (!enterTransitionStarted) {
            enterTransitionStarted = true;
            supportStartPostponedEnterTransition();
        }
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
        supportFinishAfterTransition(); // plays the photo animation in reverse
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