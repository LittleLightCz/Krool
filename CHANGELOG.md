# Changelog

## 0.1.0
- Kotlin 1.3 support
- new async builder: `krool(5) { "Hi, I am a String resource $it being initialized in parallel on the IO pool by default" }`
- removed `terminate()` since the new structured coroutines should take care of 
the cancellation automatically
- `kroolContext` is being shared globally now for all Krool resource pools

## 0.0.1
Initial draft