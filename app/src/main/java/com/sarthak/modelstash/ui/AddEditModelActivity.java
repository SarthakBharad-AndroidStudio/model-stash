package com.sarthak.modelstash.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

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
import com.sarthak.modelstash.util.BuildStatus;
import com.sarthak.modelstash.util.Haptics;
import com.sarthak.modelstash.util.InsetsHelper;
import com.sarthak.modelstash.util.Motion;
import com.sarthak.modelstash.util.PhotoDraft;
import com.sarthak.modelstash.util.PhotoLabel;
import com.sarthak.modelstash.util.PhotoStorage;
import com.sarthak.modelstash.util.ScaleFormat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds a new model, or edits one when started with EXTRA_MODEL_ID.
 * Also receives links shared from the browser ("Share -> Model Stash").
 * Holds up to six photos, each labelled by you; the first one is the cover.
 */
public class AddEditModelActivity extends AppCompatActivity {

    public static final String EXTRA_MODEL_ID = "model_id";
    public static final String EXTRA_PREFILL_NAME = "prefill_name";
    /** true = the new model goes on the Wishlist instead of the owned catalogue. */
    public static final String EXTRA_WISHLIST = "wishlist";

    private static final String STATE_PHOTO_PATHS = "photo_paths";
    private static final String STATE_PHOTO_LABELS = "photo_labels";
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
    private ChipGroup statusChips;
    private View statusGroup;
    private MaterialButton cameraButton;
    private MaterialButton galleryButton;
    private MaterialButton fetchButton;
    private MaterialButton saveButton;
    private LinearProgressIndicator fetchProgress;
    private View photoEmptyCard;
    private RecyclerView photoList;
    private View photosHint;
    private TextView photoCount;
    private View scalematesBody;
    private View expandIcon;

    private ModelRepository repository;
    /** The model being edited, or null when adding a new one. */
    @Nullable
    private ModelKit editing;
    /** The photos as they stand on screen (may not be saved yet). */
    private final PhotoDraft draft = new PhotoDraft();
    private PhotoStripAdapter photoAdapter;
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
                    addPhoto(pendingCameraPath);
                } else {
                    PhotoStorage.delete(pendingCameraPath); // cancelled: remove the empty file
                }
                pendingCameraPath = null;
            });

    /** The system photo picker, allowing several at once; extras beyond six are dropped. */
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhotos =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(PhotoDraft.MAX_PHOTOS),
                    uris -> {
                        if (uris != null && !uris.isEmpty()) {
                            copyPickedPhotos(uris);
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
        statusChips = findViewById(R.id.chips_status);
        statusGroup = findViewById(R.id.group_status);
        descriptionInput = findViewById(R.id.input_description);
        cameraButton = findViewById(R.id.button_camera);
        galleryButton = findViewById(R.id.button_gallery);
        fetchButton = findViewById(R.id.button_fetch);
        saveButton = findViewById(R.id.button_save);
        fetchProgress = findViewById(R.id.progress_fetch);
        photoEmptyCard = findViewById(R.id.card_photo);
        photoList = findViewById(R.id.list_photos);
        photosHint = findViewById(R.id.text_photos_hint);
        photoCount = findViewById(R.id.text_photo_count);
        scalematesBody = findViewById(R.id.body_scalemates);
        expandIcon = findViewById(R.id.icon_expand);
        repository = ModelRepository.get(this);

        photoAdapter = new PhotoStripAdapter(draft, new PhotoStripAdapter.Listener() {
            @Override
            public void onPhotoClick(int position, View anchor) {
                showPhotoMenu(position, anchor);
            }

            @Override
            public void onAddClick() {
                launchGallery();
            }
        });
        photoList.setAdapter(photoAdapter);
        photoList.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator() {
            {
                setAddDuration(260);
                setRemoveDuration(200);
                setMoveDuration(320);
                setChangeDuration(180);
            }
        });
        photoAdapter.dragHelper().attachToRecyclerView(photoList);

        findViewById(R.id.header_scalemates).setOnClickListener(v ->
                setScalematesOpen(scalematesBody.getVisibility() != View.VISIBLE));
        addScaleSuggestions(findViewById(R.id.chips_scale));

        clearErrorWhenEdited(linkLayout, linkInput);
        clearErrorWhenEdited(nameLayout, nameInput);
        clearErrorWhenEdited(scaleLayout, scaleInput);

        cameraButton.setOnClickListener(v -> launchCamera());
        galleryButton.setOnClickListener(v -> launchGallery());
        photoEmptyCard.setOnClickListener(v -> launchGallery());
        fetchButton.setOnClickListener(v -> fetchFromScalemates());
        saveButton.setOnClickListener(v -> save());

        // First open: the form's sections rise in one after another.
        ViewGroup form = findViewById(R.id.form);
        if (savedInstanceState == null) {
            Motion.prepareStagger(form);
            form.post(() -> Motion.staggerIn(form, 60));
        }

        if (savedInstanceState != null) {
            // Rotation or the system restarting the app while the camera was open.
            // Text fields restore themselves; the photo state is ours to restore.
            pendingCameraPath = savedInstanceState.getString(STATE_PENDING_CAMERA);
            ArrayList<String> restored = savedInstanceState.getStringArrayList(STATE_NEW_PHOTOS);
            if (restored != null) {
                newPhotos = restored;
            }
            draft.restore(savedInstanceState.getStringArrayList(STATE_PHOTO_PATHS),
                    savedInstanceState.getIntArray(STATE_PHOTO_LABELS));
            setScalematesOpen(savedInstanceState.getBoolean(STATE_SCALEMATES_OPEN));
        }

        long modelId = getIntent().getLongExtra(EXTRA_MODEL_ID, 0);
        if (modelId != 0) {
            setTitle(R.string.title_edit_model);
            saveButton.setText(R.string.save_changes);
            saveButton.setEnabled(false); // until the model and its photos have loaded
            repository.loadWithPhotos(modelId, (model, photos) -> {
                if (model == null) {
                    finish(); // deleted in the meantime
                    return;
                }
                editing = model;
                if (savedInstanceState == null) {
                    fillForm(model);
                    draft.setAll(photos);
                    refreshPhotos();
                }
                saveButton.setEnabled(true);
            });
        } else {
            wishlistMode = getIntent().getBooleanExtra(EXTRA_WISHLIST, false);
            statusGroup.setVisibility(wishlistMode ? View.GONE : View.VISIBLE);
            setTitle(wishlistMode ? R.string.title_add_wish : R.string.title_add_model);
            saveButton.setText(wishlistMode ? R.string.save_to_wishlist : R.string.save);
            if (savedInstanceState == null) {
                handleStartIntent(getIntent());
            }
        }

        refreshPhotos();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_PHOTO_PATHS, draft.paths());
        outState.putIntArray(STATE_PHOTO_LABELS, draft.labels());
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
        showStatus(model);
        descriptionInput.setText(model.description);
        linkInput.setText(model.sourceUrl);
        setScalematesOpen(model.sourceUrl != null);
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
        if (draft.isFull()) {
            showPhotosFull();
            return;
        }
        pickPhotos.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void launchCamera() {
        if (draft.isFull()) {
            showPhotosFull();
            return;
        }
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

    /** Copies the picked images into private storage, one after another, keeping as many as fit. */
    private void copyPickedPhotos(List<Uri> uris) {
        int fits = Math.min(uris.size(), draft.remaining());
        if (fits < uris.size()) {
            Toast.makeText(this, getString(R.string.photos_trimmed, fits, PhotoDraft.MAX_PHOTOS),
                    Toast.LENGTH_LONG).show();
        }
        List<Uri> wanted = new ArrayList<>(uris.subList(0, fits));
        AppDatabase.IO.execute(() -> {
            for (Uri uri : wanted) {
                try {
                    String path = PhotoStorage.copyFromUri(getApplicationContext(), uri).getAbsolutePath();
                    runOnUiThread(() -> {
                        if (isDestroyed()) {
                            PhotoStorage.delete(path); // screen already closed (or rotated): nobody will use it
                            return;
                        }
                        newPhotos.add(path);
                        addPhoto(path);
                    });
                } catch (IOException e) {
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(),
                            R.string.error_photo_copy, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /** Adds a new, unlabelled photo; you choose its label by tapping the tile. */
    private void addPhoto(String path) {
        if (!draft.add(path)) {
            PhotoStorage.delete(path); // no room left (two picks raced each other)
            return;
        }
        photoAdapter.photoAdded();
        refreshPhotoChrome();
        photoList.post(() -> photoList.smoothScrollToPosition(Math.max(0, photoAdapter.getItemCount() - 1)));
    }

    /** Tapping a tile: choose or clear its label, make it the cover, or remove it. */
    private void showPhotoMenu(int position, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.menu_photo, menu.getMenu());
        Menu items = menu.getMenu();
        int current = draft.get(position).label;
        items.findItem(R.id.label_box_art).setChecked(current == PhotoLabel.BOX_ART);
        items.findItem(R.id.label_finished).setChecked(current == PhotoLabel.FINISHED_BUILD);
        items.findItem(R.id.label_other).setChecked(current == PhotoLabel.OTHER);
        items.findItem(R.id.label_none).setChecked(!PhotoLabel.isSet(current));
        items.findItem(R.id.action_make_cover).setVisible(position != 0);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.label_box_art) {
                draft.setLabel(position, PhotoLabel.BOX_ART);
                photoAdapter.photoChanged(position);
            } else if (id == R.id.label_finished) {
                draft.setLabel(position, PhotoLabel.FINISHED_BUILD);
                photoAdapter.photoChanged(position);
            } else if (id == R.id.label_other) {
                draft.setLabel(position, PhotoLabel.OTHER);
                photoAdapter.photoChanged(position);
            } else if (id == R.id.label_none) {
                draft.setLabel(position, PhotoLabel.NONE);
                photoAdapter.photoChanged(position);
            } else if (id == R.id.action_make_cover) {
                draft.makeCover(position);
                photoAdapter.photoMadeCover(position);
                photoList.smoothScrollToPosition(0);
            } else if (id == R.id.action_remove_photo) {
                boolean wasFull = draft.isFull();
                draft.remove(position); // the file itself is tidied up on save or when leaving
                photoAdapter.photoRemoved(position, wasFull);
            } else {
                return false;
            }
            Haptics.tick(anchor);
            refreshPhotoChrome();
            return true;
        });
        menu.show();
    }

    /** Redraws the whole strip (after loading or restoring), then the counter and buttons. */
    private void refreshPhotos() {
        photoAdapter.notifyDataSetChanged(); // a whole new set of photos: nothing to animate between
        refreshPhotoChrome();
    }

    /** The empty card or the strip, the counter and the buttons. The tiles animate themselves. */
    private void refreshPhotoChrome() {
        boolean empty = draft.isEmpty();
        photoEmptyCard.setVisibility(empty ? View.VISIBLE : View.GONE);
        photoList.setVisibility(empty ? View.GONE : View.VISIBLE);
        photosHint.setVisibility(empty ? View.GONE : View.VISIBLE);
        photoCount.setText(getString(R.string.photo_count, draft.size(), PhotoDraft.MAX_PHOTOS));
        cameraButton.setEnabled(!draft.isFull());
        galleryButton.setEnabled(!draft.isFull());
    }

    private void showPhotosFull() {
        Toast.makeText(this, getString(R.string.photos_full, PhotoDraft.MAX_PHOTOS), Toast.LENGTH_SHORT).show();
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
        model.name = name;
        model.scale = emptyToNull(scale);
        model.brand = emptyToNull(text(brandInput));
        model.kitNumber = emptyToNull(text(kitNumberInput));
        model.description = emptyToNull(text(descriptionInput));
        model.sourceUrl = ScalematesParser.extractKitUrl(text(linkInput));
        // Editing keeps the list it is already on; adding uses the tab you came from.
        model.wishlist = editing != null ? editing.wishlist : wishlistMode;
        // A wishlist kit has no build status; it starts at Unbuilt when you buy it.
        model.buildStatus = model.wishlist ? BuildStatus.UNBUILT : checkedStatus();
        // model.photoPath is set by the repository: it is always the first photo (the cover).

        saved = true;
        saveButton.setEnabled(false);
        Haptics.confirm(saveButton);
        final List<String> kept = draft.paths();
        repository.save(model, draft.toPhotos(), () -> {
            // Photos taken or picked here but removed again before saving.
            // (Saved photos you removed are deleted by the repository.)
            for (String path : newPhotos) {
                if (!kept.contains(path)) {
                    PhotoStorage.delete(path);
                }
            }
            finish();
        });
    }

    // ---- Small helpers ----

    /** Shows the status row for an owned kit and ticks its chip; wishlist kits don't get one. */
    private void showStatus(ModelKit model) {
        statusGroup.setVisibility(model.wishlist ? View.GONE : View.VISIBLE);
        switch (BuildStatus.clamp(model.buildStatus)) {
            case BuildStatus.COMPLETED:
                statusChips.check(R.id.chip_status_completed);
                break;
            case BuildStatus.IN_BUILD:
                statusChips.check(R.id.chip_status_in_build);
                break;
            default:
                statusChips.check(R.id.chip_status_unbuilt);
                break;
        }
    }

    private int checkedStatus() {
        int checked = statusChips.getCheckedChipId();
        if (checked == R.id.chip_status_completed) {
            return BuildStatus.COMPLETED;
        }
        if (checked == R.id.chip_status_in_build) {
            return BuildStatus.IN_BUILD;
        }
        return BuildStatus.UNBUILT;
    }

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
