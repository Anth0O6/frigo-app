#!/usr/bin/env python3
"""
Fabrique les icônes Android à partir du logo fourni.

    pip install pillow && python3 design/genere-icones.py

**Pourquoi un script plutôt que des PNG posés à la main :** les icônes existent en
cinq densités, et les refaire à la main le jour où le logo change garantit qu'une
densité sera oubliée — celle d'un téléphone qu'on n'a pas sous les yeux. Le script
est la seule façon de savoir que les dix fichiers viennent bien de la même source.

**Ce qu'il fait, et pourquoi il ne prend pas le logo tel quel.** Une icône
adaptative Android est masquée par le lanceur — en cercle, en carré arrondi, en
goutte selon le téléphone — et seul le disque central de 72 dp sur les 108 dp de la
toile est garanti visible. Le mot « FRIGOPRO » et la rangée d'outils du logo
tombent hors de ce disque : les garder reviendrait à livrer une icône tronquée
différemment sur chaque téléphone. Le script isole donc l'**emblème** — la jauge,
le flocon et la flamme — qui est l'identité de l'application et qui, lui, se lit
encore à 48 dp.

Le logo complet reste dans `design/`, d'où il ressort pour une fiche de magasin ou
un en-tête de document.
"""

from PIL import Image
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
SOURCE = Path(__file__).resolve().parent / "logo-frigopro.png"
RES = RACINE / "app" / "src" / "main" / "res"

# L'emblème dans le logo source, mesuré sur l'image : la jauge est centrée en
# x = 625, et le texte commence vers y = 720.
BOITE = (175, 105, 1075, 715)

# Le fond de la carte du logo plafonne à 64 en luminosité. Le plancher d'alpha
# passe au-dessus, sans quoi la carte garde 30 % d'opacité et dessine son
# rectangle derrière l'emblème.
ALPHA_BAS, ALPHA_HAUT = 72, 155

# L'anneau tient entièrement dans ce cercle : tout ce qui déborde est un reste du
# liseré de la carte.
CENTRE, RAYON = (450, 452), 455

# La toile d'une icône adaptative fait 108 dp ; le lanceur peut rogner les 18 dp
# de chaque bord. L'emblème est le plus large à mi-hauteur, là où le masque
# circulaire l'est aussi : au-delà de 66 % il commencerait à se faire couper.
PART_EMBLEME = 0.66

DENSITES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# Le dégradé du fond, relevé sur le logo : un bleu vif vers le haut, qui s'assombrit
# vers les bords. Il est aussi décrit en vectoriel dans `ic_launcher_background.xml` —
# ici il ne sert qu'aux icônes héritées, que les lanceurs d'avant Android 8 affichent.
FOND_CLAIR, FOND_SOMBRE = (10, 91, 158), (4, 32, 58)


def embleme() -> Image.Image:
    """L'emblème seul, détouré sur transparence, dans un carré."""
    crop = Image.open(SOURCE).convert("RGB").crop(BOITE)
    largeur, hauteur = crop.size
    pixels = crop.load()

    decoupe = Image.new("RGBA", (largeur, hauteur), (0, 0, 0, 0))
    sortie = decoupe.load()
    for y in range(hauteur):
        for x in range(largeur):
            r, v, b = pixels[x, y]
            vif = max(r, v, b)
            if vif <= ALPHA_BAS:
                continue
            # L'alpha suit la luminosité : le halo de l'emblème est conservé au
            # lieu d'être tranché au couteau.
            a = 255 if vif >= ALPHA_HAUT else int(255 * (vif - ALPHA_BAS) / (ALPHA_HAUT - ALPHA_BAS))
            sortie[x, y] = (r, v, b, a)

    cote = max(largeur, hauteur)
    carre = Image.new("RGBA", (cote, cote), (0, 0, 0, 0))
    carre.paste(decoupe, ((cote - largeur) // 2, (cote - hauteur) // 2))

    net = carre.load()
    for y in range(cote):
        for x in range(cote):
            if net[x, y][3] and ((x - CENTRE[0]) ** 2 + (y - CENTRE[1]) ** 2) ** 0.5 > RAYON:
                net[x, y] = (0, 0, 0, 0)
    return carre


def fond(cote: int) -> Image.Image:
    """Le dégradé du logo, pour les icônes héritées."""
    image = Image.new("RGB", (cote, cote))
    pixels = image.load()
    for y in range(cote):
        for x in range(cote):
            # Radial, centré un peu au-dessus du milieu, comme sur le logo.
            d = (((x - cote / 2) ** 2 + (y - cote * 0.42) ** 2) ** 0.5) / (cote * 0.72)
            d = min(1.0, d)
            pixels[x, y] = tuple(
                round(FOND_CLAIR[c] + (FOND_SOMBRE[c] - FOND_CLAIR[c]) * d) for c in range(3)
            )
    return image


def pose(source: Image.Image, toile: int, part: float) -> Image.Image:
    """L'emblème centré sur une toile transparente, à la part demandée."""
    cible = max(1, round(toile * part))
    reduit = source.resize((cible, cible), Image.LANCZOS)
    sortie = Image.new("RGBA", (toile, toile), (0, 0, 0, 0))
    sortie.paste(reduit, ((toile - cible) // 2, (toile - cible) // 2), reduit)
    return sortie


def rond(image: Image.Image) -> Image.Image:
    """Découpe l'image en disque, pour `ic_launcher_round`."""
    cote = image.width
    masque = Image.new("L", (cote, cote), 0)
    from PIL import ImageDraw

    ImageDraw.Draw(masque).ellipse((0, 0, cote - 1, cote - 1), fill=255)
    sortie = image.convert("RGBA")
    sortie.putalpha(masque)
    return sortie


def main() -> None:
    source = embleme()
    for nom, facteur in DENSITES.items():
        dossier = RES / f"mipmap-{nom}"
        dossier.mkdir(parents=True, exist_ok=True)

        # Le calque avant de l'icône adaptative : 108 dp de toile.
        pose(source, round(108 * facteur), PART_EMBLEME).save(dossier / "ic_launcher_foreground.png")

        # Les icônes héritées : 48 dp, fond compris, jamais masquées — l'emblème y
        # occupe donc plus de place.
        cote = round(48 * facteur)
        heritee = fond(cote).convert("RGBA")
        avant = pose(source, cote, 0.86)
        heritee.alpha_composite(avant)
        heritee.convert("RGB").save(dossier / "ic_launcher.png")
        rond(heritee).save(dossier / "ic_launcher_round.png")
        print(f"  mipmap-{nom:8} avant {round(108 * facteur):3} px, héritée {cote:3} px")


if __name__ == "__main__":
    main()
