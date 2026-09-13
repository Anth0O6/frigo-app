#!/usr/bin/env python3
"""
Fabrique les courbes de saturation de `CourbesSaturation.kt`.

    pip install CoolProp && python3 donnees/genere-courbes.py

**Pourquoi ce script existe.** La première version de ces courbes avait été
écrite « de mémoire documentaire », et elle était fausse : les fluides anciens —
R-134a, R-22, R-290, R-600a, R-744 — tombaient juste, et tous les mélanges
récents étaient trop bas. Le pire trio, R-407C / R-448A / R-449A, l'était de près
d'un quart, soit **plus de 8 K d'erreur sur la température de rosée** : une
surchauffe calculée dessus annonçait 10 K là où il y en avait 2, et c'est du
liquide qui part au compresseur.

Le remède n'est pas de recopier mieux, c'est de ne plus recopier du tout. Les
valeurs sont **calculées** par CoolProp, l'implémentation libre des équations
d'état de référence — les mêmes que celles de REFPROP pour la plupart de ces
fluides. Deux conséquences qui valent d'être dites :

- **Ça se refait.** Une valeur douteuse se recontrôle en relançant ce script,
  sans dépendre de la mémoire de qui que ce soit.
- **Ça se recoupe.** N'importe quelle table constructeur donne les mêmes chiffres
  à quelques centièmes de bar près ; l'écart, lui, était visible à l'œil nu.

Le pas est de 5 K de -40 à +60 °C, en **bar absolus**, ce qui est l'unité des
tables publiées. La courbe s'arrête d'elle-même au point critique — au-delà il
n'y a plus de saturation, et le CO₂ s'arrête donc à 30 °C.
"""

from pathlib import Path
import textwrap

from CoolProp.CoolProp import PropsSI
import CoolProp

RACINE = Path(__file__).resolve().parent.parent
CIBLE = RACINE / "app/src/main/java/com/frigopro/app/data/CourbesSaturation.kt"

# Le nom du fluide dans le catalogue de l'application, et son nom CoolProp.
# Les mélanges existent tantôt en pseudo-pur (modèle de Lemmon, celui des tables
# du commerce), tantôt en mélange vrai ; on prend le premier qui répond.
FLUIDES = [
    ("R134A", ["R134a"]),
    ("R32", ["R32"]),
    ("R22", ["R22"]),
    ("R290", ["R290"]),
    ("R600A", ["IsoButane"]),
    ("R1234YF", ["R1234yf"]),
    ("R1234ZE", ["R1234ze(E)"]),
    ("R717", ["Ammonia"]),
    ("R744", ["CO2"]),
    ("R404A", ["R404A", "R404A.MIX"]),
    ("R407C", ["R407C", "R407C.MIX"]),
    ("R407F", ["R407F", "R407F.MIX"]),
    ("R410A", ["R410A", "R410A.MIX"]),
    ("R448A", ["R448A", "R448A.MIX"]),
    ("R449A", ["R449A", "R449A.MIX"]),
    ("R450A", ["R450A", "R450A.MIX"]),
    ("R452A", ["R452A", "R452A.MIX"]),
    ("R452B", ["R452B", "R452B.MIX"]),
    ("R454B", ["R454B", "R454B.MIX"]),
    ("R454C", ["R454C", "R454C.MIX"]),
    ("R455A", ["R455A", "R455A.MIX"]),
    ("R507A", ["R507A", "R507A.MIX"]),
    ("R513A", ["R513A", "R513A.MIX"]),
]

DEBUT, FIN, PAS = -40, 60, 5

# Deux pressions qui diffèrent de moins de ça s'écrivent d'une seule colonne :
# un centième de bar est sous la résolution de n'importe quel manomètre.
CONFONDUES_BAR = 0.005


def nom_coolprop(candidats):
    for c in candidats:
        try:
            PropsSI("P", "T", 273.15, "Q", 0, c)
            return c
        except Exception:
            continue
    return None


def courbe(cp):
    """Les points de la courbe, et le glissement maximal rencontré."""
    points, glissement = [], 0.0
    for t in range(DEBUT, FIN + 1, PAS):
        kelvin = t + 273.15
        try:
            bulle = PropsSI("P", "T", kelvin, "Q", 0, cp) / 1e5
            rosee = PropsSI("P", "T", kelvin, "Q", 1, cp) / 1e5
        except Exception:
            break  # point critique atteint : au-delà il n'y a plus de saturation
        try:
            t_rosee = PropsSI("T", "P", bulle * 1e5, "Q", 1, cp) - 273.15
            glissement = max(glissement, abs(t_rosee - t))
        except Exception:
            pass
        points.append(
            f"{t}:{bulle:.2f}" if abs(bulle - rosee) < CONFONDUES_BAR
            else f"{t}:{bulle:.2f}/{rosee:.2f}"
        )
    return points, glissement


def bloc_kotlin():
    lignes = [
        "    private val COURBES: Map<String, String> = mapOf(",
        f"        // Calculées par CoolProp {CoolProp.__version__} — voir",
        "        // `donnees/genere-courbes.py`. Ne pas retoucher à la main : une valeur",
        "        // corrigée ici et pas dans le script serait reperdue au calcul suivant.",
    ]
    for nom, candidats in FLUIDES:
        cp = nom_coolprop(candidats)
        if cp is None:
            raise SystemExit(f"{nom} : aucun nom CoolProp ne répond")
        points, glissement = courbe(cp)
        bornes = f"{points[0].split(':')[0]} à {points[-1].split(':')[0]} °C"
        lignes.append(f'        // {nom} — glissement max {glissement:.1f} K, {bornes}')
        lignes.append(f'        "{nom}" to """')
        for i in range(0, len(points), 8):
            lignes.append("            " + " ".join(points[i:i + 8]))
        lignes.append('        """,')
    lignes.append("    )")
    return "\n".join(lignes)


def main():
    source = CIBLE.read_text()
    debut = source.index("    private val COURBES: Map<String, String> = mapOf(")
    fin = source.index("\n    )", debut) + len("\n    )")
    CIBLE.write_text(source[:debut] + bloc_kotlin() + source[fin:])
    print(f"{CIBLE.name} : {len(FLUIDES)} courbes écrites (CoolProp {CoolProp.__version__})")


if __name__ == "__main__":
    main()
