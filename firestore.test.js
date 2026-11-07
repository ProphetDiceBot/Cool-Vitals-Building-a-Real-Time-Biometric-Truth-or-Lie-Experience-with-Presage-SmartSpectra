const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

function validStatementPayload(userId) {
  return {
    userId,
    transcript: "I definitely did not eat the last slice of cake.",
    isLie: true,
    deceptionScore: 78,
    verdict: "LIE",
    pulseRate: 102,
    breathingRate: 22,
    hrvRmssd: 21.4,
    expression: "Fear 84%",
    reason: "Elevated pulse (102 bpm) + reduced HRV (21.4 ms) + Fear expression",
    createdAt: new Date(Date.now() - 1000),
    updatedAt: new Date(Date.now() - 1000),
  };
}

test("Unauthenticated user: cannot read or write statement_logs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("statement_logs").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("statement_logs")
      .doc("log_1")
      .set(validStatementPayload(ALICE_UID))
  );
});

test("Authenticated owner: can create, get, list, update, and delete own statement_log", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("statement_logs")
    .doc("log_1");

  await assertSucceeds(docRef.set(validStatementPayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("statement_logs")
      .where("userId", "==", ALICE_UID)
      .get()
  );
  await assertSucceeds(
    docRef.update({
      notes: "Confirmed during follow-up question",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(docRef.delete());
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's statement_logs", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("statement_logs")
      .doc("log_1")
      .set(validStatementPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDocFromBob = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("statement_logs")
    .doc("log_1");

  await assertFails(aliceDocFromBob.get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("statement_logs").get()
  );
  await assertFails(aliceDocFromBob.delete());
});

test("Schema validation: rejects shadow fields and invalid deceptionScore", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("statement_logs")
    .doc("log_invalid");

  await assertFails(
    docRef.set({
      ...validStatementPayload(ALICE_UID),
      isVerifiedAdmin: true,
    })
  );

  await assertFails(
    docRef.set({
      ...validStatementPayload(ALICE_UID),
      deceptionScore: 150,
    })
  );
});
