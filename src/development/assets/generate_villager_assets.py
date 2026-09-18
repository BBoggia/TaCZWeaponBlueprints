"""Rebuild the original villager pixel art, models, and job-site recipes.

Optional authoring utility: requires Python 3 and Pillow. Normal Gradle builds
use the checked-in resources and do not execute or depend on this script.
"""
from pathlib import Path
from PIL import Image,ImageDraw
import json,random
resources=Path(__file__).resolve().parents[2]/'main/resources'
root=resources/'assets/taczweaponblueprints';tex=root/'textures/block';tex.mkdir(parents=True,exist_ok=True)
def texture(name,base,style):
 im=Image.new('RGBA',(16,16),base);d=ImageDraw.Draw(im);r=random.Random(name)
 for y in range(16):
  for x in range(16):
   delta=r.choice([-7,-3,0,0,3,6]);im.putpixel((x,y),tuple(max(0,min(255,c+delta)) for c in base)+(255,))
 if style=='wood':
  for y in [3,7,11,15]:d.line((0,y,15,y),fill='#362c25')
  for x,y in [(4,0),(11,4),(7,8),(3,12)]:d.line((x,y,x,y+2),fill='#514031')
 elif style=='steel':
  d.rectangle((0,0,15,15),outline='#303c40');d.line((1,1,14,1),fill='#9daba8')
  for x,y in [(2,2),(13,2),(2,13),(13,13)]:d.point((x,y),fill='#c4c8b4')
 elif style=='blueprint':
  for y in [3,7,11]:d.line((1,y,14,y),fill='#386b7c')
  for x in [3,7,11]:d.line((x,1,x,14),fill='#386b7c')
  d.rectangle((3,4,12,10),outline='#a8d6d3');d.line((6,2,6,13),fill='#8fbdc0');d.line((2,13,11,13),fill='#c8dcc9')
 elif style=='paper':
  for y in [4,7,10]:d.line((3,y,12,y),fill='#8d9e94')
  d.rectangle((9,9,13,13),outline='#426e7a')
 elif style=='drawer':
  for y in [1,8]:
   d.rectangle((1,y,14,y+5),fill='#65503c',outline='#322b25');d.line((6,y+2,9,y+2),fill='#c8ae6b')
 elif style=='tray':
  d.rectangle((0,0,15,15),outline='#25383b')
  for x in [3,7,11]:d.rectangle((x,4,x+1,10),fill='#c1a365');d.point((x,3),fill='#e4c785')
 im.save(tex/(name+'.png'))
for args in [('villager_oak',(102,75,48),'wood'),('villager_steel',(76,91,95),'steel'),('villager_brass',(153,121,58),'steel'),('villager_blueprint',(31,74,91),'blueprint'),('villager_paper',(212,204,171),'paper'),('gunsmith_drawer',(78,60,45),'drawer'),('ballistics_tray',(46,64,67),'tray')]:texture(*args)
def cube(a,b,t,faces=None):return {'from':a,'to':b,'faces':{f:{'texture':'#'+(faces or {}).get(f,t)} for f in ['north','south','east','west','up','down']}}
def model(name,desk):
 e=[]
 for x in [1,12]:
  for z in [1,12]:e.append(cube([x,0,z],[x+3,11,z+3],'wood'))
 e += [cube([0,10,0],[16,12,16],'wood'),cube([1,3,11],[15,5,14],'wood'),cube([1,6,2],[15,10,14],'wood',{'north':'drawer'})]
 if desk:
  e += [cube([0,12,0],[16,13,16],'wood'),cube([2,13,2],[10,13.2,11],'paper'),cube([9,13,9],[15,14,15],'tray'),cube([11,13,2],[13,14,4],'brass'),cube([11.6,14,2.6],[12.4,16,3.4],'brass'),cube([10,15.3,2],[14,16,4],'steel'),cube([1,13,12],[7,13.3,14],'blueprint')]
 else:
  e += [cube([0,12,0],[16,13,16],'steel'),cube([2,13,7],[11,13.2,14],'blueprint'),cube([3,13,2],[10,14,6],'steel'),cube([3,14,2],[5,16,6],'steel'),cube([8,14,2],[10,16,6],'steel'),cube([4,14,3],[9,15,5],'brass'),cube([10,14,3],[14,15,4],'steel'),cube([13,13,3],[14,16,4],'wood')]
 out={'parent':'minecraft:block/block','textures':{'particle':'taczweaponblueprints:block/villager_oak','wood':'taczweaponblueprints:block/villager_oak','steel':'taczweaponblueprints:block/villager_steel','brass':'taczweaponblueprints:block/villager_brass','blueprint':'taczweaponblueprints:block/villager_blueprint','paper':'taczweaponblueprints:block/villager_paper','drawer':'taczweaponblueprints:block/gunsmith_drawer','tray':'taczweaponblueprints:block/ballistics_tray'},'elements':e,'display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.625,.625,.625]}}}
 (root/'models/block'/f'{name}.json').write_text(json.dumps(out,indent=2)+'\n')
model('gunsmith_station',False);model('ballistics_desk',True)
def clothing(name,zombie):
 im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im);research=name=='ballistics_researcher'
 coat='#254d68' if research else '#424e5b';shade='#183448' if research else '#2b343e'
 light='#376880' if research else '#5c6873';trim='#78b7c5' if research else '#c57a43'
 # Body, inflated robe and shoulders. Leave the last two robe rows and legs
 # transparent so biome clothing still supplies the hem and boots.
 d.rectangle((16,20,43,37),fill=shade)
 for x in [22,36]:d.rectangle((x,26,x+7,37),fill=coat)
 d.rectangle((0,44,27,61),fill=shade)
 for x in [6,20]:
  d.rectangle((x,44,x+7,61),fill=coat)
  d.line((x,44,x,60),fill=light);d.line((x+7,44,x+7,60),fill=shade)
 d.rectangle((6,38,21,43),fill=shade)
 # Arm UV differs: folded villagers have a separate horizontal forearm cube;
 # zombies use long, separate sleeves and retain bare hands at their ends.
 d.rectangle((44,22,59,25),fill=shade)
 for x in [44,48,52,56]:
  d.rectangle((x,26,x+3,34 if zombie else 33),fill=coat)
  d.line((x,26,x,32),fill=light)
  d.line((x,32,x+3,32),fill=trim)
 if not zombie:
  d.rectangle((44,38,59,41),fill=coat)
  d.rectangle((40,42,63,45),fill=coat)
  d.line((44,42,51,42),fill=light)
  d.rectangle((47,42,48,45),fill=shade)
 d.rectangle((0,52,27,53),fill='#292c2d')
 d.rectangle((9,52,10,53),fill='#c5a665')
 if research:
  # Cream collar above the folded arms, split coat, brass buttons and blueprint
  # seams. The large back reticle remains legible at vanilla texture density.
  d.rectangle((9,44,10,47),fill='#ddd4ae');d.point((10,47),fill='#b9aa86')
  d.line((10,48,10,61),fill=shade)
  for y in [49,55,58,61]:d.point((9,y),fill='#d5b665')
  for y in [55,60]:
   d.line((6,y,8,y),fill=trim);d.line((11,y,13,y),fill=trim)
  d.line([(23,45),(26,48),(23,51),(20,48),(23,45)],fill=trim)
  d.point((23,48),fill='#c3dce0')
  d.line((21,55,21,60),fill=light);d.line((26,55,26,60),fill=light)
  d.line((20,58,27,58),fill=trim)
  # Rolled notes on the coat's side, visible around the arms.
  d.rectangle((15,51,17,58),fill='#b9aa86');d.rectangle((15,51,16,58),fill='#e2d8b5')
  d.line((15,54,17,54),fill='#745333');d.point((16,54),fill='#d5b665')
  # The inflated hat cube supplies a single raised inspection lens and band.
  d.rectangle((32,10,63,10),fill='#4c4033')
  d.rectangle((44,8,47,11),fill='#997439');d.rectangle((45,9,46,10),fill='#5799be');d.point((45,9),fill='#badce2')
 else:
  # Broad copper sling on front and back; charcoal split tool tabard below arms.
  for x in [6,20]:
   for y in range(44,52):
    dx=min(6,(y-44)//2);d.line((x+dx,y,x+dx+1,y),fill=trim)
   d.rectangle((x+1,54,x+6,59),fill='#323334');d.line((x+3,54,x+3,59),fill='#222a30')
  d.rectangle((8,47,10,48),fill='#bbc3c4');d.point((9,47),fill=shade)
  # Silver caliper/wrench and three copper-tipped cartridges.
  d.line((8,54,8,58),fill='#b9c6c9');d.line((7,54,9,54),fill='#e0e4d9');d.point((7,53),fill='#b9c6c9');d.point((9,53),fill='#b9c6c9')
  for x in [11,13,15]:
   d.line((x,54,x,56),fill='#b2a580');d.point((x,54),fill='#d3884d')
  d.line((11,57,15,57),fill='#70503b')
  # Ear defenders on both sides, joined by a narrow crown band. The face and
  # nose remain entirely vanilla; no new model geometry is required.
  d.rectangle((43,0,44,7),fill='#272d33')
  for x in [40,47]:d.line((x,12,x,15),fill='#272d33')
  for x in [32,48]:
   d.rectangle((x+3,8,x+4,11),fill='#272d33')
   d.rectangle((x+2,11,x+5,15),fill='#252a2f')
   d.rectangle((x+3,12,x+4,14),fill='#b66b3c')
 if zombie:
  for xy in [(6,59),(7,60),(13,57),(12,61),(20,60),(26,59),(48,34),(56,34)]:d.point(xy,fill=(0,0,0,0))
 folder=root/'textures/entity'/('zombie_villager' if zombie else 'villager')/'profession';folder.mkdir(parents=True,exist_ok=True)
 im.save(folder/(name+'.png'));(folder/(name+'.png.mcmeta')).write_text('{"villager":{"hat":"full"}}\n')
for n in ['gunsmith','ballistics_researcher']:
 for z in [False,True]:clothing(n,z)
# Accessible recipes from the reviewed design.
for name,top in [('gunsmith_station',{'I':'iron_ingot','C':'copper_ingot'}),('ballistics_desk',{'I':'paper','C':'glass_pane'})]:
 out={'type':'minecraft:crafting_shaped','pattern':['III','PCP','P P'],'key':{'I':{'item':'minecraft:'+top['I']},'C':{'item':'minecraft:'+top['C']},'P':{'tag':'minecraft:planks'}},'result':{'item':'taczweaponblueprints:'+name}}
 (resources/'data/taczweaponblueprints/recipes'/(name+'.json')).write_text(json.dumps(out,indent=2)+'\n')
 adv={'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:'+top['I']]}]}},'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'taczweaponblueprints:'+name}}},'requirements':[['has_material','has_the_recipe']],'rewards':{'recipes':['taczweaponblueprints:'+name]}}
 p=(resources/'data/taczweaponblueprints/advancements/recipes/decorations');p.mkdir(parents=True,exist_ok=True);(p/(name+'.json')).write_text(json.dumps(adv,indent=2)+'\n')
