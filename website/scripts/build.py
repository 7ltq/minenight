"""Dependency-light static build, including responsive encodings of real renders."""
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC, DIST = ROOT / 'src', ROOT / 'dist'
ART = ROOT / 'assets' / 'artwork'
REQUIRED = ['crystal', 'battle', 'bots', 'ranks', 'vote', 'worlds', 'discord', 'play']

def build():
    subprocess.run([sys.executable,str(ROOT/'scripts'/'create-legal-pages.py')],check=True)
    subprocess.run([sys.executable,str(ROOT/'scripts'/'create-store-page.py')],check=True)
    subprocess.run([sys.executable,str(ROOT/'scripts'/'create-vote-page.py')],check=True)
    subprocess.run([sys.executable,str(ROOT/'scripts'/'create-support-page.py')],check=True)
    subprocess.run([sys.executable,str(ROOT/'scripts'/'create-player-pages.py')],check=True)
    missing = [name for name in REQUIRED if not (ART / (name+'.png')).exists()]
    if missing: raise SystemExit('Missing real Blender renders: '+', '.join(missing))
    DIST.mkdir(exist_ok=True)
    shutil.copytree(SRC / 'assets', DIST / 'assets', dirs_exist_ok=True)
    out_art = DIST / 'assets' / 'art'
    out_art.mkdir(exist_ok=True)
    dimensions = {}
    for name in REQUIRED:
        with Image.open(ART / f'{name}.png') as im:
            if im.mode != 'RGBA' or im.getextrema()[3] == (255,255):
                raise ValueError(f'{name}: expected an actually transparent RGBA Blender render')
            dimensions[name] = im.size
            target = out_art / f'{name}.webp'
            temporary = target.with_suffix('.tmp')
            im.save(temporary, 'WEBP', quality=91, method=6)
            temporary.replace(target)
            small = im.copy()
            small.thumbnail((700,700))
            target = out_art / f'{name}-small.webp'
            temporary = target.with_suffix('.tmp')
            small.save(temporary, 'WEBP', quality=90, method=6)
            temporary.replace(target)
    for file in SRC.iterdir():
        if file.is_file():
            if file.suffix == '.html':
                html = file.read_text(encoding='utf-8')
                def image_tag(match):
                    tag = match.group(0)
                    name = re.search(r'assets/art/(\w+)\.png', tag).group(1)
                    width, height = dimensions[name]
                    tag = re.sub(r'width="\d+"', f'width="{width}"', tag)
                    tag = re.sub(r'height="\d+"', f'height="{height}"', tag)
                    tag = tag.replace(f'assets/art/{name}.png', f'assets/art/{name}.webp')
                    small_width = min(700, round(width * min(700/width,700/height)))
                    return tag[:-1] + f' srcset="assets/art/{name}-small.webp {small_width}w, assets/art/{name}.webp {width}w" sizes="(max-width: 600px) 360px, (max-width: 1050px) 480px, 750px">'
                html = re.sub(r'<img\b[^>]*src="assets/art/\w+\.png"[^>]*>', image_tag, html)
                (DIST / file.name).write_text(html,encoding='utf-8')
            else: shutil.copy2(file, DIST / file.name)
    (DIST / '404.html').write_text('''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Page not found · MineNight</title><link rel="stylesheet" href="/styles.css"><main class="wrap" style="padding-block:80px"><p class="eyebrow ice-text">MINENIGHT</p><h1>Back to familiar ground.</h1><p class="muted">This page could not be found.</p><a class="button" href="/">Return to MineNight</a></main></html>''',encoding='utf-8')
    files = [{'file':str(p.relative_to(DIST)).replace('\\','/'),'bytes':p.stat().st_size} for p in DIST.rglob('*') if p.is_file()]
    (ROOT / 'docs').mkdir(exist_ok=True)
    (ROOT / 'docs' / 'build-manifest.json').write_text(json.dumps(files,indent=2),encoding='utf-8')
    print(f'Built {len(files)} static files. Total: {sum(f["bytes"] for f in files)/1024/1024:.2f} MiB. No runtime dependencies.')

if __name__ == '__main__': build()
