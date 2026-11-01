#!/usr/bin/env python3
"""Hakee sovelluksen kuvakkeet Googlen Material Symbols -kokoelmasta (Rounded,
paino 400, 24 dp; Apache 2.0) ja kirjoittaa ne res/drawable-kansioon.

Väri tulee aina koodista (setTint/setColorFilter/asetusrivin tyyli), joten
kokoelman ?attr-sävytys poistetaan; näppäimistöpalvelun teema ei tunne sitä.

Käyttö projektin juuresta: python tools/kuvakkeet.py
"""
import re
import urllib.request

# Oma nimi -> (Material Symbols -nimi, täytetty)
ICONS = {
    'ic_add': ('add', False),
    'ic_arrow_back': ('arrow_back', False),
    'ic_arrow_down': ('arrow_downward', False),
    'ic_arrow_left': ('arrow_back', False),
    'ic_arrow_right': ('arrow_forward', False),
    'ic_arrow_up': ('arrow_upward', False),
    'ic_back': ('arrow_back', False),
    'ic_backspace': ('backspace', False),
    'ic_book': ('book_2', False),
    'ic_check': ('check', False),
    'ic_clipboard': ('content_paste', False),
    'ic_close': ('close', False),
    'ic_delete': ('delete', False),
    'ic_dock_right': ('dock_to_right', False),
    'ic_download': ('download', False),
    'ic_drag_handle': ('drag_handle', False),
    'ic_emoji': ('mood', False),
    'ic_enter': ('keyboard_return', False),
    'ic_fullscreen': ('fullscreen', False),
    'ic_link': ('link', False),
    'ic_history': ('history', False),
    'ic_info': ('info', False),
    'ic_keyboard': ('keyboard', False),
    'ic_lightbulb': ('lightbulb', False),
    'ic_mic': ('mic', False),
    'ic_more_vert': ('more_vert', False),
    'ic_palette': ('palette', False),
    'ic_pin_filled': ('keep', True),
    'ic_pin_outline': ('keep', False),
    'ic_school': ('school', False),
    'ic_search': ('search', False),
    'ic_settings': ('settings', False),
    'ic_shift': ('shift', False),
    'ic_shift_on': ('shift', True),
    'ic_shift_caps': ('shift_lock', True),
    'ic_space_bar': ('space_bar', False),
    'ic_sparkle': ('auto_awesome', False),
    'ic_spellcheck': ('spellcheck', False),
    'ic_swap': ('swap_horiz', False),
    'ic_tag': ('tag', False),
    'ic_translate': ('translate', False),
    'ic_tune': ('tune', False),
    'ic_undo': ('undo', False),
    'ic_upload': ('upload', False),
    'ic_vibration': ('vibration', False),
    'ic_visibility': ('visibility', False),
    'ic_volume': ('volume_up', False),
    'ic_web_search': ('travel_explore', False),
}
BASE = 'https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android'


def fetch(symbol, filled):
    suffix = '_fill1_24px' if filled else '_24px'
    url = f'{BASE}/{symbol}/materialsymbolsrounded/{symbol}{suffix}.xml'
    with urllib.request.urlopen(url) as response:
        return response.read().decode('utf-8')


def main():
    for name, (symbol, filled) in ICONS.items():
        xml = re.sub(r'\s*android:tint="[^"]*"', '', fetch(symbol, filled))
        with open(f'app/src/main/res/drawable/{name}.xml', 'w', encoding='utf-8', newline='\n') as out:
            out.write(xml)
    print(len(ICONS), 'kuvaketta')


if __name__ == '__main__':
    main()
