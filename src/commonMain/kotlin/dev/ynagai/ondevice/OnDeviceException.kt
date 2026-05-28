package dev.ynagai.ondevice

/** Wraps any platform inference failure. */
class OnDeviceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
