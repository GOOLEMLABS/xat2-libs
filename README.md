# xat2-libs

Kotlin Multiplatform libraries extracted from [xat2](https://xat2.me) — a private,
decentralized messaging app for Android and iOS.

All modules target **Android + iOS** (iosX64, iosArm64, iosSimulatorArm64).  
Zero individual-developer dependencies. Zero telemetry.

---

## Modules

### `kmp-markdown`
Pure Compose Multiplatform Markdown renderer backed by `AnnotatedString`. No external
dependencies beyond Compose itself.

Supports: `# headers`, `**bold**`, `*italic*`, `` `code` ``, `- bullets`, paragraph breaks.

```kotlin
SimpleMarkdown(content = "**Hello** *world*")
```

---

### `kmp-sha3`
Pure Kotlin SHA3-256 (Keccak, FIPS 202). No dependencies, no JVM-only code.

```kotlin
val hash: ByteArray = Sha3.sha3_256("hello".encodeToByteArray())
```

---

### `kmp-base58`
Bitcoin-alphabet Base58 encoder. Pure Kotlin, no dependencies.

```kotlin
val encoded: String = Base58.encode(byteArrayOf(1, 2, 3))
```

---

### `kmp-zip`
Minimal ZIP reader supporting STORE and DEFLATE. Pure Kotlin commonMain;
platform `inflate` via `java.util.zip.Inflater` (Android) and `zlib` cinterop (iOS).

```kotlin
val entries: Map<String, ByteArray> = ZipReader.read(zipBytes)
val json = entries["manifest.json"]?.decodeToString()
```

---

### `kmp-phash`
Perceptual image hashing (DCT pHash) + PhotoDNA-style content moderation pipeline.
No external dependencies. Platform grayscale decoder via `android.graphics` / CoreGraphics.

```kotlin
val grayscale = decodeToGrayscale32x32(imageBytes) ?: return
val hash = PHash.compute(grayscale)

// Content moderation
HashBlocklist.Default.loadFromLines(ncmecHashLines)
val result = ContentScanner().scan(imageBytes)
if (result is ContentScanner.Result.Blocked) { /* reject */ }
```

---

### `kmp-tor-keyformat`
Converts an Ed25519 keypair to the file format used by Tor v3 hidden services
(`hs_ed25519_secret_key`, `hs_ed25519_public_key`). Useful for apps that embed Tor
and want a deterministic `.onion` address derived from the user's Ed25519 identity.

Key expansion follows RFC 8032 §5.1.5. SHA-512 via `MessageDigest` (Android) /
`CommonCrypto CC_SHA512` (iOS) — no libsodium required.

```kotlin
val secretKeyFile: ByteArray = TorKeyFormat.secretKeyFile(libsodiumSecretKey64Bytes)
val publicKeyFile: ByteArray = TorKeyFormat.publicKeyFile(ed25519PublicKey32Bytes)
// Write these to the Tor hidden service directory before starting Tor
```

---

## Installation (JitPack)

Add JitPack to your repositories:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

Then import only the modules you need:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.GOOLEMLABS.xat2-libs:kmp-markdown:1.0.0")
    implementation("com.github.GOOLEMLABS.xat2-libs:kmp-sha3:1.0.0")
    implementation("com.github.GOOLEMLABS.xat2-libs:kmp-base58:1.0.0")
    implementation("com.github.GOOLEMLABS.xat2-libs:kmp-zip:1.0.0")
    implementation("com.github.GOOLEMLABS.xat2-libs:kmp-phash:1.0.0")
    implementation("com.github.GOOLEMLABS.xat2-libs:kmp-tor-keyformat:1.0.0")
}
```

Replace `1.0.0` with the latest [release tag](https://github.com/GOOLEMLABS/xat2-libs/releases).

---

## Requirements

| Module | Android minSdk | iOS deployment |
|---|---|---|
| All | 26 | 16.0 |

Kotlin 2.2.x · Compose Multiplatform 1.7.x (kmp-markdown only)

---

## Authors

**Pedro Luis García Alonso** · [GOOLEM LABS](https://github.com/GOOLEMLABS)

---

## License

MIT — see [LICENSE](LICENSE).
