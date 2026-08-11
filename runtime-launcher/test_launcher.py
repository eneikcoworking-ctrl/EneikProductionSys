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
