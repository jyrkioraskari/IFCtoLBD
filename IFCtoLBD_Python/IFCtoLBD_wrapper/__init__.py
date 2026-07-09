"""Small JPype-backed wrappers for the Java classes used by the examples."""

from __future__ import annotations

from pathlib import Path
from typing import Any, Mapping, Optional, Sequence, Union

import jpype
import jpype.imports  # noqa: F401 - enables Java package imports once the JVM starts


DEFAULT_CLASSPATH = str(Path(__file__).resolve().parent / "jars" / "*")


def start_jvm(
    classpath: Optional[Union[Sequence[str], str]] = None, *args: Any, **kwargs: Any
) -> None:
    """Start the JVM if it is not already running."""
    if jpype.isJVMStarted():
        return
    if classpath is None:
        classpath = [DEFAULT_CLASSPATH]
    elif isinstance(classpath, str):
        classpath = [classpath]
    jpype.startJVM(*args, classpath=list(classpath), **kwargs)


def shutdown_jvm() -> None:
    """Shut down the JVM if it is running."""
    if jpype.isJVMStarted():
        jpype.shutdownJVM()


def is_jvm_started() -> bool:
    return bool(jpype.isJVMStarted())


def _jclass(name: str) -> Any:
    start_jvm()
    return jpype.JClass(name)


def _unwrap(value: Any) -> Any:
    return value.java_object if isinstance(value, JavaObjectWrapper) else value


class JavaObjectWrapper:
    """Base wrapper that delegates unknown attributes to the Java instance."""

    java_object: Any

    def __getattr__(self, name: str) -> Any:
        return getattr(self.java_object, name)

    def __str__(self) -> str:
        return str(self.java_object)

    def __repr__(self) -> str:
        return repr(self.java_object)


class ConversionProperties(JavaObjectWrapper):
    def __init__(self) -> None:
        self.java_object = _jclass("org.linkedbuildingdata.ifc2lbd.ConversionProperties")()


class IFCtoLBDConverter(JavaObjectWrapper):
    def __init__(self, base_uri: str, level: int) -> None:
        self.java_object = _jclass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")(
            base_uri, level
        )

    def convert(self, ifc_file: str, properties: Optional[ConversionProperties] = None) -> Any:
        if properties is None:
            return self.java_object.convert(ifc_file)
        return self.java_object.convert(ifc_file, _unwrap(properties))

    def setProperty_replace_map(self, replacements: Union[Mapping[str, str], str]) -> Any:
        if isinstance(replacements, str):
            return self.java_object.setProperty_replace_map(replacements)
        return self.java_object.setProperty_replace_map(JavaHashMap(replacements).java_object)


class QueryFactory:
    @staticmethod
    def create(query_string: str) -> Any:
        return _jclass("org.apache.jena.query.QueryFactory").create(query_string)


class QueryExecutionFactory:
    @staticmethod
    def create(query: Any, model: Any) -> Any:
        return _jclass("org.apache.jena.query.QueryExecutionFactory").create(
            _unwrap(query), _unwrap(model)
        )


class JavaHashMap(JavaObjectWrapper):
    def __init__(self, values: Optional[Mapping[Any, Any]] = None) -> None:
        self.java_object = _jclass("java.util.HashMap")()
        if values:
            for key, value in values.items():
                self.java_object.put(key, value)

    def put(self, key: Any, value: Any) -> Any:
        return self.java_object.put(key, value)


__all__ = [
    "ConversionProperties",
    "IFCtoLBDConverter",
    "JavaHashMap",
    "JavaObjectWrapper",
    "QueryExecutionFactory",
    "QueryFactory",
    "is_jvm_started",
    "shutdown_jvm",
    "start_jvm",
]
