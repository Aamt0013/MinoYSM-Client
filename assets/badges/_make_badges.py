"""生成自托管的 README 徽章 PNG。

为什么不用 shields.io：img.shields.io 在部分网络环境下不可达，README 里的徽章会
全部显示为破图。改成仓库内静态图片后，渲染不依赖任何外部服务。

输出为 2 倍尺寸（高 40px），README 里用 height="20" 显示，兼顾清晰度与尺寸。
"""
from PIL import Image, ImageDraw, ImageFont
import os

OUT = r"D:\mods\YSM\OpenYSM-Updated\release\assets\badges"
os.makedirs(OUT, exist_ok=True)

SCALE = 2
HEIGHT = 20 * SCALE
FONT_SIZE = 11 * SCALE
PAD = 6 * SCALE
RADIUS = 3 * SCALE

LATIN = r"C:\Windows\Fonts\segoeui.ttf"
LATIN_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
CJK = r"C:\Windows\Fonts\msyh.ttc"

GREY = (85, 85, 85)

BADGES = [
    ("minecraft", "Minecraft", "26.2",     (98, 180, 122), LATIN, LATIN),
    ("fabric",    "Fabric",    "0.19.3+",  (59, 130, 246), LATIN, LATIN),
    ("java",      "Java",      "25",       (254, 125, 55), LATIN, LATIN),
    ("license",   "License",   "Apache-2.0", (59, 130, 246), LATIN, LATIN),
    ("download",  "下载",       "Latest",   (240, 80, 51),  CJK,   LATIN),
]


def measure(draw, text, font):
    box = draw.textbbox((0, 0), text, font=font)
    return box[2] - box[0], box[3] - box[1], box[1]


def make(name, left, right, color, left_font_path, right_font_path):
    lf = ImageFont.truetype(left_font_path, FONT_SIZE)
    rf = ImageFont.truetype(right_font_path, FONT_SIZE)

    probe = Image.new("RGBA", (10, 10))
    pd = ImageDraw.Draw(probe)
    lw = measure(pd, left, lf)[0] + PAD * 2
    rw = measure(pd, right, rf)[0] + PAD * 2
    total = lw + rw

    img = Image.new("RGBA", (int(total), HEIGHT), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 圆角底：先画整体圆角矩形，再左右分段填色
    d.rounded_rectangle([0, 0, total - 1, HEIGHT - 1], radius=RADIUS, fill=GREY)
    d.rectangle([lw, 0, total - 1, HEIGHT - 1], fill=color)
    # 右侧两角补圆角
    d.rounded_rectangle([lw, 0, total - 1, HEIGHT - 1], radius=RADIUS, fill=color)
    d.rectangle([lw, 0, lw + RADIUS, HEIGHT - 1], fill=color)

    def draw_text(x0, w, text, font):
        tw, th, ty = measure(d, text, font)
        d.text((x0 + (w - tw) / 2, (HEIGHT - th) / 2 - ty), text, font=font, fill=(255, 255, 255, 255))

    draw_text(0, lw, left, lf)
    draw_text(lw, rw, right, rf)

    path = os.path.join(OUT, f"{name}.png")
    img.save(path, "PNG", optimize=True)
    print(f"{name}.png  {img.width}x{img.height}  {os.path.getsize(path)} bytes")


for b in BADGES:
    make(*b)
