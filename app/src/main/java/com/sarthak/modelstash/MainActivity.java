package com.sarthak.modelstash;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.net.Uri;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import com.sarthak.modelstash.ui.AddEditModelActivity;
import com.sarthak.modelstash.ui.CatalogueFragment;
import com.sarthak.modelstash.ui.DashboardFragment;
import com.sarthak.modelstash.util.Haptics;
import com.sarthak.modelstash.util.InsetsHelper;
import com.sarthak.modelstash.util.ThemePrefs;
import com.sarthak.modelstash.ui.WishlistFragment;
import com.sarthak.modelstash.data.AppDatabase;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.data.ModelRepository;
import com.sarthak.modelstash.util.CsvImport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Home screen: toolbar with logo, the current tab, the "Add model" button and the bottom bar. */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private ExtendedFloatingActionButton fab;
    /** True while the Wishlist tab is showing: the + button then adds a wanted kit. */
    private boolean onWishlistTab;

    /** Opens the system file picker for a CSV to import. */
    private final ActivityResultLauncher<String[]> pickCsv =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importCsv(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this); // must come before super.onCreate
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        InsetsHelper.applySystemBars(findViewById(R.id.main), true, false);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        fab = findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> openAddScreen());

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            Haptics.tick(bottomNav);
            showTab(item.getItemId());
            return true;
        });
        bottomNav.setOnItemReselectedListener(item -> {
            // Already on this tab: do nothing instead of rebuilding it.
        });
        if (savedInstanceState == null) {
            showTab(R.id.nav_dashboard); // after rotation the FragmentManager restores the tab itself
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_appearance) {
            chooseAppearance();
            return true;
        }
        if (item.getItemId() == R.id.action_import) {
            // Some file managers label CSVs as plain text, so accept both.
            pickCsv.launch(new String[]{"text/csv", "text/comma-separated-values", "text/plain", "*/*"});
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Lets the dashboard's "See all" button switch to the Catalogue tab. */
    public void openCatalogue() {
        bottomNav.setSelectedItemId(R.id.nav_catalogue);
    }

    public void openAddScreen() {
        openAddScreen(onWishlistTab);
    }

    /** wishlist = true adds to the Wishlist instead of the owned catalogue. */
    public void openAddScreen(boolean wishlist) {
        startActivity(new Intent(this, AddEditModelActivity.class)
                .putExtra(AddEditModelActivity.EXTRA_WISHLIST, wishlist));
    }

    /** Fragments call this while scrolling: the button shrinks going down and grows going up. */
    public void onContentScrolled(int dy) {
        if (dy > 6 && fab.isExtended()) {
            fab.shrink();
        } else if (dy < -6 && !fab.isExtended()) {
            fab.extend();
        }
    }

    private void showTab(int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_catalogue) {
            fragment = new CatalogueFragment();
        } else if (itemId == R.id.nav_wishlist) {
            fragment = new WishlistFragment();
        } else {
            fragment = new DashboardFragment();
        }
        onWishlistTab = itemId == R.id.nav_wishlist;
        fab.setText(onWishlistTab ? R.string.add_to_wishlist : R.string.add_model);
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.tab_enter, R.anim.tab_exit)
                .replace(R.id.fragment_container, fragment)
                .commit();
        fab.extend();
    }

    /** Reads the chosen CSV in the background and adds every row it understands. */
    private void importCsv(Uri uri) {
        AppDatabase.IO.execute(() -> {
            String csv;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new IOException("Could not open the file");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                csv = out.toString(StandardCharsets.UTF_8.name());
            } catch (IOException e) {
                runOnUiThread(() -> showImportMessage(getString(R.string.import_failed)));
                return;
            }
            // Importing on the Wishlist tab fills the wishlist; anywhere else, the catalogue.
            List<ModelKit> models = CsvImport.parse(csv, onWishlistTab);
            runOnUiThread(() -> {
                if (models.isEmpty()) {
                    showImportMessage(getString(R.string.import_empty));
                    return;
                }
                ModelRepository.get(this).importAll(models, added -> showImportMessage(
                        getResources().getQuantityString(R.plurals.import_done, added, added)));
            });
        });
    }

    private void showImportMessage(String message) {
        Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG).show();
    }

    private void chooseAppearance() {
        String[] options = {
                getString(R.string.appearance_dark),
                getString(R.string.appearance_light),
                getString(R.string.appearance_system)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.appearance)
                .setSingleChoiceItems(options, ThemePrefs.get(this), (dialog, which) -> {
                    dialog.dismiss();
                    ThemePrefs.set(this, which); // the ids match the array order: DARK, LIGHT, SYSTEM
                })
                .show();
    }
}