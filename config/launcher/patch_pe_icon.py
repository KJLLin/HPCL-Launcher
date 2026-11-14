import struct, sys, zipfile
from PIL import Image

EXE = sys.argv[1]
LOGO_DIR = '/workspace/hpcl/config/launcher'
d = bytearray(open(EXE,'rb').read())

pe = struct.unpack_from('<I',d,0x3c)[0]
nsec = struct.unpack_from('<H',d,pe+6)[0]
opt_sz = struct.unpack_from('<H',d,pe+20)[0]
opt = pe+24
magic = struct.unpack_from('<H',d,opt)[0]
dd = opt + (112 if magic==0x20b else 96)
rsrc_rva, rsrc_sz = struct.unpack_from('<II', d, dd+16)
sec = opt+opt_sz
secs=[]
for i in range(nsec):
    o=sec+i*40
    name=bytes(d[o:o+8]).rstrip(b'\0').decode('latin1')
    vs,va,rs,ptr = struct.unpack_from('<IIII',d,o+8)
    secs.append((name,va,vs,ptr,rs))
def r2o(rva):
    for n,va,vs,ptr,rs in secs:
        if va<=rva<va+max(vs,rs): return ptr+(rva-va)
    return None
base = r2o(rsrc_rva)
assert base is not None, 'no .rsrc'

def walk(rel, path=()):
    off=base+rel
    named,idd = struct.unpack_from('<HH',d,off+12)
    res=[]
    for i in range(named+idd):
        e=base+rel+16+i*8
        nameid,dataoff = struct.unpack_from('<II',d,e)
        nm=nameid & 0x7fffffff
        child=dataoff & 0x7fffffff
        if dataoff & 0x80000000:
            res += walk(child, path+(nm,))
        else:
            rva,sz,cp,resv = struct.unpack_from('<IIII',d,base+child)
            res.append((path+(nm,), rva, sz, child))
    return res

leaves = walk(0)
icons = [(path,rva,sz,child) for path,rva,sz,child in leaves if path[0]==3]
groups = [(path,rva,sz,child) for path,rva,sz,child in leaves if path[0]==14]
assert len(icons)==4 and len(groups)==1, (len(icons),len(groups))

def make_dib(size):
    im = Image.open(f'{LOGO_DIR}/hpcl-logo-{size}.png').convert('RGBA').resize((size,size), Image.NEAREST)
    W=H=size
    hdr = struct.pack('<IiiHHIIiiII', 40, W, 2*H, 1, 32, 0, W*H*4, 0,0,0,0)
    xor=bytearray()
    px=im.load()
    for y in range(H-1,-1,-1):
        for x in range(W):
            r,g,b,a = px[x,y]
            xor += bytes((b,g,r,a))
    mask_row = ((W+31)//32)*4
    return bytes(hdr)+bytes(xor)+bytes(mask_row*H)

# 256px frame is stored as a PNG. The RT_ICON slot is small (1521 bytes),
# so use an opaque 128-colour palette PNG (fully-opaque square tile, no alpha
# needed) which stays well under the slot while remaining crisp.
import io
_im256 = Image.open(f'{LOGO_DIR}/hpcl-logo-256.png').convert('RGB').quantize(colors=128, method=2)
_buf = io.BytesIO(); _im256.save(_buf, format='PNG', optimize=True)
png256 = _buf.getvalue()
assert len(png256) <= 1521, f'256 png still too big {len(png256)}'

# 按数据条目里记录的尺寸判定（DIB header biWidth），或 PNG
newdata={}
for path,rva,sz,child in icons:
    off=r2o(rva)
    head=bytes(d[off:off+8])
    if head.startswith(b'\x89PNG'):
        size=256; data=png256
    else:
        biSize=struct.unpack_from('<I',d,off)[0]
        size=struct.unpack_from('<i',d,off+4)[0]
        data=make_dib(size)
    assert len(data)<=sz, f'icon {size} too big {len(data)}>{sz}'
    # 写入数据 + 零填充
    d[off:off+sz] = data + b'\0'*(sz-len(data))
    # 更新 IMAGE_RESOURCE_DATA_ENTRY.Size
    struct.pack_into('<I', d, base+child+4, len(data))
    newdata[path[-1] if False else size] = (len(data), path)
    print(f'RT_ICON {size}x{size}: {sz} -> {len(data)} bytes')

# 更新 GRPICONDIR 每个 entry 的 dwBytesInRes
gpath,grva,gsz,gchild = groups[0]
goff=r2o(grva)
reserved,typ,count=struct.unpack_from('<HHH',d,goff)
print('GRPICONDIR count', count)
# entry 结构: BYTE w,h,cc,res; WORD planes,bitcount; DWORD bytesInRes; WORD id
by_size = {size:v[0] for size,v in newdata.items()}
for k in range(count):
    o=goff+6+k*14
    w,h,cc,res,planes,bc,bir,nid = struct.unpack_from('<BBBBHHIH',d,o)
    dim = w or 256
    if dim in by_size:
        struct.pack_into('<I', d, o+8, by_size[dim])
        print(f'  group entry {k}: {dim}x{dim} id={nid} bytes {bir} -> {by_size[dim]}')

open(EXE,'wb').write(bytes(d))
print('PATCHED', EXE)
