#!/usr/bin/env python3
"""Sintetiza desde cero la música del video de Tablero (sin muestras ni librerías de audio).

120 BPM, La menor, progresión Am – F – C – G (un acorde por compás), 24 compases = 48 s.
Instrumentos: pad (sierras desafinadas con filtro aditivo), pluck en corcheas con eco,
bajo, bombo, hi-hat, clap y un lead sencillo. Todo es numpy puro.

Uso: python3 sintetizar.py salida.wav
"""
import sys
import wave

import numpy as np

SR = 44100
BPM = 120
BEAT = 60.0 / BPM           # 0,5 s
BAR = BEAT * 4              # 2 s
BARS = 24
DUR = BARS * BAR            # 48 s
N = int(SR * DUR)
rng = np.random.default_rng(7)

L = np.zeros(N)
R = np.zeros(N)


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def put(buf, t0, sig, gain=1.0):
    i = int(t0 * SR)
    if i >= N:
        return
    j = min(N, i + len(sig))
    buf[i:j] += sig[: j - i] * gain


def stereo(sig, t0, gain=1.0, pan=0.0):
    """pan -1 (izq) .. 1 (der), ley de potencia constante."""
    a = (pan + 1) * np.pi / 4
    put(L, t0, sig, gain * np.cos(a))
    put(R, t0, sig, gain * np.sin(a))


def env(n, a=0.005, d=0.1, s=0.7, r=0.1):
    e = np.ones(n)
    na, nd, nr = int(a * SR), int(d * SR), int(r * SR)
    na = max(1, min(na, n))
    e[:na] = np.linspace(0, 1, na)
    nd = min(nd, n - na)
    if nd > 0:
        e[na:na + nd] = np.linspace(1, s, nd)
        e[na + nd:] = s
    nr = min(nr, n)
    if nr > 0:
        e[-nr:] *= np.linspace(1, 0, nr)
    return e


def saw_add(freq, n, harmonics, detune_cents=0.0):
    """Sierra aditiva con tope de armónicos (hace de filtro pasa-bajos)."""
    t = np.arange(n) / SR
    f = freq * 2 ** (detune_cents / 1200)
    out = np.zeros(n)
    for k in range(1, harmonics + 1):
        if f * k > 9000:
            break
        out += np.sin(2 * np.pi * f * k * t) / k
    return out * (2 / np.pi)


# ---------------------------------------------------------------- armonía
CHORDS = [  # notas MIDI (triadas), raíz del bajo
    ([57, 60, 64], 33),   # Am: A3 C4 E4 / A1
    ([53, 57, 60], 29),   # F : F3 A3 C4 / F1
    ([60, 64, 67], 36),   # C : C4 E4 G4 / C2
    ([55, 59, 62], 31),   # G : G3 B3 D4 / G1
]
# melodía del lead (grados dentro de la escala de La menor), por compás de 16 a 21
LEAD = {
    8:  [(0, 76, 1.5), (2, 79, 0.5), (3, 76, 0.5), (3.5, 74, 0.5)],
    9:  [(0, 72, 1.5), (2, 74, 0.5), (3, 76, 1.0)],
    10: [(0, 79, 1.5), (2, 76, 0.5), (3, 74, 0.5), (3.5, 72, 0.5)],
    11: [(0, 74, 2.0), (2.5, 76, 0.5), (3, 79, 1.0)],
    12: [(0, 81, 1.5), (2, 79, 0.5), (3, 76, 1.0)],
    13: [(0, 77, 1.5), (2, 76, 0.5), (3, 72, 1.0)],
    14: [(0, 76, 1.0), (1, 79, 1.0), (2, 84, 2.0)],
    15: [(0, 83, 1.5), (2, 79, 0.5), (3, 74, 1.0)],
    16: [(0, 76, 1.5), (2, 79, 0.5), (3, 76, 0.5), (3.5, 74, 0.5)],
    17: [(0, 72, 1.5), (2, 74, 0.5), (3, 76, 1.0)],
    18: [(0, 79, 1.5), (2, 81, 0.5), (3, 79, 0.5), (3.5, 76, 0.5)],
    19: [(0, 74, 2.0), (2.5, 76, 0.5), (3, 79, 1.0)],
    20: [(0, 84, 1.5), (2, 81, 0.5), (3, 79, 1.0)],
    21: [(0, 81, 1.5), (2, 79, 0.5), (3, 76, 1.0)],
    22: [(0, 76, 2.0), (2, 72, 2.0)],
    23: [(0, 69, 4.0)],
}


# ---------------------------------------------------------------- instrumentos
def pad(notes, n):
    sig = np.zeros(n)
    for m in notes:
        for det in (-9, 0, 9):
            sig += saw_add(midi(m), n, 14, det)
    sig /= len(notes) * 3
    return sig


def pluck(freq, dur=0.5):
    n = int(SR * dur)
    t = np.arange(n) / SR
    sig = (np.sin(2 * np.pi * freq * t)
           + 0.45 * np.sin(2 * np.pi * freq * 2 * t) * np.exp(-t * 9)
           + 0.2 * np.sin(2 * np.pi * freq * 3 * t) * np.exp(-t * 14))
    return sig * np.exp(-t * 7.5) * env(n, 0.002, 0.01, 1.0, 0.02)


def lead(freq, dur):
    n = int(SR * (dur + 0.25))
    t = np.arange(n) / SR
    vib = 1 + 0.0035 * np.sin(2 * np.pi * 5.2 * t) * np.minimum(1, t * 3)
    ph = 2 * np.pi * np.cumsum(freq * vib) / SR
    sig = np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph)
    return sig * env(n, 0.02, 0.1, 0.75, 0.22)


def bass(freq, dur):
    n = int(SR * dur)
    t = np.arange(n) / SR
    sig = np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * freq * 2 * t) * np.exp(-t * 6)
    return sig * env(n, 0.004, 0.08, 0.8, 0.06)


def kick():
    n = int(SR * 0.45)
    t = np.arange(n) / SR
    f = 45 + 95 * np.exp(-t * 28)
    ph = 2 * np.pi * np.cumsum(f) / SR
    click = rng.standard_normal(n) * np.exp(-t * 400) * 0.25
    return (np.sin(ph) * np.exp(-t * 7.5) + click)


def hat(open_=False):
    n = int(SR * (0.22 if open_ else 0.05))
    t = np.arange(n) / SR
    x = rng.standard_normal(n)
    x = np.diff(x, prepend=0)          # pasa-altos tosco
    x = np.diff(x, prepend=0)
    return x * np.exp(-t * (18 if open_ else 90)) * 0.5


def clap():
    n = int(SR * 0.28)
    t = np.arange(n) / SR
    x = rng.standard_normal(n)
    x = x - np.convolve(x, np.ones(6) / 6, mode="same")   # quita graves
    e = np.zeros(n)
    for off in (0.0, 0.011, 0.023):
        k = int(off * SR)
        e[k:] += np.exp(-(t[: n - k]) * 70) * 0.7
    e += np.exp(-t * 16) * 0.35
    return x * e


def reverb(sig, wet=0.3, rt=1.6):
    n = int(SR * rt)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-t * 4.2)
    ir[: int(0.012 * SR)] *= 0.2
    size = 1 << (len(sig) + n).bit_length()
    y = np.fft.irfft(np.fft.rfft(sig, size) * np.fft.rfft(ir, size), size)[: len(sig)]
    y /= np.max(np.abs(y)) + 1e-9
    return sig * (1 - wet) + y * wet * np.max(np.abs(sig))


def echo(buf, delay, fb=0.38, taps=5, lp=0.6):
    out = buf.copy()
    d = int(delay * SR)
    cur = buf
    for k in range(1, taps + 1):
        cur = np.concatenate([np.zeros(d), cur])[:N] * fb
        # suaviza cada repetición para que suenen más oscuras
        cur = lp * cur + (1 - lp) * np.concatenate([[0], cur[:-1]])
        out += cur
    return out


# ---------------------------------------------------------------- pistas
padL = np.zeros(N)
padR = np.zeros(N)
pluckL = np.zeros(N)
pluckR = np.zeros(N)
leadM = np.zeros(N)
bassM = np.zeros(N)
drums = np.zeros(N)
kick_env = np.ones(N)  # para el efecto de bombeo (sidechain) del pad

for bar in range(BARS):
    t0 = bar * BAR
    notes, root = CHORDS[bar % 4]
    n = int(SR * BAR)

    # pad: entra suave, sube en las secciones llenas
    lvl = 0.55 if bar < 4 else 0.7
    if bar >= 22:
        lvl = 0.6
    p = pad(notes, n) * env(n, 0.35, 0.3, 0.9, 0.5) * lvl
    put(padL, t0, p, 1.0)
    put(padR, t0, np.roll(p, 220), 1.0)  # leve ensanchado estéreo

    # pluck en corcheas: patrón de arpegio
    if bar < 22:
        order = [0, 1, 2, 1, 0, 1, 2, 1]
        pl_gain = 0.34 if bar < 2 else 0.5
        for i in range(8):
            m = notes[order[i]] + (12 if i % 4 == 3 else 0)
            s = pluck(midi(m + 12))
            pan = -0.5 if i % 2 == 0 else 0.5
            stereo_gain = pl_gain * (1.0 if i % 2 == 0 else 0.8)
            a = (pan + 1) * np.pi / 4
            put(pluckL, t0 + i * BEAT / 2, s, stereo_gain * np.cos(a))
            put(pluckR, t0 + i * BEAT / 2, s, stereo_gain * np.sin(a))

    # bajo: desde el compás 4
    if 4 <= bar < 22:
        pat = [(0, 1.5), (2.5, 0.5), (3, 0.75), (3.5, 0.5)]
        for off, ln in pat:
            put(bassM, t0 + off * BEAT, bass(midi(root + (12 if off == 3.5 else 0)), ln * BEAT * 0.95), 0.62)

    # batería
    if bar >= 2 and bar < 22:
        for i in range(8):                       # hats en corcheas
            put(drums, t0 + i * BEAT / 2, hat(open_=(i % 4 == 3)), 0.28 if i % 2 else 0.2)
    if 4 <= bar < 22:
        for b in range(4):                       # bombo a negras
            ts = t0 + b * BEAT
            put(drums, ts, kick(), 0.95)
            i0 = int(ts * SR)
            seg = np.arange(int(0.22 * SR))
            kick_env[i0:i0 + len(seg)] = np.minimum(
                kick_env[i0:i0 + len(seg)],
                (0.42 + 0.58 * (1 - np.exp(-seg / SR * 11)))[: len(kick_env[i0:i0 + len(seg)])])
        for b in (1, 3):                         # clap en 2 y 4
            put(drums, t0 + b * BEAT, clap(), 0.55)
    if bar in (3, 7):                            # redoble antes de los cambios
        for i in range(8):
            put(drums, t0 + BAR - BEAT + i * BEAT / 8, clap(), 0.18 + i * 0.05)
    if bar == 22:                                # compás final: solo un bombo y un platillo
        put(drums, t0, kick(), 0.9)
        put(drums, t0, hat(True), 0.6)

    # lead
    if bar in LEAD:
        for off, m, ln in LEAD[bar]:
            put(leadM, t0 + off * BEAT, lead(midi(m), ln * BEAT), 0.26)

# campana final (La mayor, arpegio ascendente) en el último compás: el "tilín" del cierre
for i, m in enumerate([81, 85, 88, 93]):
    t = np.arange(int(SR * 2.4)) / SR
    f = midi(m)
    s = (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 3)
         + 0.2 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 6)) * np.exp(-t * 1.6)
    stereo(s, 22 * BAR + 1.0 + i * 0.18, 0.18, pan=-0.4 + 0.27 * i)

# sidechain (bombeo) + efectos
padL *= kick_env
padR *= kick_env
padL = reverb(padL, 0.35)
padR = reverb(padR, 0.35)
pluckL = echo(pluckL, BEAT * 0.75, 0.36, 5)   # eco de corchea con puntillo
pluckR = echo(pluckR, BEAT * 0.75, 0.36, 5)
leadR = echo(leadM, BEAT * 0.75, 0.3, 4)
leadV = reverb(leadR, 0.3, 1.3)

L += padL + pluckL + leadV * 0.9 + bassM + drums
R += padR + pluckR + leadV * 0.9 + bassM + drums

# fundido de entrada y salida
fi = int(0.8 * SR)
fo = int(2.2 * SR)
fade = np.ones(N)
fade[:fi] = np.linspace(0, 1, fi)
fade[-fo:] = np.linspace(1, 0, fo) ** 1.5
L *= fade
R *= fade

# limitador suave y normalización a -1 dBFS
mix = np.stack([L, R], axis=1)
mix = np.tanh(mix * 1.25) / np.tanh(1.25)
mix *= 0.40 / np.max(np.abs(mix))  # nivel de fondo para acompañar la imagen

pcm = (mix * 32767).astype("<i2")
out = sys.argv[1] if len(sys.argv) > 1 else "musica.wav"
with wave.open(out, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print(f"{out}: {DUR:.0f} s, {BPM} BPM, pico {np.max(np.abs(mix)):.2f}")
