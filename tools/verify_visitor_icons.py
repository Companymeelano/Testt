#!/usr/bin/env python3
"""Check the local SVG source and Android vector copies remain in sync."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
SVG = '{http://www.w3.org/2000/svg}'

for name in ('visit', 'refresh', 'call', 'route', 'next', 'customer', 'product', 'cart', 'result', 'toolbox'):
    svg = ET.parse(ROOT / 'design/icons/visitor' / f'{name}.svg').getroot()
    vector = ET.parse(ROOT / 'app/src/main/res/drawable' / f'visitor_action_{name}.xml').getroot()
    source = [(p.attrib['d'], p.attrib['opacity']) for p in svg.findall(SVG + 'path')]
    target = [(p.attrib[ANDROID + 'pathData'], p.attrib[ANDROID + 'fillAlpha']) for p in vector.findall('path')]
    assert source and source == target, f'{name}: SVG and vector differ'
    assert vector.attrib.get(ANDROID + 'autoMirrored', 'false') == ('true' if name == 'next' else 'false'), name
    print(f'{name}: SVG and vector match')
