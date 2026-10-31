#!/bin/sh
# Checks the app's hand-written Meshtastic protobuf code (MeshProto.java) against the official
# Meshtastic Python package: messages made by Python are read by Java, and Java's messages are read by Python.
# Needs: python3 with meshtastic==2.7.11, a JDK, and org.json (json-20240303.jar) in this folder.
set -e
cd "$(dirname "$0")"
python3 - <<'PY'
from meshtastic.protobuf import mesh_pb2, portnums_pb2
fr=mesh_pb2.FromRadio(); p=fr.packet
setattr(p,'from',0xe3caefdd); p.to=0xffffffff; p.rx_time=1793441721; p.rx_snr=10.5; p.rx_rssi=-71; p.hop_start=1; p.hop_limit=1
pos=mesh_pb2.Position(latitude_i=-337611936, longitude_i=1218405345, altitude=78, time=1793441721, ground_speed=27, ground_track=12345678, sats_in_view=9)
p.decoded.portnum=portnums_pb2.POSITION_APP; p.decoded.payload=pos.SerializeToString()
fr2=mesh_pb2.FromRadio(); q=fr2.packet; setattr(q,'from',0xea0f8cab); q.decoded.portnum=1; q.decoded.payload=b'PG1 0123abcd rear x1y2'; q.rx_snr=-3.25
fr3=mesh_pb2.FromRadio(); fr3.my_info.my_node_num=0xea0f8cab
open('cases.txt','w').write('\n'.join(x.SerializeToString().hex() for x in (fr,fr2,fr3)))
PY
[ -f json-20240303.jar ] || curl -fsS -o json-20240303.jar https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar
rm -rf out && mkdir -p out/src/au/bhe/pilotgap
cp ../../android/app/src/main/java/au/bhe/pilotgap/MeshProto.java MeshProtoCheck.java out/src/au/bhe/pilotgap/
sed -i 's/^public class MeshProtoCheck/public class MeshProtoCheck/' out/src/au/bhe/pilotgap/MeshProtoCheck.java
javac -cp json-20240303.jar -d out out/src/au/bhe/pilotgap/*.java
java -cp out:json-20240303.jar au.bhe.pilotgap.MeshProtoCheck > result.txt
python3 - <<'PY'
import json
from meshtastic.protobuf import mesh_pb2
L=open('result.txt').read().splitlines(); fails=[]
def chk(n,ok):
    print(('PASS ' if ok else 'FAIL ')+n); ok or fails.append(n)
k,node,my,js=L[0].split(' ',3); j=json.loads(js)
chk('position read: unit, place, signal', k=='1' and node=='!e3caefdd' and abs(j['lat']+33.7611936)<1e-9 and abs(j['lon']-121.8405345)<1e-9 and j['snr']==10.5 and j['rssi']==-71 and j['t']==1793441721 and j['sats']==9)
k,node,my,js=L[1].split(' ',3); j=json.loads(js)
chk('text read', k=='2' and node=='!ea0f8cab' and j['text']=='PG1 0123abcd rear x1y2' and j['snr']==-3.25)
chk('own node number read', L[2].split(' ')[2]=='!ea0f8cab')
t=mesh_pb2.ToRadio(); t.ParseFromString(bytes.fromhex(L[3].split()[1]))
chk('text sent is a valid Meshtastic message to everyone', t.packet.to==0xffffffff and t.packet.decoded.portnum==1 and t.packet.decoded.payload==b'PG1 0123abcd truck abc123' and t.packet.id==12345)
t=mesh_pb2.ToRadio(); t.ParseFromString(bytes.fromhex(L[4].split()[1]))
chk('config request is valid', t.want_config_id==777)
raise SystemExit(1 if fails else 0)
PY
