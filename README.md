# Grogut (Android)

Paste a YouTube / YT Music link, Or share link from youtube to Grogut. Pick **MP3 / M4A / AAC / OPUS** and a
**bitrate**, get the file. Everything runs on the phone.

## Build
Android Studio (Koala+) → open this folder → Run. Or:
```
./gradlew assembleRelease        # APK: app/build/outputs/apk/release/app-release.apk (debug-signed, installable)
```
Needs JDK 17 and Android SDK 34 (Android Studio installs both). minSdk 24.

## When YouTube breaks (updating the extractor)
The engine is [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), one Gradle dependency, one line
in `gradle/libs.versions.toml`:
```
./scripts/update-extractor.sh     # bumps that line to the newest release
./gradlew assembleRelease         # rebuild, reinstall
```
* `.github/workflows/update-extractor.yml` checks daily and opens a PR with the bump; merging it triggers
  `build.yml`, which builds a fresh APK.
* A fix merged upstream but not yet tagged: `./gradlew assembleRelease -PextractorSnapshot=<short-commit-hash>`.
* Only `core/Engine.java` touches the extractor API, so an API change upstream means editing one file.

## Formats
| Format | ORIG | Re-encode choices | How |
|---|---|---|---|
| MP3 | – | VBR (V0), 128, 192, 256, 320 | decode (MediaCodec) → pure-Java LAME (`:lame` module) + ID3v2 + cover |
| M4A | YouTube's AAC untouched | 128–320 | remux, or MediaCodec AAC encoder; tags + cover |
| AAC | YouTube's AAC untouched | 128–320 | raw ADTS stream |
| OPUS | YouTube's Opus untouched | 64–160 (needs Android 10+ encoder) | Ogg Opus writer, tags + cover |

ORIG is instant and lossless relative to YouTube's stream. Re-encoding above the source bitrate (~128–160 kbps)
only makes bigger files.

## Tests
`tests/*.java` are plain-JVM harnesses (MP3/M4A/ADTS/Ogg-Opus writers verified with ffprobe, chunked downloader and
link parser against a local HTTP server). The Android-specific parts (MediaCodec, UI, service) need a device.

Licenses: `lame/` is java-lame (LGPL-2.1). NewPipeExtractor is GPL-3.0, so this app as a whole is GPL-3.0.
Only download content you own or have permission to use; downloading from YouTube generally violates its ToS.
