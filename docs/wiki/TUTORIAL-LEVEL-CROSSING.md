# Tutorial: a working level crossing

**What you get:** when a train comes, the lights flash, the bell rings and the barriers go down. When the train has
passed, everything opens again. It works for trains in both directions.

This takes about 5 minutes. Follow the steps in order.

---

## What you need

All from the **MAGIC** creative tab:

| Item | How many | What it is |
|---|---|---|
| **Railroad Crossing Controller** | 1 | The "brain" of the crossing. A small cabinet next to the track. |
| **Polish Level Crossing** | 2 (one per side of the road) | The lights and the bell. |
| **Crossing Gate (Left)** / **Crossing Gate (Right)** | optional, 2 to 4 | The barriers. |
| **Axle Counter** | 2 per track | Detects the trains. |
| **Crossing Linker** | 1 | The tool that connects everything. |

You also need MTR's **brush** to check your work and to change settings.

---

## Step 1: build the road and the track

The road must cross an MTR track that trains already run on.

---

## Step 2: place the lights

Place a **Polish Level Crossing** on each side of the road, next to the track.

**Stand on the road** when you place it, looking at the spot: it turns to face you, which is the side drivers come from.
On a curve, look diagonally to turn it by 45°.

---

## Step 3: place the barriers (optional)

Place a **Crossing Gate**, again **standing on the road** in front of it.

- **Left** gate: its arm swings to the **left**, as you see it from the road.
- **Right** gate: its arm swings to the **right**.

Pick the one whose arm goes across your road.

---

## Step 4: place the controller

Place one **Railroad Crossing Controller** next to the track, near the crossing. Any spot works.

---

## Step 5: place the axle counters (the train detectors)

1. Hold the **Axle Counter**.
2. Look at the rail and **right-click**. A small box clamps onto the rail.
3. Do this **on both sides of the crossing, on every track**:
   - side A: one counter per track
   - side B: one counter per track

**How far from the crossing?** Far enough that the barriers are down before the train arrives: about **10 seconds of
travel**. Easy rule: **train speed in km/h × 3 = blocks**. For example 40 km/h → 120 blocks, 80 km/h → 240 blocks.

To remove a counter: hit it (left click).

---

## Step 6: connect everything with the Crossing Linker

The Crossing Linker has 4 modes. **Sneak + right-click** (in the air) to change mode. The colour of the outline shows
the mode:

`Inactive` → **Start Node (green)** → **End Node (red)** → **Object Linking (blue)** → `Inactive` ...

### 6a. Counters on side A (green)

1. Set the linker to **Start Node** (green).
2. **Right-click the controller.** It gets a green outline.
3. **Right-click each axle counter on side A** (one per track).

### 6b. Counters on side B (red)

1. Set the linker to **End Node** (red).
2. **Right-click the controller.**
3. **Right-click each axle counter on side B.**

(Which side is "A" does not matter: the crossing works for trains going either way.)

### 6c. Lights and barriers (blue)

1. Set the linker to **Object Linking** (blue).
2. **Right-click the controller.**
3. **Right-click every Polish Level Crossing and every Crossing Gate** of this crossing.

Clicked something by mistake? Click it again in the same mode: that removes the link.

---

## Step 7: check your work

Hold MTR's **brush**. You see lines going to the controller:

- **green** lines from the side A counters,
- **red** lines from the side B counters,
- **blue** lines to the lights and barriers.

If one is missing, redo that part of step 6.

---

## Step 8: test it

Send a train. When it passes the first counter, the lights flash and about 3 seconds later the barriers go down. When
the train has passed the counter on the other side, everything opens again.

---

## Extras (optional)

All with MTR's **brush**, right-click:

- **On the controller:** a screen with every connected part (*Components*), the timing of lights and barriers
  (*Timeline & Controls*, also "closed by hand") and more.
- **On a level crossing:** the bell sound (or no sound) and how fast the lights flash. There is a Portuguese bell
  (Bell 6).
- **On a gate:** the arm length (4 to 11 blocks) and its sound.
- **On an axle counter:** it detects trains in *both directions* → *only one direction* → *only the other*. Useful when
  a counter should only react to trains going towards the crossing.

**Using a train detector instead of counters:** with the linker in **Object Linking** (blue), right-click the
controller, then right-click a **Train Detector** (MAGIC's, or an MTR train sensor). The crossing closes while the
detector is on.

**Redstone:** the controller and every level crossing give a redstone signal while a train is in the crossing. A level
crossing also flashes when you give it redstone (lever, button).

---

## It doesn't work: checklist

| What you see | Why | Fix |
|---|---|---|
| Nothing happens when a train passes | Counters not linked, or on the wrong track | Step 7: look for the green/red lines. Every track needs its own counters. |
| Lights flash but barriers do not move | The gates are not linked | Step 6c |
| Some lights stay dark | That level crossing is not linked | Step 6c |
| The crossing closes too late | The counters are too close | Move them further away (step 5) |
| The crossing stays closed for a long time | A train stopped or turned back between the counters | It opens by itself after 2 minutes without trains. Check that the other side has counters. |
| "This object is too far from the controller" | Lights, barriers and detectors must be within 256 blocks of the controller (counters can be at any distance) | Move the controller closer |
| A counter does not stick | You are not aiming at an MTR rail | Aim at the rail itself |
