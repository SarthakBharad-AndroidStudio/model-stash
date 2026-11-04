package com.sarthak.modelstash;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.sarthak.modelstash.data.AppDatabase;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.data.ModelRepository;
import com.sarthak.modelstash.ui.AddEditModelActivity;
import com.sarthak.modelstash.ui.CatalogueFragment;
import com.sarthak.modelstash.ui.DashboardFragment;
import com.sarthak.modelstash.ui.WishlistFragment;
import com.sarthak.modelstash.util.CsvImport;
import com.sarthak.modelstash.util.Haptics;
import com.sarthak.modelstash.util.InsetsHelper;
import com.sarthak.modelstash.util.ThemePrefs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Home screen: toolbar with logo, the current tab, the "Add model" button and the bottom bar.
 *
 * Back on the Catalogue or Wishlist tab returns to Home; back on Home closes the app.
 * A new tab wipes in over the old one from the side it sits on in the bottom bar,
 * and tapping the tab you're already on scrolls it back to the top.
 */
public class MainActivity extends AppCompatActivity {

    private static final String STATE_TAB = "tab";

    private BottomNavigationView bottomNav;
    private ExtendedFloatingActionButton fab;
    private AppBarLayout appBar;
    /** The tab on screen: R.id.nav_dashboard, nav_catalogue or nav_wishlist. */
    @IdRes
    private int currentTab = R.id.nav_dashboard;
    /** True while the Wishlist tab is showing: the + button then adds a wanted kit. */
    private boolean onWishlistTab;

    /** Opens the system file picker for a CSV to import. */
    private final ActivityResultLauncher<String[]> pickCsv =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importCsv(uri);
                }
            });

    /**
     * Back on any tab but Home goes to Home instead of closing the app. It's only
     * switched on while another tab is showing, so on Home, back behaves normally.
     */
    private final OnBackPressedCallback backToHome = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            bottomNav.setSelectedItemId(R.id.nav_dashboard);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this); // must come before super.onCreate
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        InsetsHelper.applySystemBars(findViewById(R.id.main), true, false);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false); // our own logo + title are in the layout

        appBar = findViewById(R.id.app_bar);
        fab = findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> openAddScreen(onWishlistTab));

        getOnBackPressedDispatcher().addCallback(this, backToHome);

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            Haptics.tick(bottomNav);
            showTab(item.getItemId());
            return true;
        });
        // Already on this tab: glide back to the top instead of rebuilding it.
        bottomNav.setOnItemReselectedListener(item -> scrollCurrentTabToTop());

        if (savedInstanceState == null) {
            showTab(R.id.nav_dashboard);
        } else {
            // After rotation the FragmentManager restores the fragment itself;
            // only our own notes about which tab it is need restoring.
            applyTabState(savedInstanceState.getInt(STATE_TAB, R.id.nav_dashboard), false);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
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

    // ---- Called by the fragments ----

    /** Lets the dashboard's "See all" button and stat cards switch to the Catalogue tab. */
    public void openCatalogue() {
        bottomNav.setSelectedItemId(R.id.nav_catalogue);
    }

    public void openAddScreen() {
        openAddScreen(onWishlistTab);
    }

    /**
     * wishlist = true adds to the Wishlist instead of the owned catalogue. The add
     * screen wipes in over this one, like every other screen (see the theme).
     */
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

    // ---- Tabs ----

    private void showTab(@IdRes int itemId) {
        Fragment fragment;
        if (itemId == R.id.nav_catalogue) {
            fragment = new CatalogueFragment();
        } else if (itemId == R.id.nav_wishlist) {
            fragment = new WishlistFragment();
        } else {
            fragment = new DashboardFragment();
        }

        // Wipe: the new tab slides in over the old one, which drifts a little the other
        // way and dims. It comes from the right when the tab sits to the right of the
        // current one in the bottom bar, and from the left otherwise, so the motion
        // always matches where your finger went. (FragmentContainerView draws the
        // incoming tab on top.)
        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true);
        boolean firstTab = getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null;
        if (!firstTab) { // the app's very first screen just appears; the splash screen hands over to it
            boolean forward = tabIndex(itemId) >= tabIndex(currentTab);
            transaction.setCustomAnimations(
                    forward ? R.anim.tab_wipe_in_from_right : R.anim.tab_wipe_in_from_left,
                    forward ? R.anim.tab_wipe_out_to_left : R.anim.tab_wipe_out_to_right);
        }
        transaction.replace(R.id.fragment_container, fragment).commit();

        appBar.setExpanded(true, true);
        applyTabState(itemId, true);
    }

    /** Position in the bottom bar: Home 0, Catalogue 1, Wishlist 2. */
    private static int tabIndex(@IdRes int itemId) {
        if (itemId == R.id.nav_catalogue) {
            return 1;
        }
        if (itemId == R.id.nav_wishlist) {
            return 2;
        }
        return 0;
    }

    /** Back-button behaviour, the + button's label and the Wishlist flag for one tab. */
    private void applyTabState(@IdRes int itemId, boolean animate) {
        currentTab = itemId;
        onWishlistTab = itemId == R.id.nav_wishlist;
        backToHome.setEnabled(itemId != R.id.nav_dashboard);
        setFabText(onWishlistTab ? R.string.add_to_wishlist : R.string.add_model, animate);
    }

    /**
     * Changes the + button's label. Instead of the button jumping to its new width,
     * it shrinks to a circle, swaps the text and grows back out.
     */
    private void setFabText(@StringRes int text, boolean animate) {
        CharSequence wanted = getText(text);
        if (TextUtils.equals(fab.getText(), wanted)) {
            fab.extend();
            return;
        }
        if (!animate || !fab.isExtended()) {
            fab.setText(wanted);
            fab.extend();
            return;
        }
        fab.shrink(new ExtendedFloatingActionButton.OnChangedCallback() {
            @Override
            public void onShrunken(ExtendedFloatingActionButton button) {
                button.setText(wanted);
                button.extend();
            }
        });
    }

    /** Smoothly scrolls the visible tab back to its top, and brings the toolbar and + button back. */
    private void scrollCurrentTabToTop() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        View root = fragment != null ? fragment.getView() : null;
        View scrollable = root != null ? findMainScroller(root) : null;
        if (scrollable instanceof RecyclerView) {
            ((RecyclerView) scrollable).smoothScrollToPosition(0);
        } else if (scrollable instanceof NestedScrollView) {
            ((NestedScrollView) scrollable).smoothScrollTo(0, 0);
        }
        appBar.setExpanded(true, true);
        fab.extend();
        Haptics.tick(bottomNav);
    }

    /**
     * The tab's main vertical scroller: the first NestedScrollView or vertical
     * RecyclerView, searching level by level, so the Home tab's sideways
     * carousels inside its scroll view are never picked by mistake.
     */
    @Nullable
    private static View findMainScroller(View root) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.poll();
            if (view instanceof NestedScrollView) {
                return view;
            }
            if (view instanceof RecyclerView) {
                RecyclerView.LayoutManager lm = ((RecyclerView) view).getLayoutManager();
                boolean horizontal = lm instanceof LinearLayoutManager
                        && ((LinearLayoutManager) lm).getOrientation() == RecyclerView.HORIZONTAL;
                if (!horizontal) {
                    return view;
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    queue.add(group.getChildAt(i));
                }
            }
        }
        return null;
    }

    // ---- CSV import ----

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

    // ---- Appearance ----

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
