import json,time,urllib.request,urllib.error,zipfile,io
from pathlib import Path
import argparse
parser=argparse.ArgumentParser();parser.add_argument('--url',default='http://localhost:8017');args=parser.parse_args()
base=args.url
def api(path,data=None):
 with urllib.request.urlopen(base+path,data=data,timeout=40) as r:return json.load(r)
rows=[]
for percent,count in [(25,2),(50,4),(75,6),(100,9)]:
 j=api(f'/api/jobs?kind=paper&su=.23&sl=.1&sizePercent={percent}&repeats=3',b'')
 while True:
  s=api('/api/jobs/'+j['id'])
  if s['state']!='RUNNING':break
  time.sleep(.1)
 assert s['state']=='COMPLETED',s
 r=api('/api/jobs/'+j['id']+'/result');m=r['measurement'];resources=m['resources']
 assert r['report']['transactionCount']==count
 assert r['manifest']['sizePercent']==percent
 assert r['oracle']['state']=='PASSED',r['oracle']
 assert len(m['runMeasurements'])==3 and all(x['runtimeMs']>=0 and x['heapPeakMiB']>0 for x in m['runMeasurements'])
 assert resources['samples'] and resources['heapUsedMiBPeak']>0
 assert all(x['systemTotalMiB']>=x['systemUsedMiB']>=0 for x in resources['samples'])
 assert resources['processResidentMiBPeak']>0,resources
 with urllib.request.urlopen(base+'/api/jobs/'+j['id']+'/export?format=zip') as response:
  z=zipfile.ZipFile(io.BytesIO(response.read()));assert 'resource-samples.csv' in z.namelist();assert z.testzip() is None
 rows.append(r);print('PASS size',percent,'rows',count,'oracle, 3 runs, RAM and ZIP',flush=True)
try:api('/api/jobs?kind=paper&sizePercent=0',b'');raise AssertionError('Invalid size accepted')
except urllib.error.HTTPError as e:assert e.code==400
cases=api('/api/testcases/run',b'')
assert cases['passed']==cases['total']==20
assert all(c['memoryAfter']['heapUsedMiB']>0 for c in cases['cases'])
Path('tmp/resource-result.json').write_text(json.dumps(rows[-1]),encoding='utf-8')
print('PASS invalid size and 20 cases with heap/system snapshots')
