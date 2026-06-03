import ast
import os
import sys

# Note: this checks all import statements unconditionally, including those inside
# try/except blocks. Scripts that guard optional imports with try/except ImportError
# may trigger a false positive here.
#
# Output protocol: a missing module is reported as a single line
#   MISSING:<module_name>
# on stdout, followed by sys.exit(1). Any other non-zero exit (uncaught exception,
# Unicode/IO error, etc.) is treated by the Java caller as a crash, not a missing
# dependency, so the user is not falsely prompted to reinstall packages.

script_path = sys.argv[1]
sys.path.insert(0, os.path.dirname(os.path.abspath(script_path)))

with open(script_path, encoding='utf-8') as f:
    source = f.read()

tree = ast.parse(source)

for node in ast.walk(tree):
    if isinstance(node, ast.Import):
        for alias in node.names:
            module = alias.name.split('.')[0]
            try:
                __import__(module)
            except Exception:
                print(f"MISSING:{module}")
                sys.exit(1)
    elif isinstance(node, ast.ImportFrom) and node.level == 0 and node.module:
        module = node.module.split('.')[0]
        try:
            __import__(module)
        except Exception:
            print(f"MISSING:{module}")
            sys.exit(1)
