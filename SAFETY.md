# Native automation safety — 0.10.1

After explicit Start/Resume, a pending task waits for the menu click and other held movement/action controls to release in focused gameplay. It then starts automatically; Stop cancels that pending authorization. The wait performs no task actions. Fresh manual input while a task executes still pauses it until explicit Resume.

Protection belongs to an explicitly running or resumed Analyzer task. It stays inactive for restored checkpoints and user-paused tasks. Movement, turning, mining, building and inventory actions are original local controllers; Baritone is not required.

The default cautious path search avoids loaded terrain hazards, unsupported gaps and nearby hostiles. Proactive protection surveys nearby hostile mobs before contact, with a default 16-block range. Neutral unprovoked endermen/piglins, passive mobs and players are not selected as combat targets. A threat pauses the original operation and preserves its stage.

The guard chooses between ordinary cooldown/reach-limited melee defense, checked retreat and available-food recovery. It favors retreat under explosive danger or low health. A bounded loaded-terrain graph searches beyond the former two-block corridor, scores pursuit/cover and checks the next full-body movement corridor every tick. Every edge must avoid cutting closer to an imminent creeper, including the middle of diagonal segments. An urgent checked escape can backpedal without a slow 180-degree camera turn. These remain normal movement inputs and visible camera rotations.

The HUD reports PROTECTING or SAFETY_HOLD. A hold continues reevaluating threats, terrain and food; it does not silently restart a blocked prerequisite. After the default three-second quiet delay, health/hunger checks gate resumption. A previously blocked stage returns to its blocked reason. User Pause/Stop, death, disconnect, unexpected world change, resource reload and manual input release owned controls.

Settings cover defense, proactive avoidance, avoidance/threat range, retreat health (10 HP), quiet delay, food consumption below hunger 14, minimum health 6 HP, minimum hunger 6, escape-search radius, cover preference, path/drop limits, menu pausing and manual-input pausing. HUD/menu appearance remains independent of automation behavior.

This is bounded PvE protection. It does not fight players, guarantee survival, plan every projectile impact or solve every terrain trap. Equipment, food, loaded paths, vanilla reach/cooldowns and server behavior still matter. Deep/enclosed water, vehicles, flight, inaccessible exits or exhausted supplies can require manual recovery. Native evidence is in [VALIDATION.md](VALIDATION.md); historical fixtures describe their older versions only.
