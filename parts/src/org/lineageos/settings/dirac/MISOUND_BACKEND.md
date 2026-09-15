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
