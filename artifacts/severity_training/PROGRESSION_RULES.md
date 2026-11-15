# Progression Rules

Current status: thresholds are not defined.

Do not hard-code `IMPROVED`, `STABLE`, or `WORSENED` thresholds until validated data exists.

## Future Rule Shape

```text
delta = currentSeverity - previousSeverity

if model unavailable:
    UNCERTAIN
elif different disease:
    UNCERTAIN
elif image quality insufficient:
    UNCERTAIN
elif abs(delta) <= validatedTolerance:
    STABLE
elif delta > worseningThreshold:
    WORSENED
elif delta < improvementThreshold:
    IMPROVED
else:
    UNCERTAIN
```

## Threshold Source

Thresholds must come from at least one of:

- validation/test model error
- annotation variability
- expert-defined clinical tolerance

They must not come from arbitrary examples.
