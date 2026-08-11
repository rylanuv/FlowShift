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

# Increase to zoom in (e.g. 1.2), decrease to zoom out
ZOOM_FACTOR = 1.12 

# Manual offsets to adjust visual centering (in pixels of the original image)
# Positive Y_OFFSET shifts the logo UP; negative shifts it DOWN.
# Positive X_OFFSET shifts the logo LEFT; negative shifts it RIGHT.
X_OFFSET = 0
Y_OFFSET = -9


def center_and_crop_icon(img):
    """Finds the center of the orange sign and crops a perfect square centered on it,
    completely removing the outer white background/border."""
    img = img.convert("RGBA")
    data = np.array(img)
    r, g, b, a = data[:, :, 0], data[:, :, 1], data[:, :, 2], data[:, :, 3]
    
    # Detect orange pixels of the central logo
    orange_mask = (r > 180) & (g > 80) & (g < 180) & (b < 80) & (a > 0)
    y_indices, x_indices = np.where(orange_mask)
    
    if len(x_indices) == 0:
        print("Warning: No orange pixels detected, falling back to default centering.")
        w, h = img.size
        cx, cy = w / 2, h / 2
        # Default fallback bounds
        min_dist = min(cx, cy) - 50
    else:
        # Calculate center of the orange logo
        cx = (np.min(x_indices) + np.max(x_indices)) / 2.0
        cy = (np.min(y_indices) + np.max(y_indices)) / 2.0
        print(f"Detected Orange Sign center at: ({cx:.2f}, {cy:.2f})")
        
        # Apply manual offsets for visual correction
        cx += X_OFFSET
        cy += Y_OFFSET
        print(f"Applying offsets ({X_OFFSET}, {Y_OFFSET}) -> Adjusted center: ({cx:.2f}, {cy:.2f})")
        
        # Detect the foreground boundary (non-white/non-transparent)
        bg_mask = (r > 230) & (g > 230) & (b > 230)
        fg_mask = ~bg_mask & (a > 0)
        y_fg, x_fg = np.where(fg_mask)
        
        if len(x_fg) > 0:
            # Distance from adjusted center to each edge of the icon background
            dist_left = cx - np.min(x_fg)
            dist_right = np.max(x_fg) - cx
            dist_top = cy - np.min(y_fg)
            dist_bottom = np.max(y_fg) - cy
            
            # Use the minimum distance to ensure a perfect square stays within bounds
            min_dist = min(dist_left, dist_right, dist_top, dist_bottom)
        else:
            min_dist = min(cx, cy) - 50

    # Define crop box centered on adjusted (cx, cy)
    left = int(cx - min_dist)
    top = int(cy - min_dist)
    right = int(cx + min_dist)
    bottom = int(cy + min_dist)
    
    print(f"Cropping square from [{left}, {top}] to [{right}, {bottom}] (Size: {right-left}x{bottom-top})")
    cropped = img.crop((left, top, right, bottom))
    return cropped


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

    print("Centering and cropping icon based on central logo...")
    img = center_and_crop_icon(img)
    print(f"After centering & crop: {img.size}")

    if ZOOM_FACTOR > 1.0:
        print(f"Applying zoom factor of {ZOOM_FACTOR}...")
        img = zoom_image(img, ZOOM_FACTOR)
        print(f"After zoom: {img.size}")

    # Flatten onto black for the final icons (no transparency artifacts)
    img_flat = flatten_on_black(img)

    # Generate launcher icons
    print("\n--- Generating launcher icons ---")
    for folder, size in LAUNCHER_SIZES.items():
        folder_path = os.path.join(RES_DIR, folder)
        os.makedirs(folder_path, exist_ok=True)

        resized = img_flat.resize((size, size), Image.LANCZOS)

        launcher_path = os.path.join(folder_path, "ic_launcher.webp")
        resized.save(launcher_path, "WEBP", quality=90)
        print(f"  OK {launcher_path} ({size}x{size})")

        round_path = os.path.join(folder_path, "ic_launcher_round.webp")
        resized.save(round_path, "WEBP", quality=90)
        print(f"  OK {round_path} ({size}x{size})")

    # Generate foreground icons for adaptive icons
    print("\n--- Generating adaptive foreground icons ---")
    for folder, size in FOREGROUND_SIZES.items():
        folder_path = os.path.join(RES_DIR, folder)
        os.makedirs(folder_path, exist_ok=True)

        fg = create_foreground(img_flat, size)

        fg_path = os.path.join(folder_path, "ic_launcher_foreground.webp")
        fg.save(fg_path, "WEBP", quality=90)
        print(f"  OK {fg_path} ({size}x{size})")

    print("\nAll icons generated successfully!")


if __name__ == "__main__":
    main()
