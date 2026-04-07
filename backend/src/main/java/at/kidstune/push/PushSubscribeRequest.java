package at.kidstune.push;

/**
 * Body sent by the browser when registering a push subscription.
 * Matches the output of {@code JSON.stringify(pushSubscription.toJSON())}.
 */
public record PushSubscribeRequest(String endpoint, String p256dh, String auth) {}
