#!/usr/bin/env python3
"""Génère l'icône adaptative Android (fond, premier plan, monochrome 432 × 432) à partir de art/icon_reference.jpg.

Le canard-cavalier est détouré de l'image de référence (rouge nettement supérieur au bleu, trous de l'œil comblés),
agrandi pour occuper ≈ 80 % de la zone visible (72 dp sur 108) avec une ombre douce ; le fond est un damier
bleu → violet de 4 cases visibles, adouci et vignetté. Usage : python3 scripts/make_icon.py (Pillow, NumPy).
"""
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import numpy as np
import os, sys
ROOT=os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
SRC=os.path.join(ROOT, 'art', 'icon_reference.jpg')
OUT=os.path.join(ROOT, 'app', 'src', 'main', 'res', 'mipmap-xxxhdpi')
im=Image.open(SRC).convert('RGB')
x0,y0,x1,y1=158,39,354,246
art=im.crop((x0,y0,x1,y1))
a=np.array(art).astype(float)
R,G,B=a[...,0],a[...,1],a[...,2]
# Canard : rouge nettement au-dessus du bleu (jaune, orange, reflets) — fond bleu/violet sinon.
soft=np.clip((R-B-15)/45.0,0,1)
hard=(soft>0.5).astype(np.uint8)*255
m=Image.fromarray(hard,'L')
# Remplit les trous (œil noir et blanc, reflets) : remplissage depuis le bord sur le masque inversé.
inv=ImageChops.invert(m)
ImageDraw.floodfill(inv,(0,0),128)
ImageDraw.floodfill(inv,(inv.width-1,0),128)
ImageDraw.floodfill(inv,(0,inv.height-1),128)
ImageDraw.floodfill(inv,(inv.width-1,inv.height-1),128)
holes=np.array(inv)==255
alpha=np.maximum(soft, holes.astype(float))
# garder la plus grande composante (le canard) : supprimer pixels isolés en bas/bords
alpha_img=Image.fromarray((alpha*255).astype(np.uint8),'L').filter(ImageFilter.MedianFilter(3))
duck=art.convert('RGBA'); duck.putalpha(alpha_img)
bbox=alpha_img.point(lambda v:255 if v>40 else 0).getbbox()
duck=duck.crop(bbox)
print('duck bbox',bbox,duck.size)
S=432
def lerp(c1,c2,t): return tuple(int(c1[i]+(c2[i]-c1[i])*t) for i in range(3))
def hexc(h): return tuple(int(h[i:i+2],16) for i in (1,3,5))
# Fond : damier 9×9 (6 cases visibles dans la zone sûre), dégradé bleu → violet, légère vignette.
bg=Image.new('RGB',(S,S)); px=bg.load()
L1,L2=hexc('#2f5590'),hexc('#5a3f9e'); D1,D2=hexc('#1c3c72'),hexc('#3a2876')
cell=S/6
for y in range(S):
    for x in range(S):
        t=(x+y)/(2*S)
        light=(int(x//cell)+int(y//cell))%2==1
        c=lerp(L1,L2,t) if light else lerp(D1,D2,t)
        dx=(x-S/2)/(S/2); dy=(y-S/2)/(S/2); v=1-0.30*min(1,(dx*dx+dy*dy)**0.5)**2
        px[x,y]=tuple(int(k*v) for k in c)
bg=bg.filter(ImageFilter.GaussianBlur(3.5))
bg.save(os.path.join(OUT, 'ic_launcher_background.png'))
# Premier plan : canard centré, hauteur ≈ 78 % de la zone visible (72 dp sur 108).
fg=Image.new('RGBA',(S,S),(0,0,0,0))
target_h=int(288*0.80)
scale=target_h/duck.height
d=duck.resize((int(duck.width*scale),target_h),Image.LANCZOS)
d=d.filter(ImageFilter.UnsharpMask(radius=1.2,percent=60,threshold=2))
# ombre douce sous le canard
sh=Image.new('RGBA',(S,S),(0,0,0,0)); shd=d.split()[3].point(lambda v:int(v*0.45))
blk=Image.new('RGBA',d.size,(0,0,0,255)); blk.putalpha(shd)
ox=(S-d.width)//2+4; oy=(S-d.height)//2+6
sh.paste(blk,(ox+4,oy+8),blk); sh=sh.filter(ImageFilter.GaussianBlur(8))
fg=Image.alpha_composite(fg,sh)
fg.alpha_composite(d,(ox,oy))
fg.save(os.path.join(OUT, 'ic_launcher_foreground.png'))
mono=Image.new('RGBA',(S,S),(0,0,0,0)); white=Image.new('RGBA',d.size,(255,255,255,255)); white.putalpha(d.split()[3]); mono.alpha_composite(white,(ox,oy)); mono.save(os.path.join(OUT, 'ic_launcher_monochrome.png'))
