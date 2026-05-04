import * as admin from 'firebase-admin';

let initialized = false;

export function getFirebaseAdmin(): admin.app.App {
  if (!initialized) {
    const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
    if (!raw) {
      console.warn('⚠️  FIREBASE_SERVICE_ACCOUNT not set — FCM push disabled');
      // Return a dummy app that will gracefully fail on send
    } else {
      try {
        const serviceAccount = JSON.parse(raw);
        admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
        initialized = true;
        console.log('✅ Firebase Admin initialized');
      } catch (e) {
        console.error('❌ Failed to init Firebase Admin:', e);
      }
    }
  }
  return admin.app();
}

/**
 * Send a data-only FCM push to a list of device tokens.
 * Data-only = our SleekFirebaseMessagingService handles it and shows a styled notification.
 */
export async function sendFcmPush(
  tokens:     string[],
  data: {
    senderName: string;
    content:    string;
    chatId:     string;
    chatName:   string;
  },
): Promise<void> {
  if (!initialized || tokens.length === 0) return;
  try {
    const app = getFirebaseAdmin();
    await app.messaging().sendEachForMulticast({
      tokens,
      data: {
        senderName: data.senderName,
        content:    data.content.slice(0, 200), // truncate for payload size
        chatId:     data.chatId,
        chatName:   data.chatName,
      },
      android: {
        priority: 'high',
      },
    });
  } catch (e) {
    console.error('FCM send error:', e);
  }
}
