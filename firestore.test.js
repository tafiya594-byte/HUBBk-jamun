const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";
const CHARLIE_UID = "charlie_uid_303";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(ms = 2000) {
  return new Date(Date.now() - ms);
}

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users_public").get());
  await assertFails(unauthDb.collection("users_private").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("chats").get());
  await assertFails(unauthDb.collection("statuses").get());
  await assertFails(unauthDb.collection("calls").get());
  await assertFails(
    unauthDb.collection("usernames").doc("alice").set({
      uid: ALICE_UID,
      displayName: "Alice",
      createdAt: pastDate(),
    })
  );
});

test("Username reservation and split public/private user profile isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("usernames").doc("alice_abid").set({
      uid: ALICE_UID,
      displayName: "Alice",
      createdAt: now,
    })
  );

  // Spoofed username uid must fail
  await assertFails(
    bobDb.collection("usernames").doc("bob_spoof").set({
      uid: ALICE_UID,
      displayName: "Bob",
      createdAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users_public").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice_abid",
      displayName: "Alice",
      about: "Hey there! Using ABID Messenger",
      online: true,
      lastSeen: now,
      lastSeenPrivacy: "everyone",
      statusPrivacy: "everyone",
      readReceipts: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users_private").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      themeMode: "system",
      blockedUids: [],
      pinnedChatIds: [],
      archivedChatIds: [],
      mutedChatIds: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can read Alice's public profile, NOT Alice's private PII doc
  await assertSucceeds(bobDb.collection("users_public").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users_private").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users_public").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: pastDate(500),
    })
  );
});

test("Chat creation, filtered listing, messages, receipts, and member isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  const now = pastDate(3000);

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      type: "direct",
      title: "",
      description: "",
      memberUids: [ALICE_UID, BOB_UID],
      adminUids: [ALICE_UID],
      createdBy: ALICE_UID,
      lastMessageText: "",
      lastMessageType: "none",
      lastMessageSenderId: "",
      lastMessageAt: now,
      typingUids: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered query succeeds for member, unfiltered fails
  await assertSucceeds(
    bobDb.collection("chats").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(bobDb.collection("chats").get());
  // Non-member Charlie cannot read chat
  await assertFails(charlieDb.collection("chats").doc(chatId).get());

  // Send message in chat
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").set({
      chatId,
      senderId: ALICE_UID,
      senderName: "Alice",
      type: "text",
      text: "Hello Bob!",
      edited: false,
      deletedForAll: false,
      deliveredTo: [ALICE_UID],
      readBy: [ALICE_UID],
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob marks delivered/read
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      deliveredTo: [ALICE_UID, BOB_UID],
      readBy: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(500),
    })
  );

  // Bob cannot edit Alice's message text
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      text: "Tampered",
      edited: true,
      updatedAt: pastDate(400),
    })
  );

  // Alice can edit her own message text
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      text: "Hello Bob! (edited)",
      edited: true,
      updatedAt: pastDate(300),
    })
  );

  // Filtered message query succeeds; unfiltered or direct doc read by Charlie fails
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").get()
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").get()
  );
});

test("Status creation, 24h expiry constraint, and viewer recording", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(2000);
  const expires24h = new Date(Date.now() + 24 * 60 * 60 * 1000);

  await assertSucceeds(
    aliceDb.collection("statuses").doc("status_1").set({
      userId: ALICE_UID,
      authorName: "Alice",
      type: "text",
      text: "Working on ABID Messenger!",
      backgroundHex: "#008955",
      privacy: "everyone",
      viewerUids: [],
      expiresAt: expires24h,
      createdAt: now,
    })
  );

  // Reject far-future expiration (>25h)
  await assertFails(
    aliceDb.collection("statuses").doc("status_bad").set({
      userId: ALICE_UID,
      authorName: "Alice",
      type: "text",
      text: "Forever status",
      backgroundHex: "#008955",
      privacy: "everyone",
      viewerUids: [],
      expiresAt: new Date(Date.now() + 72 * 60 * 60 * 1000),
      createdAt: now,
    })
  );

  // Bob records view
  await assertSucceeds(
    bobDb.collection("statuses").doc("status_1").update({
      viewerUids: [BOB_UID],
    })
  );
  // Bob cannot delete Alice's status
  await assertFails(bobDb.collection("statuses").doc("status_1").delete());
});

test("WebRTC call signaling, ICE candidates, and terminal state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate(2000);

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      callType: "video",
      status: "ringing",
      participantUids: [ALICE_UID, BOB_UID],
      offerSdp: "v=0...",
      offerType: "offer",
      durationSeconds: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Charlie cannot inspect call
  await assertFails(charlieDb.collection("calls").doc("call_1").get());

  // Bob answers call
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "answered",
      answerSdp: "v=0...",
      answerType: "answer",
      updatedAt: pastDate(500),
    })
  );

  // ICE candidate exchange
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").collection("iceCandidates").doc("ice_1").set({
      senderId: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      sdpMid: "0",
      sdpMLineIndex: 0,
      sdp: "candidate:123 1 udp 2122260223 192.168.1.2 54321 typ host",
      createdAt: pastDate(400),
    })
  );

  // End call (terminal state)
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ended",
      durationSeconds: 42,
      updatedAt: pastDate(200),
    })
  );

  // Terminal state lock prevents mutation after ended
  await assertFails(
    bobDb.collection("calls").doc("call_1").update({
      status: "answered",
      updatedAt: pastDate(100),
    })
  );
});

test("User reporting works and is immutable", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      reporterId: ALICE_UID,
      reportedUserId: BOB_UID,
      reason: "Spam",
      details: "Unsolicited messages",
      status: "open",
      createdAt: now,
    })
  );

  await assertFails(bobDb.collection("reports").doc("rep_1").get());
  await assertFails(aliceDb.collection("reports").doc("rep_1").delete());
});
