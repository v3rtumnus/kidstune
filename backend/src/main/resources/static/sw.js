/**
 * KidsTune Service Worker – handles Web Push notifications for the dashboard.
 */

self.addEventListener('push', function (event) {
    let data = { title: 'KidsTune', body: 'Neuer Musikwunsch', url: '/web/requests' };
    try {
        data = Object.assign(data, event.data.json());
    } catch (_) { /* malformed payload – use defaults */ }

    event.waitUntil(
        self.registration.showNotification(data.title, {
            body: data.body,
            icon: '/favicon.ico',
            tag:  'kidstune-request',
            data: { url: data.url }
        })
    );
});

self.addEventListener('notificationclick', function (event) {
    event.notification.close();
    const targetUrl = (event.notification.data && event.notification.data.url)
        ? event.notification.data.url
        : '/web/requests';

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (windowClients) {
            for (const client of windowClients) {
                if (client.url.includes(targetUrl) && 'focus' in client) {
                    return client.focus();
                }
            }
            if (clients.openWindow) {
                return clients.openWindow(targetUrl);
            }
        })
    );
});
