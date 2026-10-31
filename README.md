# Salotto

A fork of [Homestead](https://github.com/Sarg338/Homestead): a home screen for Fire TV with no
ads. It also takes over the Home button, which Fire OS normally doesn't let any app do.

<img src="screenshots/home.jpg" alt="Salotto on a Fire TV Stick: the side rail on the left, the clock and date at the top right, the Canali TV tile and three apps in the dock, and a grid of square icons below, over Van Gogh's Starry Night.">

**Not for Vega OS.** The newest Amazon sticks (Fire TV Stick 4K Select and the 2026 Fire TV
Stick 4K, the ones without "Plus" or "Max") run Vega OS. It isn't Android and takes no APK. The
Plus and Max run Fire OS.

## What it does

- **Your apps on one screen.** No sponsored rows, no autoplaying trailers. It opens when you
  press Home, in about 170 ms, and sorts apps by how often you use them.
- **Canali TV.** A page for Italian live TV: Rai and Mediaset channels and radios, each opening
  the right app on that channel.
- **Search.** Apps and channels by name, from a keyboard of letters on the TV.
- **A phone as a remote.** Scan a QR code on the TV and the phone gets the remote's buttons,
  a keyboard that types into any text field on the TV, a way to open a web address on the TV,
  the search and forms for the main settings. Off until you turn it on; the code changes every
  minute and works once.
- **Your remote's buttons.** The coloured buttons, Exit or any button the TV passes on can
  open Home, an app or a Canali TV channel.
- **Your look.** Gradients, black for OLED, four public-domain paintings or your own photo.
  Three settings pages that change the home screen as you go.
- **The TV itself.** Screen & sound (HDMI-CEC, overscan, screensaver), **TV Health** (what
  slows the TV or tracks you, one press to fix) and **Debloat** (Amazon packages switched off,
  never uninstalled, always reversible).
- **No computer needed.** An on-device tunnel sets up the Home button and the permissions from
  the remote. The app updates itself, and only installs when you press Install.

<table>
<tr>
<td width="50%"><img src="screenshots/canali-tv.jpg" alt="The Canali TV page: tabs for Rai, Mediaset, Radio Mediaset and other apps, and a card for each Rai channel in its own colour."><br><sub><b>Canali TV</b>: Rai, Mediaset and radios, each opening the right app.</sub></td>
<td width="50%"><img src="screenshots/search.jpg" alt="The search page: a keyboard of letters on the left, and the results for 'ra' on the right, Radio Mediaset stations and Rai 1 to 5."><br><sub><b>Search</b>: apps and channels, typed with the arrows or from the phone.</sub></td>
</tr>
<tr>
<td width="50%"><img src="screenshots/pair-phone.jpg" alt="The pairing screen: a QR code, six digits that change every minute, and the TV's address."><br><sub><b>Pair a phone</b>: a QR code and a code that works once.</sub></td>
<td width="50%"><img src="screenshots/tv-remote-buttons.jpg" alt="The TV remote buttons page: Red opens YouTube, Blue opens Twitch, Exit goes Home."><br><sub><b>Your remote's buttons</b>: red, green, yellow, blue, Exit or any other.</sub></td>
</tr>
<tr>
<td width="50%"><img src="screenshots/backgrounds.jpg" alt="The background page: gradients, your own photo and public-domain paintings, over the home screen."><br><sub><b>Backgrounds</b>: gradients, paintings or your own photo.</sub></td>
<td width="50%"><img src="screenshots/phone.jpg" alt="The phone's page, in three tabs: the remote's buttons, the search with results for 'rai', and the settings forms."><br><sub><b>The phone's page</b>: the buttons, the search and the main settings.</sub></td>
</tr>
</table>

## Install

1. Download `homestead.apk` from the
   [latest release](https://github.com/giu-napoletano29/Salotto-FireTv/releases/latest) and
   sideload it: `adb install homestead.apk`, or the Downloader app on the TV.
2. Turn on **ADB debugging** under Settings, My Fire TV, Developer options. If it isn't there,
   open My Fire TV, About, and press Select on the device name seven times.
3. Open **Salotto**. The first-run setup walks you through the rest, including the Home button.

Without that setup Salotto still works as an app you open from Amazon's app row; only the Home
button needs it. The whole of it, from a computer too, is in [docs/SETUP.md](docs/SETUP.md).

## Tested on

Real hardware, not emulators.

| Device | Model | Fire OS | What works |
|---|---|---|---|
| Amazon Fire TV Stick | `AFTSS` | 7.7.1.7 | Everything, behind a Samsung TV over HDMI-CEC. About 70-75 MB, no janky frames. |
| TCL 40V5D (Fire TV Edition) | `AFTTIFB900` | 14 | The launcher and side rail, Home button, Inputs entry, Live TV tile, Screen & sound, settings pages. Amazon's launcher is switched off. |

The original lists other devices where Homestead worked; Salotto hasn't been tried on them. If
you try it on one, [say so](docs/DEVELOPING.md#reporting-a-device).

## Know before you install

- **Without `READ_LOGS`**, Amazon's home screen flashes for a moment on each Home press. Setup
  grants it. [SECURITY.md](SECURITY.md) says exactly what the app can access, and what goes over
  the network.
- **The phone remote is plain HTTP**, on your home network only. Its buttons need the debugging
  link from setup.
- **It can switch Amazon packages off.** Nothing is uninstalled and every change can be undone
  from the app (**Undo everything this app switched off**). Do that before uninstalling.

More: [how the Home button works and the known limitations](docs/HOW-IT-WORKS.md), [your own
background photo and removing Salotto](docs/SETUP.md), [building and contributing](docs/DEVELOPING.md),
[making a release](docs/RELEASING.md), and the [changelog](CHANGELOG.md).

## Where it comes from

Salotto is a fork of [Sarg338/Homestead](https://github.com/Sarg338/Homestead), under the same
licence, and adds the things in the [changelog](CHANGELOG.md). It used to be published here as
Homestead-TCL40v5d. The package name is still `com.homestead.launcher`, so a new build installs
over an older one and keeps your settings. Builds from this repository are signed with a
different key than the original's, so to come from the original you have to uninstall it first.

## Licence

GPL-3.0-or-later. See [LICENSE](LICENSE). This app can change system settings and package states
on your television, so anyone shipping a modified build has to publish what they changed.
