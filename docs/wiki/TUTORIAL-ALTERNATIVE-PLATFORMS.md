# Tutorial: alternative platforms

**What it does:** a station has two (or more) platforms for the same direction. Your route stops at platform 1. When
platform 1 is taken by another train, your train goes to platform 2 instead of waiting outside the station.

This takes about 2 minutes. Follow the steps in order.

---

## Before you start: check your station

You need **all** of these, or nothing will happen:

1. **Both platforms are inside the same MTR station.** In the Dashboard, the station's area (the coloured rectangle)
   must cover platform 1 **and** platform 2.
2. **Both platforms are for the same kind of vehicle** (both train platforms, not one train and one boat platform).
3. **The tracks are connected.** A train coming into the station must be able to drive into platform 2 (there are
   points/switches before the station), and drive out of it to the next station. If you cannot drive a train there
   yourself, MAGIC cannot either.

---

## Step 1: turn the feature on (only once)

Alternative platforms are **off** when you install MAGIC.

1. Open MTR's **Config** screen (or Mod Menu → MAGIC → Configure).
2. Click the **MAGIC** button (top right) to open MAGIC's settings.
3. Open the **Dashboard** tab and set **Alternative Platforms** to **ON**.

On a server, this setting is the server's: ask the server owner to turn it on.

---

## Step 2: open your route

1. Open the MTR **Dashboard**.
2. Click the **Routes** tab at the top.
3. Find your route and click its **edit** button. The left side now shows the route's list of stops.

---

## Step 3: open the alternative platforms screen

Click the big **Alternative platforms...** button at the bottom of the list of stops (just above the route's name and
colour). You can also **right-click** a stop in the list → **Alternative Platforms**.

(If neither appears: the feature is off (step 1), or the station has no other platform (see "Before you start").)

---

## Step 4: add the other platform

The screen has two sides:

- **Left: the route's stops.** Click the stop you want to change (the selected one has a ▶).
- **Right: that stop's platforms.** At the top, **★ Main platform** is the platform your route normally uses. Below
  are the station's other platforms, each with an **Add** button.

Click **Add** next to the platform the train may use instead. It moves to the **Alternatives** list. That's it: it is
saved straight away.

- Changed your mind? Click **Remove** next to it.
- Big station with many platforms? Click **Any free platform of the station: OFF** to turn it **ON**. The train then
  uses whichever platform of the station is free (also platforms you build later).

Want alternatives at more stations? Click another stop on the left and repeat.

Click **Done** when finished.

---

## Step 5: check it

The stop now shows a mark after its name, both on the left of the screen and in the route's list of stops: **⎇1**
(one alternative), **⎇2**, or **⎇ all**.

---

## How trains use it

- A train always tries its **primary** platform first.
- When it gets close to the station (and can still change track), it checks whether another train has already taken
  that platform. If yes, it drives to a **free** alternative.
- A train stuck at a red signal in front of the station can also be sent to a free alternative.
- Departure boards (PIDS) show the train at the platform it really uses.

### Stations where one route ends and the next starts

At a terminus where route A ends and route B starts at the same platform (same depot), you can add the alternatives on
**either** route: both count. The alternatives screen shows a yellow line like *"Next route B starts here"* at such a
stop.

### Faster trains overtaking slower ones (optional)

Give each route a **headcode class**: in the route's screen click **Timetable**, then click **Headcode class** until it
shows the right one:

| Class | For |
|---|---|
| 9 | high speed |
| 1 | semi-fast |
| 2 | stopping / suburban |
| 3 | trains leaving the depot |

Trains overtake in this order: **9 late > 9 > 1 late > 1 > 2 late > 2 > 3 late > 3** (late = more than 1 minute
late). When a faster train is right behind a slower one, the slower one goes to the alternative platform, waits there
with its doors open until the faster one has passed, and then follows it. Routes with no class never overtake.

---

## It doesn't work: checklist

| What you see | Why | Fix |
|---|---|---|
| No **Alternative Platforms** in the right-click menu, no button | The feature is off | Step 1 |
| "No other platforms in this station" | The other platform is outside the station's area, or for another vehicle type | Make the station area cover both platforms |
| "Unavailable platform (removed or moved)" | That platform was deleted or moved | Click **Remove** next to it |
| Trains never use the alternative | They only use it when the primary platform is **taken** | Wait for two trains to arrive close together |
| Trains still wait outside | The tracks to the alternative are not connected, or the train was already too close to change | Check the points/switches; give trains more room before the station |
| Never works at one terminus of a depot set to *Repeat indefinitely* | Trains cannot change platform at the point where the depot's routes start over | Known limitation |

On a server you can see every decision in the server log: start the server with the environment variable
`JME_DEBUG_ALTERNATIVES=1`.
