# Spotify Bridge

Tiny Android app that registers as an audio player. When Mornye (Library › external
player) opens a downloaded track, it reads the file's tags and opens the **same album
in the Spotify app** instead of playing the file.

Lookup: Spotify Web API search (client credentials), album query first, then the album of a
matching track; the artist must match and the exact edition wins (`Matcher.kt`, unit-tested).
No credible match, or no credentials: it opens Spotify's search for artist + album.

**Podcasts** play locally instead: a file whose genre tag contains "podcast", or whose path
contains one of the keywords set in the app (e.g. `Podcasts, AntennaPod`), goes to the player
chosen in settings, or to Android's chooser without this app and without Mornye (opening a file
"with Mornye" switches its Library back to the internal player).

## Setup

1. Create an app on https://developer.spotify.com/dashboard (Web API; any redirect URI,
   e.g. `http://127.0.0.1:8888/callback`). Since February 2026 development-mode apps need
   the owner to have Spotify Premium.
2. Install the APK, open **Spotify Bridge**, paste Client ID and Secret, Save, then use the
   test button.
3. Mornye: Settings › Playback › Library › External player. On the first Play pick
   "Apri in Spotify" and set it as default.

## Build

No local JDK needed: Gradle runs in Docker against the SDK in `~/android-sdk`.

```bash
echo "sdk.dir=/sdk" > local.properties
docker run --rm -u $(id -u):$(id -g) -e GRADLE_USER_HOME=/gh -e HOME=/home/u \
  -e ANDROID_USER_HOME=/home/u/.android -e JAVA_TOOL_OPTIONS=-Duser.home=/home/u \
  -v ~/.gradle:/gh -v ~/.android:/home/u/.android -v ~/android-sdk:/sdk -v "$PWD":/p -w /p \
  gradle:8.5-jdk17 gradle --no-daemon testDebugUnitTest assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key in `~/.android/debug.keystore`: keep that
file, or updates will not install over the previous version. `ANDROID_USER_HOME` and
`user.home` are required: without them Gradle runs as the image's `gradle` user, creates a
fresh keystore in the container's throwaway home and every build gets a different key
(1.0.0 was built that way). Check: the cert digest must be `0ce9acb3…`:

```bash
docker run --rm -v "$PWD":/p -v ~/android-sdk:/sdk gradle:8.5-jdk17 \
  /sdk/build-tools/34.0.0/apksigner verify --print-certs /p/app/build/outputs/apk/release/app-release.apk
```

## License

MIT, see `LICENSE`.
