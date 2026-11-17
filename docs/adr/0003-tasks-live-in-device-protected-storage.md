# Tasks are stored in device-protected storage

After a restart, Android keeps an app's normal (credential-protected) storage locked until the owner enters their PIN. The task database lives in device-protected storage instead, and the app is direct-boot aware, so a task due while the phone sits restarted and locked can still be rescheduled and fire. Firing after reboot is a hard requirement and a restart overnight (a HiOS update, a flat battery) must not silence the morning's tasks. The owner accepted the cost: task titles are less protected on a stolen, restarted phone.

## Consequences

- Every component that runs before first unlock (boot receiver, fire receiver, the ring service) may only touch device-protected storage.
- Moving the database to credential-protected storage later needs a data migration.
