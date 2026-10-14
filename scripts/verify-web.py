"""Integration checks against a running local Java server; Python standard library only."""
import argparse
import json
import time
import urllib.request
import urllib.error

parser = argparse.ArgumentParser()
parser.add_argument('--url', default='http://localhost:8000')
parser.add_argument('--real-data', action='store_true')
parser.add_argument('--presets', action='store_true')
args = parser.parse_args()

def call(path, data=None, expected=200, headers=None):
    req = urllib.request.Request(args.url+path, data=data, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            status, body = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, body = error.code, error.read()
    assert status == expected, (path, status, body[:300])
    return body

def analyze(query, data=b''):
    job = json.loads(call('/api/jobs?'+query, data, 202))
    deadline = time.monotonic()+180
    while time.monotonic() < deadline:
        state = json.loads(call('/api/jobs/'+job['id']))
        if state['state'] == 'FAILED':
            return state, None
        if state['state'] == 'COMPLETED':
            return state, json.loads(call('/api/jobs/'+job['id']+'/result'))
        time.sleep(.15)
    raise AssertionError('Analysis timed out')

assert b'PIHAUPA' in call('/')
assert json.loads(call('/api/verify'))['passed']
_, paper = analyze('kind=paper&su=.23&sl=.10&compare=true')
assert paper['report']['transactionCount'] == 9
assert paper['comparison']['sameHaups']
assert paper['report']['stats']['rescanCount'] == 1
assert [b['totalTransactionUtility'] for b in paper['report']['batches']] == [141,173,240]
assert paper['report']['batches'][-1]['largePatterns'] == paper['comparison']['tight']['batches'][-1]['largePatterns']
assert 'Bước 3'.encode() in call('/api/jobs/'+paper['id']+'/export?format=txt')
assert b'Eq12Left' in call('/api/jobs/'+paper['id']+'/export?format=csv')
invalid=json.loads(call('/api/jobs?su=.1&sl=.23', b'', 400))
assert invalid['code']=='INVALID_THRESHOLDS' and invalid['action']
call('/api/jobs?kind=catalog&dataset=../outside', b'', 400)
call('/api/jobs/does-not-exist', expected=404)
call('/api/jobs', b'', 403, {'Origin':'https://unrelated.example'})
_, uploaded = analyze('kind=upload&format=transactions&name=transactions.txt&su=.5&sl=.25',b'1 2:9:4 5\n1:2:2\n2:3:3\n')
assert uploaded['report']['transactionCount'] == 3
assert uploaded['report']['batches'][-1]['totalTransactionUtility'] == 14
failed, _ = analyze('kind=upload&format=transactions',b'1 2:99:4 5\n')
assert failed['state'] == 'FAILED' and 'TU' in failed['error']
assert failed['code']=='TU_MISMATCH' and failed['action']
cases=json.loads(call('/api/testcases/run', b''))
assert cases['state']=='PASSED' and cases['passed']==20 and cases['total']==20
assert len({c['id'] for c in cases['cases']})==20
assert all(c['actual']==c['expected'] for c in cases['cases'])
print('PASS HTTP: 20 executable test cases and structured error codes.',flush=True)
print('PASS HTTP: paper, comparison, export, uploads, bad TU, thresholds, catalog boundaries, origin.')
if args.real_data:
    datasets=json.loads(call('/api/datasets'))
    assert len(datasets)==6
    for name, rows, tu, su, sl in [('NewChess',3196,36864920448,.23,.10),('NewMushroom',8416,60998108464,.05,.02)]:
        dataset=next(d for d in datasets if d['name'].lower().startswith(name.lower()))
        _, result=analyze(f'kind=catalog&dataset={dataset["id"]}&su={su}&sl={sl}')
        assert result['report']['transactionCount']==rows
        batch=result['report']['batches'][-1]
        assert batch['totalTransactionUtility']==tu
        if name=='NewMushroom':
            assert len(batch['largePatterns'])==5
            assert len(batch['preLargePatterns'])==311
        print(f'PASS HTTP real data: {name}, rows={rows}, TU={tu}, HAUP={len(batch["largePatterns"])}')

if args.presets:
    expected={'Accidents':3,'Chainstore':1,'NewChess':8,'NewConnects':1,'NewMushroom':1,'Retail':1}
    datasets=json.loads(call('/api/datasets'))
    for dataset in datasets:
        profile=dataset['profile']
        assert profile and 0<profile['sl']<profile['su']<1
        _,result=analyze(f'kind=catalog&dataset={dataset["id"]}&su={profile["su"]}&sl={profile["sl"]}')
        report=result['report']
        assert report['upperThreshold']==profile['su'] and report['lowerThreshold']==profile['sl']
        name=dataset['name'].split('-')[0]
        assert report['transactionCount']==profile['rows']
        assert report['batches'][-1]['totalTransactionUtility']==profile['totalTU']
        assert len(report['batches'][-1]['largePatterns'])==expected[name]
        status=json.loads(call('/api/jobs/'+result['id']))
        assert status['phase']=='COMPLETED' and status['importMs'] is not None and status['algorithmMs'] is not None
        assert any('Đọc hoàn tất' in line for line in status['lines'])
        print(f'PASS HTTP preset: {name}, HAUP={expected[name]}, live timing + heap + read notification',flush=True)
