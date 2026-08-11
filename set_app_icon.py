from PIL import Image
import os

SOURCE = r"E:\Apps\FlowShift addons\Icon_main00086400.png"
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
    """Create adaptive icon foreground with content in the safe zone."""
    content_size = int(target_size * 72 / 108)
    content = img_flat.resize((content_size, content_size), Image.LANCZOS)
    canvas = Image.new("RGB", (target_size, target_size), (0, 0, 0))
    offset = (target_size - content_size) // 2
    canvas.paste(content, (offset, offset))
    return canvas

def zoom_image(img, factor):
    """Zoom into the image from the center by the given factor."""
    if factor <= 1.0:
        return img
    w, h = img.size
    new_w = int(w / factor)
    new_h = int(h / factor)
    left = (w - new_w) // 2
    top = (h - new_h) // 2
    right = left + new_w
    bottom = top + new_h
    
    cropped = img.crop((left, top, right, bottom))
    return cropped.resize((w, h), Image.LANCZOS)

def main():
    print(f"Loading icon from: {SOURCE}")
    img = Image.open(SOURCE)
    
    img = img.convert("RGBA")
    img = make_square(img)
    
    # Apply a zoom factor (1.15 means zoom in by 15%)
    ZOOM_FACTOR = 1.15
    print(f"Applying zoom factor of {ZOOM_FACTOR}...")
    img = zoom_image(img, ZOOM_FACTOR)
    
    img_flat = flatten_on_black(img)
    
    for folder, size in LAUNCHER_SIZES.items():
        folder_path = os.path.join(RES_DIR, folder)
        os.makedirs(folder_path, exist_ok=True)
        resized = img_flat.resize((size, size), Image.LANCZOS)
        launcher_path = os.path.join(folder_path, "ic_launcher.webp")
        resized.save(launcher_path, "WEBP", quality=90)
        round_path = os.path.join(folder_path, "ic_launcher_round.webp")
        resized.save(round_path, "WEBP", quality=90)
        
    for folder, size in FOREGROUND_SIZES.items():
        folder_path = os.path.join(RES_DIR, folder)
        os.makedirs(folder_path, exist_ok=True)
        fg = create_foreground(img_flat, size)
        fg_path = os.path.join(folder_path, "ic_launcher_foreground.webp")
        fg.save(fg_path, "WEBP", quality=90)
        
    print("Icons successfully updated!")

if __name__ == "__main__":
    main()
