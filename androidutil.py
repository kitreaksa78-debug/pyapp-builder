#!/usr/bin/env python3
"""Helper សម្រាប់ project Android (Kotlin) ដែល PyApp Builder បង្កើត។

- `xml_escape` — escape អត្ថបទសម្រាប់ XML resource
- `launcher_icons` — កាត់/resize រូប logo ជា mipmap គ្រប់ density
"""
from __future__ import annotations

import io

ICON_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
MAX_ICON_BYTES = 5 * 1024 * 1024


def xml_escape(text: str) -> str:
    return (str(text).replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace('"', "&quot;"))


def launcher_icons(data: bytes) -> dict:
    """បម្លែងរូបភាព (PNG/JPG/WebP) ទៅ mipmap គ្រប់ density → {path: bytes}។

    រូបត្រូវកាត់ជាការ៉េ (center crop) ហើយ resize ជា PNG តម្លាភាព។
    """
    from PIL import Image, UnidentifiedImageError

    if not data:
        raise ValueError("គ្មានរូបភាព logo")
    if len(data) > MAX_ICON_BYTES:
        raise ValueError("រូប logo ធំពេក (អតិបរមា 5 MB)")
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
    except (UnidentifiedImageError, OSError) as exc:
        raise ValueError("អានរូប logo មិនបាន — សូមប្រើ PNG ឬ JPG") from exc

    img = img.convert("RGBA")
    width, height = img.size
    side = min(width, height)
    left = (width - side) // 2
    top = (height - side) // 2
    img = img.crop((left, top, left + side, top + side))

    icons = {}
    for density, px in ICON_SIZES.items():
        resized = img.resize((px, px), Image.LANCZOS)
        buf = io.BytesIO()
        resized.save(buf, format="PNG", optimize=True)
        blob = buf.getvalue()
        icons[f"app/src/main/res/mipmap-{density}/ic_launcher.png"] = blob
        icons[f"app/src/main/res/mipmap-{density}/ic_launcher_round.png"] = blob
    return icons
