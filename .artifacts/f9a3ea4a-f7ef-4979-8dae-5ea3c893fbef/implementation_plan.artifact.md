# Implementation Plan - Fix Playlist and Library Item Navigation on Wear OS

The user reported that clicking on playlists in the Wear OS app does not open them, and songs inside are not displayed. This is because the navigation for playlist, album, and artist details was not implemented (marked with `TODO`).

## User Review Required

> [!IMPORTANT]
> I will be implementing generic detail screens for Playlists, Albums, and Artists in the Wear OS module. These screens will display the list of songs and allow playing them.

## Proposed Changes

### Wear OS UI

#### [MODIFY] [WearScreens.kt](file:///C:/Users/ramir/AndroidStudioProjects/Metrolist/wear/src/main/kotlin/com/metrolist/music/ui/WearScreens.kt)
- Add `WearPlaylistSongsScreen`, `WearAlbumSongsScreen`, and `WearArtistSongsScreen` composables.
- Add necessary imports for `PlaylistSong`.

#### [MODIFY] [WearApp.kt](file:///C:/Users/ramir/AndroidStudioProjects/Metrolist/wear/src/main/kotlin/com/metrolist/music/ui/WearApp.kt)
- Implement `onPlaylistClick`, `onAlbumClick`, and `onArtistClick` callbacks in the library screens to navigate to the respective detail routes.
- Add new navigation routes for `library/playlists/{playlistId}`, `library/albums/{albumId}`, and `library/artists/{artistId}`.
- Add imports for `navArgument` and `NavType`.

---

## Verification Plan

### Manual Verification
1.  Build and deploy the Wear OS app (`:wear`).
2.  Navigate to Library -> Playlists.
3.  Click on a playlist. Verify that it opens a screen showing the songs.
4.  Click on a song. Verify that it starts playback.
5.  Repeat for Albums and Artists.

### Automated Tests
- Since this is a UI/Navigation fix, manual verification on an emulator/device is the primary method.
