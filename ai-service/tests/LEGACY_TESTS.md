# AI service test architecture notes

The Docker image starts `server.py`, which mounts the AI decision router. Spring Boot calls `/internal/ai/workflow`. The tests below document older modules still present in the repository; they are not evidence that those modules are part of the active service.

| Classification | Current tests/modules | Follow-up |
| --- | --- | --- |
| A. Active production path | `test_phase10.py`, `test_active_weather_workflow.py`, and the rewritten `test_weather_agent.py` | Run with the AI service suite. These exercise the current workflow and Open-Meteo client. |
| B. Should be migrated | `test_auth.py` endpoint cases and `test_phase3.py` medical-request/drone endpoint cases | They import FastAPI auth/request/drone endpoints not mounted by `server.py`. Spring Boot is the deployed authority for authentication, authorization, and business persistence. Keep isolated tests as reference until Spring endpoint coverage replaces them. |
| C. Obsolete; remove later after review | `test_phase9.py::test_run_mission_simulation` | It targets `app.simulation.engine`, a Mongo-backed simulator not used by the active Spring mission workflow. It is explicitly skipped with this reason. |
| D. Historical/reference production modules | `app/main.py`, MongoDB modules, old `app.agents.weather_agent`, and `app.simulation` | No current test imports `app.main` directly. These files remain in place as requested; audit external launch scripts before any later deletion. |

No MongoDB test service is started by the active Docker AI entrypoint. Some isolated legacy tests use mocks or import old Mongo-oriented schemas/repositories; they do not connect to a live MongoDB instance.

The previous Phase 10 tests expected a graph-created mission, an obsolete request schema, and an LLM explanation stage. The current graph returns recommendations through Central Hub review and does not create missions or invoke an LLM. They have been replaced with tests for the current public workflow entry point; no compatibility shims were added to production code.
