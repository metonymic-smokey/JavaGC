import re


_avg_regex = re.compile(r"\(min, avg, max\) = \(\d+.\d*, (\d+.\d*), \d+.\d*\)")
_ci_regex = re.compile(r"CI \(99\.9%\): \[(\d+\.\d*), (\d+\.\d*)\]")


def extract_times(filename: str):
    """Extract avg, 99.9% ci lower and higher"""

    with open(filename) as f:
        text = f.read()

    avg = float(next(_avg_regex.finditer(text)).group(1))

    _ci_match = next(_ci_regex.finditer(text))
    ci_lower = float(_ci_match.group(1))
    ci_higher = float(_ci_match.group(2))

    return avg, (ci_lower, ci_higher)
