# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow>=10"]
# ///
"""Derive the Google Play and landing-page images from the fastlane ones.

fastlane/metadata/android/en-US/images/ is the single source of truth (F-Droid and
IzzyOnDroid read it as-is). Google Play is stricter: screenshots may not be more than
twice as long as they are wide and may not have an alpha channel, so the 20:9 captures
are padded to 9:16 with the app's own background color and flattened. The landing page
gets small WebP copies.

Run from the repo root after replacing the fastlane screenshots:
    uv run Distribution/derive_screenshots.py
"""
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
FASTLANE = ROOT / "fastlane/metadata/android/en-US/images"
PLAY = ROOT / "App/app/src/play/play/listings/en-US/graphics"
DOCS = ROOT / "docs/assets/screenshots"
DOCS_WIDTH = 540


def edge_color(img: Image.Image) -> tuple[int, int, int]:
    """Most common color in the leftmost columns, i.e. the app's background."""
    strip = img.crop((0, 0, 4, img.height))
    return max(strip.getcolors(strip.width * strip.height))[1]


def pad_to_9_16(img: Image.Image) -> Image.Image:
    width = max(img.width, -(-img.height * 9 // 16))
    height = max(img.height, -(-img.width * 16 // 9))
    canvas = Image.new("RGB", (width, height), edge_color(img))
    canvas.paste(img, ((width - img.width) // 2, (height - img.height) // 2))
    return canvas


def reset_dir(path: Path, pattern: str) -> None:
    path.mkdir(parents=True, exist_ok=True)
    for old in path.glob(pattern):
        old.unlink()


def main() -> None:
    shots = sorted((FASTLANE / "phoneScreenshots").glob("*.png"))
    reset_dir(PLAY / "phone-screenshots", "*.png")
    reset_dir(DOCS, "*")
    for shot in shots:
        img = Image.open(shot).convert("RGB")  # flattens alpha
        play = pad_to_9_16(img)
        play.save(PLAY / "phone-screenshots" / shot.name, optimize=True)
        small = img.resize((DOCS_WIDTH, round(img.height * DOCS_WIDTH / img.width)), Image.LANCZOS)
        small.save(DOCS / f"{shot.stem}.webp", quality=85, method=6)
        print(f"{shot.name}: play {play.size}, docs {small.size}")

    for folder, name in (("icon", "icon.png"), ("feature-graphic", "featureGraphic.png")):
        reset_dir(PLAY / folder, "*")
        shutil.copy2(FASTLANE / name, PLAY / folder / name)
    # The landing page keeps its own copies of the icon and feature graphic.
    shutil.copy2(FASTLANE / "icon.png", ROOT / "docs/assets/icon.png")
    shutil.copy2(FASTLANE / "featureGraphic.png", ROOT / "docs/assets/feature.png")


if __name__ == "__main__":
    main()
