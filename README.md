# GraphHopper for Locus Map ![logo](app/src/main/res/drawable-mdpi/ic_launcher.png "Contacts for Locus Map logo")

Add-on for [Locus Map](http://www.locusmap.eu) (Android) application, created to offer offline routing capabilities to Locus Map.

Add-on is based on [GraphHopper](https://graphhopper.com/) routing system.

Ready to use [routing data](https://graphhopper.develar.org/) are available.

## Possible improvements

- TODO

## Important information

### Build setup

- base parameters for build are defined in [Dependencies.kt](https://github.com/asamm/locus-addon-graphhopper/blob/master/buildSrc/src/main/kotlin/Dependencies.kt) file (buildSrc dir).
- GraphHopper version is defined manually in module/library (to keep bundled in library directly) 

### Signing

For **DEBUG**, define environment variable
- **key**: ANDROID_SIGN_DEBUG
- **value**: 'path to debug.keystore'|android|androiddebugkey|android

For **RELEASE**, define environment variable
- **key**: ANDROID_SIGN_RELEASE
- **value**: 'path to release.keystore'|'store password'|'key alias'|'key password'

### Available

not yet published