const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const CHARLIE_UID = "charlie_uid_003";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

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

// --- 1. PRIVATE USERS (PII ISOLATION) ---

test("Private users: unauthenticated denied; owner CRUD succeeds; cross-user denied", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTime(5000);

  const aliceUserPayload = {
    userId: ALICE_UID,
    email: "alice@example.com",
    displayName: "Alice Pratama",
    photoUrl: "https://example.com/alice.jpg",
    statusText: "Siap mengobrol di SapaChat",
    discoverable: true,
    createdAt: now,
    updatedAt: now,
  };

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(aliceUserPayload));

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(aliceUserPayload));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Cross-user PII read & list must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());

  // Owner partial update succeeds (including per-user deleted chats, calls, and contacts)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      statusText: "Sedang rapat",
      discoverable: false,
      hiddenChatIds: [`${ALICE_UID}_${BOB_UID}`],
      hiddenCallIds: ["call_video_001"],
      hiddenContactUids: [BOB_UID],
      updatedAt: pastTime(1000),
    })
  );

  // Cross-user cannot tamper with Alice's hidden lists
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      hiddenContactUids: [],
      updatedAt: pastTime(900),
    })
  );

  // Shadow field or immutable createdAt mutation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastTime(1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastTime(500),
      updatedAt: pastTime(500),
    })
  );
});

// --- 2. PUBLIC PROFILES (ZERO PII, DISCOVERABLE QUERY ALIGNMENT) ---

test("Public profiles: discoverable query succeeds; unfiltered query fails; non-discoverable hidden", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTime(5000);

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Pratama",
      statusText: "Ada di SapaChat",
      discoverable: true,
      online: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Matches Kotlin query: collection("public_profiles").whereEqualTo("discoverable", true)
  await assertSucceeds(
    bobDb.collection("public_profiles").where("discoverable", "==", true).get()
  );

  // Unfiltered list must fail
  await assertFails(bobDb.collection("public_profiles").get());

  // Ghost PII field rejected on create
  await assertFails(
    bobDb.collection("public_profiles").doc(BOB_UID).set({
      userId: BOB_UID,
      email: "bob@example.com",
      displayName: "Bob",
      statusText: "Hi",
      discoverable: true,
      online: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Owner can update displayName and custom Base64 profile picture photoData
  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      displayName: "Alice Updated",
      photoData: "base64avatarpreviewdata",
      updatedAt: pastTime(1200),
    })
  );

  // Oversized photoData (> 120000 chars) and cross-user avatar edit must fail
  await assertFails(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      photoData: "a".repeat(120001),
      updatedAt: pastTime(1100),
    })
  );
  await assertFails(
    bobDb.collection("public_profiles").doc(ALICE_UID).update({
      photoData: "hijacked",
      updatedAt: pastTime(1100),
    })
  );

  // Non-existent profile lookup returns missing doc cleanly instead of PERMISSION_DENIED
  const missingSnap = await assertSucceeds(
    bobDb.collection("public_profiles").doc("nonexistent_uid_999").get()
  );
  if (missingSnap.exists) {
    throw new Error("Expected missing profile doc to not exist");
  }

  // If Alice hides profile, Bob cannot get it
  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      discoverable: false,
      updatedAt: pastTime(1000),
    })
  );
  await assertFails(bobDb.collection("public_profiles").doc(ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("public_profiles").doc(ALICE_UID).get());
});

// --- 2b. UNIQUE PATHNER ID RESERVATIONS ---

test("Pathner IDs: owner claims unique handle; peer lookup succeeds; list/hijack/invalid denied", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTime(5000);

  const aliceHandlePayload = {
    pathnerId: "alice_01",
    userId: ALICE_UID,
    createdAt: now,
    updatedAt: now,
  };

  // Unauthenticated denied
  await assertFails(unauthDb.collection("pathner_ids").doc("alice_01").get());
  await assertFails(unauthDb.collection("pathner_ids").doc("alice_01").set(aliceHandlePayload));

  // Alice claims unique handle & saves pathnerId on her profile
  await assertSucceeds(aliceDb.collection("pathner_ids").doc("alice_01").set(aliceHandlePayload));
  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      pathnerId: "alice_01",
      displayName: "alice_01",
      statusText: "Ada di The Pathner",
      discoverable: true,
      online: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can resolve Alice's handle via get, but cannot list all handles or hijack/delete Alice's handle
  await assertSucceeds(bobDb.collection("pathner_ids").doc("alice_01").get());
  await assertFails(bobDb.collection("pathner_ids").get());
  await assertFails(
    bobDb.collection("pathner_ids").doc("alice_01").set({
      pathnerId: "alice_01",
      userId: BOB_UID,
      createdAt: pastTime(1000),
      updatedAt: pastTime(1000),
    })
  );
  await assertFails(bobDb.collection("pathner_ids").doc("alice_01").delete());

  // Invalid format (too short or uppercase) rejected
  await assertFails(
    bobDb.collection("pathner_ids").doc("ab").set({
      pathnerId: "ab",
      userId: BOB_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("pathner_ids").doc("BobUpper").set({
      pathnerId: "BobUpper",
      userId: BOB_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Owner can release her own handle
  await assertSucceeds(aliceDb.collection("pathner_ids").doc("alice_01").delete());
});

// --- 3. CHATS & MESSAGES (TEXT, IMAGE, VIDEO, FILE) ---

test("Chats and messages: participant CRUD and filtered queries succeed; outsider denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastTime(5000);
  const chatId = `${ALICE_UID}_${BOB_UID}`;

  const chatPayload = {
    groupChat: false,
    createdBy: ALICE_UID,
    participantUids: [ALICE_UID, BOB_UID],
    participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
    participantPhotos: {},
    lastMessageText: "Halo Bob!",
    lastMessageType: "TEXT",
    lastMessageSenderId: ALICE_UID,
    lastMessageAt: now,
    createdAt: now,
    updatedAt: now,
  };

  // Empty list query & non-existent deterministic chat get (self + direct) succeed for owner/participants, fail for outsider
  await assertSucceeds(
    aliceDb.collection("chats").where("participantUids", "array-contains", ALICE_UID).get()
  );
  await assertSucceeds(aliceDb.collection("chats").doc(`self_${ALICE_UID}`).get());
  await assertFails(bobDb.collection("chats").doc(`self_${ALICE_UID}`).get());
  await assertSucceeds(aliceDb.collection("chats").doc(chatId).get());
  await assertSucceeds(bobDb.collection("chats").doc(chatId).get());
  await assertFails(charlieDb.collection("chats").doc(chatId).get());

  await assertSucceeds(aliceDb.collection("chats").doc(chatId).set(chatPayload));

  // Clearing typing status before typingUsers map exists must succeed
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).update({
      updatedAt: pastTime(1100),
    })
  );

  // Participants can set and clear only their own typing indicator
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).update({
      [`typingUsers.${ALICE_UID}`]: pastTime(1000),
      updatedAt: pastTime(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).update({
      [`typingUsers.${BOB_UID}`]: pastTime(800),
      updatedAt: pastTime(800),
    })
  );
  // Bob cannot spoof or clear Alice's typing status; outsider Charlie denied
  await assertFails(
    bobDb.collection("chats").doc(chatId).update({
      [`typingUsers.${ALICE_UID}`]: pastTime(700),
      updatedAt: pastTime(700),
    })
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).update({
      [`typingUsers.${CHARLIE_UID}`]: pastTime(700),
      updatedAt: pastTime(700),
    })
  );

  // Matches Kotlin query: collection("chats").whereArrayContains("participantUids", uid)
  await assertSucceeds(
    bobDb.collection("chats").where("participantUids", "array-contains", BOB_UID).get()
  );
  await assertFails(bobDb.collection("chats").get());
  await assertFails(charlieDb.collection("chats").doc(chatId).get());

  // Send text message with explicit sentAt server timestamp
  const msg1Payload = {
    chatId,
    senderId: ALICE_UID,
    senderName: "Alice",
    participantUids: [ALICE_UID, BOB_UID],
    messageType: "TEXT",
    text: "Halo Bob, ini file proyeknya!",
    sentAt: now,
    deliveredToUids: [ALICE_UID],
    readByUids: [ALICE_UID],
    deleted: false,
    createdAt: now,
    updatedAt: now,
  };
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").set(msg1Payload)
  );

  // Send file/image message with reversed participantUids order (same set) and optional attachment fields (NO `id` key!)
  const msg2Payload = {
    chatId,
    senderId: BOB_UID,
    senderName: "Bob",
    participantUids: [BOB_UID, ALICE_UID],
    messageType: "IMAGE",
    text: "Foto liburan",
    fileName: "liburan.jpg",
    mimeType: "image/jpeg",
    fileSizeBytes: 120400,
    mediaUrl: "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0317638482.firebasestorage.app/o/chats%2Fchat1%2Fliburan.jpg?alt=media",
    mediaData: "base64sampledata",
    readByUids: [BOB_UID],
    deleted: false,
    createdAt: pastTime(2000),
    updatedAt: pastTime(2000),
  };
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_2").set(msg2Payload)
  );
  // Send AUDIO voice clip message and update chat preview with lastMessageType: "AUDIO"
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_voice_1").set({
      chatId,
      senderId: ALICE_UID,
      senderName: "Alice",
      participantUids: [ALICE_UID, BOB_UID],
      messageType: "AUDIO",
      text: "",
      fileName: "voice_note.m4a",
      mimeType: "audio/mp4",
      fileSizeBytes: 24576,
      durationMs: 6500,
      mediaData: "base64audioclipdata",
      deliveredToUids: [ALICE_UID],
      readByUids: [ALICE_UID],
      deleted: false,
      createdAt: pastTime(1800),
      updatedAt: pastTime(1800),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).update({
      lastMessageText: "🎤 Pesan suara (00:06)",
      lastMessageType: "AUDIO",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: pastTime(1800),
      updatedAt: pastTime(1800),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_bad_url").set({
      ...msg2Payload,
      mediaUrl: "https://example.com/" + "x".repeat(1000),
    })
  );

  // Matches Kotlin message listener query
  await assertSucceeds(
    aliceDb
      .collection("chats")
      .doc(chatId)
      .collection("messages")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );

  // Outsider cannot read Alice/Bob's messages (direct get, unfiltered list, or querying Alice's UID all denied)
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").get()
  );
  await assertFails(
    charlieDb
      .collection("chats")
      .doc(chatId)
      .collection("messages")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );
  const charlieEmptySnap = await assertSucceeds(
    charlieDb
      .collection("chats")
      .doc(chatId)
      .collection("messages")
      .where("participantUids", "array-contains", CHARLIE_UID)
      .get()
  );
  if (!charlieEmptySnap.empty) {
    throw new Error("Expected outsider scoped query to return zero messages");
  }
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("msg_hack").set({
      ...msg1Payload,
      senderId: CHARLIE_UID,
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
    })
  );

  // Bob marks Alice's message delivered, then read (including chat preview receipt timestamps)
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      deliveredToUids: [ALICE_UID, BOB_UID],
      deliveredAt: pastTime(1500),
      updatedAt: pastTime(1500),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).update({
      lastMessageDeliveredAt: pastTime(1500),
      updatedAt: pastTime(1500),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      deliveredToUids: [ALICE_UID, BOB_UID],
      readByUids: [ALICE_UID, BOB_UID],
      readAt: pastTime(1000),
      updatedAt: pastTime(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).update({
      lastMessageReadAt: pastTime(1000),
      updatedAt: pastTime(1000),
    })
  );

  // sentAt is immutable; outsider Charlie cannot forge receipts; future timestamps rejected
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      sentAt: pastTime(950),
      updatedAt: pastTime(950),
    })
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      deliveredToUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: pastTime(900),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      readAt: new Date(Date.now() + 60000),
      updatedAt: pastTime(800),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc(chatId).update({
      lastMessageReadAt: new Date(Date.now() + 60000),
      updatedAt: pastTime(800),
    })
  );

  // Forwarded message succeeds with forwarded: true
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_forwarded_1").set({
      chatId,
      senderId: BOB_UID,
      senderName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      messageType: "TEXT",
      text: "Halo Bob, ini file proyeknya!",
      forwarded: true,
      deliveredToUids: [BOB_UID],
      readByUids: [BOB_UID],
      deleted: false,
      createdAt: pastTime(1200),
      updatedAt: pastTime(1200),
    })
  );

  // Participant can delete-for-me (add self to hiddenForUids), but cannot forge another user's UID or remove existing entries
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      hiddenForUids: [ALICE_UID],
      updatedAt: pastTime(1100),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      hiddenForUids: [BOB_UID],
      updatedAt: pastTime(1100),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      hiddenForUids: [BOB_UID, ALICE_UID],
      updatedAt: pastTime(1050),
    })
  );
  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      hiddenForUids: [ALICE_UID],
      updatedAt: pastTime(1020),
    })
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      hiddenForUids: [BOB_UID, ALICE_UID, CHARLIE_UID],
      updatedAt: pastTime(1010),
    })
  );

  // Bob cannot soft-delete Alice's message; Alice can soft-delete her own
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      deleted: true,
      text: "",
      updatedAt: pastTime(1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      deleted: true,
      text: "",
      updatedAt: pastTime(1000),
    })
  );

  // Reject future timestamp & @DocumentId `id` injection
  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_future").set({
      ...msg1Payload,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_id_field").set({
      ...msg1Payload,
      id: "msg_id_field",
    })
  );
});

// --- 4. VIDEO & VOICE CALL SIGNALING (TERMINAL STATE LOCKING) ---

test("Calls: caller starts ringing, callee answers, toggles media, ends, terminal state locked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastTime(5000);
  const callId = "call_video_001";

  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).set({
      chatId: `${ALICE_UID}_${BOB_UID}`,
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      callType: "VIDEO",
      status: "RINGING",
      callerCameraOn: true,
      callerMuted: false,
      calleeCameraOn: true,
      calleeMuted: false,
      durationSeconds: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Matches Kotlin call query
  await assertSucceeds(
    bobDb.collection("calls").where("participantUids", "array-contains", BOB_UID).get()
  );
  await assertFails(charlieDb.collection("calls").doc(callId).get());

  // Bob answers call
  await assertSucceeds(
    bobDb.collection("calls").doc(callId).update({
      status: "ACTIVE",
      calleeCameraOn: true,
      calleeMuted: false,
      updatedAt: pastTime(2000),
    })
  );

  // Alice mutes mic during active call
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).update({
      callerMuted: true,
      updatedAt: pastTime(1500),
    })
  );

  // Alice ends call
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).update({
      status: "ENDED",
      durationSeconds: 42,
      updatedAt: pastTime(1000),
    })
  );

  // Terminal state locking: cannot modify ENDED call
  await assertFails(
    aliceDb.collection("calls").doc(callId).update({
      status: "ACTIVE",
      durationSeconds: 100,
      updatedAt: pastTime(500),
    })
  );
});
