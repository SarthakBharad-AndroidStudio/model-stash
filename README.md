# Model Stash

An Android app for keeping track of the scale model kits you own — and the ones you still want.

Built because "do I already have this one?" is a hard question to answer while standing in a hobby shop.

<!-- Add a screenshot or two here once you have them:
<p align="center">
  <img src="docs/dashboard.png" width="260">
  <img src="docs/catalogue.png" width="260">
  <img src="docs/detail.png" width="260">
</p>
-->

## What it does

**Catalogue** — every kit you own, with a photo, scale, brand, kit number and your own notes. Photos come from the camera or the gallery and are stored inside the app's own folder, so nothing lands in your camera roll.

**Search that answers the actual question** — type a name or a kit number and the screen tells you plainly whether you own it, with a button to add it if you don't.

**Wishlist** — a second list for kits you want to buy. Same add flow, kept apart from what you own, and one button on a kit moves it into your catalogue once you've bought it.

**Dashboard** — how many kits you have, how many brands, your most common scale, what you added in the last 30 days, and the newest additions.

**Scalemates import** — paste the link to a kit page and the app fills in the name, kit number, brand and scale for you. Sharing a link from your browser opens the same form, pre-filled. Adding the link is entirely optional; everything can be typed by hand, which matters for small-manufacturer kits that aren't listed anywhere.

**CSV import** — bulk-add a list you already keep in a spreadsheet.

## Importing a CSV

Four columns are read; everything else in the file is ignored.

```
name,number,brand,scale
Russian Heavy Tank JS-2 Model 1944 ChKZ,32571,Tamiya,1:48
IJN Battleship Mikasa (Full Hull Special),CH128 (64128),Hasegawa,1:700
Yamashiro 1944 full hull with metal barrel,,Aoshima,
```

- Only `name` is required — a row with an empty number, brand or scale is still imported.
- A header row maps the columns, so they can be in any order. Common spellings are understood: `Kit No.`, `Kit number`, `Model number`, `Manufacturer`, `Maker`, `Ratio`, `Size`. Without a header the order above is assumed.
- Scales are normalised: `72`, `1/72` and `1:72` all become `1:72`. Anything that isn't a scale is left empty rather than rejecting the row.
- Quoted fields, semicolon separators (German Excel) and Windows line endings all work.
- Which list gets filled follows the tab you're on — import from the Wishlist tab and the rows land there.
- An import always adds; it never overwrites.

## About Scalemates

Scalemates has no public API, and its `robots.txt` asks automated tools to stay out of search. So the app doesn't crawl it and never goes looking for a kit on its own. It fetches exactly one page — the kit link you paste — and reads the title and description from it. Requests identify the app honestly in the User-Agent. If you'd rather not use it at all, every field is typeable.

## Built with

- Java, no Kotlin, minSdk 26 (Android 8.0)
- Room for storage, with real migrations so an update never wipes your kits
- ViewModel + LiveData, one activity with three fragments
- Material 3 with a custom palette, light and dark themes, dark by default
- Glide for photos, shared-element transitions between the grid and the detail screen
- Tanker, Quilon and Satoshi for type

No accounts, no analytics, no network calls except the Scalemates fetch you ask for. The database lives on the phone and is covered by Android's automatic backup.

## Project layout

```
app/src/main/java/com/sarthak/modelstash/
├── MainActivity.java          tabs, toolbar, CSV import, theme switching
├── ModelStashApp.java         applies the saved light/dark preference at startup
├── data/                      Room entity, DAO, database, repository
├── scalemates/                one-page fetcher and its HTML parser
├── ui/                        fragments, activities, adapter, view model
└── util/                      scale formatting, CSV parsing, photo storage, stats
```

The pure-Java parts — scale normalisation, CSV parsing, the Scalemates parser, sorting and stats — have unit tests under `app/src/test/` that run without a device.

## Building it

1. Clone the repo and open it in Android Studio.
2. Let Gradle sync; dependencies come from Google's Maven repository and Maven Central.
3. Run on a device or emulator (API 26 or newer).

For a release build: **Build → Generate Signed App Bundle / APK → APK**, create or pick a keystore, choose the `release` variant. Keep the `.jks` file and its passwords somewhere safe — without them you can't ship an update that installs over the old one.

## Fonts

The `res/font` folder contains **Tanker**, **Quilon** and **Satoshi**. These are third-party typefaces under their own licenses, not the license below. Check each foundry's terms before redistributing this repo publicly, and replace them if the terms don't allow it.

## License

MIT — see [LICENSE](LICENSE). Covers the code, not the bundled fonts or any kit data.