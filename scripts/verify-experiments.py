"""Experiment workflow integration: real HTTP, independent oracle, repeated/isolated runs and saved archives."""
import argparse,hashlib,io,json,statistics,time,urllib.request,urllib.error,zipfile
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--url',default='http://localhost:8016');args=parser.parse_args()
def call(path,data=None,expected=200):
 try:
  with urllib.request.urlopen(urllib.request.Request(args.url+path,data=data),timeout=120) as response:code,body=response.status,response.read()
 except urllib.error.HTTPError as e:code,body=e.code,e.read()
 assert code==expected,(path,code,body[:300]);return body
def wait(job):
 for _ in range(600):
  status=json.loads(call('/api/jobs/'+job['id']))
  if status['state']!='RUNNING':return status
  time.sleep(.1)
 raise AssertionError('Job timeout')
job=json.loads(call('/api/jobs?kind=paper&su=.23&sl=.1&compare=true&repeats=3',b'',202))
assert wait(job)['state']=='COMPLETED'
r=json.loads(call('/api/jobs/'+job['id']+'/result'))
assert r['oracle']['state']=='PASSED' and r['oracle']['expectedHaups']==3
m=r['measurement'];assert len(m['samplesMs'])==3 and statistics.median(m['samplesMs'])==m['medianMs']
assert m['minMs']<=m['medianMs']<=m['maxMs']
assert r['comparison']['isolated'] and r['comparison']['sameHaups']
assert r['comparison']['original']['stats']['rescanCount']==2 and r['comparison']['tight']['stats']['rescanCount']==1
assert len(r['manifest']['sha256'])==64 and r['manifest']['warmupRuns']==1
archive=zipfile.ZipFile(io.BytesIO(call('/api/runs/'+job['id']+'/export')))
assert archive.testzip() is None
for filename in ['manifest.json','verification.json','timing-samples.csv','comparison.csv','BAO_CAO.html']:assert filename in archive.namelist()
assert json.loads(archive.read('manifest.json'))['sha256']==r['manifest']['sha256']
assert len(archive.read('timing-samples.csv').decode().splitlines())==4
assert any(x['id']==job['id'] for x in json.loads(call('/api/runs')))
assert json.loads(call('/api/runs/'+job['id']))['oracle']['state']=='PASSED'
call('/api/jobs?kind=paper&repeats=2',b'',400)
call('/api/runs/../outside',expected=404)
Path('tmp').mkdir(exist_ok=True);Path('tmp/experiment-result.json').write_text(json.dumps(r),encoding='utf-8')
print('PASS experiment: oracle, median/range, isolated comparison, persistent result/ZIP and manifest',flush=True)
datasets=json.loads(call('/api/datasets'))
if datasets:
 dataset=next((d for d in datasets if 'Chainstore' in d['name']),datasets[0])
 pending=json.loads(call('/api/jobs?kind=catalog&dataset='+dataset['id']+'&su=.0001&sl=.00005&repeats=5',b'',202))
 call('/api/jobs/'+pending['id']+'/cancel',b'',202)
 assert wait(pending)['state']=='CANCELLED'
 assert not any(x['id']==pending['id'] for x in json.loads(call('/api/runs')))
 after=json.loads(call('/api/jobs?kind=paper',b'',202));assert wait(after)['state']=='COMPLETED'
 print('PASS cancellation: no partial archive, cleanup, worker accepts next run',flush=True)
 chess=next((d for d in datasets if 'NewChess' in d['name']),None)
 if chess:
  p=chess['profile'];j=json.loads(call(f"/api/jobs?kind=catalog&dataset={chess['id']}&su={p['su']}&sl={p['sl']}",b'',202));assert wait(j)['state']=='COMPLETED'
  real=json.loads(call('/api/jobs/'+j['id']+'/result'));assert real['report']['transactionCount']==3196 and len(real['report']['batches'][-1]['largePatterns'])==8
  assert real['oracle']['state']=='NOT_CHECKED' and real['manifest']['hashKind']=='raw-file-sha256'
  Path('tmp/experiment-large.json').write_text(json.dumps(real),encoding='utf-8')
  print('PASS NewChess: profile unchanged, 8 HAUP, raw SHA-256, oracle scope honest',flush=True)

