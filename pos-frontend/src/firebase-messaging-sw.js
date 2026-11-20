importScripts('https://www.gstatic.com/firebasejs/9.23.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/9.23.0/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: "AIzaSyB52zwwfpkRUkhaQoacF62xCeE_R2mB2pY",
  authDomain: "atemporal-f0125.firebaseapp.com",
  projectId: "atemporal-f0125",
  storageBucket: "atemporal-f0125.firebasestorage.app",
  messagingSenderId: "314117413244",
  appId: "1:314117413244:web:47cfea19f16e77ef35acf9"
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage(function(payload) {
  const title = payload.notification?.title || 'Notificación';
  const options = {
    body: payload.notification?.body || '',
    data: payload.data || {},
  };
  // Si el mensaje trae "notification", el SDK de Firebase ya muestra el banner;
  // mostrarlo aqui tambien producia notificaciones duplicadas.
  if (!payload.notification) {
    self.registration.showNotification(title, options);
  }

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
