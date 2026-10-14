import urllib.request,json,time,zipfile,io
base='http://localhost:8015'
def get(path):return urllib.request.urlopen(base+path,timeout=20).read()
tests=json.loads(urllib.request.urlopen(urllib.request.Request(base+'/api/testcases/run',method='POST')).read())
assert tests['passed']==20
r=json.loads(urllib.request.urlopen(urllib.request.Request(base+'/api/jobs?kind=paper&compare=true',method='POST')).read())
for _ in range(100):
 s=json.loads(get('/api/jobs/'+r['id']))
 if s['state']!='RUNNING':break
 time.sleep(.1)
assert s['state']=='COMPLETED',s
z=zipfile.ZipFile(io.BytesIO(get('/api/jobs/'+r['id']+'/export?format=zip')))
assert z.testzip() is None
assert {'BAO_CAO.html','measurements.csv','performance-by-rows.csv','comparison.csv'}<=set(z.namelist())
assert b'<svg' in z.read('BAO_CAO.html')
assert b'threadCpuMs' in z.read('measurements.csv')
t=zipfile.ZipFile(io.BytesIO(get('/api/testcases/export')))
assert len(json.loads(t.read('testcases.json'))['cases'])==20
assert b'<svg' in t.read('TESTCASES.html')
print('PASS HTTP: ZIP analysis charts/measurements/comparison and ZIP test cases; all entries readable.')
