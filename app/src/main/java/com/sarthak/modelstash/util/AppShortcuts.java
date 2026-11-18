package com.sarthak.modelstash.util;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.sarthak.modelstash.MainActivity;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.ui.AddEditModelActivity;

import java.util.Arrays;
import java.util.List;

/**
 * Long-press the app icon: "Search stash", "Add kit", "Add to wishlist".
 * "Search stash" opens the Catalogue with the keyboard up: the fastest answer to
 * "do I already own this?" while standing in a shop.
 *
 * Published from code (not res/xml/shortcuts.xml) because the debug build has a
 * different package name, which an XML shortcut would have to hard-code.
 */
public final class AppShortcuts {

    private AppShortcuts() {
    }

    public static void publish(Context context) {
        Intent home = new Intent(context, MainActivity.class).setAction(Intent.ACTION_MAIN);

        ShortcutInfoCompat search = new ShortcutInfoCompat.Builder(context, "search")
                .setShortLabel(context.getString(R.string.shortcut_search_short))
                .setLongLabel(context.getString(R.string.shortcut_search_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_search))
                .setIntent(new Intent(context, MainActivity.class).setAction(MainActivity.ACTION_SEARCH))
                .setRank(0)
                .build();

        ShortcutInfoCompat add = new ShortcutInfoCompat.Builder(context, "add")
                .setShortLabel(context.getString(R.string.shortcut_add_short))
                .setLongLabel(context.getString(R.string.shortcut_add_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_add))
                // Home underneath, so back from the add screen lands in the app, not on the launcher.
                .setIntents(new Intent[]{home, new Intent(context, AddEditModelActivity.class)
                        .setAction(AddEditModelActivity.ACTION_ADD)
                        .putExtra(AddEditModelActivity.EXTRA_WISHLIST, false)})
                .setRank(1)
                .build();

        ShortcutInfoCompat wish = new ShortcutInfoCompat.Builder(context, "wish")
                .setShortLabel(context.getString(R.string.shortcut_wish_short))
                .setLongLabel(context.getString(R.string.shortcut_wish_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_wish))
                .setIntents(new Intent[]{home, new Intent(context, AddEditModelActivity.class)
                        .setAction(AddEditModelActivity.ACTION_ADD)
                        .putExtra(AddEditModelActivity.EXTRA_WISHLIST, true)})
                .setRank(2)
                .build();

        List<ShortcutInfoCompat> shortcuts = Arrays.asList(search, add, wish);
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts);
        } catch (RuntimeException ignored) {
            // Some launchers refuse (or rate-limit) shortcuts; the app works the same without them.
        }
    }
}
