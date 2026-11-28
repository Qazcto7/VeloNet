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

- **Eunomia `0.3.16+` & Universal Cooperative Mixins:**
  - Verified zero-conflict compatibility with **Eunomia `0.3.16+26.3`** (custom payload injection, transport selection, and smoke-tested wire limits).
  - Uses cooperative `@Inject(HEAD, cancellable = true)` hooks in `Varint21FrameDecoderMixin` (with higher priority than PacketFixer's default injector to skip unnecessary off-heap `helperBuf` allocations) and `Connection.createFrameEncoder` instead of destructive `@Overwrite`s, keeping vanilla bytecode intact for third-party mixins.
- **ViaFabricPlus 5.1.4+ Pipeline Support:**
  - Automatically reorders Netty `compress` / `decompress` handlers around `via-decoder` / `via-encoder` and maintains ViaFabricPlus's `via-flow-control` (`NoReadFlowControlHandler`) ordering without disconnects.
  - Detects ViaLegacy's `vialegacy-pre-netty-length-remover` (cached via channel attribute) and seamlessly switches to unified single-buffer framing to prevent `IndexOutOfBoundsException` on legacy servers.
  - Preserves `KryptonPipelineEvent` signaling safely when Via pipeline handlers are active, preventing LAN server `NoSuchElementException` crashes alongside ViaFabricPlus 5.1.4.
- **PacketFixer Integration & Off-Heap Leak Fixes:**
  - Dynamically reads and clamps PacketFixer's configured packet size limit (`dev.tonimatas.packetfixer.util.Config.getDecoderSize()`) instead of hard-failing on large modded payloads.
  - Immediately releases vanilla's unused `Varint21FrameDecoder.helperBuf` at construction and bypasses PacketFixer's per-packet `@Inject(HEAD)` `Unpooled.directBuffer(...)` allocation while retaining a leak-safe cleanup guard.
  - Guards `Utf8String.write` against 32-bit signed integer overflow when PacketFixer passes large string length limits (`> Integer.MAX_VALUE / 3`).
- **e4mc Compatibility:**
  - Replaces fragile login encryption redirection with MixinExtras `@WrapOperation` on `ServerLoginPacketListenerImpl.handleKey`, gracefully delegating when `e4mc` disables packet-level encryption on Dialtone connections and falling back cleanly to vanilla encryption if native initialization fails.
- **Netty Lifecycle & Cross-Platform Safety:**
  - Intercepts writes on inactive channels in `write()` (`PipelineUtil.dropWriteIfInactive`) instead of returning empty output from `encode()`, eliminating Netty 4.2 `EncoderException: must produce at least one message` crashes.
  - Prevents in-place cipher mutation on shared packet buffers (`refCnt() > 1`) via `PipelineUtil.ensureMutableCompatibleBuffer`, and disposes native codecs cleanly in `handlerRemoved` / `handlerRemoved0`.
  - Ensures single-NIO-buffer compatibility (`nioBufferCount() == 1`) for `CompositeByteBuf` inputs on `JavaVelocityCompressor` (fixing Windows and Intel macOS crashes).
  - Enforces exact uncompressed byte-count verification after `inflate()` alongside zero-byte `threshold == 0` handling and a DEFLATE 1032:1 expansion-ratio zip-bomb guard.
  - Extends vanilla `CompressionDecoder` / `CompressionEncoder` directly so `instanceof` checks in other mods continue to work, while ending unused vanilla `Inflater`/`Deflater` instances immediately.
- **Additional Networking Refinements:**
  - Coalesces small packets (`<= 128` bytes) into a single buffer in `MinecraftVarintPrepender` while preserving zero-copy scatter/gather writes for larger packets.
  - Preserves Minecraft 26.3's `BandwidthDebugMonitor` (F3 network graph) metrics in `Varint21FrameDecoder`.
  - Removes the upstream entity tracker mixin so VeloNet stays strictly focused on the networking layer with zero conflicts alongside mods like **Eunomia**, **Sodium**, **Lithium**, **TT20**, **SiliciumX**, **HyperBoost**, **ModernFix**, and **ResourceTrimmer**.

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
