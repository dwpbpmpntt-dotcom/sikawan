// Service worker SIKAWAN: menyimpan halaman pembuka agar aplikasi bisa dipasang di layar utama.
const CACHE = 'sikawan-v1';
const FILES = ['./', './index.html', './manifest.webmanifest', './ikon-192.png', './ikon-512.png', './favicon.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES))); self.skipWaiting(); });
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(k => Promise.all(k.filter(x => x !== CACHE).map(x => caches.delete(x)))));
  self.clients.claim();
});
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url);
  if (u.origin !== location.origin || e.request.method !== 'GET') return;
  // Utamakan versi terbaru dari jaringan; pakai simpanan bila sedang luring
  e.respondWith(fetch(e.request).then(r => {
    const c = r.clone(); caches.open(CACHE).then(x => x.put(e.request, c)); return r;
  }).catch(() => caches.match(e.request).then(r => r || caches.match('./index.html'))));
});
