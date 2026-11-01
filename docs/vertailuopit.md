# Vertailuopit: AOSP LatinIME ja HeliBoard

Lähdekoodit kansiossa `vertailu/` (ei repossa). AOSP LatinIME on
Apache 2.0- ja HeliBoard GPL-3.0-lisensoitu, joten molemmista saa
lainata ratkaisuja; FUTO Keyboardin lisenssi ei ole yhteensopiva,
joten sitä käytetään vain käytöksen vertailukohtana.

## Mikä meillä on jo kohdallaan

- Kirjoitus committina ilman composing-tekstiä välttää suurimman osan
  web- ja Compose-kenttien composing-alueongelmista, joita AOSP-suku
  joutuu kiertämään.
- Välimerkkisääntöjen kenttärajaukset (osoite-, sähköposti-, koodi- ja
  numerokentät pois) vastaavat LatinIME:n ja HeliBoardin käytäntöä.
- Merkkipoisto näppäintapahtumana (KEYCODE_DEL) on sama reitti, jota
  HeliBoard käyttää web-kenttien varareittinä — se toimii myös
  selaimissa, jotka eivät ymmärrä deleteSurroundingTextiä.
- Numerorivin piilotus siirtää numerot ylärivin pitkiin painalluksiin —
  sama ratkaisu kuin HeliBoardissa.
- Käännösrivin puskuri käsittelee grafeemit (emojit) BreakIteratorilla
  kuten HeliBoardin poistologiikka.

## Otettu käyttöön

Kaikki aiemmin poimitut asiat on toteutettu:

1. **Hitaan kentän tunnistus** (LatinIME: 200 ms, 10 min muisti):
   `SlowConnectionGuard`. Hitaassa kentässä valinnaiset kyselyt odottavat
   kirjoitustaukoa, eikä kirjaimelta kysytä kentän tekstiä missään kentässä.
2. **Oma ja vieras muutos odotetulla kursoripositiolla**
   (isBelatedExpectedUpdate): `CursorExpectation`. Aikaikkuna on varareittinä
   näppäintapahtumille, joiden vaikutusta ei voi tietää.
3. **Sovelluskohtaisten poikkeusten paikka** (HeliBoard AppWorkarounds):
   `AppQuirks` ja `FieldProfile`. HeliBoardin Firefox-poikkeuksia ei otettu,
   koska ne koskevat composing-tekstiä, jota ARK ei käytä kirjoittaessa, ja
   NO_SUGGESTIONS-pakotus poistaisi automaattikorjauksen.
4. **Puolustava tarkistus ennen peruutuksia**: automaattikorjauksen,
   peruutusnapin ja kaksoisvälilyönnin pisteen peruutus tarkistavat kentän tekstin.
5. **Valehteleva alkukursori** (tryFixLyingCursorPosition): `CursorExpectation.begin`.
6. **Kaksoisvälilyönti pisteeksi** peruutuksineen.
7. **Sanapoiston varareitti**: jos kenttä ei reagoi tekstipoistoon, sana
   poistetaan näppäintapahtumin ja kentässä jatketaan niillä.

## Sanelusta

HeliBoard ei toteuta omaa sanelua vaan vaihtaa järjestelmän
puhesyöte-IME:hen. Oma jaksotettu istunto -ratkaisumme on tätä
pidemmällä; FUTOn Whisper-pohjainen offline-tunnistus on paras
esikuva tehtävälle #43.
