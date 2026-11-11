#!/usr/bin/env python3
r"""
Generates an encrypted Prima Barcode login QR code.

The app decrypts these with `loginQrKey` from its external system configuration — shipped in
the bundled assets/ext_system_defaults_*.json and stored on the device once a configuration is
loaded. The same key must be passed here, so a code only works on devices carrying that key.
Rotating means putting a new key in the configuration, distributing it (Load built-in defaults
or Import from file), and reissuing every code; no new APK is needed.

    # one-off, prints the payload to paste into any QR generator
    python make_login_qr.py --user 'PRIMA-COMMERCE\filip' --key "$KEY"

    # writes filip.png directly, with the user name under the code (needs: pip install qrcode[pil])
    python make_login_qr.py --user 'PRIMA-COMMERCE\filip' --key "$KEY" --out filip.png

    # the same with a caption of your choice; --name "" leaves the image without one
    python make_login_qr.py --user 'PRIMA-COMMERCE\filip' --key "$KEY" --out filip.png --name "Ana Kovačić"

    # generate a fresh key to put in the configuration's "loginQrKey"
    python make_login_qr.py --new-key

One backslash between domain and name: inside single quotes, bash and PowerShell pass it as typed.

The password is prompted for rather than passed as an argument, so it doesn't end up in shell
history or in the process list where anyone on the machine could read it.

Requires: pip install cryptography
"""

import argparse
import base64
import getpass
import json
import os
import sys

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    sys.exit("Missing dependency. Run: pip install cryptography")

IV_BYTES = 12
KEY_BYTES = 32

# Pixels per module and quiet-zone width in modules — the same as tools/login_qr.html.
BOX = 8
BORDER = 4

# The caption under the code follows login_qr.html: semibold, as large as 4 modules, down to 2,
# on at most two lines as wide as the code itself. It goes below the quiet zone, never into it:
# those four light modules around the code are what a scanner uses to find its edge.
CAPTION_MAX = 4 * BOX
CAPTION_MIN = 2 * BOX
CAPTION_MAX_LINES = 2

# Tried in order by file name; Pillow looks them up in the system font folders. Every one of them
# has the Croatian letters (č ć đ š ž).
CAPTION_FONTS = ("seguisb.ttf", "segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf",
                 "LiberationSans-Bold.ttf", "Arial Bold.ttf")


def new_key() -> str:
    return base64.b64encode(os.urandom(KEY_BYTES)).decode()


def encrypt(username: str, password: str, key_b64: str) -> str:
    key = base64.b64decode(key_b64)
    if len(key) != KEY_BYTES:
        sys.exit(f"Key must be base64 for exactly {KEY_BYTES} bytes (got {len(key)}).")
    # separator=(',', ':') keeps the payload compact, which keeps the QR less dense
    plaintext = json.dumps(
        {"username": username, "password": password}, separators=(",", ":")
    ).encode()
    iv = os.urandom(IV_BYTES)
    ciphertext = AESGCM(key).encrypt(iv, plaintext, None)
    return base64.b64encode(iv + ciphertext).decode()


def caption_font_name() -> str:
    from PIL import ImageFont
    for name in CAPTION_FONTS:
        try:
            ImageFont.truetype(name, CAPTION_MIN)
            return name
        except OSError:
            continue
    sys.exit("No font found for the caption; pass --name \"\" to write the code without one.")


def wrap_words(draw, text: str, font, max_width: int) -> list:
    """Greedy word wrap. A single word wider than the line is left whole; the caller sees that it
    does not fit and tries a smaller size."""
    lines, line = [], ""
    for word in text.split():
        candidate = f"{line} {word}" if line else word
        if not line or draw.textlength(candidate, font=font) <= max_width:
            line = candidate
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def wrap_chars(draw, text: str, font, max_width: int) -> list:
    """Last resort, for a caption too wide even at the smallest size: break wherever a line is full."""
    lines, line = [], ""
    for ch in text:
        if line and draw.textlength(line + ch, font=font) > max_width:
            lines.append(line.rstrip())
            line = ch if ch.strip() else ""
        else:
            line += ch
    if line:
        lines.append(line)
    return lines


def add_caption(code, text: str):
    """Returns the code with `text` written under its quiet zone, centred, in black on white."""
    from PIL import Image, ImageDraw, ImageFont

    code = code.convert("L")  # grayscale, so the text can be anti-aliased
    side = code.width
    max_width = side - 2 * BORDER * BOX  # as wide as the code, quiet zone excluded
    probe = ImageDraw.Draw(code)
    font_name = caption_font_name()

    size, font, lines = CAPTION_MIN, None, None
    for candidate in range(CAPTION_MAX, CAPTION_MIN - 1, -2):
        candidate_font = ImageFont.truetype(font_name, candidate)
        candidate_lines = wrap_words(probe, text, candidate_font, max_width)
        fits = all(probe.textlength(l, font=candidate_font) <= max_width for l in candidate_lines)
        if fits and len(candidate_lines) <= CAPTION_MAX_LINES:
            size, font, lines = candidate, candidate_font, candidate_lines
            break
    if font is None:
        font = ImageFont.truetype(font_name, CAPTION_MIN)
        lines = wrap_chars(probe, text, font, max_width)

    line_height = round(size * 1.25)
    out = Image.new("L", (side, side + len(lines) * line_height + BORDER * BOX), 255)
    out.paste(code, (0, 0))
    draw = ImageDraw.Draw(out)
    for i, line in enumerate(lines):
        width = draw.textlength(line, font=font)
        draw.text(((side - width) / 2, side + i * line_height), line, fill=0, font=font)
    return out


def main() -> None:
    # The help text and a caption may carry č, ć or đ. A console or a redirect in a legacy code
    # page cannot print them, and that is no reason to fail — least of all after the PNG is written.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(errors="replace")

    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--new-key", action="store_true", help="print a fresh key and exit")
    ap.add_argument("--user", help="username, including domain if used (DOMAIN\\user or user@domain)")
    ap.add_argument("--key", help="base64 AES-256 key; falls back to $PRIMA_QR_KEY")
    ap.add_argument("--out", help="write a PNG here instead of printing the payload")
    ap.add_argument("--name", help="caption under the code in the PNG; defaults to --user, \"\" for none."
                                   " Not part of the code itself")
    args = ap.parse_args()

    if args.new_key:
        print(new_key())
        return

    if not args.user:
        ap.error("--user is required (or use --new-key)")
    # The same check as login_qr.html. A doubled backslash is how an escaped example from a source
    # file reads when it is copied as it stands; NAV knows no such user, and the code would fail
    # at sign-in with nothing to say why.
    if "\\\\" in args.user:
        ap.error("the user name has two backslashes in a row; a real one has one, e.g. PRIMA-COMMERCE\\filip")

    key_b64 = args.key or os.environ.get("PRIMA_QR_KEY")
    if not key_b64:
        ap.error("no key: pass --key or set PRIMA_QR_KEY")

    password = getpass.getpass(f"Password for {args.user}: ")
    if not password:
        sys.exit("Password must not be empty — the app rejects codes missing either field.")

    payload = encrypt(args.user, password, key_b64)

    if not args.out:
        print(payload)
        return

    try:
        import qrcode
    except ImportError:
        sys.exit("PNG output needs: pip install 'qrcode[pil]'  (or drop --out to print the payload)")

    # High error correction: these get printed, taped up and scanned in warehouse lighting.
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_H, box_size=BOX, border=BORDER)
    qr.add_data(payload)
    qr.make(fit=True)
    image = qr.make_image(fill_color="black", back_color="white").get_image()

    # Like the page's "Ime za prikaz": the user name unless told otherwise, nothing when told "".
    caption = (args.user if args.name is None else args.name).strip()
    if caption:
        image = add_caption(image, caption)
    image.save(args.out)
    # The caption as typed, not repr(): repr would show PRIMA-COMMERCE\filip with two backslashes.
    print(f"Wrote {args.out} ({len(payload)} chars, QR version {qr.version}"
          + (f', caption "{caption}")' if caption else ", no caption)"))


if __name__ == "__main__":
    main()
