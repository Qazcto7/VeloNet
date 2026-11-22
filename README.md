# VeloNet ⚡

**VeloNet** *(formerly known as KryptonPlus)* is an independent, unofficial fork of [Krypton](https://github.com/astei/krypton) by Andrew Steinborn (`tuxed`) for **Minecraft 26.3 (Fabric)**, powered by [PaperMC Velocity](https://github.com/PaperMC/Velocity) networking natives.

> **Disclaimer:** VeloNet is an independent community fork and is **not affiliated with, sponsored by, or endorsed by** Andrew Steinborn (`tuxed`) or the original Krypton project.

---

## What Upstream Krypton Does

Upstream [Krypton](https://github.com/astei/krypton) optimizes Minecraft's Netty networking stack by:
- Replacing vanilla Java `Deflater`/`Inflater` packet compression with Velocity's native `libdeflate` bindings (with JVM fallback).
- Replacing vanilla Java cipher handlers with Velocity's native OpenSSL AES-CFB8 encryption (with JVM AES-NI fallback).
- Speeding up VarInt length calculation/encoding, VarInt21 frame splitting, and UTF-8 string serialization.
- Tuning Netty's pooled allocator chunk size (`io.netty.allocator.maxOrder=9`) and preventing redundant leak detector overrides.

---

## What VeloNet Changes & Improves

VeloNet diverges significantly from upstream Krypton to provide modern mod-pack compatibility, eliminate off-heap native memory leaks, and harden the Netty pipeline across platforms:

- **ViaFabricPlus 5.1.3+ Pipeline Support:**
  - Automatically reorders Netty `compress` / `decompress` handlers around `via-decoder` / `via-encoder` and maintains ViaFabricPlus 5.1.3's `via-flow-control` (`NoReadFlowControlHandler`) ordering without disconnects.
  - Detects ViaLegacy's `vialegacy-pre-netty-length-remover` (cached via channel attribute) and seamlessly switches to unified single-buffer framing to prevent `IndexOutOfBoundsException` on legacy servers.
  - Preserves `KryptonPipelineEvent` signaling for third-party pipeline listeners.
- **PacketFixer Integration & Off-Heap Leak Fixes:**
  - Dynamically reads and clamps PacketFixer's configured packet size limit (`dev.tonimatas.packetfixer.util.Config.getDecoderSize()`) instead of hard-failing on large modded payloads.
  - Immediately releases vanilla's unused `Varint21FrameDecoder.helperBuf` at construction and after PacketFixer's `@Inject(HEAD)` re-allocation, eliminating a per-connection off-heap direct memory leak.
  - Guards `Utf8String.write` against 32-bit signed integer overflow when PacketFixer passes large string length limits (`> Integer.MAX_VALUE / 3`).
- **e4mc Compatibility:**
  - Replaces fragile login encryption redirection with MixinExtras `@WrapOperation` on `ServerLoginPacketListenerImpl.handleKey`, gracefully delegating when `e4mc` disables packet-level encryption on Dialtone connections and falling back cleanly to vanilla encryption if native initialization fails.
- **Netty Lifecycle & Cross-Platform Safety:**
  - Intercepts writes on inactive channels in `write()` (`PipelineUtil.dropWriteIfInactive`) instead of returning empty output from `encode()`, eliminating Netty 4.2 `EncoderException: must produce at least one message` crashes.
  - Removes premature native cipher/compressor disposal during outbound `close()`, relying safely on `handlerRemoved` / `handlerRemoved0` lifecycle cleanup and guarding buffer release on `Throwable`/`Error`.
  - Ensures single-NIO-buffer compatibility (`nioBufferCount() == 1`) for `CompositeByteBuf` inputs on `JavaVelocityCompressor` (fixing Windows and Intel macOS crashes).
  - Handles zero-byte frames cleanly when compression `threshold == 0` and adds a DEFLATE theoretical expansion-ratio (1032:1) zip-bomb guard.
  - Extends vanilla `CompressionDecoder` / `CompressionEncoder` directly so `instanceof` checks in other mods continue to work, while ending unused vanilla `Inflater`/`Deflater` instances immediately.
- **Additional Networking Refinements:**
  - Coalesces small packets (`<= 128` bytes) into a single buffer in `MinecraftVarintPrepender` while preserving zero-copy scatter/gather writes for larger packets.
  - Preserves Minecraft 26.3's `BandwidthDebugMonitor` (F3 network graph) metrics in `Varint21FrameDecoder`.
  - Removes the upstream entity tracker mixin so VeloNet stays strictly focused on the networking layer with zero conflicts alongside mods like **Eunomia**, **Sodium**, **Lithium**, **TT20**, **ModernFix**, and **ResourceTrimmer**.

---

## Compatibility & Requirements

| Requirement | Details |
| --- | --- |
| **Mod Loader** | Fabric (`Fabric Loader >= 0.18.4`) |
| **Minecraft** | `26.3` |
| **Java** | `Java 25+` |
| **Dependencies** | None *(Fabric API is **not** required)* |
| **Environment** | Client & Server *(Singleplayer, Multiplayer, Dedicated Servers)* |
| **Drop-In Compatibility** | Declares `provides: ["krypton", "kryptonplus"]` — do not install alongside upstream **Krypton** |

---

## Building from Source

Requires **JDK 25**. All dependencies (including `com.velocitypowered:velocity-native`) are resolved directly from official Maven repositories.

```bash
./gradlew clean build
```

Built JAR files are written to `build/libs/`:
- `velonet-<version>.jar`
- `velonet-<version>-sources.jar`

---

## Credits & License

- Upstream **Krypton** mod by [Andrew Steinborn (`tuxed`)](https://github.com/astei/krypton)
- Native compression & encryption libraries by [PaperMC Velocity](https://github.com/PaperMC/Velocity)

Licensed under the **GNU Lesser General Public License v3.0 (LGPL-3.0)** — see [LICENSE](LICENSE) for full license terms.
