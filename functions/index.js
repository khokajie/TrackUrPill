const functions = require("firebase-functions");
const admin = require("firebase-admin");
const moment = require("moment-timezone");

admin.initializeApp();

// Define valid frequencies and days globally
const VALID_FREQUENCIES = ["Once", "Daily", "Weekly"];
const VALID_DAYS = [
  "Sunday",
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
];

/**
 * Calculate the next reminder time
 * @param {object} reminder - The reminder object containing schedule details
 * @param {string} userTimeZone - The user's timezone (e.g., "Asia/Hong_Kong")
 * @return {number} Next reminder time in milliseconds (UTC)
 */
function calculateNextReminderTime(reminder, userTimeZone) {
  console.log("Raw reminder data:", JSON.stringify(reminder));
  console.log("Received userTimeZone:", userTimeZone);

  if (!reminder || typeof reminder !== "object") {
    throw new Error("Invalid reminder object");
  }

  if (!reminder.frequency) {
    throw new Error("Frequency is required");
  }

  // Validate and clean frequency
  const frequency = String(reminder.frequency).trim();
  console.log("Processing frequency:", frequency);

  if (!VALID_FREQUENCIES.includes(frequency)) {
    throw new Error(
      `Invalid frequency. Must be one of: ${VALID_FREQUENCIES.join(
        ", ",
      )}. Got: ${frequency}`,
    );
  }

  let nextTime;

  switch (frequency) {
    case "Once": {
      if (!reminder.date) {
        throw new Error("Date is required for 'Once' frequency");
      }

      const dateStr = String(reminder.date).trim();
      if (!/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) {
        throw new Error(
          `Invalid date format. Expected YYYY-MM-DD, got: ${dateStr}`,
        );
      }

      // Create the exact moment in the user's timezone
      const reminderTime = moment
        .tz(
          `${dateStr} ${String(reminder.hour).padStart(2, "0")}:${String(
            reminder.minute,
          ).padStart(2, "0")}`,
          "YYYY-MM-DD HH:mm",
          userTimeZone,
        )
        .millisecond(0);

      console.log("Created reminder time:", {
        inputDate: dateStr,
        inputHour: reminder.hour,
        inputMinute: reminder.minute,
        timezone: userTimeZone,
        localDateTime: reminderTime.format("YYYY-MM-DD HH:mm:ss Z"),
        utcDateTime: reminderTime.utc().format("YYYY-MM-DD HH:mm:ss Z"),
        timestamp: reminderTime.valueOf(),
      });

      if (!reminderTime.isValid()) {
        throw new Error("Invalid date/time combination");
      }

      const now = moment().tz(userTimeZone);
      console.log("Current time in user timezone:", now.format());

      if (reminderTime.isBefore(now)) {
        throw new Error("Reminder time must be in the future");
      }

      nextTime = reminderTime.valueOf();
      break;
    }

    case "Daily": {
      const hour = parseInt(reminder.hour, 10);
      const minute = parseInt(reminder.minute, 10);

      if (isNaN(hour) || hour < 0 || hour > 23) {
        throw new Error(`Invalid hour: ${reminder.hour}`);
      }
      if (isNaN(minute) || minute < 0 || minute > 59) {
        throw new Error(`Invalid minute: ${reminder.minute}`);
      }

      const now = moment().tz(userTimeZone);
      const reminderTime = now.clone().set({
        hour: hour,
        minute: minute,
        second: 0,
        millisecond: 0,
      });

      console.log(
        "Daily reminder time before adjustment:",
        reminderTime.format(),
      );

      if (reminderTime.isBefore(now)) {
        reminderTime.add(1, "day");
        console.log(
          "Daily reminder time after adding a day:",
          reminderTime.format(),
        );
      }

      nextTime = reminderTime.valueOf();
      break;
    }

    case "Weekly": {
      if (!reminder.day || !VALID_DAYS.includes(reminder.day)) {
        throw new Error(
          `Invalid day. Must be one of: ${VALID_DAYS.join(", ")}`,
        );
      }

      const hour = parseInt(reminder.hour, 10);
      const minute = parseInt(reminder.minute, 10);

      if (isNaN(hour) || hour < 0 || hour > 23) {
        throw new Error(`Invalid hour: ${reminder.hour}`);
      }
      if (isNaN(minute) || minute < 0 || minute > 59) {
        throw new Error(`Invalid minute: ${reminder.minute}`);
      }

      const now = moment().tz(userTimeZone);
      const dayIndex = VALID_DAYS.indexOf(reminder.day);
      const reminderTime = now.clone().day(dayIndex).set({
        hour: hour,
        minute: minute,
        second: 0,
        millisecond: 0,
      });

      console.log(
        "Weekly reminder time before adjustment:",
        reminderTime.format(),
      );

      if (reminderTime.isBefore(now)) {
        reminderTime.add(1, "week");
        console.log(
          "Weekly reminder time after adding a week:",
          reminderTime.format(),
        );
      }

      nextTime = reminderTime.valueOf();
      break;
    }

    default:
      throw new Error(`Invalid frequency: ${frequency}`);
  }

  if (!nextTime) {
    throw new Error("Failed to calculate reminder time");
  }

  console.log("Calculated time:", {
    timestamp: nextTime,
    local: moment(nextTime).tz(userTimeZone).format(),
    utc: moment(nextTime).utc().format(),
  });

  return nextTime;
}

/**
 * Schedule a reminder
 * @param {object} data - The reminder data
 * @param {object} context - The function context
 * @returns {Promise<object>} Operation result
 */
exports.scheduleReminder = functions.https.onCall(async (data, context) => {
  try {
    console.log("Raw request data:", JSON.stringify(data));

    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "User must be authenticated",
      );
    }

    if (!data || typeof data !== "object") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Invalid request data",
      );
    }

    const { reminder, userTimeZone } = data;

    console.log("Received reminder:", JSON.stringify(reminder));
    console.log("Received userTimeZone:", userTimeZone);

    if (!reminder || typeof reminder !== "object") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Invalid reminder data",
      );
    }

    // Validate required fields
    const requiredFields = [
      "reminderId",
      "medicationId",
      "frequency",
      "hour",
      "minute",
    ];
    for (const field of requiredFields) {
      if (reminder[field] === undefined || reminder[field] === null) {
        throw new functions.https.HttpsError(
          "invalid-argument",
          `Missing required field: ${field}`,
        );
      }
    }

    if (!VALID_FREQUENCIES.includes(reminder.frequency)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        `Invalid frequency: ${reminder.frequency}`,
      );
    }

    if (!userTimeZone || typeof userTimeZone !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Valid timezone is required",
      );
    }

    let nextReminderTime;
    try {
      nextReminderTime = calculateNextReminderTime(reminder, userTimeZone);
    } catch (error) {
      console.error("Time calculation error:", error);
      throw new functions.https.HttpsError(
        "invalid-argument",
        `Failed to calculate reminder time: ${error.message}`,
      );
    }

    // Retrieve Medication document
    const medicationDoc = await admin
      .firestore()
      .collection("Medication")
      .doc(reminder.medicationId)
      .get();

    if (!medicationDoc.exists) {
      throw new functions.https.HttpsError("not-found", "Medication not found");
    }

    const medication = medicationDoc.data();
    const userId = medication.userId;

    // Get user document and FCM token
    let userDoc = await admin
      .firestore()
      .collection("Patient")
      .doc(userId)
      .get();

    if (!userDoc.exists) {
      userDoc = await admin
        .firestore()
        .collection("Caregiver")
        .doc(userId)
        .get();
    }

    if (!userDoc.exists) {
      throw new functions.https.HttpsError("not-found", "User not found");
    }

    const fcmToken = userDoc.data().fcmToken;
    if (!fcmToken) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "FCM token not found",
      );
    }

    const batch = admin.firestore().batch();

    const reminderRef = admin
      .firestore()
      .collection("Reminder")
      .doc(reminder.reminderId);

    const reminderData = {
      userId: userId,
      medicationId: reminder.medicationId,
      medicationName: medication.medicationName,
      dosage: medication.dosage,
      frequency: String(reminder.frequency).trim(),
      hour: parseInt(reminder.hour, 10),
      minute: parseInt(reminder.minute, 10),
      day: reminder.day || null,
      date: reminder.date || null,
      userTimeZone: userTimeZone,
      nextScheduledTime: admin.firestore.Timestamp.fromMillis(nextReminderTime),
      status: "active",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    batch.set(reminderRef, reminderData);

    const scheduledTaskRef = admin
      .firestore()
      .collection("ScheduledTasks")
      .doc(reminder.reminderId);

    const scheduledTaskData = {
      type: "reminder",
      reminderId: reminder.reminderId,
      userId: userId,
      nextScheduledTime: admin.firestore.Timestamp.fromMillis(nextReminderTime),
      fcmToken: fcmToken,
      status: "scheduled",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    batch.set(scheduledTaskRef, scheduledTaskData);

    await batch.commit();

    console.log("Reminder scheduled successfully:", {
      reminderId: reminder.reminderId,
      nextTime: moment(nextReminderTime).format(),
    });

    return {
      success: true,
      scheduled: true,
      nextReminderTime: nextReminderTime,
    };
  } catch (error) {
    console.error("Error in scheduleReminder:", error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError(
      "internal",
      error.message || "An unknown error occurred",
    );
  }
});

/**
 * Process scheduled reminders
 * @returns {Promise<null>} Null on completion
 */
exports.processScheduledReminders = functions.pubsub
  .schedule("every 1 minutes")
  .onRun(async () => {
    const now = admin.firestore.Timestamp.now();
    console.log(
      "Processing scheduled reminders at:",
      now.toDate().toISOString(),
    );

    try {
      const querySnapshot = await admin
        .firestore()
        .collection("ScheduledTasks")
        .where("type", "==", "reminder")
        .where("status", "==", "scheduled")
        .where("nextScheduledTime", "<=", now)
        .get();

      if (querySnapshot.empty) {
        console.log("No reminders to process at this time.");
        return null;
      }

      console.log(`Found ${querySnapshot.size} reminders to process.`);

      const promises = querySnapshot.docs.map(async (doc) => {
        const scheduledTask = doc.data();
        const { reminderId, fcmToken, userId } = scheduledTask;

        // Defensive Coding: Ensure reminderId and userId are defined
        if (!reminderId || !userId) {
          console.error(
            "ScheduledTask missing reminderId or userId:",
            scheduledTask,
          );
          await doc.ref.update({
            status: "error",
            error: "Missing reminderId or userId",
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
          return;
        }

        try {
          const reminderDoc = await admin
            .firestore()
            .collection("Reminder")
            .doc(reminderId)
            .get();

          if (!reminderDoc.exists || reminderDoc.data().status !== "active") {
            await doc.ref.update({
              status: "canceled",
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            });
            console.log(
              `Reminder ${reminderId} is no longer active and has been canceled.`,
            );
            return;
          }

          const reminder = reminderDoc.data();

          // Log before sending notification
          console.log(`Sending notification to FCM token: ${fcmToken}`);

          // Ensure all data fields are strings
          const dosage = reminder.dosage || "1 Tablet"; // Default dosage if not specified

          // Define title and body based on reminder type
          const title = "Medication Reminder";
          const body = `It's time to take your ${reminder.medicationName}: ${dosage}`;

          const dataPayload = {
            type: "reminder", // Specify the type
            medicationId: String(reminder.medicationId),
            dosage: dosage.toString(), // Convert to string for FCM
            userId: String(reminder.userId),
            title: title,
            body: body,
          };

          // Create a unique notificationId
          const notificationId = admin
            .firestore()
            .collection("Notification")
            .doc().id;

          // Add notificationId to dataPayload
          dataPayload.notificationId = notificationId;

          // Send data-only FCM message
          const messagingResponse = await admin.messaging().send({
            data: dataPayload, // All values are strings
            android: {
              priority: "high",
            },
            token: fcmToken,
          });

          console.log(
            `Notification sent for reminder ${reminderId} with Notification ID: ${notificationId}`,
          );
          console.log(
            `FCM Response for reminder ${reminderId}:`,
            messagingResponse,
          );

          // Create notification record
          const notificationRef = admin
            .firestore()
            .collection("Notification")
            .doc(notificationId);

          await notificationRef.set({
            reminderId: reminderId,
            type: "reminder", // Specify the type
            userId: userId,
            message: `Time to take ${reminder.medicationName} (${dosage})`,
            status: "Sent",
            timestamp: admin.firestore.Timestamp.now(),
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
          });

          const frequency = String(reminder.frequency).trim();

          if (frequency === "Once") {
            await Promise.all([
              reminderDoc.ref.update({
                status: "completed",
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              }),
              doc.ref.update({
                status: "completed",
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              }),
            ]);
            console.log(`Reminder ${reminderId} marked as completed.`);
          } else {
            const nextTime = calculateNextReminderTime(
              reminder,
              reminder.userTimeZone,
            );
            const nextTimestamp =
              admin.firestore.Timestamp.fromMillis(nextTime);

            await Promise.all([
              reminderDoc.ref.update({
                nextScheduledTime: nextTimestamp, // Updated to Timestamp
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              }),
              doc.ref.update({
                nextScheduledTime: nextTimestamp, // Updated to Timestamp
                status: "scheduled",
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              }),
            ]);
            console.log(`Reminder ${reminderId} rescheduled for next time.`);
          }
        } catch (error) {
          console.error(`Error processing reminder ${reminderId}:`, error);
          await doc.ref.update({
            status: "error",
            error: error.message,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
        }
      });

      await Promise.all(promises);
      console.log(`Processed ${querySnapshot.size} reminders.`);
      return null;
    } catch (error) {
      console.error("Error processing reminders:", error);
      // Optionally, handle the error (e.g., retry logic, alerting)
      return null;
    }
  });

/**
 * Cancel a reminder
 * @param {object} data - The cancellation data
 * @param {object} context - The function context
 * @returns {Promise<object>} Operation result
 */
exports.cancelReminder = functions.https.onCall(async (data, context) => {
  try {
    // Authentication check
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "User must be authenticated.",
      );
    }

    // Validate input
    const { reminderId } = data;
    if (!reminderId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "reminderId is required.",
      );
    }

    // Verify reminder exists
    const reminderDoc = await admin
      .firestore()
      .collection("Reminder")
      .doc(reminderId)
      .get();

    if (!reminderDoc.exists) {
      throw new functions.https.HttpsError("not-found", "Reminder not found");
    }

    // Batch update both documents
    const batch = admin.firestore().batch();

    // Update Reminder document
    const reminderRef = admin
      .firestore()
      .collection("Reminder")
      .doc(reminderId);

    batch.update(reminderRef, {
      status: "canceled",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    // Update ScheduledTask document
    const scheduledTaskRef = admin
      .firestore()
      .collection("ScheduledTasks")
      .doc(reminderId);

    batch.update(scheduledTaskRef, {
      status: "canceled",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    await batch.commit();
    console.log(`Reminder ${reminderId} has been canceled successfully.`);

    return {
      success: true,
      message: "Reminder canceled successfully",
    };
  } catch (error) {
    console.error("Error canceling reminder:", error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError(
      "internal",
      error.message || "Failed to cancel reminder",
    );
  }
});

/**
 * Callable function to send a patient invitation.
 * @param {object} data - Contains patientEmail and caregiverId.
 * @param {object} context - Authentication context.
 * @returns {object} - Success or error message.
 */
exports.sendPatientInvitation = functions.https.onCall(
  async (data, context) => {
    try {
      // Authentication check
      if (!context.auth) {
        throw new functions.https.HttpsError(
          "unauthenticated",
          "User must be authenticated.",
        );
      }

      const { patientEmail, caregiverId } = data;

      // Validate input
      if (!patientEmail || typeof patientEmail !== "string") {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "Valid patientEmail is required.",
        );
      }

      if (!caregiverId || typeof caregiverId !== "string") {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "Valid caregiverId is required.",
        );
      }

      // Find patient by email
      const patientQuerySnapshot = await admin
        .firestore()
        .collection("Patient")
        .where("userEmail", "==", patientEmail)
        .get();

      if (patientQuerySnapshot.empty) {
        throw new functions.https.HttpsError(
          "not-found",
          "No patient found with the provided email.",
        );
      }

      const patientDoc = patientQuerySnapshot.docs[0];
      const patientId = patientDoc.id;
      const fcmToken = patientDoc.data().fcmToken;

      if (!fcmToken) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Patient does not have a valid FCM token.",
        );
      }

      // Fetch caregiver's username using caregiverId
      const caregiverDoc = await admin
        .firestore()
        .collection("Caregiver")
        .doc(caregiverId)
        .get();

      if (!caregiverDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "No caregiver found with the provided caregiverId.",
        );
      }

      const caregiverData = caregiverDoc.data();
      const caregiverUsername = caregiverData.userName || "Caregiver";

      // Create a unique notificationId (document ID)
      const notificationDocRef = admin
        .firestore()
        .collection("Notification")
        .doc();
      const notificationId = notificationDocRef.id;

      const notificationData = {
        userId: patientId,
        senderId: caregiverId,
        message: `A caregiver (${caregiverUsername}) wants to help you to monitor your medication adherence. 
Check your app to accept the invitation.`,
        type: "invitation",
        status: "Sent",
        timestamp: admin.firestore.Timestamp.now(),
      };

      // Define title and body based on invitation
      const title = "Caregiver Invitation";
      const body = `A caregiver (${caregiverUsername}) wants to help you to monitor your 
medication adherence. Check your app to accept the invitation.`;

      // Prepare dataPayload without notificationId
      const dataPayload = {
        type: "invitation", // Specify the type
        userId: patientId,
        senderId: caregiverId,
        notificationId: notificationId,
        title: title,
        body: body,
      };

      // Prepare FCM message with dataPayload only
      const message = {
        token: fcmToken,
        data: dataPayload,
        android: {
          priority: "high",
        },
      };

      // Send FCM notification using send() instead of sendToDevice()
      await admin.messaging().send(message);

      // Update the Notification document with FCM response and receivedAt timestamp
      await notificationDocRef.set(
        {
          ...notificationData,
        },
        { merge: true },
      );

      return { success: true, message: "Invitation sent successfully." };
    } catch (error) {
      console.error("Error sending patient invitation:", error);
      if (error instanceof functions.https.HttpsError) {
        throw error;
      }
      throw new functions.https.HttpsError(
        "internal",
        error.message || "An unknown error occurred.",
      );
    }
  },
);

/**
 * Callable function to handle patient response to caregiver invitation.
 * @param {object} data - Contains caregiverId, response, and notificationId.
 * @param {object} context - Authentication context.
 * @returns {object} - Success or error message.
 */
exports.responseInvitation = functions.https.onCall(async (data, context) => {
  try {
    console.log("Raw request data:", JSON.stringify(data));

    // 1. Authentication Check
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "User must be authenticated.",
      );
    }

    const patientId = context.auth.uid;

    // 2. Input Validation
    const { caregiverId, response, notificationId } = data;

    if (!caregiverId || typeof caregiverId !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Valid caregiverId is required.",
      );
    }

    if (!response || (response !== "accept" && response !== "decline")) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Valid response is required ('accept' or 'decline').",
      );
    }

    if (!notificationId || typeof notificationId !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Valid notificationId is required.",
      );
    }

    console.log(
      `${patientId} response: ${response} to caregiver ID: ${caregiverId}, Notification ID: ${notificationId}`,
    );

    // 3. Retrieve Caregiver Document
    const caregiverDoc = await admin
      .firestore()
      .collection("Caregiver")
      .doc(caregiverId)
      .get();

    if (!caregiverDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "No caregiver found with the provided caregiverId.",
      );
    }

    const caregiverData = caregiverDoc.data();
    const caregiverFcmToken = caregiverData.fcmToken;

    if (!caregiverFcmToken) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Caregiver does not have a valid FCM token.",
      );
    }

    // 4. Retrieve Patient Document
    const patientDoc = await admin
      .firestore()
      .collection("Patient")
      .doc(patientId)
      .get();

    if (!patientDoc.exists) {
      throw new functions.https.HttpsError("not-found", "Patient not found.");
    }

    const patientData = patientDoc.data();
    const patientUserName = patientData.userName || "Patient";

    // 5. Retrieve Specific Invitation Notification Using notificationId
    const invitationDoc = await admin
      .firestore()
      .collection("Notification")
      .doc(notificationId)
      .get();

    if (!invitationDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Invitation notification not found.",
      );
    }

    const invitationData = invitationDoc.data();

    // Validate that the notification is an invitation and is pending
    if (
      invitationData.type !== "invitation" ||
      invitationData.userId !== patientId ||
      invitationData.senderId !== caregiverId ||
      invitationData.status !== "Sent"
    ) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Invalid invitation notification.",
      );
    }

    // 6. Update Invitation Status
    const newStatus = response === "accept" ? "Accepted" : "Declined";

    await admin
      .firestore()
      .collection("Notification")
      .doc(notificationId)
      .update({
        status: newStatus,
      });

    console.log(`Invitation ${notificationId} marked as ${newStatus}.`);

    // 7. Create a Response Notification for the Caregiver
    const responseNotificationRef = admin
      .firestore()
      .collection("Notification")
      .doc();

    const responseMessage =
      response === "accept" ?
        `${patientUserName} has accepted your invitation. Check your app to view the response.` :
        `${patientUserName} has declined your invitation. Check your app to view the response.`;

    const responseNotificationData = {
      type: "response",
      userId: caregiverId, // Recipient
      senderId: patientId, // Sender
      message: responseMessage,
      status: "Sent",
      timestamp: admin.firestore.Timestamp.now(),
    };

    await responseNotificationRef.set(responseNotificationData);

    console.log(
      `Response Notification ${responseNotificationRef.id} created for caregiver ${caregiverId}.`,
    );

    // 8. Add Patient to Caregiver's patientList if accepted
    if (response === "accept") {
      await admin
        .firestore()
        .collection("Caregiver")
        .doc(caregiverId)
        .update({
          patientList: admin.firestore.FieldValue.arrayUnion(patientId),
        });
      console.log(
        `Patient ID '${patientId}' added to caregiver '${caregiverId}' patientList.`,
      );
    }

    // 9. Prepare Data Payload for FCM
    const dataPayload = {
      type: "response",
      notificationId: responseNotificationRef.id,
      userId: caregiverId,
      senderId: patientId,
      message: responseMessage,
      title: "Invitation Response",
      body: responseMessage,
    };

    // 10. Send FCM Notification to Caregiver
    const fcmMessage = {
      token: caregiverFcmToken,
      data: dataPayload,
      android: {
        priority: "high",
      },
    };

    const fcmResponse = await admin.messaging().send(fcmMessage);

    console.log(
      `FCM Notification sent to caregiver ${caregiverId}:`,
      fcmResponse,
    );

    return { success: true, message: "Response sent successfully." };
  } catch (error) {
    console.error("Error in responseInvitation:", error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError(
      "internal",
      error.message || "An unknown error occurred.",
    );
  }
});

/**
 * Callable function to notify user about low medication stock.
 * @param {object} data - Contains userId, medicationName, and currentStock.
 * @param {object} context - Authentication context.
 * @returns {object} - Success or error message.
 */
exports.notifyLowMedicationStock = functions.https.onCall(
  async (data, context) => {
    try {
      console.log("Raw request data:", JSON.stringify(data));

      // 1. Authentication Check
      if (!context.auth) {
        throw new functions.https.HttpsError(
          "unauthenticated",
          "User must be authenticated.",
        );
      }

      const { userId, medicationName, currentStock } = data;

      // 2. Input Validation
      if (!userId || typeof userId !== "string") {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "Valid userId is required.",
        );
      }

      if (!medicationName || typeof medicationName !== "string") {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "Valid medicationName is required.",
        );
      }

      if (currentStock === undefined || typeof currentStock !== "number") {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "Valid currentStock is required.",
        );
      }

      console.log(
        `Authenticated userId: ${userId} requesting low stock notification for medication: 
${medicationName} with currentStock: ${currentStock}`,
      );

      // 3. Retrieve User's FCM Token using Helper Function
      const userFcmToken = await getUserFcmToken(userId);

      // 4. Prepare Notification Payload
      const notificationId = admin
        .firestore()
        .collection("Notification")
        .doc().id; // Generate a new notification ID

      const notificationData = {
        type: "low_stock",
        userId: userId, // Recipient
        senderId: "system", // Sender (could be "system" or another identifier)
        message: `Your stock for ${medicationName} is low (${currentStock} left). Please consider restocking.`,
        status: "Sent",
        timestamp: admin.firestore.Timestamp.now(),
      };

      // Optionally, store the notification in Firestore
      await admin
        .firestore()
        .collection("Notification")
        .doc(notificationId)
        .set(notificationData);

      console.log(
        `Notification data stored with notificationId: ${notificationId}`,
      );

      // 5. Prepare Data Payload for FCM
      const dataPayload = {
        type: "low_stock",
        notificationId: notificationId,
        userId: userId,
        senderId: "system",
        message: notificationData.message,
        title: "Low Medication Stock",
        body: notificationData.message,
      };

      // 6. Prepare FCM Message
      const fcmMessage = {
        token: userFcmToken,
        data: dataPayload,
        android: {
          priority: "high",
        },
      };

      // 7. Send Notification via FCM
      const fcmResponse = await admin.messaging().send(fcmMessage);

      console.log(
        `FCM Notification sent to userId: ${userId} - MessageId: ${fcmResponse}`,
      );

      return {
        success: true,
        message: "Low stock notification sent successfully.",
      };
    } catch (error) {
      console.error("Error in notifyLowMedicationStock:", error);
      if (error instanceof functions.https.HttpsError) {
        throw error;
      }
      throw new functions.https.HttpsError(
        "internal",
        error.message || "An unknown error occurred.",
      );
    }
  },
);

/**
 * Callable function to send an instant reminder to a patient.
 * @param {object} data - Contains medicationId, patientId, and caregiverId.
 * @param {object} context - Authentication context.
 * @returns {object} - Success or error message.
 */
exports.sendInstantReminder = functions.https.onCall(async (data, context) => {
  try {
    console.log("sendInstantReminder called with data:", JSON.stringify(data));

    // 1. Authentication Check
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "User must be authenticated.",
      );
    }

    // 2. Input Validation
    const { medicationId, patientId, caregiverId } = data;

    if (!medicationId || typeof medicationId !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Valid medicationId is required.",
      );
    }

    if (!patientId || typeof patientId !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Valid patientId is required.",
      );
    }

    if (!caregiverId || typeof caregiverId !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Valid caregiverId is required.",
      );
    }

    // 3. Retrieve Medication Details
    const medicationDoc = await admin
      .firestore()
      .collection("Medication")
      .doc(medicationId)
      .get();

    if (!medicationDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Medication not found.",
      );
    }

    const medicationData = medicationDoc.data();
    const medicationName = medicationData.medicationName;
    const dosage = medicationData.dosage;

    if (!medicationName || !dosage) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Medication name or dosage is missing.",
      );
    }

    // 4. Retrieve Caregiver's Name
    const caregiverDoc = await admin
      .firestore()
      .collection("Caregiver")
      .doc(caregiverId)
      .get();

    if (!caregiverDoc.exists) {
      throw new functions.https.HttpsError("not-found", "Caregiver not found.");
    }

    const caregiverData = caregiverDoc.data();
    const caregiverName = caregiverData.userName || "Caregiver";

    // 5. Retrieve Patient's FCM Token using existing helper
    const userFcmToken = await getUserFcmToken(patientId);

    // 6. Format the Reminder Message
    const formattedMessage = `${caregiverName} reminds you to take ${medicationName} ：${dosage}`;

    // 7. Create a unique notificationId
    const notificationId = admin
      .firestore()
      .collection("Notification")
      .doc().id;

    // 8. Log the Notification in Firestore
    const notificationData = {
      type: "reminder",
      userId: patientId, // Recipient
      senderId: caregiverId, // Sender
      message: formattedMessage,
      status: "Sent",
      timestamp: admin.firestore.Timestamp.now(),
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    await admin
      .firestore()
      .collection("Notification")
      .doc(notificationId)
      .set(notificationData);

    // 9. Prepare Data Payload for FCM
    const dataPayload = {
      type: "reminder",
      notificationId: notificationId,
      message: formattedMessage,
      title: "Medication Reminder",
      body: formattedMessage,
      medicationId: medicationId,
      dosage: dosage,
    };

    // 10. Prepare FCM Message
    const fcmMessage = {
      token: userFcmToken,
      data: dataPayload,
      android: {
        priority: "high",
      },
    };

    // 11. Send Notification via FCM
    const fcmResponse = await admin.messaging().send(fcmMessage);

    console.log(
      `FCM Notification sent to patient ${patientId}: Message ID: ${fcmResponse}`,
    );

    return { success: true, message: "Reminder sent successfully." };
  } catch (error) {
    console.error("Error in sendInstantReminder:", error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError(
      "internal",
      error.message || "An unknown error occurred.",
    );
  }
});

/**
 * Helper function to retrieve the FCM token for a given userId by checking both "Patient" and "Caregiver" collections.
 * @param {string} userId - The ID of the user.
 * @return {Promise<string>} - The FCM token of the user.
 * @throws {functions.https.HttpsError} - Throws error if user not found or FCM token is missing.
 */
async function getUserFcmToken(userId) {
  console.log(`Retrieving user document for userId: ${userId}`);

  // Attempt to fetch from "Patient" collection
  let userDoc = await admin.firestore().collection("Patient").doc(userId).get();

  // If not found in "Patient", try "Caregiver"
  if (!userDoc.exists) {
    console.log(
      `UserId: ${userId} not found in "Patient". Checking "Caregiver" collection.`,
    );
    userDoc = await admin.firestore().collection("Caregiver").doc(userId).get();
  }

  // If still not found, throw an error
  if (!userDoc.exists) {
    console.error(
      `UserId: ${userId} not found in both "Patient" and "Caregiver" collections.`,
    );
    throw new functions.https.HttpsError(
      "not-found",
      "User not found in Patient or Caregiver collections.",
    );
  }

  const userData = userDoc.data();

  // Check if FCM token exists
  if (!userData || !userData.fcmToken) {
    console.error(`UserId: ${userId} does not have an FCM token.`);
    throw new functions.https.HttpsError(
      "failed-precondition",
      "User does not have a valid FCM token.",
    );
  }

  console.log(`FCM token retrieved for userId: ${userId}`);
  return userData.fcmToken;
}
