package dev.ynagai.ondevice

import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel

/**
 * Single process-wide ML Kit client backing the stateless capability functions
 * ([checkOnDeviceStatus], [downloadOnDeviceModel], [warmUpOnDevice], [countOnDeviceTokens]).
 *
 * These are global top-level functions with no lifecycle handle to own a client, and
 * [Generation.getClient] allocates a brand-new [GenerativeModel] — each holding its own
 * worker [java.util.concurrent.ExecutorService] — on every call. Calling it per
 * invocation would leak a client+executor each time, which matters most for
 * [countOnDeviceTokens] on the per-prompt routing path. Sharing one lazily-created
 * client avoids that and lets a single [warmUpOnDevice] benefit every later query: the
 * on-device weights live in the system AICore service, so this handle is a thin proxy
 * and warmup is process-shared. The generator keeps its own injectable client, which
 * the shared warmup still benefits for the same reason.
 */
internal val capabilityClient: GenerativeModel by lazy { Generation.getClient() }
