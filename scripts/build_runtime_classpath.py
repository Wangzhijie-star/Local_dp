#!/usr/bin/env python3
import argparse
import os
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path


NS = {"m": "http://maven.apache.org/POM/4.0.0"}
M2_REPO = Path(os.environ.get("USERPROFILE", "")) / ".m2" / "repository"


@dataclass(frozen=True)
class Gav:
    group_id: str
    artifact_id: str
    version: str


@dataclass
class Dependency:
    group_id: str
    artifact_id: str
    version: str | None
    scope: str
    optional: bool
    type_: str
    exclusions: set[tuple[str, str]] = field(default_factory=set)


@dataclass
class PomModel:
    gav: Gav
    packaging: str
    properties: dict[str, str]
    dependency_management: dict[tuple[str, str], str]
    dependencies: list[Dependency]


MODEL_CACHE: dict[Path, PomModel] = {}
JAR_PACKAGINGS = {"jar", "bundle"}


def text_of(parent, tag):
    node = parent.find(f"m:{tag}", NS)
    return node.text.strip() if node is not None and node.text else None


def resolve_placeholders(value, properties):
    if value is None:
        return None
    previous = None
    current = value
    while previous != current:
        previous = current
        start = current.find("${")
        if start == -1:
            break
        end = current.find("}", start)
        if end == -1:
            break
        key = current[start + 2:end]
        replacement = properties.get(key)
        if replacement is None:
            break
        current = current[:start] + replacement + current[end + 1:]
    return current


def pom_path_for(gav: Gav):
    return M2_REPO / Path(*gav.group_id.split(".")) / gav.artifact_id / gav.version / f"{gav.artifact_id}-{gav.version}.pom"


def jar_path_for(gav: Gav):
    return M2_REPO / Path(*gav.group_id.split(".")) / gav.artifact_id / gav.version / f"{gav.artifact_id}-{gav.version}.jar"


def normalize_version(version: str):
    parts = []
    for token in version.replace("-", ".").split("."):
        if token.isdigit():
            parts.append((0, int(token)))
        else:
            parts.append((1, token))
    return tuple(parts)


def find_available_gav(gav: Gav):
    artifact_dir = M2_REPO / Path(*gav.group_id.split(".")) / gav.artifact_id
    if not artifact_dir.exists():
        return gav

    version_dirs = [item for item in artifact_dir.iterdir() if item.is_dir()]
    if not version_dirs:
        return gav

    requested_major = gav.version.split(".", 1)[0]
    available_with_jar = []
    available_with_pom = []
    for item in version_dirs:
        jar_file = item / f"{gav.artifact_id}-{item.name}.jar"
        pom_file = item / f"{gav.artifact_id}-{item.name}.pom"
        if jar_file.exists():
            available_with_jar.append(item.name)
        elif pom_file.exists():
            available_with_pom.append(item.name)

    available = available_with_jar or available_with_pom
    same_major = [name for name in available if name.split(".", 1)[0] == requested_major]
    candidates = same_major or available
    if not candidates:
        return gav
    selected_version = sorted(candidates, key=normalize_version)[-1]
    return Gav(gav.group_id, gav.artifact_id, selected_version)


def parse_dependency(node, properties, dependency_management):
    group_id = resolve_placeholders(text_of(node, "groupId"), properties)
    artifact_id = resolve_placeholders(text_of(node, "artifactId"), properties)
    version = resolve_placeholders(text_of(node, "version"), properties)
    if version is None:
        version = dependency_management.get((group_id, artifact_id))
    scope = resolve_placeholders(text_of(node, "scope"), properties) or "compile"
    optional = (resolve_placeholders(text_of(node, "optional"), properties) or "false").lower() == "true"
    type_ = resolve_placeholders(text_of(node, "type"), properties) or "jar"
    exclusions = set()
    exclusions_parent = node.find("m:exclusions", NS)
    if exclusions_parent is not None:
        for exclusion in exclusions_parent.findall("m:exclusion", NS):
            ex_group = resolve_placeholders(text_of(exclusion, "groupId"), properties)
            ex_artifact = resolve_placeholders(text_of(exclusion, "artifactId"), properties)
            exclusions.add((ex_group, ex_artifact))
    return Dependency(group_id, artifact_id, version, scope, optional, type_, exclusions)


def load_model(pom_file: Path):
    pom_file = pom_file.resolve()
    if pom_file in MODEL_CACHE:
        return MODEL_CACHE[pom_file]

    root = ET.parse(pom_file).getroot()
    parent_node = root.find("m:parent", NS)

    parent_model = None
    parent_properties = {}
    parent_dm = {}
    inherited_group = None
    inherited_version = None

    if parent_node is not None:
        parent_group = text_of(parent_node, "groupId")
        parent_artifact = text_of(parent_node, "artifactId")
        parent_version = text_of(parent_node, "version")
        relative_path = text_of(parent_node, "relativePath")
        if relative_path:
            candidate = (pom_file.parent / relative_path).resolve()
            if candidate.is_dir():
                candidate = candidate / "pom.xml"
            if candidate.exists():
                parent_pom = candidate
            else:
                parent_pom = pom_path_for(Gav(parent_group, parent_artifact, parent_version))
        else:
            parent_pom = pom_path_for(Gav(parent_group, parent_artifact, parent_version))
        parent_model = load_model(parent_pom)
        parent_properties = dict(parent_model.properties)
        parent_dm = dict(parent_model.dependency_management)
        inherited_group = parent_model.gav.group_id
        inherited_version = parent_model.gav.version

    group_id = text_of(root, "groupId") or inherited_group
    artifact_id = text_of(root, "artifactId")
    version = text_of(root, "version") or inherited_version
    packaging = text_of(root, "packaging") or "jar"

    properties = dict(parent_properties)
    properties.update(
        {
            "project.groupId": group_id,
            "project.artifactId": artifact_id,
            "project.version": version,
            "pom.groupId": group_id,
            "pom.artifactId": artifact_id,
            "pom.version": version,
        }
    )

    properties_node = root.find("m:properties", NS)
    if properties_node is not None:
        for child in list(properties_node):
            tag = child.tag.split("}", 1)[-1]
            value = child.text.strip() if child.text else ""
            properties[tag] = resolve_placeholders(value, properties)

    dependency_management = dict(parent_dm)
    dep_mgmt_node = root.find("m:dependencyManagement", NS)
    if dep_mgmt_node is not None:
        for node in dep_mgmt_node.findall("m:dependencies/m:dependency", NS):
            dep = parse_dependency(node, properties, dependency_management)
            if dep.scope == "import" and dep.type_ == "pom" and dep.version:
                imported_pom = pom_path_for(Gav(dep.group_id, dep.artifact_id, dep.version))
                if imported_pom.exists():
                    imported_model = load_model(imported_pom)
                    dependency_management.update(imported_model.dependency_management)
                else:
                    print(
                        f"warning: skip missing imported bom {dep.group_id}:{dep.artifact_id}:{dep.version}",
                        file=sys.stderr,
                    )
            elif dep.version:
                dependency_management[(dep.group_id, dep.artifact_id)] = dep.version

    dependencies = []
    deps_node = root.find("m:dependencies", NS)
    if deps_node is not None:
        for node in deps_node.findall("m:dependency", NS):
            dep = parse_dependency(node, properties, dependency_management)
            dependencies.append(dep)

    model = PomModel(
        gav=Gav(group_id, artifact_id, version),
        packaging=packaging,
        properties=properties,
        dependency_management=dependency_management,
        dependencies=dependencies,
    )
    MODEL_CACHE[pom_file] = model
    return model


def should_include(scope):
    return scope in ("compile", "runtime")


def collect_runtime_jars(project_pom: Path):
    project_model = load_model(project_pom)
    resolved_jars = []
    seen = set()
    root_dependency_management = dict(project_model.dependency_management)

    def visit(dep: Dependency, inherited_exclusions: set[tuple[str, str]], is_direct: bool = False):
        if dep.optional or not should_include(dep.scope):
            return
        if (dep.group_id, dep.artifact_id) in inherited_exclusions:
            return
        resolved_version = dep.version if is_direct and dep.version else root_dependency_management.get(
            (dep.group_id, dep.artifact_id), dep.version
        )
        if not resolved_version:
            raise RuntimeError(f"Cannot resolve version for {dep.group_id}:{dep.artifact_id}")

        gav = Gav(dep.group_id, dep.artifact_id, resolved_version)
        pom_file = pom_path_for(gav)
        jar_file = jar_path_for(gav)
        if not pom_file.exists() and not jar_file.exists():
            gav = find_available_gav(gav)

        key = (gav.group_id, gav.artifact_id, gav.version)
        if key in seen:
            return
        seen.add(key)

        pom_file = pom_path_for(gav)
        jar_file = jar_path_for(gav)
        if not pom_file.exists():
            if not jar_file.exists():
                print(
                    f"warning: skip missing dependency {gav.group_id}:{gav.artifact_id}:{gav.version}",
                    file=sys.stderr,
                )
                return
            resolved_jars.append(jar_file)
            return

        model = load_model(pom_file)
        if model.packaging in JAR_PACKAGINGS:
            if not jar_file.exists():
                fallback_gav = find_available_gav(gav)
                fallback_jar = jar_path_for(fallback_gav)
                fallback_pom = pom_path_for(fallback_gav)
                if fallback_gav != gav and fallback_jar.exists():
                    gav = fallback_gav
                    jar_file = fallback_jar
                    if fallback_pom.exists():
                        model = load_model(fallback_pom)
                else:
                    print(f"warning: skip dependency without jar {gav.group_id}:{gav.artifact_id}:{gav.version}", file=sys.stderr)
                    return
            resolved_jars.append(jar_file)

        next_exclusions = set(inherited_exclusions)
        next_exclusions.update(dep.exclusions)
        for child in model.dependencies:
            visit(child, next_exclusions)

    for dep in project_model.dependencies:
        visit(dep, set(), is_direct=True)

    return resolved_jars


def main():
    parser = argparse.ArgumentParser(description="Build runtime classpath from pom.xml using local .m2 repository")
    parser.add_argument("--pom", default="pom.xml")
    parser.add_argument("--output", required=True)
    parser.add_argument("--prepend", action="append", default=[])
    args = parser.parse_args()

    project_pom = Path(args.pom).resolve()
    jars = collect_runtime_jars(project_pom)
    parts = [str(Path(item).resolve()) for item in args.prepend]
    parts.extend(str(jar.resolve()) for jar in jars)
    output = ";".join(parts)
    Path(args.output).write_text(output, encoding="utf-8")
    print(f"Wrote runtime classpath with {len(jars)} jars to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
