# Trainer verification (240 FPS)

Against [apexmovement.tech/superglidetrainer](https://apexmovement.tech/superglidetrainer/) with:

- FPS = **240**
- Jump = **Space**
- Crouch = **Control** (Left Ctrl)

## Formula (site source)

```
frame_time = 1 / fps
frames = delay_seconds / frame_time
chance = frames*100           if frames < 1
chance = (2-frames)*100       if 1 <= frames < 2
chance = 0                    otherwise
```

Ideal delay at 240 FPS: **4.1667 ms** (1.0 frame).

## Automated checks

`scripts/verify_superglide_timing.py` (exact formula):

| delay_ms | frames | chance |
|---------|--------|--------|
| 4.1667 | 1.000 | 100% |
| 0.2 mm @ 48 mm/s | 1.000 | 100% |

`scripts/trainer_browser_verify.py` + follow-up Selenium run against the live trainer (browser `setTimeout` has ~1 ms jitter, which the site FAQ also warns about):

| requested delay | measured frames | chance |
|-----------------|-----------------|--------|
| 2.0 ms | 0.5 | 48% |
| 3.5 ms | 0.7 | 72% |
| ~4.17 ms | ~1.2 | ~80% |
| 6.0 ms | 1.4 | 56% |
| 10.0 ms | 2.4 | 0% |

Takeaway: the trainer confirms the **1-frame @ 240 FPS ≈ 4.17 ms** target. Tune your Wooting `C` DKS travel gap until the trainer sits near **1.0 frame / high 80–99%**, then lock mantle timing in the firing range.
