from PIL import Image
import numpy as np
import os

SOURCE = r"e:\Apps\FlowShift - Stop Scrolling\test_out.png"
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

ZOOM_FACTOR = 1.12
X_OFFSET = 0
Y_OFFSET = -9

def center_and_crop_icon(img):
    img = img.convert("RGBA")
    data = np.array(img)
    r, g, b, a = data[:, :, 0], data[:, :, 1], data[:, :, 2], data[:, :, 3]
    
    orange_mask = (r > 180) & (g > 80) & (g < 180) & (b < 80) & (a > 0)
    y_indices, x_indices = np.where(orange_mask)
    
    if len(x_indices) == 0:
        w, h = img.size
        cx, cy = w / 2, h / 2
        min_dist = min(cx, cy) - 50
    else:
        cx = (np.min(x_indices) + np.max(x_indices)) / 2.0
        cy = (np.min(y_indices) + np.max(y_indices)) / 2.0
        cx += X_OFFSET
        cy += Y_OFFSET
        
        bg_mask = (r > 230) & (g > 230) & (b > 230)
        fg_mask = ~bg_mask & (a > 0)
        y_fg, x_fg = np.where(fg_mask)
        
        if len(x_fg) > 0:
            dist_left = cx - np.min(x_fg)
            dist_right = np.max(x_fg) - cx
            dist_top = cy - np.min(y_fg)
            dist_bottom = np.max(y_fg) - cy
            min_dist = min(dist_left, dist_right, dist_top, dist_bottom)
        else:
            min_dist = min(cx, cy) - 50

    left = int(cx - min_dist)
    top = int(cy - min_dist)
    right = int(cx + min_dist)
    bottom = int(cy + min_dist)
    
    cropped = img.crop((left, top, right, bottom))
    return cropped

def zoom_image(img, factor):
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

def create_foreground(img, target_size):
    content_size = int(target_size * 72 / 108)
    content = img.resize((content_size, content_size), Image.LANCZOS)
    canvas = Image.new("RGBA", (target_size, target_size), (0, 0, 0, 0))
    offset = (target_size - content_size) // 2
    canvas.paste(content, (offset, offset), content)
    return canvas

def main():
    print(f"Loading icon from: {SOURCE}")
    img = Image.open(SOURCE)
    
    # We want to remove the white background to make it transparent
    img = img.convert("RGBA")
    data = np.array(img)
    r, g, b, a = data[:, :, 0], data[:, :, 1], data[:, :, 2], data[:, :, 3]
    white_mask = (r > 240) & (g > 240) & (b > 240)
    data[white_mask, 3] = 0 # set alpha to 0 for white background pixels
    img = Image.fromarray(data)

    img = center_and_crop_icon(img)
    if ZOOM_FACTOR > 1.0:
        img = zoom_image(img, ZOOM_FACTOR)

    for folder, size in LAUNCHER_SIZES.items():
        folder_path = os.path.join(RES_DIR, folder)
        os.makedirs(folder_path, exist_ok=True)
        resized = img.resize((size, size), Image.LANCZOS)
        
        # for legacy icons, let's keep them transparent but maybe we want black background? 
        # Wait, if adaptive icons are used, legacy icons are barely seen. We can just keep them transparent.
        # But wait, legacy icons often need a black background if the app theme expects it. Let's create an opaque version for legacy icons just in case, but actually transparent is usually better or standard. Let's make it opaque black just like before for legacy, but transparent for foreground.
        bg = Image.new("RGBA", resized.size, (0, 0, 0, 255))
        bg.paste(resized, (0, 0), resized)
        bg = bg.convert("RGB")

        launcher_path = os.path.join(folder_path, "ic_launcher.webp")
        bg.save(launcher_path, "WEBP", quality=90)

        round_path = os.path.join(folder_path, "ic_launcher_round.webp")
        bg.save(round_path, "WEBP", quality=90)
        print(f"  Legacy icons OK ({size}x{size})")

    for folder, size in FOREGROUND_SIZES.items():
        folder_path = os.path.join(RES_DIR, folder)
        os.makedirs(folder_path, exist_ok=True)
        fg = create_foreground(img, size)
        fg_path = os.path.join(folder_path, "ic_launcher_foreground.webp")
        fg.save(fg_path, "WEBP", quality=90)
        print(f"  Foreground OK {fg_path} ({size}x{size})")

    print("\nAll icons generated successfully!")

if __name__ == "__main__":
    main()
