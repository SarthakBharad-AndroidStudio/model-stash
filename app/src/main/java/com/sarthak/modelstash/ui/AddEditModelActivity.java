package com.sarthak.modelstash.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.AppDatabase;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.data.ModelRepository;
import com.sarthak.modelstash.scalemates.KitInfo;
import com.sarthak.modelstash.scalemates.ScalematesFetcher;
import com.sarthak.modelstash.scalemates.ScalematesParser;
import com.sarthak.modelstash.util.Haptics;
import com.sarthak.modelstash.util.InsetsHelper;
import com.sarthak.modelstash.util.PhotoStorage;
import com.sarthak.modelstash.util.ScaleFormat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Adds a new model, or edits one when started with EXTRA_MODEL_ID.
 * Also receives links shared from the browser ("Share -> Model Stash").
 */
public class AddEditModelActivity extends AppCompatActivity {

    public static final String EXTRA_MODEL_ID = "model_id";
    public static final String EXTRA_PREFILL_NAME = "prefill_name";

    /** true = the new model goes on the Wishlist instead of the owned catalogue. */
    public static final String EXTRA_WISHLIST = "wishlist";

    private static final String STATE_PHOTO = "photo";
    private static final String STATE_PENDING_CAMERA = "pending_camera";
    private static final String STATE_NEW_PHOTOS = "new_photos";
    private static final String STATE_SCALEMATES_OPEN = "scalemates_open";

    /** One-tap suggestions under the scale field: the most common kit scales. */
    private static final String[] COMMON_SCALES = {"1:24", "1:35", "1:48", "1:72", "1:144", "1:350", "1:700"};

    private TextInputLayout linkLayout;
    private TextInputLayout nameLayout;
    private TextInputLayout scaleLayout;
    private TextInputEditText linkInput;
    private TextInputEditText nameInput;
    private TextInputEditText scaleInput;
    private TextInputEditText brandInput;
    private TextInputEditText kitNumberInput;
    private TextInputEditText descriptionInput;
    private ImageView photoView;
    private MaterialButton removePhotoButton;
    private MaterialButton fetchButton;
    private MaterialButton saveButton;
    private LinearProgressIndicator fetchProgress;
    private View photoEmpty;
    private View scalematesBody;
    private View expandIcon;

    private ModelRepository repository;
    /** The model being edited, or null when adding a new one. */
    @Nullable
    private ModelKit editing;
    /** The photo currently shown (may not be saved yet). */
    @Nullable
    private String photoPath;
    /** File the camera app is writing into right now. */
    @Nullable
    private String pendingCameraPath;
    /** Photos created on this screen; the unused ones are deleted when leaving. */
    private ArrayList<String> newPhotos = new ArrayList<>();
    private boolean saved;

    /** Adding a wanted kit rather than an owned one. */
    private boolean wishlistMode;

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (pendingCameraPath == null) {
                    return;
                }
                if (Boolean.TRUE.equals(success)) {
                    showPhoto(pendingCameraPath);
                } else {
                    PhotoStorage.delete(pendingCameraPath); // cancelled: remove the empty file
                }
                pendingCameraPath = null;
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    copyPickedPhoto(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_edit_model);
        InsetsHelper.applySystemBars(findViewById(R.id.root), true, true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_close); // "close" rather than "back"
        }

        linkLayout = findViewById(R.id.layout_link);
        nameLayout = findViewById(R.id.layout_name);
        scaleLayout = findViewById(R.id.layout_scale);
        linkInput = findViewById(R.id.input_link);
        nameInput = findViewById(R.id.input_name);
        scaleInput = findViewById(R.id.input_scale);
        brandInput = findViewById(R.id.input_brand);
        kitNumberInput = findViewById(R.id.input_kit_number);
        descriptionInput = findViewById(R.id.input_description);
        photoView = findViewById(R.id.image_photo);
        removePhotoButton = findViewById(R.id.button_remove_photo);
        fetchButton = findViewById(R.id.button_fetch);
        saveButton = findViewById(R.id.button_save);
        fetchProgress = findViewById(R.id.progress_fetch);
        photoEmpty = findViewById(R.id.photo_empty);
        scalematesBody = findViewById(R.id.body_scalemates);
        expandIcon = findViewById(R.id.icon_expand);
        repository = ModelRepository.get(this);

        findViewById(R.id.header_scalemates).setOnClickListener(v ->
                setScalematesOpen(scalematesBody.getVisibility() != View.VISIBLE));
        addScaleSuggestions(findViewById(R.id.chips_scale));

        clearErrorWhenEdited(linkLayout, linkInput);
        clearErrorWhenEdited(nameLayout, nameInput);
        clearErrorWhenEdited(scaleLayout, scaleInput);

        findViewById(R.id.button_camera).setOnClickListener(v -> launchCamera());
        findViewById(R.id.button_gallery).setOnClickListener(v -> launchGallery());
        findViewById(R.id.card_photo).setOnClickListener(v -> launchGallery());
        removePhotoButton.setOnClickListener(v -> showPhoto(null));
        fetchButton.setOnClickListener(v -> fetchFromScalemates());
        saveButton.setOnClickListener(v -> save());

        if (savedInstanceState != null) {
            // Rotation or the system restarting the app while the camera was open.
            // Text fields restore themselves; the photo state is ours to restore.
            pendingCameraPath = savedInstanceState.getString(STATE_PENDING_CAMERA);
            ArrayList<String> restored = savedInstanceState.getStringArrayList(STATE_NEW_PHOTOS);
            if (restored != null) {
                newPhotos = restored;
            }
            setScalematesOpen(savedInstanceState.getBoolean(STATE_SCALEMATES_OPEN));
        }

        long modelId = getIntent().getLongExtra(EXTRA_MODEL_ID, 0);
        if (modelId != 0) {
            setTitle(R.string.title_edit_model);
            saveButton.setText(R.string.save_changes);
            saveButton.setEnabled(false); // until the model has loaded
            repository.load(modelId, model -> {
                if (model == null) {
                    finish(); // deleted in the meantime
                    return;
                }
                editing = model;
                if (savedInstanceState == null) {
                    fillForm(model);
                }
                saveButton.setEnabled(true);
            });
        } else {
            wishlistMode = getIntent().getBooleanExtra(EXTRA_WISHLIST, false);
            setTitle(wishlistMode ? R.string.title_add_wish : R.string.title_add_model);
            saveButton.setText(wishlistMode ? R.string.save_to_wishlist : R.string.save);
            if (savedInstanceState == null) {
                handleStartIntent(getIntent());
            }
        }

        if (savedInstanceState != null) {
            showPhoto(savedInstanceState.getString(STATE_PHOTO));
        } else if (modelId == 0) {
            showPhoto(null);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PHOTO, photoPath);
        outState.putString(STATE_PENDING_CAMERA, pendingCameraPath);
        outState.putStringArrayList(STATE_NEW_PHOTOS, newPhotos);
        outState.putBoolean(STATE_SCALEMATES_OPEN, scalematesBody.getVisibility() == View.VISIBLE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && !saved) {
            // Left without saving: throw away photos taken or picked on this screen.
            for (String path : newPhotos) {
                PhotoStorage.delete(path);
            }
        }
    }

    // ---- Filling the form ----

    private void handleStartIntent(Intent intent) {
        String prefillName = intent.getStringExtra(EXTRA_PREFILL_NAME);
        if (prefillName != null) {
            nameInput.setText(prefillName);
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            setScalematesOpen(true);
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            String kitUrl = ScalematesParser.extractKitUrl(shared);
            if (kitUrl != null) {
                linkInput.setText(kitUrl);
                fetchFromScalemates();
            } else {
                linkInput.setText(shared);
                linkLayout.setError(getString(R.string.error_not_scalemates));
            }
        }
    }

    private void fillForm(ModelKit model) {
        nameInput.setText(model.name);
        scaleInput.setText(model.scale);
        brandInput.setText(model.brand);
        kitNumberInput.setText(model.kitNumber);
        descriptionInput.setText(model.description);
        linkInput.setText(model.sourceUrl);
        setScalematesOpen(model.sourceUrl != null);
        showPhoto(model.photoPath);
    }

    private void applyKitInfo(KitInfo info) {
        if (info.name != null) {
            nameInput.setText(info.name);
        }
        if (info.scale != null) {
            scaleInput.setText(info.scale);
        }
        if (info.brand != null) {
            brandInput.setText(info.brand);
        }
        if (info.kitNumber != null) {
            kitNumberInput.setText(info.kitNumber);
        }
        String summary = info.summaryLine();
        if (!summary.isEmpty() && text(descriptionInput).isEmpty()) {
            descriptionInput.setText(summary);
        }
        Snackbar.make(saveButton, R.string.fetched_check_details, Snackbar.LENGTH_LONG).show();
    }

    // ---- Scalemates ----

    /** Expands or collapses the import card; the arrow turns to match. */
    private void setScalematesOpen(boolean open) {
        scalematesBody.setVisibility(open ? View.VISIBLE : View.GONE);
        expandIcon.animate().rotation(open ? 180f : 0f).setDuration(200).start();
    }

    private void fetchFromScalemates() {
        String kitUrl = ScalematesParser.extractKitUrl(text(linkInput));
        if (kitUrl == null) {
            linkLayout.setError(getString(R.string.error_not_scalemates));
            return;
        }
        linkLayout.setError(null);
        setFetching(true);
        ScalematesFetcher.fetch(kitUrl, new ScalematesFetcher.Callback() {
            @Override
            public void onSuccess(KitInfo info) {
                if (isDestroyed()) {
                    return;
                }
                setFetching(false);
                applyKitInfo(info);
            }

            @Override
            public void onError(String message) {
                if (isDestroyed()) {
                    return;
                }
                setFetching(false);
                linkLayout.setError(message);
            }
        });
    }

    private void setFetching(boolean fetching) {
        fetchProgress.setVisibility(fetching ? View.VISIBLE : View.GONE);
        fetchButton.setEnabled(!fetching);
    }

    // ---- Photos ----

    private void launchGallery() {
        pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void launchCamera() {
        File file = PhotoStorage.newPhotoFile(this);
        pendingCameraPath = file.getAbsolutePath();
        newPhotos.add(pendingCameraPath);
        try {
            takePicture.launch(PhotoStorage.uriForCamera(this, file));
        } catch (ActivityNotFoundException e) {
            pendingCameraPath = null;
            Toast.makeText(this, R.string.error_no_camera, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyPickedPhoto(Uri uri) {
        AppDatabase.IO.execute(() -> {
            try {
                String path = PhotoStorage.copyFromUri(getApplicationContext(), uri).getAbsolutePath();
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        PhotoStorage.delete(path); // screen already closed (or rotated): nobody will use it
                        return;
                    }
                    newPhotos.add(path);
                    showPhoto(path);
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                        R.string.error_photo_copy, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showPhoto(@Nullable String path) {
        photoPath = path;
        removePhotoButton.setVisibility(path == null ? View.GONE : View.VISIBLE);
        photoEmpty.setVisibility(path == null ? View.VISIBLE : View.GONE);
        if (path == null) {
            Glide.with(this).clear(photoView);
            photoView.setImageDrawable(null);
            return;
        }
        Glide.with(this)
                .load(new File(path))
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(photoView);
    }

    // ---- Saving ----

    private void save() {
        String name = text(nameInput);
        if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.error_name_required));
            nameInput.requestFocus();
            return;
        }
        String scale = ScaleFormat.normalize(text(scaleInput));
        if (scale == null) {
            scaleLayout.setError(getString(R.string.error_scale));
            scaleInput.requestFocus();
            return;
        }

        ModelKit model = editing != null ? editing : new ModelKit();
        String previousPhoto = model.photoPath;
        model.name = name;
        model.scale = emptyToNull(scale);
        model.brand = emptyToNull(text(brandInput));
        model.kitNumber = emptyToNull(text(kitNumberInput));
        model.description = emptyToNull(text(descriptionInput));
        model.photoPath = photoPath;
        model.sourceUrl = ScalematesParser.extractKitUrl(text(linkInput));
        // Editing keeps the list it is already on; adding uses the tab you came from.
        model.wishlist = editing != null ? editing.wishlist : wishlistMode;

        saved = true;
        saveButton.setEnabled(false);
        Haptics.confirm(saveButton);
        final String keptPhoto = photoPath;
        repository.save(model, () -> {
            // Clean up photos that were replaced or never used.
            for (String path : newPhotos) {
                if (!path.equals(keptPhoto)) {
                    PhotoStorage.delete(path);
                }
            }
            if (previousPhoto != null && !previousPhoto.equals(keptPhoto)) {
                PhotoStorage.delete(previousPhoto);
            }
            finish();
        });
    }

    // ---- Small helpers ----

    /** Adds a chip per common scale; tapping one fills in the scale field. */
    private void addScaleSuggestions(ChipGroup group) {
        for (String scale : COMMON_SCALES) {
            Chip chip = new Chip(this);
            chip.setText(scale);
            chip.setOnClickListener(v -> {
                Haptics.tick(v);
                scaleInput.setText(scale);
                scaleInput.setSelection(scale.length());
            });
            group.addView(chip);
        }
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    @Nullable
    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static void clearErrorWhenEdited(TextInputLayout layout, TextInputEditText input) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                layout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}