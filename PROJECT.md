# Pit Stop

## Overview

**Platform**: Two native mobile applications: iOS & Android, One web backend and live leaderboard website.
**Purpose**: An interactive, racing themed mini-game collection for a school car show booth. Visitors compete in short challenges, earn scores, and compare results on a leaderboard.
**Target Audience**: Students, families, parents, and car enthussiasts of all ages.
**Typical Session**: 30 - 60 seconds per game.

**Core Experience**:
	1. A visitor approaches and sees and attractive racing-themed home screen.
	2. They select a mini-game
	3. The app explains the rules in one short instruction
	4. They play a quick, touch based challenge
	5. Their score can be added to the leaderboard
	6. The booth operator uses the interaction as an opportunity to showcase student-built apps

The experience should be usable without an account, or lengthy instructions.

## The Mini Games

### Reaction Lights

**Objective**: Test how quickly the player reacts to a green light. Like a racing countdown before a rally car gets let go.
**Gameplay**:
	1. The player taps Start
	2. A red light appears and the player waits
	3. After a random delay, the light turns green
	4. The player taps as quickly as possible
	5. The app displays their reaction time in milliseconds

**Rules**:
	- The wait period should be randomly generated, roughly 2 - 5 seconds
	- Tapping before green is a false start and results in a failed attempt
	- Record the reaction time from the green-light event to the registered tap
	- Provide an option to retry, add to leaderboard, or quit

**Scoring**: Lower reaction time is better. Rank by fastest valid reaction time.

### Fill it up

**Objective**: Fill a "gas" gague to as close to a set number as possible. So, for example, they are given $10 as their goal, and $2.25 per gallon.
**Gameplay**:
	1. The player presses and holds down a fill button
	2. The gauge and the total cost increase
	3. When the player lets go that is their score
	4. The app calculates how far from the target dollar amount they are

**Rules**:
	- The cost of gas, and total value they should fill up to are random. But the total should be in increments of 0.50
	- Provide an option to retry, add to leaderboard, or quit

**Scoring**: Accuracy is based on absolute difference from the total goal. Higher accuracy wins

### Perfect Pit Stop

**Objective**: Complete a simulated pit stop by tapping all four tires as quickly as possible.
**Gameplay**:
	1. Display a top-down of a car with 4 tires
	2. The player taps start
	3. All four tires become interactive targets
	4. Each tire changes appearance when tapped correctly
	5. Once all four tires are tapped, the round ends

**Rules**:
	- Each tire can only be changed once
	- Tapped tires should visibly change state
	- Disable further input after the round ends
	- Tapping outside of the available tires reduces score
	- Provide an option to retry, add to leaderboard, or quit

**Scoring**: Elapsed time from start to fourt correct tire tap. +1 second for each mis-tap. Lower score is better

## App Structure & Navigation

The app should be simple enough that someone can walk up and start playing without assistance.

They should be prompted with the available games, and some text describing how cool the mobile app dev program is.

They play a game and are prompted to add to leaderboard, retry, or go back to home. If adding to leaderboard they should be prompted
to put in a name, that is not offensive.

The apps should fall-back to a local storage of the leaderboard if the server is unavailable

## Server / Web Structure

1. A simple REST API for sending leaderboard updates to.
2. A leaderboard page that automatically updates. It should show all three leaderboards side by side.

## Visual Design

The app / leaderboard should feel like a polished motorsports arcade game.

- Racing Yellow: #F7C843
- Signal Red: #E7473F
- Supporting Colors:
	- #15191F
	- #252B34
	- #FFF

**Design Requirements**
	- Large, easy to tap buttons suitable for a shared tablet. (Mobile phones are not required, just iPads / Tablets)
	- High contrast and readable typography outdoors
	- Bold numbers for milliseconds and elapsed times
	- Smooth, brief transitions and feedback animations
	- Landscape and Portrait layouts where practical
	- Haptic feedback where supported, but never make it essential to gameplay
	- Sound effects should be optional and off or muted by default for a busy public event
	- Respect system text scaling and provide accessible labels for interactive controls
	- Do not rely on color alone to communicate game state; use labels, icons, or shapes as well

Shared technical behavior

Both apps should implement equivalent game rules and scoring logic, with the same design language.

Offline-first: All games must work without a network connection.

Accurate timing: Use a monotonic clock, not the wall clock. For reaction testing, record the time when the green state is triggered and when the touch is registered.

Lifecycle safety: Handle the app being backgrounded, interrupted, or resumed. Cancel or invalidate an active round if timing integrity cannot be maintained.

Persistent data: Save leaderboard entries locally and handle app restarts without losing them.

Input protection: Prevent double submissions, repeated tire taps, and multiple timers running simultaneously.

No mandatory permissions: The app should not need location, contacts, camera, or microphone access.

No accounts or analytics SDKs required for the initial version.

Consistent behavior: Define scoring calculations once in the specification and reproduce them in both projects.

For reaction timing, note that a phone's touch hardware, display refresh, and operating system introduce measurement variation. The app should present scores as a fun competition rather than a scientifically precise measure of reflexes.
