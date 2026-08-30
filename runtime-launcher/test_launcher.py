"""
Real, non-mocked coverage for launcher.py's port-remap logic (2026-08-11, reliability-strengthening
plan). This is the exact class of bug none of the Java-side mocked tests could ever catch tonight:
docker-cli missing, wrong health-check defaults, port collision, and the ports: list-concatenation
merge bug all live in this Python sidecar's own real interaction with YAML and Docker Compose, not in
any Java business logic. Runs against a real docker-compose.yml written to a temp file and real
PyYAML parsing/dumping - never a mock of yaml or of the file system.

Run with: pip install pytest && pytest test_launcher.py
"""
import yaml

from launcher import _remap_ports, EXTERNAL_PORT_BASE


def _write_compose(tmp_path, content: str):
    compose_file = tmp_path / "docker-compose.yml"
    compose_file.write_text(content)
    return compose_file


def test_remaps_a_single_published_port_matching_the_real_incident(tmp_path):
    # Exact shape of test-forty-third's real docker-compose.yml that collided with this factory's own
    # backend on host port 8080.
    compose_file = _write_compose(tmp_path, """
services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
""")

    port_map = _remap_ports(compose_file)

    assert port_map == {"app": EXTERNAL_PORT_BASE}
    rewritten = yaml.safe_load(compose_file.read_text())
    assert rewritten["services"]["app"]["ports"] == [f"{EXTERNAL_PORT_BASE}:8080"]
    # Every other key must survive untouched - the whole point is a surgical port rewrite, not a
    # regeneration of the file.
    assert rewritten["services"]["app"]["build"] == {"context": ".", "dockerfile": "Dockerfile"}
    assert rewritten["services"]["app"]["environment"] == ["SPRING_PROFILES_ACTIVE=prod"]


def test_remaps_multiple_services_to_sequential_ports(tmp_path):
    compose_file = _write_compose(tmp_path, """
services:
  app:
    ports:
      - "8080:8080"
  admin:
    ports:
      - "9000:9000"
""")

    port_map = _remap_ports(compose_file)

    assert port_map == {"app": EXTERNAL_PORT_BASE, "admin": EXTERNAL_PORT_BASE + 1}
    rewritten = yaml.safe_load(compose_file.read_text())
    assert rewritten["services"]["app"]["ports"] == [f"{EXTERNAL_PORT_BASE}:8080"]
    assert rewritten["services"]["admin"]["ports"] == [f"{EXTERNAL_PORT_BASE + 1}:9000"]


def test_a_service_with_no_ports_key_is_left_untouched(tmp_path):
    compose_file = _write_compose(tmp_path, """
services:
  worker:
    build:
      context: .
""")
    original = compose_file.read_text()

    port_map = _remap_ports(compose_file)

    assert port_map == {}
    assert compose_file.read_text() == original


def test_a_malformed_compose_file_returns_an_empty_map_instead_of_raising(tmp_path):
    compose_file = _write_compose(tmp_path, "not: [valid, yaml: structure")

    port_map = _remap_ports(compose_file)

    assert port_map == {}


def test_bound_memory_gives_every_unbounded_service_the_declared_limit(tmp_path, monkeypatch):
    """Plan §4.44. The launcher starts the product through the host docker socket, so its own 256m binds
    nothing it starts. Measured 2026-08-30: the product answered Cloudflare 530/1033 because no container of
    it existed - the launcher was kept down precisely because starting it reopened that unbounded path."""
    import launcher

    compose = tmp_path / "docker-compose.resolved.yml"
    compose.write_text(
        "services:\n"
        "  api:\n"
        "    image: api\n"
        "  db:\n"
        "    image: db\n"
        "    mem_limit: 128m\n"
    )
    monkeypatch.setattr(launcher, "CLIENT_STACK_MEM_LIMIT", "384m")

    bounded = launcher._bound_memory(compose)

    spec = launcher.yaml.safe_load(compose.read_text())
    assert bounded == 1
    assert spec["services"]["api"]["mem_limit"] == "384m"
    assert spec["services"]["db"]["mem_limit"] == "128m", "the client's own decision is not overwritten"


def test_bound_memory_changes_nothing_when_no_limit_is_declared(tmp_path, monkeypatch):
    """The other half: with nothing declared the launcher behaves exactly as before, so an operator who has
    not set the bound is not silently given one this factory invented."""
    import launcher

    compose = tmp_path / "docker-compose.resolved.yml"
    original = "services:\n  api:\n    image: api\n"
    compose.write_text(original)
    monkeypatch.setattr(launcher, "CLIENT_STACK_MEM_LIMIT", "")

    assert launcher._bound_memory(compose) == 0
    assert compose.read_text() == original
