# CaesarBandBridge

This is an independently packaged, clean-room companion for CampusAI. It does not embed,
fork, link, or copy Gadgetbridge source code. `GadgetbridgeIntentAdapter` uses only the public
Android Intent action names documented at <https://gadgetbridge.org/internals/automations/intents/>.

The current slice can request Gadgetbridge activity-history synchronization and listen for its
documented connected/sync-finished broadcasts. Those broadcasts do **not** provide a complete
connection state query or live Band 9 measurements. Consequently, heart rate, steps, battery,
wearing state, and raw Band 9 protocol capabilities stay unavailable until an independently
verified adapter is implemented and validated on the target firmware.

The separate package, storage directory, signature permission, and Keystore vault keep pairing
material out of CampusAI. Gadgetbridge-owned keys are not extracted. If future work copies or
links AGPL-covered Gadgetbridge implementation, distribution and corresponding-source duties
must be reassessed; this module intentionally avoids that boundary.
