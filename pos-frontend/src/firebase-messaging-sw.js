importScripts('https://www.gstatic.com/firebasejs/9.23.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/9.23.0/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: "AIzaSyA-wTcvwf5gh8mtYFaYXN8VeY-kxYiQ8v8",
  authDomain: "pos-si2.firebaseapp.com",
  projectId: "pos-si2",
  storageBucket: "pos-si2.firebasestorage.app",
  messagingSenderId: "181449830908",
  appId: "1:181449830908:web:eab5f979040782aa218279"
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage(function(payload) {
  const title = payload.notification?.title || 'Notificación';
  const options = {
    body: payload.notification?.body || '',
    data: payload.data || {},
  };
  self.registration.showNotification(title, options);

  self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
    for (const client of windowClients) {
      client.postMessage({
        type: 'STOCK_ALERT',
        payload: {
          title: title,
          body: options.body,
          data: payload.data || {},
        },
      });
    }
  });
});

self.addEventListener('notificationclick', function(event) {
  event.notification.close();
  const url = '/?notification=' + encodeURIComponent(JSON.stringify(event.notification.data || {}));
  event.waitUntil(clients.matchAll({type: 'window'}).then(windowClients => {
    for (let client of windowClients) {
      if (client.url === url && 'focus' in client) return client.focus();
    }
    if (clients.openWindow) return clients.openWindow(url);
  }));
});
