import ast
import sys

script_path = sys.argv[1]

with open(script_path) as f:
    source = f.read()

tree = ast.parse(source)

for node in ast.walk(tree):
    if isinstance(node, ast.Import):
        for alias in node.names:
            module = alias.name.split('.')[0]
            try:
                __import__(module)
            except ImportError:
                print(module)
                sys.exit(1)
    elif isinstance(node, ast.ImportFrom) and node.level == 0 and node.module:
        module = node.module.split('.')[0]
        try:
            __import__(module)
        except ImportError:
            print(module)
            sys.exit(1)
