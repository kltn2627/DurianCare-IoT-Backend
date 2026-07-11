from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


def to_camel(value: str) -> str:
    parts = value.split("_")
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


class CamelModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, alias_generator=to_camel)


class RiskLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class DecisionSupport(CamelModel):
    risk_level: RiskLevel
    immediate_actions: list[str] = Field(default_factory=list)
    biological_plan: list[str] = Field(default_factory=list)
    organic_plan: list[str] = Field(default_factory=list)
    chemical_plan: list[str] = Field(default_factory=list)
    monitoring_plan: list[str] = Field(default_factory=list)
    export_readiness: list[str] = Field(default_factory=list)
    farmer_notes: list[str] = Field(default_factory=list)

