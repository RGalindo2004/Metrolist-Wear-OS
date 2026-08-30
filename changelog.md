---v13.6.4

- Added welcome message on home screen (@RGalindo2004)
- Added background update notifications (@RGalindo2004)
- Improved Wear OS authentication flow (@RGalindo2004)

---v13.6.3

This is a hotfix release to fix borked lyrics and media controller. We apologize for the inconvenience.  

~ MetrolistGroup

---v13.6.2
# THE FUTURE OF METROLIST
Metrolist KMP is almost ready. We are ironing out the remaining bugs and preparing for release, but it is still at least a couple of weeks away.

# Major changes
- Fixed playback issues caused by changes that also broke official apps (@nyxiereal)
- Fixed playlist sync duplication and out-of-memory crashes (@kairosci)
- Improved automatic player configuration updates for future YouTube changes (@mostafaalagamy @nyxiereal)

## Notable new features
- Added handling for KMP updates and migrations (@nyxiereal)

---v13.6.1
# THE FUTURE OF METROLIST
The new Kotlin Multiplatform version of Metrolist is now in a good state, and we are aiming to release it within the next month. Until then, the current app will remain in maintenance mode and receive bug fixes and minor improvements.

# Major changes
- Improved playback reliability and recovery from YouTube player failures (@alltechdev @JASK625 @kairosci @mostafaalagamy @nyxiereal)
- Fixed black screens, startup crashes, and playback freezes (@kairosci @mostafaalagamy @nyxiereal)
- Improved Listen Together synchronization (@nyxiereal)

---v13.2.0

- Improved Android Auto voice search matching and radio queue generation (@FireLion137)
- Added an Android Auto search limit and optimized local searches to prevent out-of-memory crashes (@FireLion137)
- Fixed podcast playback errors (@kairosci)
- Fixed crossfade timing at non-default playback speeds (@kairosci)
- Fixed the app lingering in the background after it was closed (@kairosci)
- Fixed sleep timer dialog layouts and menus containing long translated text (@kairosci)
- Fixed the persistent shuffle setting not working (@SimoneFelici)
- Dimmed the repeat button when repeat is disabled (@arpitagarwal1301)
- Rounded the corners of exported lyrics images (@arpitagarwal1301)
- Updated dependencies (@nyxiereal)

## New Contributors
* @SimoneFelici made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/4102
* @arpitagarwal1301 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/4178

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.6.1...v13.6.2

---v13.1.1
- Improved playback reliability and fixed media controller (@nyxiereal)
- Added support for untranslating lyrics (@nyxiereal)
- Rewrote music recognizer in pure Kotlin, removing NDK dependency and reducing APK size (@mostafaalagamy)
- Overhauled lyrics: added LyricsPlus provider, AI lyric fixes, untranslation support, and provider priority settings (@nyxiereal)
- Changed listen together to use protobuf, lowering latency and improving reliability (@nyxiereal)
- Added auto-approve setting for listen together song requests (@nyxiereal)
- Added an option to persist the sleep timer default value (@johannesbrauer)
- Added a dialog on logout to keep or clear library data (@alltechdev)

## Other improvements
- Fixed backup restore causing playback errors due to stale auth credentials (@alltechdev)
- The CSV import dialog is now scrollable (@kairosci)
- Fixed Android 15 foreground service crashes (@kairosci)
- Fixed a crash on the About screen on some devices (@mostafaalagamy)
- Fixed home screen playlist navigation routing to wrong screen (@mostafaalagamy)
- Fixed crash when creating local playlists (@mostafaalagamy)

## New Contributors
* @johannesbrauer made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/2991

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.1.1...v13.2.0
