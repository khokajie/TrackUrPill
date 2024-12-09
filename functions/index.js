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
          const dosage = reminder.dosage || 1; // Default dosage if not specified

          const dataPayload = {
            reminderId: String(reminder.reminderId),
            medicationId: String(reminder.medicationId),
            medicationName: String(reminder.medicationName),
            dosage: dosage.toString(), // Convert to string for FCM
            userId: String(reminder.userId),
            // Include notificationId later
          };

          // Create a unique notificationId
          const notificationId = admin
            .firestore()
            .collection("Notification")
            .doc().id;

          // Add notificationId to dataPayload
          dataPayload.notificationId = notificationId;

          // Define actions (handled client-side)
          // Since 'actions' are not supported in FCM payload for Android, we remove them here.

          // Send notification without 'actions'
          const messagingResponse = await admin.messaging().send({
            notification: {
              title: "Medication Reminder",
              body: `Time to take ${reminder.medicationName} (${dosage})`,
            },
            data: dataPayload, // All values are strings
            android: {
              priority: "high",
              notification: {
                channelId: "medication_reminders",
                priority: "high",
                // Removed 'actions' from here
              },
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
            type: "reminder",
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
        .where("email", "==", patientEmail)
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

      // Create a unique notificationId (document ID)
      const notificationId = admin
        .firestore()
        .collection("Notification")
        .doc().id;

      const notificationData = {
        userId: patientId,
        senderId: caregiverId,
        message:
          "A caregiver has invited you to TrackUrPill. Check your app to get started.",
        type: "invitation",
        status: "Pending",
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      };

      await admin
        .firestore()
        .collection("Notification")
        .doc(notificationId)
        .set(notificationData);

      // Prepare FCM payload without 'click_action'
      const payload = {
        notification: {
          title: "You've Been Invited!",
          body: notificationData.message,
          // Removed 'click_action' to handle it in the app
        },
        data: {
          type: notificationData.type,
          notificationId: notificationId,
          senderId: caregiverId,
        },
      };

      // Send FCM notification
      const response = await admin.messaging().sendToDevice(fcmToken, payload);
      console.log(`FCM Response: ${JSON.stringify(response)}`);

      // Optionally, update the Notification document with FCM response
      await admin
        .firestore()
        .collection("Notification")
        .doc(notificationId)
        .update({
          fcmResponse: response,
          status: "Sent",
          sentAt: admin.firestore.FieldValue.serverTimestamp(),
        });

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
