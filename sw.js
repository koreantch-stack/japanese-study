// 네트워크 우선: 온라인이면 항상 최신 자료, 오프라인이면 마지막으로 본 내용을 보여 줌
const CACHE = 'jstudy-v2';
self.addEventListener('install', e => { self.skipWaiting(); });
self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET' || !req.url.startsWith(self.location.origin)) return;
  const url = new URL(req.url);
  // lessons.json?v=시간값 처럼 매번 바뀌는 주소는 같은 키로 저장
  const key = new Request(url.origin + url.pathname);
  e.respondWith(
    fetch(req, { cache: 'no-store' }).then(res => {
      if (res && res.ok) {
        const copy = res.clone();
        caches.open(CACHE).then(c => c.put(key, copy));
      }
      return res;
    }).catch(() => caches.match(key).then(r => r || caches.match('./index.html')))
  );
});
