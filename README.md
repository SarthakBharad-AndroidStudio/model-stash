# Model Stash

An Android app for keeping track of the scale model kits you own, what stage each build is at, and the ones you still want.

Built because "do I already have this one?" is a hard question to answer while standing in a hobby shop.

## Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/dashboard.png" width="240"><br><sub>Dashboard</sub></td>
    <td align="center"><img src="docs/catalogue.png" width="240"><br><sub>Catalogue &amp; search</sub></td>
    <td align="center"><img src="docs/detail.png" width="240"><br><sub>Kit detail</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/wishlist.png" width="240"><br><sub>Wishlist</sub></td>
    <td align="center"><img src="docs/add.png" width="240"><br><sub>Adding a kit</sub></td>
    <td align="center"><img src="docs/light.png" width="240"><br><sub>Light theme</sub></td>
  </tr>
</table>

## Features

**Catalogue** — every kit you own, as a photo grid. Each kit holds a name, scale, brand, kit number, a description and a photo. Sort by name, by newest or by scale.

**Build stages** — every owned kit is *Unbuilt*, *On the bench* or *Completed*. The detail screen shows the stage as a chip; tapping it saves a new stage immediately, without opening the edit form. A chip row in the Catalogue filters by stage, independently of the sort order and the search box.

**Search that answers the actual question** — type a name or a kit number and the screen says plainly whether you own it: *You own "Spitfire Mk.I"*, *You don't own it yet*, or *3 models match*. If you don't own it, a button takes you straight to the add form with the name already filled in. With a stage filter on, the same line reports the share instead: *Showing 4 of 46*.

**Wishlist** — a second list for kits you want to buy, kept apart from what you own. Same add flow, same fields. When you actually buy one, a single button moves it into your catalogue with its photo and notes intact, starting at *Unbuilt*.

**Dashboard** — how many kits you own, how many are built and how many are on the bench, your brand count, your most common scale, and what you added in the last 30 days. Below that, a carousel of what you're currently building and another of the newest additions. *See all* on either one opens the Catalogue already filtered to match.

**Photos** — camera or gallery, stored in the app's own private folder so they never clutter your camera roll. Tapping a card grows its photo into the detail screen's header.

**Scalemates import** — paste the link to a kit page and the app fills in the name, kit number, brand and scale. Sharing a link from your browser opens the same form, already filled in. Everything is optional: you can type every field by hand, which matters for small-manufacturer kits that aren't listed anywhere.

**CSV import** — bulk-add a list you already keep in a spreadsheet.

**Light and dark** — dark by default, light if you prefer, or follow the system. Switchable from the toolbar.

## Importing a CSV

Four columns are read; everything else in the file is ignored.

```
name,number,brand,scale
Russian Heavy Tank JS-2 Model 1944 ChKZ,32571,Tamiya,1:48
IJN Battleship Mikasa (Full Hull Special),CH128 (64128),Hasegawa,1:700
Yamashiro 1944 full hull with metal barrel,,Aoshima,
```

- Only `name` is required — a row with an empty number, brand or scale is still imported.
- A header row maps the columns, so they can be in any order. Common spellings are understood: `Kit No.`, `Kit number`, `Model number`, `Manufacturer`, `Maker`, `Ratio`, `Size`. Without a header, the order above is assumed.
- Scales are normalised: `72`, `1/72` and `1:72` all become `1:72`. Anything that isn't a scale is left empty rather than rejecting the row.
- Quoted fields, semicolon separators (as German Excel writes them), Windows line endings and a leading byte-order mark all work.
- Which list gets filled follows the tab you're on — import from the Wishlist tab and the rows land there.
- Imported kits arrive as *Unbuilt*. An import always adds; it never overwrites and never deletes.

## How the Scalemates import works

Scalemates has no public API, and its `robots.txt` asks automated tools to stay out of search. So the app doesn't crawl it and never goes looking for a kit on its own.

What it does instead is fetch exactly one page — the kit URL you paste or share — on a background thread, identifying itself honestly in the User-Agent. `ScalematesParser` reads the `og:title` meta tag for the kit name and the meta description for the brand, kit number and release year, decoding HTML entities as it goes.

The scale is worth a note, because Scalemates writes it several different ways. The parser looks in widening circles and stops at the first hit: a labelled `scale 1:48` in the meta description, a bare `1:48` or `1/700` there, the same in the title, and finally a labelled one anywhere in the page's visible text. A page with no scale at all — a tool, a detail set — leaves the field empty rather than guessing.

Everything it finds lands in the form as editable text, so a wrong guess costs one correction rather than a bad record. Only kit URLs (`scalemates.com/kits/…`) are accepted; anything else is rejected with a message rather than fetched.

## Architecture

One activity hosting three fragments for the tabs, plus two activities for the flows that deserve their own screen.

```
MainActivity ─ DashboardFragment    stats, on the bench, recently added
             ├ CatalogueFragment    search, stage filter, sort, grid
             └ WishlistFragment     grid of kits you want

AddEditModelActivity   add or edit, Scalemates fetch, photos
ModelDetailActivity    one kit, collapsing photo header, stage picker, delete / edit / move
```

**Data** — Room with a single `models` table. A `wishlist` boolean decides which list a kit belongs to, which is what makes moving between them a one-field update rather than a copy, and a `build_status` int holds the stage. Schema changes ship as real migrations (`1→2` added the wishlist flag, `2→3` the kit number, `3→4` the build status), so updating the app never wipes what's on the phone. All writes go through `ModelRepository` onto a fixed thread pool; the UI never touches the database directly.

**State** — `MainViewModel` exposes LiveData for the stats, the recent list, what's on the bench, the wishlist and the search results. The grid's contents are search results, narrowed to one build stage, then sorted: three inputs feeding one `MediatorLiveData`, so whichever one changes re-runs the chain. Searching re-queries the database rather than filtering in memory. Because the view model is scoped to the activity, switching tabs keeps your search, sort and filter — and lets the dashboard set the Catalogue's filter before handing over.

**Lists** — `ListAdapter` with a `DiffUtil` callback, so inserts and edits animate instead of blinking. The grids use a staggered enter animation; the dashboard carousels use a custom `StartSnapHelper` that aligns cards to the left edge and switches snapping off at the end of the list, because the stock centre-snapping helper leaves a short carousel with only one position it can settle in.

**Transitions** — tapping a card runs a shared-element transition into the detail screen. The enter transition is postponed until Glide reports the photo is decoded, so the animation starts from a real image rather than an empty frame.

**Theming** — one `DayNight` theme with the palette split across `values/colors.xml` and `values-night/colors.xml`, so light and dark are the same layout with different tokens. The saved preference is applied in `ModelStashApp.onCreate` via `AppCompatDelegate`, before the first activity inflates, which avoids the flash of the wrong theme on launch. Typography is set through `textAppearance*` theme attributes rather than a blanket `fontFamily`, so each text style keeps its own typeface.

**Edge-to-edge** — the app draws behind the system bars and applies insets per screen through a small `InsetsHelper`, so the FABs sit above the navigation bar and scrolling content clears the status bar.

**Photos** — the camera path writes through a `FileProvider` into app-private storage, so no storage permission is needed. Replaced and abandoned photos are deleted when you leave the form, and a kit's photo is deleted with the kit.

## Project layout

```
app/src/main/java/com/sarthak/modelstash/
├── MainActivity.java          tabs, toolbar, CSV import, theme switching
├── ModelStashApp.java         applies the saved light/dark preference at startup
├── data/                      ModelKit entity, DAO, database + migrations, repository
├── scalemates/                single-page fetcher and its HTML parser
├── ui/                        fragments, activities, list adapter, snap helper, view model
└── util/                      build stages, scale formatting, CSV parsing, photo storage, stats
```

## Tests

The logic that doesn't need a device lives in plain Java classes and is unit-tested: scale normalisation, CSV parsing, the Scalemates HTML parser, build-stage handling, sorting and the dashboard stats. 25 tests, no device required.

```
./gradlew test
```

## Tech stack

- Java, minSdk 26 (Android 8.0), Material 3
- Room, ViewModel, LiveData, Fragment, RecyclerView
- Glide for image loading
- AndroidX Core SplashScreen
- Tanker, Quilon and Satoshi ([Fontshare](https://www.fontshare.com))

## Building it

1. Clone the repo and open it in Android Studio.
2. Let Gradle sync — dependencies come from Google's Maven repository and Maven Central.
3. Run on a device or emulator, API 26 or newer.

## Privacy

No accounts, no analytics, no telemetry. The only network request the app ever makes is the Scalemates page fetch you explicitly trigger. Your kits, notes and photos stay on the phone and are covered by Android's automatic backup.