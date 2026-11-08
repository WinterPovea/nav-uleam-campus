import pandas as pd, math, re, unicodedata
n=pd.read_csv('nodos.csv'); a=pd.read_csv('aristas.csv')
OUT='/home/claude/UleamNavigator/app/src/main/assets/'
def hav(la1,lo1,la2,lo2):
    R=6371000;p1,p2=math.radians(la1),math.radians(la2)
    dp=p2-p1;dl=math.radians(lo2-lo1)
    h=math.sin(dp/2)**2+math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*R*math.asin(math.sqrt(h))
def norm(s):
    s=unicodedata.normalize('NFD',str(s).lower())
    return ''.join(c for c in s if unicodedata.category(c)!='Mn')
fixes={'Aduitorio':'Auditorio','adminision':'admision','Tasty food':'Tasty Food'}
def fix(t):
    for k,v in fixes.items(): t=t.replace(k,v)
    return t
n['Descri']=n.Descri.map(fix)
n.loc[n.ID==492,'Descri']=n.loc[n.ID==492,'Descri'].str.replace('B-25','B-23')
n['tipo']=n.Categoria.str.replace(' ','_')
ids=set(n.ID)
# aristas
a['k']=a.apply(lambda r:(min(r.ID_Origen,r.ID_Destino),max(r.ID_Origen,r.ID_Destino)),axis=1)
rep=[]
bad=a[~a.ID_Origen.isin(ids)|~a.ID_Destino.isin(ids)]; rep.append(f"Aristas con nodo inexistente (omitidas): {bad[['ID_Origen','ID_Destino']].values.tolist()}")
a=a.drop(bad.index)
dup=a.k.duplicated().sum(); rep.append(f"Aristas duplicadas eliminadas: {dup}")
a=a.drop_duplicates('k')
co=n.set_index('ID')
a['distancia_m']=a.apply(lambda r:round(hav(co.Latitud[r.ID_Origen],co.Longitud[r.ID_Origen],co.Latitud[r.ID_Destino],co.Longitud[r.ID_Destino]),2),axis=1)
a['bidireccional']=1
a['tiene_escaleras']=a.Notas.fillna('').str.contains('scalera',case=False).astype(int)
a[['ID_Origen','ID_Destino','distancia_m','bidireccional','tiene_escaleras','Notas']].rename(columns={'ID_Origen':'id_origen','ID_Destino':'id_destino','Notas':'nota'}).to_csv(OUT+'aristas.csv',index=False)
used=set(a.ID_Origen)|set(a.ID_Destino)
iso=n[~n.ID.isin(used)]; rep.append(f"Nodos aislados (omitidos): {iso.ID.tolist()}")
n=n[n.ID.isin(used)]
n[['ID','Latitud','Longitud','tipo','Descri']].rename(columns={'ID':'id','Latitud':'latitud','Longitud':'longitud','Descri':'descripcion_interna'}).to_csv(OUT+'nodos.csv',index=False)
# DESTINO conectado a CONEXION?
tipo=n.set_index('ID').tipo
nb={i:set() for i in n.ID}
for _,r in a.iterrows(): nb[r.ID_Origen].add(r.ID_Destino); nb[r.ID_Destino].add(r.ID_Origen)
nocon=[i for i in n.ID if tipo[i]=='DESTINO' and not any(tipo[j]=='CONEXION' for j in nb[i])]
rep.append(f"DESTINO sin vecino CONEXION: {nocon}")
long_=a[a.distancia_m>80][['ID_Origen','ID_Destino','distancia_m']].values.tolist(); rep.append(f"Aristas >80m: {long_}")
# lugares
rows=[]
def cat(nm,t):
    l=nm.lower()
    if t=='ENTRADA':return 'PUERTA'
    if t=='ZONA_SEGURA':return 'ZONA_SEGURA'
    if re.search('tasty|agromarket',l):return 'COMIDA'
    if re.search('cancha|coliseo|estadio|gradas',l):return 'DEPORTE'
    if re.search('plaza|paraninfo|aula magna|cine|bosque',l):return 'CULTURA'
    if re.search('bienestar|rectorado|diop|empresa publica|estudios y construcciones|vicerrectorado|infraestructura',l):return 'ADMINISTRATIVO'
    return 'BLOQUE'
for _,r in n[n.tipo!='CONEXION'].iterrows():
    d=r.Descri; cod=''
    m=re.search(r'[Bb]loque (B-\d+)',d)
    if m: cod=m.group(1); nombre=f"Bloque {cod}"+(" (acceso 2)" if r.ID==314 else "")
    else:
        m2=re.search(r'\bB-\d+',d); cod=m2.group(0) if m2 else ''
        nombre=re.sub(r'^(Ingreso|Entrada( hacia| a)?)\s+','',d).strip()
        nombre=nombre[0].upper()+nombre[1:]
        if r.tipo=='ENTRADA': nombre=nombre.title().replace('Puerta','Puerta')
    c=cat(nombre,r.tipo)
    et=''
    rows.append(dict(id_nodo=r.ID,codigo=cod,nombre=nombre,descripcion='',categoria=c,etiquetas=et,busqueda=norm(f"{nombre} {cod} {et}").strip(),activo=1))
pd.DataFrame(rows).to_csv(OUT+'lugares.csv',index=False)
print('\n'.join(rep)); print(len(n),'nodos',len(a),'aristas',len(rows),'lugares')
print(pd.DataFrame(rows).categoria.value_counts())
