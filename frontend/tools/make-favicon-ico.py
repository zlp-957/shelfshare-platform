"""用标准库把 favicon 生成为 .ico（无 PIL / 无 sharp 环境下的兜底方案）。"""
import struct
import zlib

SIZE = 64
BG = (0x1b, 0x2a, 0x24, 255)
PAPER = (0xf2, 0xf7, 0xf4, 255)
AMBER = (0xdd, 0xa4, 0x5f, 255)
SEA = (0x4d, 0x8a, 0x7b, 255)


def in_round_rect(x, y, w, h, r):
    if x < 0 or y < 0 or x >= w or y >= h:
        return False
    if (x < r or x > w - r) and (y < r or y > h - r):
        cx = r if x < r else w - r
        cy = r if y < r else h - r
        return (x - cx) ** 2 + (y - cy) ** 2 <= r * r
    return True


def in_poly(x, y, pts):
    inside = False
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        if (y1 > y) != (y2 > y):
            if x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                inside = not inside
    return inside


left_page = [(31, 22.5), (15.5, 19.7), (15.5, 45.3), (31, 48.1)]
right_page = [(33, 22.5), (48.5, 19.7), (48.5, 45.3), (33, 48.1)]

rows = []
for y in range(SIZE):
    row = []
    for x in range(SIZE):
        px, py = x + 0.5, y + 0.5
        color = (0, 0, 0, 0)
        if in_round_rect(px, py, SIZE, SIZE, 14):
            color = BG
            # 书脊下方那道水平线（先画，再被书页覆盖）
            if 48.5 <= py <= 50.5 and 17 <= px <= 47:
                color = tuple(int(SEA[i] * 0.55 + BG[i] * 0.45) for i in range(3)) + (255,)
            if in_poly(px, py, left_page):
                color = PAPER
            if in_poly(px, py, right_page):
                color = AMBER
            # 书脊
            if 31 <= px <= 33 and 21.5 <= py <= 48.5:
                color = BG
        row.append(color)
    rows.append(row)


def write_png(path):
    raw = b""
    for row in rows:
        raw += b"\x00"
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)
    return png


png_bytes = write_png("favicon-64.png")

header = struct.pack("<HHH", 0, 1, 1)
entry = struct.pack("<BBBBHHII", SIZE, SIZE, 0, 0, 1, 32, len(png_bytes), 22)
with open("favicon.ico", "wb") as f:
    f.write(header + entry + png_bytes)

print("生成完成")
print("favicon.ico 字节数:", 22 + len(png_bytes))
