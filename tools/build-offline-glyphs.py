"""Generate local MapLibre SDF glyph ranges from the bundled licensed Inter font."""
import sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy.ndimage import distance_transform_edt

font_path, output = map(Path, sys.argv[1:])
font = ImageFont.truetype(str(font_path), 24)
output.mkdir(parents=True, exist_ok=True)


def varint(n):
    out = bytearray()
    while n > 127:
        out.append((n & 127) | 128)
        n >>= 7
    out.append(n)
    return bytes(out)


def integer(field, value, signed=False):
    if signed:
        value = value * 2 if value >= 0 else -value * 2 - 1
    return varint(field << 3) + varint(value)


def blob(field, value):
    if isinstance(value, str):
        value = value.encode()
    return varint((field << 3) | 2) + varint(len(value)) + value


# Latin, Latin extensions, general punctuation, and currency used in local names.
for start in (0, 256, 512, 768, 7680, 8192, 8448):
    start = start // 256 * 256
    glyphs = []
    for code in range(max(32, start), start + 256):
        ch = chr(code)
        left, top, right, bottom = font.getbbox(ch, anchor='ls')
        width, height = right - left, bottom - top
        bitmap = b''
        if width and height:
            canvas = Image.new('L', (width + 6, height + 6))
            ImageDraw.Draw(canvas).text((3 - left, 3 - top), ch, font=font, fill=255, anchor='ls')
            pixels = np.asarray(canvas) / 255.0
            inside = pixels >= .5
            if inside.any():
                distance = distance_transform_edt(inside) - distance_transform_edt(~inside)
                distance += pixels - .5
                bitmap = np.clip(255 * (.75 + distance / 8), 0, 255).astype('uint8').tobytes()
        glyph = integer(1, code) + blob(2, bitmap) + integer(3, width) + integer(4, height)
        # MapLibre's glyph baseline is calibrated to a 25px ascender at 24pt.
        glyph += integer(5, left, True) + integer(6, -top - 25, True) + integer(7, round(font.getlength(ch)))
        glyphs.append(blob(3, glyph))
    range_name = f'{start}-{start + 255}'
    stack = blob(1, 'Inter-Regular') + blob(2, range_name) + b''.join(glyphs)
    (output / f'{range_name}.pbf').write_bytes(blob(1, stack))
print('Generated offline Inter SDF glyphs')
