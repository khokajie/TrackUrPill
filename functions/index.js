const functions = require("firebase-functions");
const admin = require("firebase-admin");
const moment = require("moment-timezone");
admin.initializeApp();

// Define valid frequencies globally
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

      const [year, month, day] = dateStr.split("-").map(Number);
      const hour = parseInt(reminder.hour, 10);
      const minute = parseInt(reminder.minute, 10);

      if (isNaN(hour) || hour < 0 || hour > 23) {
        throw new Error(`Invalid hour: ${reminder.hour}`);
      }
      if (isNaN(minute) || minute < 0 || minute > 59) {
        throw new Error(`Invalid minute: ${reminder.minute}`);
      }

      console.log("Parsed components:", {
        date: dateStr,
        year,
        month,
        day,
        hour,
        minute,
      });

      const reminderTime = moment.tz(
        {
          year: year,
          month: month - 1, // Moment months are 0-based
          date: day,
          hour: hour,
          minute: minute,
          second: 0,
          millisecond: 0,
        },
        userTimeZone,
      );

      if (!reminderTime.isValid()) {
        throw new Error("Invalid date/time combination");
      }

      const now = moment().tz(userTimeZone);
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

      if (reminderTime.isBefore(now)) {
        reminderTime.add(1, "day");
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

      if (reminderTime.isBefore(now)) {
        reminderTime.add(1, "week");
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
      if (!reminder[field]) {
        throw new functions.https.HttpsError(
          "invalid-argument",
          `Missing required field: ${field}`,
        );
      }
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
      nextScheduledTime: nextReminderTime,
      status: "active",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    batch.set(reminderRef, reminderData);

    const scheduledTaskRef = admin
      .firestore()
      .collection("ScheduledTasks")
      .doc(reminder.reminderId);

    batch.set(scheduledTaskRef, {
      type: "reminder",
      reminderId: reminder.reminderId,
      userId: userId,
      nextScheduledTime: nextReminderTime,
      fcmToken: fcmToken,
      status: "scheduled",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

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
    const now = Date.now();

    try {
      const querySnapshot = await admin
        .firestore()
        .collection("ScheduledTasks")
        .where("type", "==", "reminder")
        .where("status", "==", "scheduled")
        .where("nextScheduledTime", "<=", now)
        .get();

      if (querySnapshot.empty) {
        return null;
      }

      const promises = querySnapshot.docs.map(async (doc) => {
        const scheduledTask = doc.data();
        const { reminderId, fcmToken, userId } = scheduledTask;

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
            return;
          }

          const reminder = reminderDoc.data();

          // Send notification
          await admin.messaging().send({
            notification: {
              title: "Medication Reminder",
              body: `Time to take ${reminder.medicationName} (${reminder.dosage})`,
            },
            data: {
              reminderId: reminder.reminderId,
              medicationId: reminder.medicationId,
              medicationName: reminder.medicationName,
              dosage: reminder.dosage,
              userId: reminder.userId,
            },
            android: {
              priority: "high",
              notification: {
                channelId: "medication_reminders",
                priority: "high",
              },
            },
            token: fcmToken,
          });

          // Create notification record
          const notificationRef = admin
            .firestore()
            .collection("Notifications")
            .doc();

          await notificationRef.set({
            notificationId: notificationRef.id,
            type: "reminder",
            reminderId: reminderId,
            userId: userId,
            message: `Time to take ${reminder.medicationName} (${reminder.dosage})`,
            timestamp: admin.firestore.Timestamp.fromMillis(now),
            status: "sent",
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
          } else {
            const nextTime = calculateNextReminderTime(
              reminder,
              reminder.userTimeZone,
            );

            await Promise.all([
              reminderDoc.ref.update({
                nextScheduledTime: nextTime,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              }),
              doc.ref.update({
                nextScheduledTime: nextTime,
                status: "scheduled",
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              }),
            ]);
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
      return null;
    } catch (error) {
      console.error("Error processing reminders:", error);
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
        "User must be authenticated",
      );
    }

    // Validate input
    const { reminderId } = data;
    if (!reminderId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "reminderId is required",
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
