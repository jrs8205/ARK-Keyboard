# Yleinen sanalista

Näppäimistön yleinen suomen sanalista (`app/src/main/assets/sanalista.txt`) on
muodostettu kahdesta Kotimaisten kielten keskuksen (Kotus) julkaisemasta
aineistosta:

**1. Parole-taajuuslista** — sanamuodot taajuuksineen suomen kielen
Parole-korpuksesta (noin 17 miljoonaa sanetta kirjoitettua suomea, koottu
vuosina 1996–1998).

* Aineisto: Suomen sanomalehtikielen taajuuslista (Parole),
  <https://kaino.kotus.fi/sanat/taajuuslista/parole.php>
* Julkaisija: Kotimaisten kielten keskus (Kotus), aineisto listattu myös
  Kielipankissa
* Lisenssi: **varmistamatta, tiedot ovat ristiriitaiset.**
  [Kielipankin metatietueen](https://urn.fi/urn:nbn:fi:lb-20140730146) kuvaus ja
  [ISLRN-rekisteri](https://islrn.org/resources/245-100-606-282-0/) ilmoittavat
  lisenssiksi EUPL:n. Saman metatietueen rakenteiset kentät sanovat kuitenkin
  `Licence: other` ja `Restrictions of use: academic-nonCommercialUse, other,
  shareAlike` (tarkistettu 1.11.2026). Kotuksen
  [aineistosivulla](https://kotus.fi/kotus/kieliaineistot/aineistot-verkossa/) listalla
  ei ole lisenssimerkintää, ja aineiston saa ladattua ilman kirjautumista. Asia
  varmistetaan Kotukselta (arkisto@kotus.fi) ennen seuraavaa julkaisua.
* Lähtötiedosto: `parole_frek3.zip` (sanamuodot, jotka esiintyvät korpuksessa
  vähintään kolmesti)

**2. Nykysuomen sanalista (2024)** — perusmuodot, jotka täydentävät
taajuuslistaa harvinaisemmilla sanoilla.

* Aineisto: Nykysuomen sanalista,
  <https://kaino.kotus.fi/lataa/nykysuomensanalista2024.csv>
* Julkaisija: Kotimaisten kielten keskus (Kotus)
* Lisenssi: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.fi)
* Perusmuodot, joita taajuuslistalla ei jo ole, lisätään listan jatkoksi
  pienellä oletustaajuudella (2), jolloin ne ovat täydennyksissä tarjolla
  mutta yleisyydeltään tunnettujen sanojen takana.

## Käsittely

Lista on muodostettu työkalulla [`tools/sanalista.py`](../tools/sanalista.py):

1. sanamuodot pienennetään ja saman muodon taajuudet yhdistetään
2. mukaan otetaan vain suomen kirjaimista (a–z, å, ä, ö) ja yhdysmerkeistä
   koostuvat muodot; numerot, välimerkit ja muut siivotaan pois
3. harvinaisista muodoista (alle 17 esiintymää) karsitaan todennäköiset
   kirjoitus- ja skannausvirheet: muoto jää pois, jos yhden muokkauksen päässä
   on vähintään 100 kertaa yleisempi sana, eikä se ole Nykysuomen sanalistan
   perusmuoto. Listalla virhe estäisi automaattikorjauksen, koska tunnettua sanaa
   ei korjata (esim. `ensimäisen`, 16 esiintymää)
4. Nykysuomen sanalistan perusmuodot, joita edellä ei jo ole, lisätään
   oletustaajuudella 2 (`--kotus`-parametri)

Tulostiedoston muoto: yksi rivi per sana, `sananmuoto taajuus`,
aakkosjärjestyksessä, UTF-8. Järjestys säästää latauksessa aikaa ja muistia.
Nykyinen lista: 410 162 sanaa, joista 68 006 Nykysuomen sanalistalta; 6 652
todennäköistä virhettä jäi pois.

## Kattavuus

Mitattu 1 206 eri sanalla tuoreista Ylen uutisista (pienellä alkavat sanat): listalta
puuttuu 11,6 %, kun aiemmasta 170 100 sanan listasta puuttui 21,2 %. Korpus on
1990-luvulta, joten uudet sanat ja niiden taivutusmuodot (`euron`, `koronan`,
`älypuhelimella`) puuttuvat edelleen. Näppäimistö oppii ne käyttäjältä.
