#!/usr/bin/env python3
import argparse
import qrcode
from qrcode.constants import ERROR_CORRECT_L, ERROR_CORRECT_M, ERROR_CORRECT_Q, ERROR_CORRECT_H
from qrcode.image.styledpil import StyledPilImage
from qrcode.image.styles.moduledrawers import (
    SquareModuleDrawer,
    GappedSquareModuleDrawer,
    CircleModuleDrawer,
    RoundedModuleDrawer,
    VerticalBarsDrawer,
    HorizontalBarsDrawer,
)

# Map numeric error correction levels to qrcode constants
EC_LEVELS = {
    0: ERROR_CORRECT_L,
    1: ERROR_CORRECT_M,
    2: ERROR_CORRECT_Q,
    3: ERROR_CORRECT_H,
}

# Map string styles to module drawers
MODULE_DRAWERS = {
    "square": SquareModuleDrawer(),
    "gapped_square": GappedSquareModuleDrawer(),
    "circle": CircleModuleDrawer(),
    "rounded": RoundedModuleDrawer(),
    "vertical": VerticalBarsDrawer(),
    "horizontal": HorizontalBarsDrawer(),
}

def main():
    parser = argparse.ArgumentParser(description="Generate a styled QR code image.")
    parser.add_argument("-i", "--input", required=True, help="Input text or URL for the QR code")
    parser.add_argument("-o", "--output", required=True, help="Output file path (e.g., qr.png)")
    parser.add_argument(
        "-ec", "--error_correction", type=int, default=1, choices=range(0, 4),
        help="Error correction level: 0=L, 1=M, 2=Q, 3=H (default: 1)"
    )
    parser.add_argument("-cf", "--fill_color", default="black", help="Color of the QR code dots (default: black)")
    parser.add_argument("-cb", "--back_color", default="white", help="Background color (default: white)")
    parser.add_argument("-b", "--border", type=int, default=4, help="Border size in boxes (default: 4)")
    parser.add_argument("-s", "--target_size", type=int, default=300,
                        help="Target image width in pixels (auto-scales box size, default: 300px)")
    parser.add_argument("-st", "--style", choices=MODULE_DRAWERS.keys(), default="square",
                        help="QR module style: square, gapped_square, circle, rounded, vertical, horizontal")

    args = parser.parse_args()

    # Step 1: Create a temporary QR object to determine version and module count
    temp_qr = qrcode.QRCode(
        error_correction=EC_LEVELS[args.error_correction],
        box_size=1,  # temporary minimal size for calculation
        border=args.border,
    )
    temp_qr.add_data(args.input)
    temp_qr.make(fit=True)

    # Step 2: Get exact number of modules (without border)
    version = temp_qr.version
    modules_count = temp_qr.modules_count
    total_modules = modules_count + 2 * args.border

    # Step 3: Compute the ideal box size
    box_size = max(1, args.target_size // total_modules)

    # Step 4: Build the final QR object with the calculated box_size
    qr = qrcode.QRCode(
        version=version,
        error_correction=EC_LEVELS[args.error_correction],
        box_size=box_size,
        border=args.border,
    )
    qr.add_data(args.input)
    qr.make(fit=False)

    # Step 5: Generate styled image
    img = qr.make_image(
        image_factory=StyledPilImage,
        module_drawer=MODULE_DRAWERS[args.style],
        fill_color=args.fill_color,
        back_color=args.back_color
    )
    img.save(args.output)

    # Step 6: Debug info
    final_size = total_modules * box_size
    print(f"✅ QR saved to {args.output}")
    print(f"Style used: {args.style}")
    print(f"Calculated image size: {final_size}px (target: {args.target_size}px)")

if __name__ == "__main__":
    main()
