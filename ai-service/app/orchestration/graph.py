"""Stateless LangGraph orchestration for the Spring-owned delivery workflow.

Graph sequence:
START -> Priority -> Supply Matching -> WAIT_FOR_SUPPLIER
      -> Allocation -> Route -> Weather -> ETA -> Safety
      -> AWAITING_CENTRAL_HUB_REVIEW -> END

The supplier-matching call ends at WAIT_FOR_SUPPLIER. Once Spring has recorded
supplier acceptance and payment, it invokes the same graph with the accepted
supplier and ``supplier_accepted=True`` to obtain flight recommendations.
This graph never writes application records or dispatches a mission.
"""
from __future__ import annotations

from typing import Any

from langgraph.graph import END, START, StateGraph

from app.orchestration.state import DispatchState


def _trail(state: DispatchState, agent: str, decision: Any, reason: Any,
           score: Any = None, **extra: Any) -> list[dict[str, Any]]:
    row = {"agent": agent, "decision": str(decision), "reason": str(reason)}
    if score is not None:
        row["score"] = score
    row.update(extra)
    return state.get("decision_trail", []) + [row]


async def node_priority(state: DispatchState) -> dict[str, Any]:
    from app.api.endpoints.ai_decision import run_priority_agent

    request = state["request"]
    result = run_priority_agent(request.medical_type, request.urgency, request.weight_kg)
    return {"priority_result": result,
            "decision_trail": _trail(state, "PriorityAgent", result["priority_level"],
                                     result["reason"], result["priority_score"])}


async def node_supply_matching(state: DispatchState) -> dict[str, Any]:
    from app.api.endpoints.ai_decision import run_supply_match_agent

    request = state["request"]
    result = run_supply_match_agent(request.destination, request.required_quantity,
                                    state.get("supplier_candidates", []))
    return {"supply_matching_result": result,
            "decision_trail": _trail(state, "SupplyMatchingAgent", result["status"],
                                     result["reason"],
                                     result.get("recommended_supplier", {}).get("match_score"))}


async def node_wait_for_supplier(state: DispatchState) -> dict[str, Any]:
    accepted = bool(state.get("supplier_accepted"))
    status = "SUPPLIER_ACCEPTED" if accepted else "WAIT_FOR_SUPPLIER"
    return {"final_status": status,
            "decision_trail": _trail(state, "SupplierWorkflow", status,
                                     "Spring Boot owns supplier acceptance and inventory reservation.")}


async def node_allocation(state: DispatchState) -> dict[str, Any]:
    from app.api.endpoints.ai_decision import run_allocation_agent

    request = state["request"]
    result = run_allocation_agent(request.drones, request.pickup, request.destination,
                                  request.weight_kg, request.preferred_zone)
    status = "NO_FEASIBLE_DRONE" if result.get("decision") == "NO_FEASIBLE_DRONE" else "ALLOCATED"
    return {"allocation_result": result,
            "final_status": status,
            "decision_trail": _trail(state, "AllocationAgent",
                                     result.get("drone_code") or result.get("decision"),
                                     result.get("reason", "No allocation explanation provided."),
                                     result.get("score"), tier=result.get("tier"))}


async def node_route(state: DispatchState) -> dict[str, Any]:
    from app.api.endpoints.ai_decision import run_route_agent

    request = state["request"]
    allocation = state["allocation_result"]
    result = run_route_agent(allocation["drone_lat"], allocation["drone_lng"],
                             request.pickup, request.destination, request.no_fly_zones)
    failed = result.get("status") != "SAFE"
    return {"route_result": result,
            "final_status": "UNSAFE_ROUTE" if failed else state.get("final_status"),
            "halt_reason": result.get("reason") if failed else None,
            "decision_trail": _trail(state, "RouteAgent", result["algorithm"],
                                     result["reason"], distance_km=result["distance_km"])}


async def node_weather(state: DispatchState) -> dict[str, Any]:
    from app.api.endpoints.ai_decision import run_weather_agent

    request = state["request"]
    result = await run_weather_agent(request.pickup, request.destination, request.current_weather)
    return {"weather_result": result,
            "decision_trail": _trail(state, "WeatherAgent", result["flight_condition"],
                                     result["reason"], result["confidence"])}


async def node_eta(state: DispatchState) -> dict[str, Any]:
    from app.api.endpoints.ai_decision import run_eta_agent

    request = state["request"]
    route = state["route_result"]
    weather = state["weather_result"]
    result = run_eta_agent(route["distance_km"], wind_kmh=weather["wind_speed_kmh"],
                           temp_c=weather["temperature_c"], payload_kg=request.weight_kg)
    updated_route = {**route,
                     "baseline_eta_minutes": result["baseline_eta_minutes"],
                     "ml_predicted_eta_minutes": result["ml_predicted_eta_minutes"],
                     "final_eta_minutes": result["final_eta_minutes"],
                     "model_name": result["model_name"],
                     "eta_minutes": result["final_eta_minutes"]}
    return {"eta_result": result, "route_result": updated_route,
            "decision_trail": _trail(state, "ETAPredictionAgent",
                                     f"{result['final_eta_minutes']} min", result["reason"], 0.99)}


async def node_safety(state: DispatchState) -> dict[str, Any]:
    from app.api.endpoints.ai_decision import run_safety_agent

    request = state["request"]
    result = run_safety_agent(state["priority_result"], state["weather_result"],
                              state["allocation_result"], state["route_result"], request.weight_kg)
    status = "AWAITING_CENTRAL_HUB_REVIEW" if result["status"] == "PASS" else result["status"]
    return {"safety_result": result, "final_status": status,
            "halt_reason": None if result["status"] == "PASS" else result["reason"],
            "decision_trail": _trail(state, "SafetyAgent", result["status"],
                                     result["reason"], result["score"])}


async def node_monitoring(state: DispatchState) -> dict[str, Any]:
    """Run telemetry anomaly evaluation after Spring has dispatched a mission."""
    from app.api.endpoints.ai_decision import run_monitoring_anomaly_agent

    result = run_monitoring_anomaly_agent(state["monitoring_request"])
    return {"monitoring_result": result}


def _after_supplier(state: DispatchState) -> str:
    if not state.get("supplier_accepted"):
        return END
    return "node_allocation"


def _after_weather(state: DispatchState) -> str:
    return END if state.get("weather_result", {}).get("flight_condition") == "UNSAFE" else "node_eta"


def _after_route(state: DispatchState) -> str:
    return END if state.get("final_status") == "UNSAFE_ROUTE" else "node_weather"


def _after_safety(state: DispatchState) -> str:
    return END


def build_dispatch_graph():
    """Compile the sole authoritative agent workflow (no DB side effects)."""
    builder = StateGraph(DispatchState)
    builder.add_node("node_priority", node_priority)
    builder.add_node("node_supply_matching", node_supply_matching)
    builder.add_node("node_wait_for_supplier", node_wait_for_supplier)
    builder.add_node("node_allocation", node_allocation)
    builder.add_node("node_route", node_route)
    builder.add_node("node_weather", node_weather)
    builder.add_node("node_eta", node_eta)
    builder.add_node("node_safety", node_safety)
    builder.add_node("node_monitoring", node_monitoring)

    builder.add_conditional_edges(START,
                                  lambda state: "node_monitoring" if state.get("workflow_stage") == "MONITORING" else "node_priority",
                                  {"node_monitoring": "node_monitoring", "node_priority": "node_priority"})
    builder.add_edge("node_monitoring", END)
    builder.add_edge("node_priority", "node_supply_matching")
    builder.add_edge("node_supply_matching", "node_wait_for_supplier")
    builder.add_conditional_edges("node_wait_for_supplier", _after_supplier,
                                  {"node_allocation": "node_allocation",
                                   "node_weather": "node_weather", END: END})
    builder.add_conditional_edges("node_allocation",
                                  lambda state: END if state.get("final_status") == "NO_FEASIBLE_DRONE" else "node_route",
                                  {"node_route": "node_route", END: END})
    builder.add_conditional_edges("node_route", _after_route,
                                  {"node_weather": "node_weather", END: END})
    builder.add_conditional_edges("node_weather", _after_weather,
                                  {"node_eta": "node_eta", END: END})
    builder.add_edge("node_eta", "node_safety")
    builder.add_conditional_edges("node_safety", _after_safety, {END: END})
    return builder.compile()


dispatch_graph = build_dispatch_graph()
