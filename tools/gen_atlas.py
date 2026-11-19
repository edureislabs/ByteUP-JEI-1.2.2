#!/usr/bin/env python3
"""Gera as texturas do ByteUP JEI (estilo JEI classico). Uso: python tools/gen_atlas.py
Saida: src/main/resources/assets/byteup/textures/gui/{jei_screen,sel,placeholder}.png
As coordenadas DEVEM bater com ui/Layout.java."""
from PIL import Image, ImageDraw
import os

W, H = 660, 370
BLACK=(0,0,0,255); FACE=(198,198,198,255); HI=(255,255,255,255); SH=(85,85,85,255)
SLOT=(139,139,139,255); SLOTD=(55,55,55,255); INSET=(176,176,176,255); DARK=(40,40,40,255)

img = Image.new("RGBA", (W, H), (0,0,0,0))
d = ImageDraw.Draw(img)
def rect(x,y,w,h,c): d.rectangle([x,y,x+w-1,y+h-1], fill=c)

def panel(x,y,w,h):
    rect(x,y,w,h,BLACK)
    for cx,cy in ((x,y),(x+w-1,y),(x,y+h-1),(x+w-1,y+h-1)): img.putpixel((cx,cy),(0,0,0,0))
    rect(x+1,y+1,w-2,h-2,FACE)
    rect(x+1,y+1,w-2,2,HI); rect(x+1,y+1,2,h-2,HI)          # luz em cima/esquerda
    rect(x+1,y+h-3,w-2,2,SH); rect(x+w-3,y+1,2,h-2,SH)      # sombra embaixo/direita
    img.putpixel((x+w-3,y+1),FACE); img.putpixel((x+1,y+h-3),FACE)

def button(x,y,w,h):
    rect(x,y,w,h,BLACK); rect(x+1,y+1,w-2,h-2,FACE)
    rect(x+1,y+1,w-2,1,HI); rect(x+1,y+1,1,h-2,HI)
    rect(x+1,y+h-2,w-2,1,SH); rect(x+w-2,y+1,1,h-2,SH)

def inset(x,y,w,h,fill=INSET):
    rect(x,y,w,h,fill)
    rect(x,y,w,1,SH); rect(x,y,1,h,SH)
    rect(x,y+h-1,w,1,HI); rect(x+w-1,y,1,h,HI)

def slot(x,y,w,h):
    rect(x,y,w,h,SLOT)
    rect(x,y,w,1,SLOTD); rect(x,y,1,h,SLOTD)
    rect(x,y+h-1,w,1,HI); rect(x+w-1,y,1,h,HI)

def tri(cx,cy,half,direction,color=DARK):
    """Triangulo isosceles: 'left' = ponta a esquerda, 'right' = ponta a direita."""
    for col in range(half+1):
        n = col if direction=="left" else half-col
        x = cx-half//2+col
        for dy in range(-n,n+1): img.putpixel((x,cy+dy),color)

# ---- abas (5) + botao de modo
for i in range(5): button(10+32*i, 0, 30, 27)
button(180, 0, 60, 27)

# ---- painel principal (receitas)
panel(0, 26, 360, 344)
for y in (32, 57):
    button(10, y, 22, 18); tri(21, y+9, 4, "left")
    button(328, y, 22, 18); tri(339, y+9, 4, "right")
    inset(34, y, 292, 18)

for T in (82, 226):
    inset(10, T, 340, 138, fill=(184,184,184,255))
    for r in range(3):
        for c in range(3): slot(30+28*c, T+28+28*r, 26, 26)
    # seta grande (36x20)
    ax, ay = 146, T+59
    rect(ax, ay+6, 22, 8, SLOT); 
    for col in range(14):
        n = 10-int(col*10/13)
        for dy in range(-n, n+1): img.putpixel((ax+22+col, ay+10+dy), SLOT)
    slot(204, T+52, 34, 34)
    button(316, T+110, 22, 22)
    rect(316+6, T+110+10, 10, 2, DARK); rect(316+10, T+110+6, 2, 10, DARK)

# ---- painel de itens
panel(372, 0, 288, 370)
button(382, 13, 22, 18); tri(393, 22, 4, "left")
button(628, 13, 22, 18); tri(639, 22, 4, "right")
inset(406, 13, 220, 18)
for r in range(9):
    for c in range(9): slot(391+28*c, 40+28*r, 26, 26)
# campo de pesquisa (fundo preto, borda em baixo-relevo) + botao lupa
inset(391, 309, 206, 20, fill=BLACK)
button(601, 309, 24, 20)
cx, cy = 611, 317
for ang in range(0, 360, 6):
    import math
    px = cx + round(4*math.cos(math.radians(ang))); py = cy + round(4*math.sin(math.radians(ang)))
    img.putpixel((px,py), DARK)
for k in range(4): img.putpixel((cx+4+k, cy+4+k), DARK); img.putpixel((cx+5+k, cy+4+k), DARK)

out = "src/main/resources/assets/byteup/textures/gui"
os.makedirs(out, exist_ok=True)
img.save(f"{out}/jei_screen.png")

# destaque da aba ativa: 26x8 (ascent <= height exige altura 8); so as 3 primeiras linhas tem cor
sel = Image.new("RGBA",(26,8),(0,0,0,0)); sd = ImageDraw.Draw(sel)
sd.rectangle([0,0,25,2], fill=(255,216,74,255)); sd.rectangle([0,0,25,0], fill=(255,240,160,255))
sel.save(f"{out}/sel.png")

# placeholder 16x16 para itens sem textura valida
ph = Image.new("RGBA",(16,16),(0,0,0,0)); pd = ImageDraw.Draw(ph)
pd.rectangle([1,1,14,14], fill=(139,139,139,255), outline=(55,55,55,255))
pd.rectangle([7,10,8,11], fill=(255,255,255,255)); pd.rectangle([6,4,9,5], fill=(255,255,255,255))
pd.rectangle([9,6,10,7], fill=(255,255,255,255)); pd.rectangle([7,8,8,9], fill=(255,255,255,255))
ph.save(f"{out}/placeholder.png")
print("ok")
