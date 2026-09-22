# ChikChik release rules.
# CameraX, Compose, AndroidX and Accompanist ship their own consumer ProGuard/R8 rules,
# so no extra keep rules are needed. If a release-only crash ever appears, check
# `app/build/outputs/mapping/release/mapping.txt` to de-obfuscate the stack trace.
