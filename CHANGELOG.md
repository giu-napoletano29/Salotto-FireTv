# Changelog

This fork keeps the original's version numbers and adds a fourth digit or a new minor. The
original's history (up to 1.6.14) is in the git log below the fork's first commit.

## 1.8.4

Type and send links from the phone.

- The phone's page has a new tab, Send. Type on the phone and the letters arrive in the text
  field that has the focus on the TV, in any app (YouTube and Netflix searches, passwords,
  network names), with a clear button. Accented letters arrive without the accent: Android's
  key map has none. There is no Enter: in the on-screen keyboards of YouTube and others it
  would press the highlighted letter instead. To search or confirm, press OK.
- The same tab opens a web address on the TV: a YouTube link opens in the YouTube app, any
  other in the browser (or the system asks which app, if several want it). Only http and
  https addresses are accepted.
- Salotto's own Search page takes letters, digits, Space and Delete from a phone or a USB
  keyboard as well as from its on-screen keys.
- The key helper is started again if the debugging link was not up yet on the first try. It
  used to stay off for two minutes, so the first keys and typed text were lost.

## 1.8.3

Smoother dark backgrounds, and no black borders left behind by Overscan.

- Dark gradients (the backgrounds, the fade under the dock, the idle clock) are now dithered.
  On a TV in Film mode they showed hard steps, because the picture is shown as it is.
- Turning Overscan off in Screen and sound now also sets its amount back to 0. A leftover
  amount could keep shrinking the picture on some TVs even with the switch off, leaving black
  borders on the sides in Film mode.

## 1.8.2

Everything in Italian.

- Every page of the app is now translated into Italian: the settings, System settings, Screen
  and sound, TV Health, Debloat (with the description of each package), Setup, the first-run
  steps, the update pages and the Canali TV page.
- English stays the default: a TV set to any language other than Italian shows English
  throughout.
- The phone's remote page still follows the phone's language, not the TV's.

## 1.8.1

- **Any button of the TV's remote can be given a job**, not only the four coloured ones: Exit
  is listed (a Samsung sends it as CEC "Clear"), and *Add a button* waits for the next button
  pressed on the remote and learns it, so it works with whatever TV and remote there are.
  Arrows, OK and Back keep their place. Which buttons reach the Fire TV is up to the TV.
- A button can also open **a channel** of Canali TV (Rai 1, Canale 5 and the others), listed
  only while its app is installed and on. The page is in Italian too.

## 1.8.0

Search, and a phone that controls the TV.

- **Search** in the side rail: apps and Canali TV channels by name (accents and case do not
  matter), with a keyboard of letters that the arrows walk over. Before anything is typed it
  lists the apps used most recently. It runs only while the page is open.
- **Phone remote** (Settings, Behaviour; off by default). While on, a phone on the same Wi-Fi
  can press the remote's buttons, search and open apps and channels, and change the main
  settings from forms: background, columns, dock, clock, date, greeting, sort, hidden apps,
  idle clock, screensaver, interface sounds and the power LED. The port is closed as soon as
  the switch is turned off.
- **Pairing by QR code.** *Pair a phone* shows a QR code and six digits that change every
  minute and work once, and only while that screen is open. A paired phone is signed in for
  1 day, 1 week, 1 month or until it signs out (a setting); *Sign out all phones* ends every
  session. It is plain HTTP: see SECURITY.md for what protects it and what does not.
- **Quick remote buttons.** A press now takes a few milliseconds instead of about a second:
  while the phone's remote page is open, a small helper process (see SECURITY.md) presses the
  keys; it exits after 75 seconds without one.
- In Italian the Settings page and its tile are now called **Personalizzazione** and
  **Personalizza**, to tell them apart from the TV's own *Impostazioni*.

## 1.7.2

The app is now called **Salotto**, "a Homestead fork".

- The name shows on the launcher tile and banner, in Settings ("Salotto 1.7.2 · a Homestead
  fork"), in the first-run setup and in every message. The package name
  (`com.homestead.launcher`), the log tag and the licence headers stay, so this installs over
  1.7.1 and keeps its settings.
- The README opens on what Salotto is and where it comes from, and is written for it.

## 1.7.1

Simple mode goes, Settings gets reorganised, and the TCL gets the controls it was missing.
Developed and tested on the same Fire TV Stick (`AFTSS`) and TCL 40V5D.

### Settings

- **Home screen settings in three pages**: Appearance (background, columns, dock, tile style,
  clock, date, greeting), Content & order (sort, hidden tiles, sections, Live TV first) and
  Behaviour (idle clock).
- **Simple mode is gone** (setting, setup step, Menu x5). Menu and holding Select always hide
  a tile; the update pop-up follows the "check for updates" choice only.
- **Check for updates** reads this fork's releases, says "no release published yet" instead
  of "unavailable" when there is none, and is translated.
- **Screen & sound** gains a **Screensaver** choice (Off, Colours, Amazon's), **Interface
  sounds**, a **Power LED** switch (on a TV only) and, on the TCL, the **Live TV audio
  language**. The colour screensaver was TV Health's own pick; it is now one choice among
  three, and the TCL's Amazon screensaver (`com.amazon.screensaver`) is recognised as Amazon's.
- **Screen & sound on a TV that is itself the screen** (the TCL) leaves out HDMI-CEC with "the
  TV", overscan and the colour depth, which belong to a stick's HDMI output.

### Home screen

- **Inputs** entry in the rail on a TV with inputs: on the TCL it presses the TV's own INPUT
  key through the debugging link (brought up while the item has focus), with Homestead's list
  as the fallback.
- Live TV (the tuner) always comes first, and on a TV with a tuner Canali TV has no tile
  (a remote button bound to it still opens the page).
- **The Kiss** is cropped from the top, so the couple is not cut off at 16:9.

### Releases

- A push to `main` with a new `versionName` builds, signs and publishes the release
  (`.github/workflows/release.yml`, `docs/RELEASING.md`).

## 1.7.0

A new look, a rebuilt Settings, and a Canali TV page for Italian live TV. Developed and tested
on a Fire TV Stick (`AFTSS`, Fire OS 7.7) and a TCL 40V5D (Fire OS 14).

### Look

- **A side rail** instead of the top bar: the quick settings you choose come first, then the
  Appstore, System settings and Settings. Icons at rest, labels when it has focus; glass at
  rest and the settings pages' own colour when open. Back from the rail returns to the start.
- **Manrope** throughout, bundled in the app (`res/raw`, OFL).
- **A dock with no band**: Canali TV and the three most used apps, tiles 3:2, no zoom beyond
  4% on focus, a white rim on the focused tile only. Under it, a grid of **square icons**
  drawn edge to edge (a wide logo such as Prime Video's is extended into a square, not
  stretched), and the name of the focused tile above the dock.
- **Backgrounds**: a dark fade that leaves the top half untouched and goes to near-black
  under the dock, over presets and photos alike; **four public-domain paintings** bundled
  (Starry Night, The Great Wave, The Kiss, Water Lilies); the idle clock sits on a blurred,
  heavily dimmed copy of a photo or painting.
- The clock moves to the top right with a small spaced date and a network icon that turns
  amber when there is no connection.
- Very light tiles (Netflix's white banner) are toned down until they have focus.
- Settings pages take the wallpaper's colour, with a translucent row band and 6 dp corners.
- Sharper tiles: icons are decoded at 160 px and banners at the dock tile's own size.

### Canali TV

- **Tabs**: Rai, Mediaset, Radio Mediaset and other apps. Left and Right on the strip change
  tab; Down enters the cards.
- Rai's own colours (1 blue, 2 red, 3 green, 4 violet) and a TV-style card: a small label
  over one big word or number.
- **Every Mediaset channel and radio** the Infinity "Dirette" page lists, each opening
  Infinity on that channel (`/diretta/<slug>`). Checked on a Stick: Canale 5, Italia 1,
  Rete 4. The others follow the same pattern but are not individually verified.
- A near-black Canali TV tile with the Material antenna.

### Settings

- **Settings** (Homestead's own) and **System settings** (the TV's) are separate pages.
- **Screen & sound** (no root needed): HDMI-CEC switches, sleep with the TV, Miracast,
  **overscan on/off and amount**, the Stick's own **15-minute sleep**, and the colour depth
  shown for reference. System settings shows how many of these deserve a look.
- **App info** in a tile's Menu: Amazon's page for the app (cache, data, force stop).
- Quick settings now feed the rail; Settings and TV Health can be hidden like any tile.
- Italian translation of the home screen and the main Settings and System settings rows.

### Fixes

- A wallpaper drawn at half width, shifted, or black until it was chosen again (a bitmap
  inside a layer is positioned at its own size; the wallpaper now always fills its bounds).
- Focus no longer escapes behind an open panel.
- The Settings tile no longer lands on a do-nothing stub when Amazon's launcher is off.
- The page is redrawn after a wake from standby or the idle clock.

### Numbers (Fire TV Stick, Fire OS 7.7)

- Memory about 70-75 MB; no janky frames over 22 moves of the focus; cold start about
  0.95-1.8 s, most of it loading the 24 tiles.

## 1.6.14.1

- A custom build: the update check looks at this fork's releases.
