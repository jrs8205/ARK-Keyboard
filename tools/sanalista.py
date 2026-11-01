"""Muodostaa näppäimistön sanalistan Parole-taajuuslistasta.

Käyttö:
    python tools/sanalista.py parole_frek_3.txt app/src/main/assets/sanalista.txt \
        --kotus nykysuomensanalista2024.csv

Syöterivin muoto: "järjestysnro taajuus sananmuoto (osuus %)", Latin-1-koodattu.
Tulos: "sananmuoto taajuus" -rivit aakkosjärjestyksessä, UTF-8. Järjestys säästää
latauksessa aikaa ja muistia, koska sovelluksen ei tarvitse järjestää listaa.
Sanamuodot pienennetään ja siivotaan: mukaan vain suomen kirjaimista (ja
yhdysmerkeistä) koostuvat muodot.

Korpuksen harvinaisimmissa muodoissa on kirjoitus- ja skannausvirheitä
(ensimäisen, puhenjohtaja). Listalla ne estäisivät automaattikorjauksen, koska
tunnettua sanaa ei korjata. Siksi --varma-rajaa harvinaisempi muoto jää pois,
jos yhden muokkauksen päässä on --suhde kertaa yleisempi sana.

Valinnainen --kotus lisää Kotuksen nykysuomen sanalistan (TSV, 1. sarake
Hakusana) perusmuodot, joita taajuuslistalla ei jo ole, listan jatkoksi
pienellä oletustaajuudella. Perusmuotoja ei karsita virheinä.
"""
import argparse
import re

SANA = re.compile(r"^[a-zåäö]+(-[a-zåäö]+)*$")


def yhden_muokkauksen_paassa(a: str, b: str) -> bool:
    if a == b or abs(len(a) - len(b)) > 1:
        return False
    if len(a) == len(b):
        erot = [i for i in range(len(a)) if a[i] != b[i]]
        if len(erot) == 1:
            return True
        return (
            len(erot) == 2
            and erot[1] == erot[0] + 1
            and a[erot[0]] == b[erot[1]]
            and a[erot[1]] == b[erot[0]]
        )
    lyhyt, pitka = (a, b) if len(a) < len(b) else (b, a)
    return any(pitka[:i] + pitka[i + 1 :] == lyhyt for i in range(len(pitka)))


def todennakoiset_virheet(
    maarat: dict[str, int], suojatut: set[str], varma: int, suhde: int, minimi: int
) -> set[str]:
    """Harvinaiset muodot, joiden vieressä on moninkertaisesti yleisempi sana."""
    # Poistoindeksi riittää: kaksi yhden muokkauksen päässä olevaa sanaa jakaa
    # aina muodon, josta on poistettu enintään yksi kirjain.
    indeksi: dict[str, list[str]] = {}
    for sana, taajuus in maarat.items():
        if taajuus < minimi * suhde:
            continue
        indeksi.setdefault(sana, []).append(sana)
        for i in range(len(sana)):
            indeksi.setdefault(sana[:i] + sana[i + 1 :], []).append(sana)

    virheet = set()
    for sana, taajuus in maarat.items():
        if taajuus >= varma or sana in suojatut:
            continue
        ehdokkaat = set(indeksi.get(sana, ()))
        for i in range(len(sana)):
            ehdokkaat.update(indeksi.get(sana[:i] + sana[i + 1 :], ()))
        if any(
            maarat[e] >= taajuus * suhde and yhden_muokkauksen_paassa(sana, e)
            for e in ehdokkaat
        ):
            virheet.add(sana)
    return virheet


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("syote")
    p.add_argument("tulos")
    p.add_argument("--koodaus", default="latin-1")
    p.add_argument("--taajuus-sarake", type=int, default=1)
    p.add_argument("--sana-sarake", type=int, default=2)
    p.add_argument("--min", type=int, default=3)
    p.add_argument("--varma", type=int, default=17)
    p.add_argument("--suhde", type=int, default=100)
    p.add_argument("--kotus")
    p.add_argument("--kotus-taajuus", type=int, default=2)
    a = p.parse_args()

    maarat: dict[str, int] = {}
    with open(a.syote, encoding=a.koodaus) as f:
        for rivi in f:
            osat = rivi.split()
            if len(osat) <= max(a.sana_sarake, a.taajuus_sarake):
                continue
            sana = osat[a.sana_sarake].strip().lower()
            try:
                taajuus = int(osat[a.taajuus_sarake])
            except ValueError:
                continue
            if taajuus < 1 or not SANA.match(sana):
                continue
            # Sama muoto voi esiintyä eri kirjainkoolla; taajuudet yhdistetään.
            maarat[sana] = maarat.get(sana, 0) + taajuus
    maarat = {s: t for s, t in maarat.items() if t >= a.min}

    perusmuodot: set[str] = set()
    if a.kotus:
        with open(a.kotus, encoding="utf-8") as f:
            next(f)  # otsikkorivi
            for rivi in f:
                sana = rivi.split("\t", 1)[0].strip().lower()
                if sana and SANA.match(sana):
                    perusmuodot.add(sana)

    virheet = todennakoiset_virheet(maarat, perusmuodot, a.varma, a.suhde, a.min)
    sanat = {s: t for s, t in maarat.items() if s not in virheet}

    # Kotuksen perusmuodot täydentävät sanastoa pienellä oletustaajuudella;
    # ne jäävät yleisyydeltään tunnettujen sanojen taakse.
    lisatyt = 0
    for sana in perusmuodot:
        if sana not in sanat:
            sanat[sana] = a.kotus_taajuus
            lisatyt += 1

    with open(a.tulos, "w", encoding="utf-8", newline="\n") as f:
        for sana in sorted(sanat):
            f.write(f"{sana} {sanat[sana]}\n")
    print(
        f"{len(sanat)} sanaa kirjoitettu "
        f"({lisatyt} Kotuksen listalta, {len(virheet)} todennäköistä virhettä pois)"
    )


if __name__ == "__main__":
    main()
