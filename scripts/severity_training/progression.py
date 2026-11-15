from __future__ import annotations

ORDER = {"NONE": 0, "MILD": 1, "MODERATE": 2, "SEVERE": 3}


def evaluate_progression(
    previous_disease: str | None,
    previous_severity: str | None,
    current_disease: str | None,
    current_severity: str | None,
    *,
    protocol_finalized: bool,
) -> str:
    if not protocol_finalized:
        return "UNCERTAIN"
    if not previous_disease or not current_disease or previous_disease != current_disease:
        return "UNCERTAIN"
    if previous_severity not in ORDER or current_severity not in ORDER:
        return "UNCERTAIN"
    delta = ORDER[current_severity] - ORDER[previous_severity]
    if delta > 0:
        return "WORSENED"
    if delta < 0:
        return "IMPROVED"
    return "STABLE"


def treatment_action_for_progression(progression: str, *, kb_available: bool) -> str:
    if progression != "WORSENED":
        return "MONITORING_ONLY"
    return "KB_RECOMMENDATION_REQUIRED" if kb_available else "MONITORING_ONLY"
