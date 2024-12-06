const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

/**
 * Convert UTC date to user's timezone
 * @param {Date} date UTC Date object
 * @param {string} timezone User's timezone
 * @return {Date} Date in user's timezone
 */
function convertToUserTimezone(date, timezone) {
  const utcDate = new Date(date.toLocaleString("en-US", { timeZone: "UTC" }));
  const tzDate = new Date(date.toLocaleString("en-US", { timeZone: timezone }));
  const offset = utcDate.getTime() - tzDate.getTime();
  return new Date(date.getTime() + offset);
}

/**
 * Calculate the next reminder time.
 * @param {object} reminder Reminder details
 * @param {string} userTimeZone The time zone of the user
 * @return {number} Next reminder time in milliseconds (UTC)
 */
function calculateNextReminderTime(reminder, userTimeZone) {
  console.log("Calculating reminder time for:", {
    date: reminder.date,
    hour: reminder.hour,
    minute: reminder.minute,
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

      // Validate hour and minute
      const hour = Number(reminder.hour);
      const minute = Number(reminder.minute);

      if (isNaN(hour) || hour < 0 || hour > 23) {
        console.error("Invalid hour:", reminder.hour);
        throw new Error(`Invalid hour. Expected 0-23, got: ${reminder.hour}`);
      }

      if (isNaN(minute) || minute < 0 || minute > 59) {
        console.error("Invalid minute:", reminder.minute);
        throw new Error(
          `Invalid minute. Expected 0-59, got: ${reminder.minute}`,
        );
      }

      // Parse the date string
      const [month, day, year] = reminder.date.split("/");

      // Create ISO date string
      const formattedDate = `${year}-${month.padStart(2, "0")}-${day.padStart(
        2,
        "0",
      )}`;
      const timeString = `${String(hour).padStart(2, "0")}:${String(
        minute,
      ).padStart(2, "0")}:00`;

      console.log("Formatted datetime:", `${formattedDate}T${timeString}`);

      // Create date object
      const reminderDate = new Date(`${formattedDate}T${timeString}`);

      if (isNaN(reminderDate.getTime())) {
        console.error("Invalid date created:", reminderDate);
        throw new Error("Failed to create valid date from components");
      }

      nextTime = reminderDate.getTime();

      console.log("Calculated timestamp:", nextTime);
      console.log("Formatted result:", new Date(nextTime).toISOString());

      break;
    }
    case "Weekly": {
      const now = new Date();
      const currentDay = now.getDay();
      const targetDay = reminder.day;

      let daysUntilTarget = targetDay - currentDay;
      if (daysUntilTarget <= 0) {
        daysUntilTarget += 7;
      }

      const reminderDate = new Date(
        Date.UTC(
          now.getUTCFullYear(),
          now.getUTCMonth(),
          now.getUTCDate() + daysUntilTarget,
          reminder.hour,
          reminder.minute,
          0,
          0,
        ),
      );

      const userTime = convertToUserTimezone(reminderDate, userTimeZone);
      nextTime = userTime.getTime();
      break;
    }
    default:
      throw new Error("Invalid reminder frequency");
  }

  if (!nextTime || isNaN(nextTime)) {
    throw new Error("Failed to calculate valid reminder time");
  }

  return nextTime;
}

/**
 * Schedule a reminder.
 */
exports.scheduleReminder = functions.https.onCall(async (data, context) => {
  console.log("Incoming data:", data);

  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated.",
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
      "Reminder data is incomplete or invalid.",
    );
  }

  console.log("Reminder date:", reminder.date);
  console.log("Reminder time:", `${reminder.hour}:${reminder.minute}`);
  console.log("User timezone:", userTimeZone);

  try {
    const medicationDoc = await admin
      .firestore()
      .collection("Medication")
      .doc(reminder.medicationId)
      .get();

    console.log("Medication Document Exists:", medicationDoc.exists);

    if (!medicationDoc.exists) {
      throw new Error("Medication not found");
    }

    const medication = medicationDoc.data();
    const userId = medication.userId;

    console.log("User ID from Medication:", userId);

    let userDoc = await admin
      .firestore()
      .collection("Patient")
      .doc(userId)
      .get();
    console.log("Patient Document Exists:", userDoc.exists);

    if (!userDoc.exists) {
      console.log("Patient not found. Checking Caregiver collection.");
      userDoc = await admin
        .firestore()
        .collection("Caregiver")
        .doc(userId)
        .get();
      console.log("Caregiver Document Exists:", userDoc.exists);
    }

    if (!userDoc.exists) {
      throw new Error("User not found");
    }

    const userData = userDoc.data();
    console.log("User Data:", userData);

    const fcmToken = userData.fcmToken;
    if (!fcmToken) {
      throw new Error("FCM token not found");
    }

    const nextReminderTime = calculateNextReminderTime(reminder, userTimeZone);
    console.log("Calculated next reminder time (ms):", nextReminderTime);
    console.log(
      "Calculated next reminder time (readable):",
      new Date(nextReminderTime).toISOString(),
    );
    console.log(
      "Calculated next reminder time (user timezone):",
      new Date(nextReminderTime).toLocaleString("en-US", {
        timeZone: userTimeZone,
      }),
    );

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

    if (nextReminderTime <= Date.now()) {
      await admin.messaging().send(message);
      await admin
        .firestore()
        .collection("Notification")
        .doc(notificationId)
        .set(notification);
      console.log("Immediate reminder sent and notification created.");
      return { success: true, immediate: true };
    } else {
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
      console.log("Scheduled reminder created.");
      return { success: true, scheduled: true };
    }
  } catch (error) {
    console.error("Error in scheduleReminder:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});

/**
 * Process reminders every minute.
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

      if (querySnapshot.empty) {
        console.log("No scheduled reminders to process at this time.");
        return null;
      }

      const promises = [];

      querySnapshot.forEach((doc) => {
        promises.push(
          admin.firestore().runTransaction(async (transaction) => {
            const docRef = doc.ref;
            const docSnapshot = await transaction.get(docRef);

            if (
              !docSnapshot.exists ||
              docSnapshot.data().status !== "scheduled" ||
              docSnapshot.data().nextScheduledTime > now
            ) {
              return;
            }

            const reminderData = docSnapshot.data();
            const { reminder, message, notification } = reminderData;

            const userTimeZone = reminder.userTimeZone;
            if (!userTimeZone) {
              console.error(
                `User time zone missing for reminder ${reminder.reminderId}`,
              );
              return;
            }

            await admin.messaging().send(message);

            const newNotificationId = admin
              .firestore()
              .collection("Notification")
              .doc().id;
            const newNotification = {
              notificationId: newNotificationId,
              message: notification.message,
              receiveTime: admin.firestore.Timestamp.fromMillis(now),
              type: "reminder",
              status: "sent",
              userId: notification.userId,
            };
            transaction.set(
              admin
                .firestore()
                .collection("Notification")
                .doc(newNotificationId),
              newNotification,
            );

            if (
              reminder.frequency === "Daily" ||
              reminder.frequency === "Weekly"
            ) {
              const nextScheduledTime = calculateNextReminderTime(
                reminder,
                userTimeZone,
              );
              transaction.update(docRef, {
                nextScheduledTime: nextScheduledTime,
                status: "scheduled",
              });
              console.log(
                `Reminder ${reminder.reminderId} rescheduled for ${new Date(
                  nextScheduledTime,
                ).toLocaleString("en-US", { timeZone: userTimeZone })}`,
              );
            } else {
              transaction.update(docRef, { status: "sent" });
              console.log(`Reminder ${reminder.reminderId} marked as sent.`);
            }
          }),
        );
      });

      await Promise.all(promises);
      console.log(`Processed ${querySnapshot.size} reminders.`);
      return null;
    } catch (error) {
      console.error("Error processing reminders:", error);
      return null;
    }
  });

/**
 * Cancel a reminder.
 */
exports.cancelReminder = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated.",
    );
  }

  const { reminderId } = data;
  if (!reminderId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "reminderId is required.",
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

    await admin
      .firestore()
      .collection("scheduledReminders")
      .doc(reminderId)
      .update({ status: "canceled" });

    console.log(`Reminder ${reminderId} has been canceled.`);
    return { success: true };
  } catch (error) {
    console.error("Error canceling reminder:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});
