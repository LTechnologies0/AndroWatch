import re
from pathlib import Path


def patch_addsafe(content: str) -> str:
    result: list[str] = []
    i = 0
    while True:
        idx = content.find("addSafe {", i)
        if idx == -1:
            result.append(content[i:])
            break
        result.append(content[i:idx])
        line_start = content.rfind("\n", 0, idx) + 1
        indent = content[line_start:idx]
        brace = content.find("{", idx)
        depth = 0
        j = brace
        while j < len(content):
            c = content[j]
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        body = content[brace + 1 : j]
        m = re.search(
            r'(?:signal|interpretedSignal|tagsSignal|compoundSignal)\(\s*'
            r'category,\s*"([^"]+)",\s*"([^"]+)"',
            body,
            re.DOTALL,
        )
        if m:
            key, name = m.group(1), m.group(2)
            result.append(f'{indent}addSafe(category, "{key}", "{name}") {{')
            result.append(body)
            result.append(content[j])
        else:
            result.append(content[idx : j + 1])
        i = j + 1
    return "".join(result)


def main() -> None:
    root = Path(__file__).resolve().parents[1] / "collector"
    for path in root.rglob("*Collectors.kt"):
        text = path.read_text(encoding="utf-8")
        new = patch_addsafe(text)
        if new != text:
            path.write_text(new, encoding="utf-8")
            print(f"patched {path}")


if __name__ == "__main__":
    main()
