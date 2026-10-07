"""Grid A* route search for drone legs with circular no-fly constraints."""
from __future__ import annotations

import heapq
import math
from typing import Any

GRID_STEP_KM = 0.5
SAFETY_BUFFER_KM = 0.25
EARTH_KM_PER_DEGREE = 111.32


def _local_xy(lat: float, lng: float, origin_lat: float, origin_lng: float) -> tuple[float, float]:
    lat_scale = EARTH_KM_PER_DEGREE
    lng_scale = EARTH_KM_PER_DEGREE * max(0.01, math.cos(math.radians(origin_lat)))
    return ((lng - origin_lng) * lng_scale, (lat - origin_lat) * lat_scale)


def _point_segment_distance(px: float, py: float, ax: float, ay: float,
                            bx: float, by: float) -> float:
    dx, dy = bx - ax, by - ay
    length_sq = dx * dx + dy * dy
    if length_sq == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length_sq))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def _is_clear(a: tuple[float, float], b: tuple[float, float],
              zones: list[tuple[float, float, float]]) -> bool:
    return all(_point_segment_distance(cx, cy, a[0], a[1], b[0], b[1])
               > radius + SAFETY_BUFFER_KM for cx, cy, radius in zones)


def _compress(points: list[tuple[float, float]]) -> list[tuple[float, float]]:
    if len(points) < 3:
        return points
    result = [points[0]]
    for index in range(1, len(points) - 1):
        before, current, after = result[-1], points[index], points[index + 1]
        cross = ((current[0] - before[0]) * (after[1] - current[1])
                 - (current[1] - before[1]) * (after[0] - current[0]))
        if abs(cross) > 1e-6:
            result.append(current)
    result.append(points[-1])
    return result


def route_leg(start: dict[str, float], end: dict[str, float],
              no_fly_zones: list[Any], grid_step_km: float = GRID_STEP_KM) -> list[dict[str, float]]:
    """Return safe leg points; raise ValueError if the endpoints/path are blocked."""
    origin_lat, origin_lng = start["lat"], start["lng"]
    start_xy = (0.0, 0.0)
    end_xy = _local_xy(end["lat"], end["lng"], origin_lat, origin_lng)
    zones = []
    for zone in no_fly_zones:
        center = _local_xy(float(zone.center_lat), float(zone.center_lng), origin_lat, origin_lng)
        zones.append((center[0], center[1], max(0.0, float(zone.radius_km))))

    if any(math.hypot(point[0] - cx, point[1] - cy) <= radius + SAFETY_BUFFER_KM
           for point in (start_xy, end_xy) for cx, cy, radius in zones):
        raise ValueError("A route endpoint lies inside a no-fly zone or its safety buffer")
    if _is_clear(start_xy, end_xy, zones):
        return [start, end]

    relevant_radii = [radius for cx, cy, radius in zones
                      if _point_segment_distance(cx, cy, *start_xy, *end_xy) <= radius + 12.0]
    margin = max([4.0, *(radius + 2.0 for radius in relevant_radii)])
    min_x = min(start_xy[0], end_xy[0]) - margin
    max_x = max(start_xy[0], end_xy[0]) + margin
    min_y = min(start_xy[1], end_xy[1]) - margin
    max_y = max(start_xy[1], end_xy[1]) + margin

    def xy(cell: tuple[int, int]) -> tuple[float, float]:
        return min_x + cell[0] * grid_step_km, min_y + cell[1] * grid_step_km

    width = int(math.ceil((max_x - min_x) / grid_step_km))
    height = int(math.ceil((max_y - min_y) / grid_step_km))
    start_center = (round((start_xy[0] - min_x) / grid_step_km),
                    round((start_xy[1] - min_y) / grid_step_km))
    end_center = (round((end_xy[0] - min_x) / grid_step_km),
                  round((end_xy[1] - min_y) / grid_step_km))

    def endpoint_cells(center: tuple[int, int], exact: tuple[float, float]) -> list[tuple[int, int]]:
        candidates = []
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                cell = (center[0] + dx, center[1] + dy)
                if 0 <= cell[0] <= width and 0 <= cell[1] <= height:
                    cell_xy = xy(cell)
                    if _is_clear(exact, cell_xy, zones):
                        candidates.append(cell)
        return sorted(candidates, key=lambda cell: math.dist(exact, xy(cell)))

    starts = endpoint_cells(start_center, start_xy)
    goals = set(endpoint_cells(end_center, end_xy))
    if not starts or not goals:
        raise ValueError("No safe A* grid connection exists for the route endpoints")

    goal_connector = {cell: math.dist(xy(cell), end_xy) for cell in goals}
    open_heap: list[tuple[float, float, tuple[int, int]]] = []
    costs: dict[tuple[int, int], float] = {}
    parents: dict[tuple[int, int], tuple[int, int] | None] = {}
    for cell in starts:
        cost = math.dist(start_xy, xy(cell))
        costs[cell] = cost
        parents[cell] = None
        heapq.heappush(open_heap, (cost + math.dist(xy(cell), end_xy), cost, cell))

    goal_cell = None
    directions = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1),
                  (1, -1), (1, 0), (1, 1)]
    while open_heap:
        _, cost, current = heapq.heappop(open_heap)
        if cost > costs.get(current, float("inf")):
            continue
        if current in goals and _is_clear(xy(current), end_xy, zones):
            goal_cell = current
            break
        current_xy = xy(current)
        for dx, dy in directions:
            neighbor = (current[0] + dx, current[1] + dy)
            if not (0 <= neighbor[0] <= width and 0 <= neighbor[1] <= height):
                continue
            neighbor_xy = xy(neighbor)
            if not _is_clear(current_xy, neighbor_xy, zones):
                continue
            next_cost = cost + math.dist(current_xy, neighbor_xy)
            if next_cost >= costs.get(neighbor, float("inf")):
                continue
            costs[neighbor] = next_cost
            parents[neighbor] = current
            heuristic = math.dist(neighbor_xy, end_xy)
            heapq.heappush(open_heap, (next_cost + heuristic, next_cost, neighbor))

    if goal_cell is None:
        raise ValueError("A* could not find a safe route around the configured no-fly zones")

    cells = []
    cursor: tuple[int, int] | None = goal_cell
    while cursor is not None:
        cells.append(cursor)
        cursor = parents[cursor]
    cells.reverse()
    local_points = [start_xy, *(xy(cell) for cell in cells), end_xy]
    local_points = _compress(local_points)
    lat_scale = EARTH_KM_PER_DEGREE
    lng_scale = EARTH_KM_PER_DEGREE * max(0.01, math.cos(math.radians(origin_lat)))
    result = [start]
    for x, y in local_points[1:-1]:
        result.append({"lat": round(origin_lat + y / lat_scale, 6),
                       "lng": round(origin_lng + x / lng_scale, 6)})
    result.append(end)

    # Check the published, rounded geometry too; map coordinates are what get persisted.
    published = [(0.0, 0.0)]
    published.extend(_local_xy(point["lat"], point["lng"], origin_lat, origin_lng)
                     for point in result[1:-1])
    published.append(end_xy)
    if any(not _is_clear(a, b, zones) for a, b in zip(published, published[1:])):
        raise ValueError("A* route failed the final no-fly-zone geometry check")
    return result
