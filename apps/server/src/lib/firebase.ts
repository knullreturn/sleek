/* eslint-disable @typescript-eslint/no-var-requires */

let adminModule: typeof import('firebase-admin') | null = null;
let initialized = false;

async function getAdmin() {
  if (!adminModule) {
    try {
      adminModule = await import('firebase-admin');
    } catch {
      console.warn('⚠️  firebase-admin not installed — FCM push disabled');
      return null;
    }
  }
  return adminModule;
}

export async function initFirebaseAdmin(): Promise<void> {
  if (initialized) return;
  const admin = await getAdmin();
  if (!admin) return;

  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) {
    console.warn('⚠️  FIREBASE_SERVICE_ACCOUNT not set — FCM push disabled');
    return;
  }

  try {
    const serviceAccount = JSON.parse(raw);
    admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
    initialized = true;
    console.log('✅ Firebase Admin initialized');
  } catch (e) {
    console.error('❌ Failed to init Firebase Admin:', e);
  }
}

/**
 * Send a data-only FCM push to a list of device tokens.
 */
export async function sendFcmPush(
  tokens: string[],
  data: {
    senderName: string;
    content:    string;
    chatId:     string;
    chatName:   string;
  },
): Promise<void> {
  if (!initialized || tokens.length === 0) return;
  const admin = await getAdmin();
  if (!admin) return;

  try {
    await admin.app().messaging().sendEachForMulticast({
      tokens,
      data: {
        senderName: data.senderName,
        content:    data.content.slice(0, 200),
        chatId:     data.chatId,
        chatName:   data.chatName,
      },
      android: { priority: 'high' },
    });
  } catch (e) {
    console.error('FCM send error:', e);
  }
}
