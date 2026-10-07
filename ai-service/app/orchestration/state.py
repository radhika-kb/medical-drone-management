"""Stateless LangGraph state shared by Spring Boot and the AI agents.

Spring owns persistence, workflow status, payment verification, and dispatch
authorization. The graph receives a validated snapshot and returns advice only.
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional, TypedDict


class DispatchState(TypedDict, total=False):
    workflow_stage: str
    request: Any
    supplier_candidates: List[Any]
    supplier_accepted: bool
    priority_result: Dict[str, Any]
    supply_matching_result: Dict[str, Any]
    weather_result: Dict[str, Any]
    allocation_result: Dict[str, Any]
    route_result: Dict[str, Any]
    eta_result: Dict[str, Any]
    safety_result: Dict[str, Any]
    monitoring_request: Any
    monitoring_result: Dict[str, Any]
    halt_reason: Optional[str]
    final_status: str
    decision_trail: List[Dict[str, Any]]
