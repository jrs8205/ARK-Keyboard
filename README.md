# ARK-Keyboard

[![Downloads](https://img.shields.io/github/downloads/jrs8205/ARK-nappaimisto/total?label=downloads)](https://github.com/jrs8205/ARK-nappaimisto/releases)
[![Latest version](https://img.shields.io/github/v/release/jrs8205/ARK-nappaimisto?label=latest%20version)](https://github.com/jrs8205/ARK-nappaimisto/releases/latest)

🇫🇮 **[Suomenkielinen sivu](README.fi.md)**

An Android keyboard that learns your words, entirely on your device. Made for Finnish, and it
works in English too.

<picture>
  <source media="(max-width: 600px)" srcset="https://raw.githubusercontent.com/jrs8205/ARK-nappaimisto/main/docs/kuvat/esittely-kapea.png">
  <img src="docs/kuvat/esittely-levea.png" width="100%" alt="Four screenshots: word suggestions while typing, the long-press character menu, the translator and the clipboard">
</picture>

## Features

- **Learns your words** and predicts the next one
- **5–8 suggestions** on a scrollable row
- **Character menu** on long press, tap to choose
- **Translator** on the device, plus optional ✨ AI translation (with a ChatGPT subscription or an API key)
- **Dictation** with the device's or OpenAI's speech recognition
- **Clipboard** with pinnable clips
- **Emoji** with search and suggestions
- **Cursor mode** with selection, copy and paste, and a customisable toolbar
- **Themes:** follow system, light, dark, black or wallpaper colours
- **One-handed mode** and your latest copy ready to paste from the suggestion row
- **TalkBack support**, and it works on the lock screen right after the phone starts
- **Password autofill** right on the suggestion row
- **Private:** no analytics and no ads, learning stays on the device

## Languages

The app follows your phone's language: Finnish on a phone set to Finnish, English on any other.

- **Layout:** Finnish QWERTY with å, ä and ö.
- **Suggestions:** the vocabulary is mainly Finnish, with about 57,000 English words added. English
  words come after Finnish ones until you have used them, and the keyboard learns your own words
  quickly.
- **Auto-correct** is Finnish, so it is off by default unless the phone is set to Finnish.

## Download and instructions

Download the latest APK from the [Releases page](https://github.com/jrs8205/ARK-nappaimisto/releases/latest) or
[SourceForge](https://sourceforge.net/projects/ark-nappaimisto/).
Installation, API keys for AI translation and dictation, and privacy: **[Instructions](docs/instructions.md)**.

**Registered with Google.** The package name and signing key are registered in Google's Android
Developer Console, so the app will keep installing normally when Google's new
[sideloading rules](https://developer.android.com/developer-verification) take effect.

## Support development

If the app is useful to you, you can support its development:

- [GitHub Sponsors](https://github.com/sponsors/jrs8205) – monthly or one-time
- [Buy Me a Coffee](https://buymeacoffee.com/jrs8205) – no registration needed

## License

[GPL-3.0](LICENSE). The word list is based on the Institute for the Languages of Finland's
[frequency list of written Finnish](https://kaino.kotus.fi/sanat/taajuuslista/parole.php) (EUPL) and
[Modern Finnish Word List 2024](https://kotus.fi/sanakirjat/kielitoimiston-sanakirja/nykysuomen-sana-aineistot/nykysuomen-sanalista/)
(CC BY 4.0), and on the [English Speller Database](https://app.aspell.net/create) (© Kevin Atkinson).
The lists have been modified, see [docs/sanalista.md](docs/sanalista.md). Emoji data: Unicode, see
[docs/emojit.md](docs/emojit.md). Icons: [Material Symbols](https://fonts.google.com/icons) (Apache 2.0).
