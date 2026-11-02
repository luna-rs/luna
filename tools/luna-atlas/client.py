"""Fast command-line access to a running desktop atlas. Author: lare96."""
import argparse
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request


def main():
    parser=argparse.ArgumentParser(description='Query the running local Luna Atlas desktop or service.')
    parser.add_argument('--state',type=Path,default=Path(__file__).resolve().parent/'.local')
    parser.add_argument('operation',choices=['summary','catalog','contains','preferences','npcs','npc','grid','project-open','reload','position','search','propose','validate','drafts','save','render'])
    parser.add_argument('--json',default='{}',help='JSON request or @request.json')
    options=parser.parse_args()
    endpoint=json.loads((options.state/'endpoint.json').read_text(encoding='utf-8'))
    port=endpoint.get('port')
    if type(port) is not int or not 1<=port<=65535:
        raise ValueError('Invalid local atlas endpoint.')
    raw=options.json
    args=json.loads(Path(raw[1:]).read_text(encoding='utf-8')) if raw.startswith('@') else json.loads(raw)
    if not isinstance(args,dict):
        raise ValueError('Request must be a JSON object.')
    path='/api/'+('drafts' if options.operation=='save' else options.operation)
    if options.operation in ('project-open','reload'):
        path='/api/project/'+('open' if options.operation=='project-open' else 'reload')
    base=f'http://127.0.0.1:{port}'
    if options.operation in ('summary','catalog','contains','preferences','npcs','npc','grid','position','search','drafts'):
        request=urllib.request.Request(base+path+'?'+urllib.parse.urlencode({k:v for k,v in args.items() if v is not None}))
    else:
        request=urllib.request.Request(base+path,json.dumps(args).encode(),headers={'Content-Type':'application/json'})
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(request,timeout=120) as response:
            result=json.load(response)
    except urllib.error.HTTPError as error:
        raise ValueError(json.load(error).get('error','Atlas request failed.')) from None
    if options.operation=='summary' and result.get('fingerprints')!=endpoint.get('fingerprints'):
        raise ValueError('Atlas endpoint does not match the selected cache snapshot.')
    print(json.dumps(result,indent=2,ensure_ascii=True))


if __name__=='__main__':
    try:
        main()
    except (OSError,ValueError,KeyError) as error:
        raise SystemExit('Cannot query Luna Atlas: '+str(error)+'. Start the desktop app or use atlas.py for an offline query.')
