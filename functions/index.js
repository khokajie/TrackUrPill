const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

/**
 * Calculate the next reminder time
 * @param {object} reminder - The reminder object containing schedule details
 * @param {string} userTimeZone - The user's timezone
 * @return {number} Next reminder time in milliseconds (UTC)
 */
function calculateNextReminderTime(reminder, userTimeZone) {
  console.log("Calculating reminder time for:", {
    date: reminder.date,
    hour: reminder.hour,
    minute: reminder.minute,
    frequency: reminder.frequency,
    timezone: userTimeZone,
  });

  let nextTime;

  switch (reminder.frequency) {
    case "Once": {
      if (!reminder.date || typeof reminder.date !== "string") {
        console.error("Invalid date:", reminder.date);
        throw new Error(
          `Invalid date format. Expected MM/DD/YYYY string, got: ${reminder.date}`,
        );
      }

      const [month, day, year] = reminder.date.split("/");
      const hour = Number(reminder.hour);
      const minute = Number(reminder.minute);

      // Validate components
      if (!month || !day || !year) {
        throw new Error(
          `Invalid date format. Expected MM/DD/YYYY, got: ${reminder.date}`,
        );
      }
      if (isNaN(hour) || hour < 0 || hour > 23) {
        throw new Error(`Invalid hour. Expected 0-23, got: ${reminder.hour}`);
      }
      if (isNaN(minute) || minute < 0 || minute > 59) {
        throw new Error(
          `Invalid minute. Expected 0-59, got: ${reminder.minute}`,
        );
      }

      // Create date in user's timezone
      const userDate = new Date(
        `${year}-${month.padStart(2, "0")}-${day.padStart(2, "0")}T${String(
          hour,
        ).padStart(2, "0")}:${String(minute).padStart(2, "0")}:00`,
      );

      // Convert to UTC timestamp
      const utcDate = new Date(
        userDate.toLocaleString("en-US", { timeZone: "UTC" }),
      );
      nextTime = utcDate.getTime();

      // Compare with current time in user's timezone
      const nowInUserTZ = new Date(
        new Date().toLocaleString("en-US", { timeZone: userTimeZone }),
      );

      if (userDate.getTime() <= nowInUserTZ.getTime()) {
        console.error("Reminder time validation failed:", {
          reminderTime: userDate.toISOString(),
          currentTime: nowInUserTZ.toISOString(),
          timezone: userTimeZone,
        });
        throw new Error("Reminder time must be in the future");
      }

      console.log("Time calculation debug:", {
        inputDate: reminder.date,
        inputTime: `${hour}:${minute}`,
        userTimezone: userTimeZone,
        calculatedUserTime: userDate.toLocaleString("en-US", {
          timeZone: userTimeZone,
        }),
        calculatedUTC: utcDate.toISOString(),
        currentTimeInUserTZ: nowInUserTZ.toLocaleString("en-US", {
          timeZone: userTimeZone,
        }),
        timestamp: nextTime,
      });

      break;
    }

    case "Daily": {
      const now = new Date();
      const userNow = new Date(
        now.toLocaleString("en-US", { timeZone: userTimeZone }),
      );

      const reminderDate = new Date(
        userNow.getFullYear(),
        userNow.getMonth(),
        userNow.getDate(),
        reminder.hour,
        reminder.minute,
        0,
        0,
      );

      if (reminderDate <= userNow) {
        reminderDate.setDate(reminderDate.getDate() + 1);
      }

      // Convert to UTC for storage
      const utcDate = new Date(
        reminderDate.toLocaleString("en-US", { timeZone: "UTC" }),
      );
      nextTime = utcDate.getTime();
      break;
    }

    case "Weekly": {
      const targetDay = reminder.day;
      const now = new Date();
      const userNow = new Date(
        now.toLocaleString("en-US", { timeZone: userTimeZone }),
      );

      const currentDay = userNow.getDay();
      let daysUntilTarget = targetDay - currentDay;

      if (daysUntilTarget <= 0) {
        daysUntilTarget += 7;
      }

      const reminderDate = new Date(
        userNow.getFullYear(),
        userNow.getMonth(),
        userNow.getDate() + daysUntilTarget,
        reminder.hour,
        reminder.minute,
        0,
        0,
      );

      // Convert to UTC for storage
      const utcDate = new Date(
        reminderDate.toLocaleString("en-US", { timeZone: "UTC" }),
      );
      nextTime = utcDate.getTime();
      break;
    }

    default:
      throw new Error(`Invalid frequency: ${reminder.frequency}`);
  }

  if (!nextTime || isNaN(nextTime)) {
    throw new Error("Failed to calculate valid reminder time");
  }

  return nextTime;
}

/**
 * Schedule a reminder
 * @param {object} data - The reminder data
 * @param {object} context - The function context
 * @returns {Promise<object>} Operation result
 */
exports.scheduleReminder = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated",
    );
  }

  const { reminder, userTimeZone } = data;

  if (
    !reminder ||
    !reminder.reminderId ||
    !reminder.medicationId ||
    !reminder.frequency ||
    (reminder.frequency === "Weekly" && !reminder.day)
  ) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Reminder data is incomplete or invalid",
    );
  }

  try {
    const nextReminderTime = calculateNextReminderTime(reminder, userTimeZone);

    const medicationDoc = await admin
      .firestore()
      .collection("Medication")
      .doc(reminder.medicationId)
      .get();

    if (!medicationDoc.exists) {
      throw new Error("Medication not found");
    }

    const medication = medicationDoc.data();
    const userId = medication.userId;

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
      throw new Error("User not found");
    }

    const fcmToken = userDoc.data().fcmToken;
    if (!fcmToken) {
      throw new Error("FCM token not found");
    }

    const message = {
      data: {
        reminderId: reminder.reminderId,
        medicationId: reminder.medicationId,
        medicationName: medication.medicationName,
        dosage: medication.dosage,
        userId: userId,
      },
      token: fcmToken,
    };

    const notificationId = admin
      .firestore()
      .collection("Notification")
      .doc().id;
    const notification = {
      notificationId: notificationId,
      message: `Time to take ${medication.medicationName} (${medication.dosage})`,
      receiveTime: admin.firestore.Timestamp.fromMillis(nextReminderTime),
      type: "reminder",
      status: "scheduled",
      userId: userId,
    };

    await admin
      .firestore()
      .collection("scheduledReminders")
      .doc(reminder.reminderId)
      .set({
        reminder: {
          ...reminder,
          userTimeZone,
        },
        nextScheduledTime: nextReminderTime,
        message: message,
        notification: notification,
        status: "scheduled",
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

    return { success: true, scheduled: true };
  } catch (error) {
    console.error("Error in scheduleReminder:", error);
    throw new functions.https.HttpsError("internal", error.message);
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
        .collection("scheduledReminders")
        .where("status", "==", "scheduled")
        .where("nextScheduledTime", "<=", now)
        .get();

      if (querySnapshot.empty) return null;

      const promises = querySnapshot.docs.map(async (doc) => {
        const reminderData = doc.data();
        const { reminder, message, notification } = reminderData;

        // Additional check to prevent early processing
        if (reminderData.nextScheduledTime > now) return;

        try {
          // Send the notification
          await admin.messaging().send(message);

          // Create notification record
          const newNotificationId = admin
            .firestore()
            .collection("Notification")
            .doc().id;
          await admin
            .firestore()
            .collection("Notification")
            .doc(newNotificationId)
            .set({
              ...notification,
              notificationId: newNotificationId,
              receiveTime: admin.firestore.Timestamp.fromMillis(now),
              status: "sent",
            });

          // Update reminder status
          if (reminder.frequency === "Once") {
            await doc.ref.update({ status: "sent" });
          } else {
            const nextTime = calculateNextReminderTime(
              reminder,
              reminder.userTimeZone,
            );
            await doc.ref.update({
              nextScheduledTime: nextTime,
              status: "scheduled",
            });
          }
        } catch (error) {
          console.error(
            `Error processing reminder ${reminder.reminderId}:`,
            error,
          );
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
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated",
    );
  }

  const { reminderId } = data;
  if (!reminderId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "reminderId is required",
    );
  }

  try {
    const reminderDoc = await admin
      .firestore()
      .collection("scheduledReminders")
      .doc(reminderId)
      .get();

    if (!reminderDoc.exists) {
      return { success: false, message: "No reminder found with that ID" };
    }

    await reminderDoc.ref.update({ status: "canceled" });
    return { success: true };
  } catch (error) {
    console.error("Error canceling reminder:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});
