import hashlib
import sqlite3
import time


def check_community(check, player, owner, other, h, ho, hx, bridge, root):
    now = int(time.time())
    uuid = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
    fingerprint = 'b' * 64
    vote = {'id': hashlib.sha256(b'fixture-confirmed-event').hexdigest(), 'uuid': uuid,
            'name': 'Alpha_One', 'platform': 'Minecraft.buzz', 'confirmed': now}
    batch = {'bans': [{'uuid': uuid, 'name': 'PlayerIGN', 'fingerprint': fingerprint}], 'votes': [vote]}
    check(player.get('/api/votes').json()['votes'] == [], 'Latest Votes starts empty without invented events')
    check(player.post('/api/community-bridge/sync', json=batch).status_code == 401, 'Public users cannot forge confirmed votes or bans')
    check(player.post('/api/community-bridge/sync', json=batch, headers=bridge).status_code == 200, 'Trusted game bridge delivers real event fields')
    player.post('/api/community-bridge/sync', json=batch, headers=bridge)
    public = player.get('/api/votes').json()['votes']
    check(len(public) == 1 and public[0]['platform'] == 'Minecraft.buzz', 'Confirmed votes deduplicate replayed events and expose platform')
    invalid = {**batch, 'votes': [{**vote, 'uuid': '../config.php'}]}
    check(player.post('/api/community-bridge/sync', json=invalid, headers=bridge).status_code == 422, 'Vote identities cannot inject paths')
    data = {'category': 'ban-appeal', 'subject': 'Please review my ban', 'body': 'Please review the evidence of this ban.'}
    appeal = player.post('/api/tickets', data=data, headers=h).json()['id']
    url = '/api/appeals/' + appeal
    action = {'action': 'accept', 'reason': 'Evidence was reviewed by authorized staff.'}
    check(player.post(url, json=action, headers=h).status_code == 403, 'Appeal creators cannot approve their own appeal')
    check(other.get(url).status_code == 404, 'Other players cannot read appeal or audit details')
    check(owner.post(url, json=action).status_code == 403, 'Appeal decisions enforce CSRF')
    check(owner.post(url, json={**action, 'action': 'unban'}, headers=ho).status_code == 409, 'Unban requires prior acceptance')
    check(owner.post(url, json=action, headers=ho).status_code == 200, 'Staff can accept a synchronized ban appeal')
    check(owner.post(url, json=action, headers=ho).status_code == 409, 'Concurrent or duplicate appeal decisions cannot overwrite review')
    check(owner.post(url, json={**action, 'action': 'unban'}, headers=ho).status_code == 200, 'Accepted appeal exposes a separate unban action')
    check(owner.post(url, json={**action, 'action': 'unban'}, headers=ho).status_code == 409, 'Duplicate unban clicks cannot enqueue multiple jobs')
    jobs = player.post('/api/community-bridge/sync', json=batch, headers=bridge).json()['jobs']
    check(jobs[0]['uuid'] == uuid and jobs[0]['fingerprint'] == fingerprint, 'Unban targets the frozen UUID and exact reviewed ban')
    check(owner.get(url).json()['review']['state'] == 'queued', 'Queueing an unban never claims server completion')
    ack = {'ticket': appeal, 'ok': True}
    check(player.post('/api/community-bridge/ack', json=ack).status_code == 401, 'Only authenticated game worker can confirm unbans')
    player.post('/api/community-bridge/ack', json=ack, headers=bridge)
    player.post('/api/community-bridge/ack', json=ack, headers=bridge)
    result = owner.get(url).json()
    check(result['review']['state'] == 'complete' and len(result['audit']) == 3, 'Acknowledgements are idempotent and staff/reason/date are audited')
    check(all(e['staff_name'] == '7ltq' and e['created'] > 0 and e['reason'] for e in result['audit']), 'Every appeal action has a verified staff identity and reason')
    rejected = player.post('/api/tickets', data=data, headers=h).json()['id']
    check(owner.post('/api/appeals/' + rejected, json={**action, 'action': 'reject'}, headers=ho).status_code == 200, 'Staff can reject an appeal')
    check(owner.post('/api/appeals/' + rejected, json={**action, 'action': 'unban'}, headers=ho).status_code == 409, 'Rejected appeals cannot unban players')
    owner.post('/api/tickets/' + appeal + '/status', json={'status': 'closed', 'reason': 'Appeal completed.'}, headers=ho)
    owner.request('DELETE', '/api/tickets/' + appeal, json={'reason': 'Ticket retention test.'}, headers=ho)
    with sqlite3.connect(root / 'support-data/php-tests/minenight-support/data/tickets.sqlite3') as db:
        count = db.execute('SELECT count(*) FROM moderation_audit WHERE ticket=?', (appeal,)).fetchone()[0]
    check(count == 5, 'Moderation audit survives deletion of the associated ticket')
    check(player.get('/api/moderation-audit').status_code == 403, 'Moderation audit is restricted to current authorized staff')
    check(owner.get('/api/moderation-audit').status_code == 200, 'Staff can inspect retained audit records after ticket deletion')
    cache=root / 'support-data/php-tests/minenight-support/data/skins'
    cache.mkdir(exist_ok=True)
    from PIL import Image
    for kind in ['head','body']:
        Image.new('RGBA',(64,64),(40,100,180,255)).save(cache / (uuid+'-'+kind+'.png'))
    skin=player.get('/api/skins/'+uuid+'/head')
    check(skin.status_code == 200 and skin.headers['content-type'] == 'image/png', 'UUID skin endpoint serves cached PNGs')
    check('max-age=3600' in skin.headers['cache-control'], 'Skin responses enable browser caching')
    check(player.get('/api/skins/'+uuid+'/head',headers={'If-None-Match':skin.headers['etag']}).status_code == 304, 'Unchanged skin responses support conditional requests')
    check(player.get('/api/skins/cccccccc-cccc-4ccc-8ccc-cccccccccccc/head').status_code == 404, 'Skin proxy rejects unknown UUIDs before accessing the provider')
