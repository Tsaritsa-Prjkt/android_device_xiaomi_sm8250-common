# Alioth MiSound backend evidence

Inspected vendor/lib/soundfx/libmisoundfx.so from vendor/xiaomi/sm8250-common.
SHA-256: 8a8cb2b953d296697db7ff3024661126e0c4932920b0e13778fbd016aa9c00d6
This is a 32-bit MiSound AudioEffect implementation registered as
5b8e36a5-144a-4c38-b1d7-0002a5d5c51b, despite the historical Dirac class name.

The stock android.media.audiofx.MiSound Java wrapper uses parameter 25 for
enable and separately calls AudioEffect.setEnabled. Thumb disassembly of
MiSound_command shows parameter 25 dispatching to native enable/disable commands.
Music selection (parameter 4) is not a substitute for this enable contract.
The backend now synchronizes both enable layers and retains music selection.
EQ payloads remain decimal text with explicit ASCII encoding.

The binary also exposes save/restore routines for headset, EQ, mode, music,
scenario, scenario switch, surround, vocal, ear compensation (left/right and
presets), SoundID and 3D surround. Symbol presence demonstrates implementation
hooks, not validated parameter compatibility or acoustic operation. The Java
wrapper contains additional APIs shared across devices; do not expose those
features merely because Java constants exist. No new tuning presets or hearing
compensation controls are enabled by this change. Hifi behavior is unchanged.

Validation: actual DiracSound.java compiled against an AudioEffect test stub;
native/framework enable synchronization and non-finite EQ rejection passed.
Full XiaomiParts build, real parameter readback and acoustic testing remain
separate validation steps.

## Headsets, presets, scenes and Hi-Fi follow-up

MiSound_command Thumb instructions at 0x8a78-0x8a80 accept EQ indices 0..9.
Existing seven-band preset curves are retained, but bands 7..9 are cleared on
application so previous native state cannot leak into a selected preset.
The six-band constant in stock Java belongs to ear compensation, not normal EQ.
Headset choices are filtered through parameter 19 with bounded little-endian
count parsing. Existing labels are retained; model marketing names are not
inferred from the stock Java constants, which differ from this UI catalog.
Scenes 1,2,3,4 match stock music/movie/vocal/auto values; 0 is default.
Hi-Fi parameter 8 and the separate hifi_mode path are retained: the shipped
32-bit audio.primary.kona.so contains that HAL key and Hi-Fi route handlers.
Hardware Hi-Fi operation and acoustic accuracy of preset curves remain untested.

## Rooted alioth validation (2026-09-16)

Device binary SHA-256 matches the reference above. A temporary app_process
probe attached to the live MiSound session, saved scalar parameters and framework
enable state, tested writes with processing disabled, and restored them before
releasing control. All 24 UI headset IDs and scenarios 0..4 round-tripped; music
and native enable 0/1 round-tripped. All ten existing EQ gains were accepted.
EQ GET rejects band 0 although SET accepts it; bands 1..9 were readable.

Parameter 19 returned only five bytes: count=1 followed by byte 12. This is not
an intact stock count-plus-int32-list reply. Treat it as unavailable and retain
the configured catalog, without repeated malformed-response exceptions.

Parameter 8 accepted 1 but read back 0. The product sets
vendor.audio.feature.hifi_audio.enable=false. Hide Hi-Fi and skip its restore
writes while that HAL feature is disabled; never enable the HAL feature merely
to expose a switch. Other products with the feature enabled retain the control.
These checks validate command acceptance/state, not acoustic quality, every
physical headset route, or hardware Hi-Fi performance.

The patched DiracSound class also ran on the device against the real framework:
malformed-list fallback returned empty, and enable/disable readback passed.
Full logs contain ACDB -100 (no active stream) and persistent-calibration -1
errors during idle probing. Parameter success therefore does not establish DSP
application. Active playback and physical-route validation remain necessary.
The UI changes have not been built or installed by this test.
