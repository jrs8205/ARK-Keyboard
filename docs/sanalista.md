# Yleinen sanalista

Näppäimistön yleinen sanalista (`app/src/main/assets/sanalista.txt`) on muodostettu
kahdesta Kotimaisten kielten keskuksen (Kotus) julkaisemasta suomen aineistosta ja
englannin sanastosta:

**1. Parole-taajuuslista** — sanamuodot taajuuksineen suomen kielen
Parole-korpuksesta (noin 17 miljoonaa sanetta kirjoitettua suomea, koottu
vuosina 1996–1998).

* Aineisto: Suomen sanomalehtikielen taajuuslista (Parole),
  <https://kaino.kotus.fi/sanat/taajuuslista/parole.php>
* Julkaisija: Kotimaisten kielten keskus (Kotus), aineisto listattu myös
  Kielipankissa
* Lisenssi: EUPL [Kielipankin metatietueen](https://urn.fi/urn:nbn:fi:lb-20140730146)
  kuvauksen ja [ISLRN-rekisterin](https://islrn.org/resources/245-100-606-282-0/)
  mukaan. Kotuksen [periaatepäätöksen](https://kotus.fi/kotus/kieliaineistot/kieliaineistojen-kaytto/)
  mukaan sen julkiset sähköiset kieliaineistot, joiden vapaata käyttöä laki tai
  sopimukset eivät rajoita, ovat vapaasti käytettävissä. Aineisto on ladattavissa
  ilman kirjautumista, eikä Kotuksen sivuilla ole sen käyttöä kieltävää tietoa.
* Avoin huomio: Kotuksen [aineistosivulla](https://kotus.fi/kotus/kieliaineistot/aineistot-verkossa/)
  listalla ei ole omaa lisenssimerkintää, ja metatietueen rakenteiset kentät sanovat
  `Licence: other` ja `Restrictions of use: academic-nonCommercialUse, other,
  shareAlike` (tarkistettu 1.11.2026). Tietue on muunnettu vanhasta
  META-SHARE-tietueesta, jossa on sama rajoitus, joten kentät ovat todennäköisesti
  vanhentuneita. Kotus ei ole tätä vahvistanut. Jos Kotus ilmoittaa toisin, lista vaihdetaan.
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

**3. English Speller Database (ESDB, aiemmin SCOWL)** — englannin sanat koossa 50,
jotta `she` tai `wait` ei muutu automaattikorjauksessa suomen sanaksi.

* Aineisto: <https://app.aspell.net/create>, tekijä Kevin Atkinson
* Lisenssi: käyttö, muokkaus, levitys ja myynti sallittu, kunhan tekijänoikeustiedot
  säilytetään; koko teksti tiedostossa
  [`esdb-copyright.txt`](../app/src/main/assets/esdb-copyright.txt), joka kulkee sovelluksen mukana
* Mukaan otetaan vain pienellä kirjoitetut sanat ilman heittomerkkejä, joita
  listalla ei jo ole. Taajuus on 1, joten suomen sanat ovat ehdotuksissa edellä, eikä
  sanaa tarjota kirjoitusvirheen korjaukseksi, ellei käyttäjä itse käytä sitä.

## Lähdemerkintä

Kotus edellyttää, että lähde nimetään, alkuperäiseen aineistoon linkitetään ja
muokkauksesta kerrotaan:

> Sanalista perustuu Kotimaisten kielten keskuksen aineistoihin
> [Kirjoitetun suomen kielen sanojen taajuuksia](https://kaino.kotus.fi/sanat/taajuuslista/parole.php)
> (Parole-korpus 1996–1998) ja
> [Nykysuomen sanalista 2024](https://kotus.fi/sanakirjat/kielitoimiston-sanakirja/nykysuomen-sana-aineistot/nykysuomen-sanalista/)
> (CC BY 4.0). Aineistoja on muokattu: sanamuodot on pienennetty ja siivottu,
> harvinaisia todennäköisiä virhemuotoja on jätetty pois, listat on yhdistetty ja
> Nykysuomen sanalistasta on käytetty vain hakusanat.
>
> Englannin sanat: [English Speller Database](https://app.aspell.net/create), Copyright
> Kevin Atkinson, ks. [`esdb-copyright.txt`](../app/src/main/assets/esdb-copyright.txt).

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
5. englannin sanat, joita edellä ei jo ole, lisätään taajuudella 1
   (`--englanti`-parametri)

Tulostiedoston muoto: yksi rivi per sana, `sananmuoto taajuus`,
aakkosjärjestyksessä, UTF-8. Järjestys säästää latauksessa aikaa ja muistia.
Nykyinen lista: 466 826 sanaa, joista 68 006 Nykysuomen sanalistalta ja 56 664
englannin sanoja; 6 652 todennäköistä virhettä jäi pois.

## Kattavuus

Mitattu 1 206 eri sanalla tuoreista Ylen uutisista (pienellä alkavat sanat): listalta
puuttuu 11,6 %, kun aiemmasta 170 100 sanan listasta puuttui 21,2 %. Korpus on
1990-luvulta, joten uudet sanat ja niiden taivutusmuodot (`euron`, `koronan`,
`älypuhelimella`) puuttuvat edelleen. Näppäimistö oppii ne käyttäjältä.
