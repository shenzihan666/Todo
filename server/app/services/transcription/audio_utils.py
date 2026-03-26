"""PCM buffer helpers for speech streaming."""

import numpy as np


def pcm_s16le_to_float32_mono(pcm: bytes) -> np.ndarray:
    """Convert little-endian int16 PCM to float32 in [-1, 1], mono."""
    if len(pcm) % 2 != 0:
        pcm = pcm[: len(pcm) - (len(pcm) % 2)]
    if len(pcm) == 0:
        return np.array([], dtype=np.float32)
    return np.frombuffer(pcm, dtype=np.int16).astype(np.float32) / 32768.0


def rms_db(audio_float32: np.ndarray) -> float:
    """Rough RMS in dB for logging; empty returns -100."""
    if audio_float32.size == 0:
        return -100.0
    rms = float(np.sqrt(np.mean(np.square(audio_float32))))
    if rms <= 1e-10:
        return -100.0
    return 20.0 * float(np.log10(rms))
