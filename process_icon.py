from PIL import Image
import numpy as np
import os
from scipy import ndimage

SOURCE = r"E:\Apps\FlowShift addons\iconnn.png"
RES_DIR = r"e:\Apps\FlowShift - Stop Scrolling\app\src\main\res"

LAUNCHER_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

FOREGROUND_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def remove_white_border(img):
    """Remove all white pixels from the image."""
    img = img.convert("RGBA")
    data = np.array(img)
    h, w = data.shape[:2]

    white_mask = (data[:, :, 0] > 230) & (data[:, :, 1] > 230) & (data[:, :, 2] > 230)

    # Make all white pixels transparent
    data[white_mask, 3] = 0

    result = Image.fromarray(data)
    bbox = result.getbbox()
    if bbox:
        result = result.crop(bbox)
    return result


def make_square(img):
    w, h = img.size
    if w == h:
        return img
    size = max(w, h)
    new_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    new_img.paste(img, ((size - w) // 2, (size - h) // 2))
    return new_img


def flatten_on_black(img):
    """Composite the RGBA image onto a black background, producing an opaque image."""
    bg = Image.new("RGBA", img.size, (0, 0, 0, 255))
    bg.paste(img, (0, 0), img)
    return bg.convert("RGB")


def create_foreground(img_flat, target_size):
    """Create adaptive icon foreground with content in the safe zone (inner 66.67%), 
    rest filled with black."""
    content_size = int(target_size * 72 / 108)
    content = img_flat.resize((content_size, content_size), Image.LANCZOS)

    # Black canvas (opaque)
    canvas = Image.new("RGB", (target_size, target_size), (0, 0, 0))
    offset = (target_size - content_size) // 2
    canvas.paste(content, (offset, offset))
    return canvas


def main():
    print(f"Loading icon from: {SOURCE}")
    img = Image.open(SOURCE)
    print(f"Original size: {img.size}")

    print("Removing white border...")
    img = remove_white_border(img)
    print(f"After border removal: {img.size}")

    img = make_square(img)
    print(f"After squaring: {img.size}")

    # Flatten onto black for the final icons (no transparency artifacts)
    img_flat = flatten_on_black(img)

    # Generate launcher icons
    for folder, size in LAUNCHER_SIZES.items():
        folder_path = os.path.join(RES_DIR, folder)
        os.makedirs(folder_path, exist_ok=True)

        resized = img_flat.resize((size, size), Image.LANCZOS)

        launcher_path = os.path.join(folder_path, "ic_launcher.webp")
        resized.save(launcher_path, "WEBP", quality=90)
        print(f"  {launcher_path} ({size}x{size})")

        round_path = os.path.join(folder_path, "ic_launcher_round.webp")
        resized.save(round_path, "WEBP", quality=90)
        print(f"  {round_path} ({size}x{size})")

    # Generate foreground icons
    for folder, size in FOREGROUND_SIZES.items():
        folder_path = os.path.join(RES_DIR, folder)
        os.makedirs(folder_path, exist_ok=True)

        fg = create_foreground(img_flat, size)

        fg_path = os.path.join(folder_path, "ic_launcher_foreground.webp")
        fg.save(fg_path, "WEBP", quality=90)
        print(f"  {fg_path} ({size}x{size})")

    print("\nDone!")


if __name__ == "__main__":
    main()
