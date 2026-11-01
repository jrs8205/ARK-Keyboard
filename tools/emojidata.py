#!/usr/bin/env python3
"""Rakentaa emojivalitsimen aineiston app/src/main/assets/emoji.tsv.

Lähteet (Unicode License v3, ks. docs/emojit.md):
  https://unicode.org/Public/emoji/latest/emoji-test.txt
  https://github.com/unicode-org/cldr: common/annotations/fi.xml
  https://github.com/unicode-org/cldr: common/annotationsDerived/fi.xml

Käyttö:
  python tools/emojidata.py emoji-test.txt annotations/fi.xml annotationsDerived/fi.xml emoji.tsv

Rivi: kategoria <TAB> emoji <TAB> ihonvärit pilkuin <TAB> nimi <TAB> avainsanat |-merkein
"""
import html
import re
import sys

GROUPS = {
    'Smileys & Emotion': 0,
    'People & Body': 1,
    'Animals & Nature': 2,
    'Food & Drink': 3,
    'Travel & Places': 4,
    'Activities': 5,
    'Objects': 6,
    'Symbols': 7,
    'Flags': 8,
}
TONES = {chr(c) for c in range(0x1F3FB, 0x1F400)}
ANNOTATION = re.compile(r'<annotation cp="([^"]+)"( type="tts")?>([^<]*)</annotation>')
TEST_LINE = re.compile(r'^([0-9A-F ]+?)\s*;\s*fully-qualified\s*#')


def key(emoji):
    return emoji.replace('️', '')


def read_emoji(path):
    """Täysin määritellyt emojit ryhmineen tiedoston järjestyksessä."""
    group = None
    entries = []
    with open(path, encoding='utf-8') as source:
        for line in source:
            if line.startswith('# group:'):
                group = GROUPS.get(line.split(':', 1)[1].strip())
                continue
            match = TEST_LINE.match(line)
            if match and group is not None:
                entries.append((group, ''.join(chr(int(c, 16)) for c in match[1].split())))
    return entries


def read_annotations(paths):
    names, keywords = {}, {}
    for path in paths:
        with open(path, encoding='utf-8') as source:
            for cp, tts, text in ANNOTATION.findall(source.read()):
                text = html.unescape(text).strip()
                if tts:
                    names.setdefault(key(cp), text)
                else:
                    keywords.setdefault(key(cp), [k.strip() for k in text.split('|')])
    return names, keywords


def main(test_path, annotations_path, derived_path, out_path):
    names, keywords = read_annotations([annotations_path, derived_path])
    bases, variants = [], {}
    for group, emoji in read_emoji(test_path):
        tones = [c for c in emoji if c in TONES]
        if not tones:
            bases.append((group, emoji))
            continue
        # Useamman hahmon emojeista vain saman ihonvärin yhdistelmät: 25
        # vaihtoehdon valikko ei mahtuisi näppäimistölle.
        if len(set(tones)) == 1:
            base = key(''.join(c for c in emoji if c not in TONES))
            variants.setdefault(base, []).append(emoji)
    written = 0
    with open(out_path, 'w', encoding='utf-8', newline='\n') as out:
        for group, emoji in bases:
            name = names.get(key(emoji))
            if not name:
                continue
            words = keywords.get(key(emoji), [])
            kept = [w.lower() for w in words if w and w.lower() != name.lower()]
            # Liput löytyvät maan nimellä, jota avainsanoissa ei ole.
            if name.startswith('lippu: '):
                kept.append(name.split(': ', 1)[1].lower())
            out.write('\t'.join([
                str(group),
                emoji,
                ','.join(variants.get(key(emoji), [])),
                name,
                '|'.join(kept),
            ]) + '\n')
            written += 1
    print(f'{written} emojia, {len(variants)} ihonvärillistä')


if __name__ == '__main__':
    main(*sys.argv[1:5])
