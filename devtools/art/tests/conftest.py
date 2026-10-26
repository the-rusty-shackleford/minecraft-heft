"""The art tests run with devtools/art and the shared tools/pbr on the path, as build.py does."""
import sys
from pathlib import Path

ART = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ART))
sys.path.insert(0, str(ART.parents[2] / "tools" / "pbr"))
