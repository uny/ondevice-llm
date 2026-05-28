package dev.ynagai.ondevice

/** Wraps any platform on-device inference failure (single-shot or streaming). */
class OnDeviceInferenceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
