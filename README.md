# ARK-näppäimistö

[![Lataukset](https://img.shields.io/github/downloads/jrs8205/ARK-nappaimisto/total?label=lataukset)](https://github.com/jrs8205/ARK-nappaimisto/releases)
[![Uusin versio](https://img.shields.io/github/v/release/jrs8205/ARK-nappaimisto?label=uusin%20versio)](https://github.com/jrs8205/ARK-nappaimisto/releases/latest)

Suomalainen Android-näppäimistö, joka oppii sinun sanasi – kokonaan
laitteella.

<picture>
  <source media="(max-width: 600px)" srcset="https://raw.githubusercontent.com/jrs8205/ARK-nappaimisto/main/docs/kuvat/esittely-kapea.png">
  <img src="docs/kuvat/esittely-levea.png" width="100%" alt="Neljä kuvakaappausta: sanaehdotukset kirjoitettaessa, pitkän painalluksen merkkivalikko, käännösnäkymä ja leikepöytä">
</picture>

## In English

ARK Keyboard is a private Android keyboard that learns your words, entirely on your device.
It is made for Finnish and also works in English: the app follows your phone's language
(English or Finnish), and it knows about 57,000 English words on top of its Finnish vocabulary.

- **Layout:** Finnish QWERTY with å, ä and ö.
- **Suggestions:** the vocabulary is mainly Finnish, so English words come after Finnish ones until
  you have used them. The keyboard learns your own words quickly.
- **Auto-correct** is Finnish and is off by default unless the phone is set to Finnish.

Download the latest APK from the [Releases page](https://github.com/jrs8205/ARK-nappaimisto/releases/latest).
Setup, tips and privacy: **[Instructions](docs/instructions.md)**.

## Ominaisuudet

- **Oppii sinun sanasi** ja ennustaa seuraavan sanan
- **5–8 ehdotusta** vieritettävällä rivillä
- **Merkkivalikko** pitkällä painalluksella, valinta napauttamalla
- **Käännösnäkymä** laitteella sekä valinnainen ✨ AI-käännös (ChatGPT-tilauksella tai API-avaimella)
- **Sanelu** laitteen tai OpenAI:n puheentunnistuksella
- **Leikepöytä** kiinnitettävine leikkeineen
- **Emojit** sivuittain, suomenkielisellä haulla ja ehdotuksilla
- **Kursoritila** valinnalla, kopioinnilla ja liittämisellä sekä muokattava työkalurivi
- **Teemat:** järjestelmän mukaan, vaalea, tumma, musta tai taustakuvan värit
- **Yhden käden tila** ja tuore kopio ehdotusriviltä liitettävänä
- **TalkBack-tuki** ja toiminta lukitusnäytöllä heti käynnistyksen jälkeen
- **Salasanojen täyttö** suoraan ehdotusriviltä
- **Yksityinen:** ei analytiikkaa eikä mainoksia, oppiminen pysyy laitteella

## Lataus ja ohjeet

Lataa uusin APK [Releases-sivulta](https://github.com/jrs8205/ARK-nappaimisto/releases/latest)
tai [SourceForgesta](https://sourceforge.net/projects/ark-nappaimisto/).
Asennus, AI-käännöksen ja sanelun API-avaimet sekä yksityisyys:
**[Ohjeet](docs/ohjeet.md)**.

**Rekisteröity Googlelle.** Paketin nimi ja allekirjoitusavain on rekisteröity
Googlen Android Developer Consoleen, joten sovellus asentuu jatkossakin
normaalisti, kun Googlen uudet
[sivulataussäännöt](https://developer.android.com/developer-verification)
tulevat voimaan.

## Tue kehitystä

Jos sovelluksesta on sinulle hyötyä, voit tukea sen kehitystä:

- [GitHub Sponsors](https://github.com/sponsors/jrs8205) – kuukausittain tai kertaluontoisesti
- [Buy Me a Coffee](https://buymeacoffee.com/jrs8205) – ilman rekisteröitymistä

## Lisenssi

[GPL-3.0](LICENSE). Sanalista perustuu Kotimaisten kielten keskuksen aineistoihin
[Kirjoitetun suomen kielen sanojen taajuuksia](https://kaino.kotus.fi/sanat/taajuuslista/parole.php)
(EUPL) ja
[Nykysuomen sanalista 2024](https://kotus.fi/sanakirjat/kielitoimiston-sanakirja/nykysuomen-sana-aineistot/nykysuomen-sanalista/)
(CC BY 4.0) sekä englannin osalta [English Speller Databaseen](https://app.aspell.net/create)
(© Kevin Atkinson). Aineistoja on muokattu, ks. [docs/sanalista.md](docs/sanalista.md).
Emojiaineisto: Unicode, ks.
[docs/emojit.md](docs/emojit.md). Kuvakkeet:
[Material Symbols](https://fonts.google.com/icons) (Apache 2.0).
