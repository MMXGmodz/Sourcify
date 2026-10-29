'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {musicApi}=require('../src/music-api.cjs');
test('shared search preserves the original extractor request and validates input',async()=>{
 const calls=[],api=musicApi(async args=>{calls.push(args);return JSON.stringify({entries:[{id:'abcdefghi01',title:'Track',channel:'Artist',duration:60},{id:'../../path'}]});});
 assert.equal((await api.search('Artist song'))[0].artist,'Artist');assert(calls[0].includes('ytsearch20:Artist song'));
 await assert.rejects(api.search(''));await assert.rejects(api.search('x',51));
});
test('shared resolver caches valid HTTPS sources and isolates audio/video',async()=>{
 let n=0;const api=musicApi(async args=>{n++;assert(args.includes('https://www.youtube.com/watch?v=abcdefghi01'));return JSON.stringify({url:'https://rr1.googlevideo.com/videoplayback',duration:123});});
 assert.equal((await api.remote('abcdefghi01')).duration,123);await api.remote('abcdefghi01');assert.equal(n,1);await api.remote('abcdefghi01',true);assert.equal(n,2);
 await assert.rejects(api.remote('path'));await assert.rejects(musicApi(async()=>'{"url":"http://localhost/x"}').remote('abcdefghi01'));
});
test('concurrent metadata and stream requests share one extractor process',async()=>{
 let n=0;const api=musicApi(async()=>{n++;await new Promise(r=>setTimeout(r,20));return JSON.stringify({url:'https://rr1.googlevideo.com/x',title:'Song',artist:'Artist',album:'Album'});});
 const [a,b]=await Promise.all([api.remote('abcdefghi01'),api.remote('abcdefghi01')]);assert.equal(n,1);assert.equal(a,b);assert.equal(a.metadata.album,'Album');
});
