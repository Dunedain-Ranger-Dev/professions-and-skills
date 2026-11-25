"""Makes every texture this mod ships from vanilla Minecraft textures, recolored, so none is original art.

Run from the project root, after a Gradle build or run (it reads vanilla's textures from the Minecraft resources jar
that ModDevGradle downloads): python tools/vanilla_textures.py

Each texture is a vanilla one recolored to a target color, keeping vanilla's shading (each pixel's brightness maps onto
shades of the color). Some keep only part of the vanilla texture first (an axe's head without its handle, a bow without
its string), and a few are put together from two (a potion bottle with its liquid tinted). These stand in until real
art exists. Needs Pillow (pip install pillow).
"""
import io
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src/main/resources/assets/ddprofessions/textures"
VANILLA_JAR = ROOT / "build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar"

JAR = None


def vanilla(path):
    return Image.open(io.BytesIO(JAR.read(f"assets/minecraft/textures/{path}.png"))).convert("RGBA")


def vanilla_meta(path):
    name = f"assets/minecraft/textures/{path}.png.mcmeta"
    return JAR.read(name) if name in JAR.namelist() else None


def luminance(pixel):
    r, g, b = pixel[:3]
    return 0.299 * r + 0.587 * g + 0.114 * b


def chroma(pixel):
    return max(pixel[:3]) - min(pixel[:3])


def opaque(image):
    return [(x, y) for y in range(image.height) for x in range(image.width) if image.getpixel((x, y))[3] > 0]


def recolor(image, color, keep=None):
    """The image recolored to a color, keeping its shading: its darkest pixel well below the color, its brightest a
    little above. With keep, only the pixels it accepts stay (the rest turn clear)."""
    out = image.copy()
    pixels = opaque(image)
    if keep:
        for p in pixels:
            if not keep(image.getpixel(p)):
                out.putpixel(p, (0, 0, 0, 0))
        pixels = [p for p in pixels if keep(image.getpixel(p))]
        # Drop small stray pieces (a sword's pommel without its grip), keeping the big ones (a bow's two limbs).
        pieces, left = [], set(pixels)
        while left:
            piece, todo = set(), [left.pop()]
            while todo:
                x, y = todo.pop()
                piece.add((x, y))
                for n in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if n in left:
                        left.remove(n)
                        todo.append(n)
            pieces.append(piece)
        big = set().union(*(piece for piece in pieces if len(piece) * 4 >= max(map(len, pieces))))
        for p in pixels:
            if p not in big:
                out.putpixel(p, (0, 0, 0, 0))
        pixels = [p for p in pixels if p in big]
    shades = [luminance(image.getpixel(p)) for p in pixels]
    low, high = min(shades), max(shades)
    target = ((color >> 16) & 255, (color >> 8) & 255, color & 255)
    for p in pixels:
        pixel = image.getpixel(p)
        t = 0.5 if high == low else (luminance(pixel) - low) / (high - low)
        scale = 0.45 + 0.8 * t
        out.putpixel(p, tuple(max(0, min(255, round(c * scale))) for c in target) + (pixel[3],))
    return out


def tint(image, color):
    """The image multiplied by a color, as Minecraft tints potion liquid and leather."""
    out = image.copy()
    target = ((color >> 16) & 255, (color >> 8) & 255, color & 255)
    for p in opaque(image):
        pixel = image.getpixel(p)
        out.putpixel(p, tuple(round(pixel[i] * target[i] / 255) for i in range(3)) + (pixel[3],))
    return out


def grey(pixel):
    """Metal or string, not wood: little color."""
    return chroma(pixel) < 24


def wood(pixel):
    return chroma(pixel) >= 24


# Whole recolors: our texture -> (vanilla texture, color, which pixels to keep or None).
RECOLORS = {
    # Dusts, salts and fluxes.
    "item/arcane_dust": ("item/glowstone_dust", 0xA37BD5, None),
    "item/runic_flux": ("item/redstone", 0x934AC7, None),
    "item/runeforged_flux": ("item/redstone", 0xE1A429, None),
    "item/sunsalt": ("item/glowstone_dust", 0xE9992B, None),
    "item/sand_flux": ("item/gunpowder", 0xC5B37B, None),
    "item/borax_flux": ("item/gunpowder", 0xDBDFE3, None),
    "item/assay_salt": ("item/sugar", 0xDBCF8F, None),
    "item/bone_ash": ("item/bone_meal", 0xD1CDC1, None),
    "item/borax": ("item/quartz", 0xE2E8EB, None),
    # Herbs.
    "item/barrowmoss": ("item/kelp", 0x506E46, None),
    "block/barrowmoss_patch": ("block/glow_lichen", 0x5E7A50, None),
    "item/emberroot": ("item/nether_wart", 0xBE501E, None),
    # Metal parts.
    "item/axe_head": ("item/iron_axe", 0xADB3C1, grey),
    "item/blade": ("item/iron_sword", 0xA6ACBA, grey),
    "item/chain_links": ("item/chain", 0xB5BBC9, None),
    "item/rivets": ("item/iron_nugget", 0x8E94A2, None),
    "item/armor_plate": ("item/paper", 0xA4AAB8, None),
    "item/coke_infused_iron_ingot": ("item/iron_ingot", 0x5A5A62, None),
    # Gold.
    "item/fine_gold": ("item/gold_ingot", 0xE2B739, None),
    "item/gold_fittings": ("item/gold_nugget", 0xF4CD45, None),
    "item/gold_amulet": ("item/heart_of_the_sea", 0xE9BD31, None),
    # Leather.
    "item/leather_base": ("item/leather_chestplate", 0x834F2B, None),
    "item/leather_panel": ("item/paper", 0x90562E, None),
    "item/refined_leather": ("item/leather", 0x9A5E36, None),
    "item/hardened_leather": ("item/leather", 0x59341C, None),
    "item/scraped_hide": ("item/rabbit_hide", 0xB89068, None),
    "item/leather_scraps": ("item/armadillo_scute", 0x734321, None),
    "item/straps": ("item/dried_kelp", 0x693919, None),
    # Cloth.
    "item/cloth_panel": ("item/paper", 0xCEC4B0, None),
    "item/gambeson_base": ("item/leather_chestplate", 0xC9BDA1, None),
    "item/yarn": ("item/slime_ball", 0xE5DFD1, None),
    "item/sized_yarn": ("item/slime_ball", 0xEBE1BD, None),
    "item/staff_wrap": ("item/phantom_membrane", 0x9B7DCD, None),
    "item/bowstring": ("item/string", 0xD2CEBE, None),
    # Wood.
    "item/seasoned_billet": ("item/brick", 0x9B7341, None),
    "item/cured_billet": ("item/brick", 0x6F4C29, None),
    "item/stave": ("item/stick", 0xB08A55, None),
    "item/haft": ("item/stick", 0x91693C, None),
    "item/staff_shaft": ("item/bamboo", 0x875F37, None),
    "item/wand_core": ("item/bone", 0xA57D4B, None),
    "item/bow_limbs": ("item/bow", 0x9B7341, wood),
    "item/stock": ("item/crossbow_standby", 0x6F4C29, wood),
    "item/forged_staff": ("item/blaze_rod", 0x8C6341, None),
    "item/forged_wand": ("item/breeze_rod", 0xB0875D, None),
    # Magic.
    "item/arcane_focus": ("item/ender_eye", 0x9B6FD8, None),
    # The Call Companion spell's icon.
    "gui/spell_icons/call_companion": ("item/lead", 0xD8B04A, None),
    # Barding: mail from iron horse armor, plate from diamond (recolored to steel). Leather barding stays vanilla's
    # undyed leather horse armor, since it's dyed like it.
    "entity/horse/armor/horse_armor_mail_barding": ("entity/horse/armor/horse_armor_iron", 0x8A8F99, None),
    "entity/horse/armor/horse_armor_plate_barding": ("entity/horse/armor/horse_armor_diamond", 0xB5BCC8, None),
}

# Vanilla textures used as they are.
COPIES = {
    "entity/horse/armor/horse_armor_leather_barding": "entity/horse/armor/horse_armor_leather",
    "entity/wolf/wolf_armor": "entity/wolf/wolf_armor",
}

# Bottled liquids: vanilla's potion with its liquid tinted.
BOTTLES = {
    "item/bark_liquor": 0x6E461E,
    "item/elder_liquor": 0x562E34,
    "item/tannin_liquor": 0x653D1D,
    "item/heartwood_resin": 0xB45A1E,
    "item/linseed_oil": 0xC8AA46,
    "item/quenching_oil": 0xAA781E,
    "item/silk_sizing": 0xE4ECF2,
    "item/wheat_sizing": 0xD8C89A,
    "item/arcane_sizing": 0xB48CE6,
    "item/sylvan_sap": 0x78C86E,
}

# Liquids in vats: vanilla's still water (animated), recolored.
LIQUIDS = {
    "block/quenching_oil": 0x7D531B,
    "block/sizing": 0xD6D0B6,
    "block/tanning_liquor": 0x603A1A,
    "block/wood_finish": 0xC4963C,
}


def bottle(color):
    out = Image.new("RGBA", (16, 16))
    out.alpha_composite(tint(vanilla("item/potion_overlay"), color))
    out.alpha_composite(vanilla("item/potion"))
    return out


def ring(color):
    """A ring: vanilla's firework star with its middle taken out."""
    star = vanilla("item/firework_star")
    xs = [x for x, y in opaque(star)]
    ys = [y for x, y in opaque(star)]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    hollow = star.copy()
    for x, y in opaque(star):
        if (x - cx) ** 2 + (y - cy) ** 2 < 2.6 ** 2:
            hollow.putpixel((x, y), (0, 0, 0, 0))
    return recolor(hollow, color)


def borax_deposit():
    """Vanilla's iron ore with its specks recolored white."""
    ore = vanilla("block/iron_ore")
    specks = [p for p in opaque(ore) if chroma(ore.getpixel(p)) > 14]
    shades = [luminance(ore.getpixel(p)) for p in specks]
    low, high = min(shades), max(shades)
    out = ore.copy()
    for p in specks:
        t = (luminance(ore.getpixel(p)) - low) / (high - low)
        v = round(190 + 60 * t)
        out.putpixel(p, (v, v, min(255, v + 6), 255))
    return out


def particle_at(path, color, corner):
    """A vanilla particle, recolored, placed on a clear 16x16 with its bottom-right at corner."""
    image = recolor(vanilla(path), color)
    box = image.getbbox()
    piece = image.crop(box)
    out = Image.new("RGBA", (16, 16))
    out.alpha_composite(piece, (corner[0] - piece.width, corner[1] - piece.height))
    return out


def forged_mark():
    """The mark on forged gear's icon: a small gold sparkle in the bottom-right corner."""
    return particle_at("particle/glitter_3", 0xFFD23C, (16, 16))


def tempered_shine():
    """Two white sparkles over an iron ingot."""
    out = particle_at("particle/glitter_3", 0xFFFFFF, (9, 9))
    out.alpha_composite(particle_at("particle/glitter_1", 0xFFFFFF, (13, 6)))
    return out


def unstable_cracks():
    """Cracks across an iron ingot: vanilla's block-breaking cracks, kept to the ingot's inside."""
    ingot = vanilla("item/iron_ingot")
    cracks = vanilla("block/destroy_stage_6")
    inside = {p for p in opaque(ingot) if luminance(ingot.getpixel(p)) > 90}
    out = Image.new("RGBA", (16, 16))
    for p in inside:
        pixel = cracks.getpixel(p)
        if pixel[3] > 0 and luminance(pixel) < 128:
            out.putpixel(p, (0x23, 0x19, 0x19, 255))
    return out


def dual_wielding():
    """The Dual Wielding skill's icon: vanilla's iron sword crossed with a mirrored copy of itself."""
    sword = vanilla("item/iron_sword")
    crossed = sword.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    crossed.alpha_composite(sword)
    return crossed


def save(name, image, meta=None):
    path = TEXTURES / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    meta_path = path.with_name(path.name + ".mcmeta")
    if meta:
        meta_path.write_bytes(meta)
    elif meta_path.exists():
        meta_path.unlink()


def main():
    global JAR
    JAR = zipfile.ZipFile(VANILLA_JAR)
    made = {}
    for name, (source, color, keep) in RECOLORS.items():
        made[name] = recolor(vanilla(source), color, keep)
    for name, source in COPIES.items():
        made[name] = vanilla(source)
    for name, color in BOTTLES.items():
        made[name] = bottle(color)
    made["item/gold_ring"] = ring(0xEDC83C)
    made["block/borax_deposit"] = borax_deposit()
    made["item/forged_mark"] = forged_mark()
    made["item/tempered_shine"] = tempered_shine()
    made["item/unstable_cracks"] = unstable_cracks()
    made["gui/skill_icons/dual_wielding"] = dual_wielding()
    for name, image in made.items():
        save(name, image)
    water = vanilla("block/water_still")
    for name, color in LIQUIDS.items():
        save(name, recolor(water, color), vanilla_meta("block/water_still"))
    shipped = {str(p.relative_to(TEXTURES).with_suffix("")).replace("\\", "/") for p in TEXTURES.rglob("*.png")}
    missing = shipped - set(made) - set(LIQUIDS)
    print(f"{len(made) + len(LIQUIDS)} textures written from vanilla")
    if missing:
        print("Not made from vanilla (still original):", ", ".join(sorted(missing)))


if __name__ == "__main__":
    main()
