"""
PatchCam On-Device AST Validator
Runs embedded in CPython via Chaquopy.

The validator returns structured parser evidence so the Android/LLM
diagnostic layer can explain the error instead of repeating a generic
"invalid syntax" message.
"""
import ast
import json


def validate_ast(source_code: str) -> str:
    try:
        ast.parse(source_code)
        return json.dumps({
            "valid": True,
            "error": None,
            "line": None,
            "offset": None,
            "source_line": None,
        })
    except SyntaxError as exc:
        source_line = None
        if exc.lineno and 1 <= exc.lineno <= len(source_code.splitlines()):
            source_line = source_code.splitlines()[exc.lineno - 1]

        return json.dumps({
            "valid": False,
            "error": str(exc),
            "line": exc.lineno,
            "offset": exc.offset,
            "source_line": source_line,
        })
    except Exception as exc:
        return json.dumps({
            "valid": False,
            "error": str(exc),
            "line": None,
            "offset": None,
            "source_line": None,
        })
