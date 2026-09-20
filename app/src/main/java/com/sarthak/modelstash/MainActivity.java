package com.sarthak.modelstash;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.sarthak.modelstash.ui.AddEditModelActivity;
import com.sarthak.modelstash.ui.CatalogueFragment;
import com.sarthak.modelstash.ui.DashboardFragment;
import com.sarthak.modelstash.util.Haptics;
import com.sarthak.modelstash.util.InsetsHelper;
import com.sarthak.modelstash.util.ThemePrefs;
import com.sarthak.modelstash.ui.WishlistFragment;

/** Home screen: toolbar with logo, the current tab, the "Add model" button and the bottom bar. */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private ExtendedFloatingActionButton fab;
    /** True while the Wishlist tab is showing: the + button then adds a wanted kit. */
    private boolean onWishlistTab;

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