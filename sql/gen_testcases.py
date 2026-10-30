# -*- coding: utf-8 -*-
"""
生成 test_case 测试点 + 题目描述/样例 的 SQL。

设计原则：
  1. 每个测试点的 expected 都由独立参考实现计算，不手写，避免算错。
  2. 对易出现多解的题（两数之和）强制校验“有且仅有一组解”。
  3. 对边界条件（空串、全负数、全水、单元素、极值）显式覆盖。
  4. 生成后用另一套算法交叉校验（见 verify_db.py）。
"""
import bisect
import os
import random

OUT_SQL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "testcase.sql")

# ---------------------------------------------------------------- 参考实现

def solve_ab(inp):
    a, b = map(int, inp.split())
    return str(a + b)

def two_sum_pairs(nums, target):
    """O(n + 解数) 枚举全部下标对，避免大数组时 O(n^2) 校验。"""
    positions = {}
    pairs = []
    for i, value in enumerate(nums):
        for j in positions.get(target - value, []):
            pairs.append((j, i))
        positions.setdefault(value, []).append(i)
    return pairs

def solve_two_sum(inp):
    lines = inp.splitlines()
    n, target = map(int, lines[0].split())
    nums = list(map(int, lines[1].split()))
    assert len(nums) == n, (len(nums), n)
    pairs = two_sum_pairs(nums, target)
    assert len(pairs) == 1, f"两数之和必须唯一解，实际 {pairs}"
    i, j = pairs[0]
    return f"{i} {j}"

def solve_palindrome(inp):
    x = int(inp.strip())
    s = str(x)
    return "true" if s == s[::-1] else "false"

def solve_brackets(inp):
    mapping = {")": "(", "]": "[", "}": "{"}
    stack = []
    for ch in inp:
        if ch in "([{":
            stack.append(ch)
        else:
            if not stack or stack[-1] != mapping[ch]:
                return "false"
            stack.pop()
    return "true" if not stack else "false"

def solve_climb(inp):
    n = int(inp.strip())
    a, b = 1, 1
    for _ in range(n):
        a, b = b, a + b
    return str(a)

def solve_max_subarray(inp):
    lines = inp.splitlines()
    n = int(lines[0])
    nums = list(map(int, lines[1].split()))
    assert len(nums) == n, (len(nums), n)
    best = cur = nums[0]
    for v in nums[1:]:
        cur = max(v, cur + v)
        best = max(best, cur)
    return str(best)

def solve_longest_unique(inp):
    last, left, best = {}, 0, 0
    for i, ch in enumerate(inp):
        if ch in last and last[ch] >= left:
            left = last[ch] + 1
        last[ch] = i
        best = max(best, i - left + 1)
    return str(best)

def solve_binary_search(inp):
    lines = inp.splitlines()
    n, target = map(int, lines[0].split())
    nums = list(map(int, lines[1].split()))
    assert len(nums) == n
    assert all(nums[i] < nums[i + 1] for i in range(n - 1)), "二分查找要求严格递增"
    i = bisect.bisect_left(nums, target)
    return str(i) if i < n and nums[i] == target else "-1"

def solve_islands(inp):
    lines = inp.splitlines()
    m, n = map(int, lines[0].split())
    grid = [line.split() for line in lines[1:1 + m]]
    assert len(grid) == m and all(len(r) == n for r in grid), "网格尺寸不符"
    seen = [[False] * n for _ in range(m)]
    count = 0
    for i in range(m):
        for j in range(n):
            if grid[i][j] != "1" or seen[i][j]:
                continue
            count += 1
            stack = [(i, j)]
            seen[i][j] = True
            while stack:
                x, y = stack.pop()
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < m and 0 <= ny < n and grid[nx][ny] == "1" and not seen[nx][ny]:
                        seen[nx][ny] = True
                        stack.append((nx, ny))
    return str(count)

def solve_trap(inp):
    lines = inp.splitlines()
    n = int(lines[0])
    h = list(map(int, lines[1].split()))
    assert len(h) == n
    left, right = 0, n - 1
    left_max = right_max = water = 0
    while left < right:
        if h[left] < h[right]:
            left_max = max(left_max, h[left])
            water += left_max - h[left]
            left += 1
        else:
            right_max = max(right_max, h[right])
            water += right_max - h[right]
            right -= 1
    return str(water)

# ---------------------------------------------------------------- 交叉校验实现

def brute_max_subarray(nums):
    best = None
    for i in range(len(nums)):
        s = 0
        for j in range(i, len(nums)):
            s += nums[j]
            best = s if best is None else max(best, s)
    return best

def prefix_max_subarray(nums):
    # 前缀和 + 维护最小前缀，与 Kadane 独立
    best = -10**30
    min_prefix = 0
    prefix = 0
    for v in nums:
        prefix += v
        best = max(best, prefix - min_prefix)
        min_prefix = min(min_prefix, prefix)
    return best

def brute_longest_unique(s):
    best = 0
    for i in range(len(s)):
        seen = set()
        for j in range(i, len(s)):
            if s[j] in seen:
                break
            seen.add(s[j])
            best = max(best, j - i + 1)
    return best

def set_window_longest(s):
    """集合窗口实现，与“记录最后位置”的实现相互独立。"""
    seen = set()
    left = best = 0
    for right, ch in enumerate(s):
        while ch in seen:
            seen.remove(s[left])
            left += 1
        seen.add(ch)
        best = max(best, right - left + 1)
    return best

def prefix_trap(h):
    n = len(h)
    if n == 0:
        return 0
    left = [0] * n
    right = [0] * n
    cur = 0
    for i in range(n):
        cur = max(cur, h[i])
        left[i] = cur
    cur = 0
    for i in range(n - 1, -1, -1):
        cur = max(cur, h[i])
        right[i] = cur
    return sum(min(left[i], right[i]) - h[i] for i in range(n))

def union_find_islands(inp):
    lines = inp.splitlines()
    m, n = map(int, lines[0].split())
    grid = [line.split() for line in lines[1:1 + m]]
    parent = list(range(m * n))
    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x
    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra
    for i in range(m):
        for j in range(n):
            if grid[i][j] != "1":
                continue
            if i > 0 and grid[i - 1][j] == "1":
                union(i * n + j, (i - 1) * n + j)
            if j > 0 and grid[i][j - 1] == "1":
                union(i * n + j, i * n + j - 1)
    roots = {find(i * n + j) for i in range(m) for j in range(n) if grid[i][j] == "1"}
    return len(roots)

# ---------------------------------------------------------------- 用例构造

def make_unique_two_sum(n, target, lo, hi, seed):
    rnd = random.Random(seed)
    for attempt in range(1, 10000):
        nums = [rnd.randint(lo, hi) for _ in range(n)]
        pairs = two_sum_pairs(nums, target)
        if len(pairs) == 1:
            return nums, pairs[0]
    raise RuntimeError("未能生成唯一解")

def grid_input(grid):
    m, n = len(grid), len(grid[0])
    return f"{m} {n}\n" + "\n".join(" ".join(row) for row in grid)

def make_grid(m, n, p, seed):
    rnd = random.Random(seed)
    return [["1" if rnd.random() < p else "0" for _ in range(n)] for _ in range(m)]

CASES = {}   # pid -> list[(input|None, expected, note)]

# 1. Hello Judge —— 无输入，输出固定；重复测试点没有意义，只保留 1 组
CASES[1] = [(None, "hello judge", "无输入固定输出")]

# 2. A+B
ab_inputs = [
    ("1 2", "样例"),
    ("0 0", "零值"),
    ("-1 -2", "两个负数"),
    ("1000000000 1000000000", "正数上界附近"),
    ("-1000000000 -1000000000", "负数下界附近"),
    ("1000000000 -1000000000", "正负抵消"),
    ("0 -5", "零与负数"),
    ("123456789 987654321", "两个大正数"),
    ("-7 3", "负数加正数"),
    ("2147483647 0", "int 最大值"),
    ("-2147483648 0", "int 最小值"),
    ("42 0", "加零"),
]
CASES[2] = [(i, solve_ab(i), n) for i, n in ab_inputs]

# 3. 两数之和
two_sum_manual = [
    (4, 9, [2, 7, 11, 15], "样例"),
    (2, 3, [1, 2], "最短数组"),
    (4, -1, [-3, 1, 2, -4], "负目标值"),
    (3, 6, [3, 3, 4], "重复元素"),
    (3, 0, [0, 0, 5], "两个 0"),
    (5, 0, [-1000000000, 1000000000, 5, 7, 9], "极值抵消"),
    (8, 17, [3, 8, 14, -2, 5, 10, 20, 1], "答案位于前段"),
    (6, 100, [40, 60, 1, 98, 55, 46], "较大目标值"),
    (10, 25, [-5, 30, 12, 14, 7, 19, 2, 24, -10, 40], "含负数的中等数组"),
]
CASES[3] = []
for n, target, nums, note in two_sum_manual:
    inp = f"{n} {target}\n" + " ".join(map(str, nums))
    CASES[3].append((inp, solve_two_sum(inp), note))

for n, target, lo, hi, seed, note in [
    (20, 7, -20, 20, 301, "随机 20 个"),
    (50, -30, -100, 100, 302, "随机 50 个，负目标"),
    (100, 1000, 0, 1000, 303, "随机 100 个"),
    (300, 12345, -10000, 20000, 304, "随机 300 个"),
    (1000, 500000, 0, 1000000, 305, "随机 1000 个，规模测试"),
]:
    nums, (i, j) = make_unique_two_sum(n, target, lo, hi, seed)
    inp = f"{n} {target}\n" + " ".join(map(str, nums))
    # 唯一性再确认
    assert len(two_sum_pairs(nums, target)) == 1
    CASES[3].append((inp, f"{i} {j}", note))

# 规模上限：1..100000 中只有 99999 + 100000 = 199999
large_nums = list(range(1, 100001))
large_target = 199999
assert two_sum_pairs(large_nums, large_target) == [(99998, 99999)]
CASES[3].append((f"{len(large_nums)} {large_target}\n" + " ".join(map(str, large_nums)),
                 "99998 99999", "规模 100000，唯一解在末尾"))

# 4. 回文数
pal_inputs = [
    ("121", "样例"),
    ("-121", "负数"),
    ("10", "末位为 0"),
    ("0", "零"),
    ("1", "个位数"),
    ("11", "两位回文"),
    ("1001", "偶数位回文"),
    ("1000", "偶数位非回文"),
    ("12321", "奇数位回文"),
    ("12345", "普通非回文"),
    ("999999999", "全 9"),
    ("1000000001", "首尾为 1 中间全 0"),
    ("1234554321", "十位回文"),
    ("2147483647", "int 最大值"),
    ("-2147483648", "int 最小值"),
]
CASES[4] = [(i, solve_palindrome(i), n) for i, n in pal_inputs]

# 5. 有效的括号
bracket_inputs = [
    ("()[]{}", "样例：三种括号依次配对"),
    ("()", "单对括号"),
    ("(]", "类型不匹配"),
    ("([)]", "交叉嵌套"),
    ("{[]}", "正确嵌套"),
    ("(((", "只有左括号"),
    (")))", "只有右括号"),
    ("]", "单个右括号"),
    ("(", "单个左括号"),
    (")", "单个右括号"),
    ("{[()]}", "三层嵌套"),
    ("(){}[]", "连续配对"),
    ("(" * 50 + ")" * 50, "长合法串"),
    ("(" * 50 + ")" * 49, "长串少一个右括号"),
    ("(){}}{", "提前闭合后多余"),
    ("[])", "多余右括号"),
]
CASES[5] = [(i, solve_brackets(i), n) for i, n in bracket_inputs]
CASES[5].append(("(" * 5000 + ")" * 5000, "true", "长度 10000 的合法串（规模上限）"))

# 6. 爬楼梯
CASES[6] = [(str(n), solve_climb(str(n)), f"n={n}") for n in
            [5, 1, 2, 3, 4, 10, 15, 20, 25, 30, 35, 40, 44, 45]]

# 7. 最大子数组和
max_sub_manual = [
    ([-2, 1, -3, 4, -1, 2, 1, -5, 4], "样例"),
    ([5], "单元素正数"),
    ([-3], "单元素负数"),
    ([-1, -2, -3, -4], "全负数"),
    ([1, 2, 3, 4, 5], "全正数"),
    ([0, 0, 0, 0], "全零"),
    ([5, -10, 5, -10, 5, -10, 5], "正负交替"),
    ([-1, 2, 3, -5, 4, 6], "跨负数的增益段"),
    ([8, -19, 5, -4, 20], "最大段在末尾"),
    ([1, -1] * 5 + [1], "多次震荡"),
    ([-2, -1, -3, -4, -1, -2, -1], "全负数取最大单元素"),
]
CASES[7] = []
for nums, note in max_sub_manual:
    inp = f"{len(nums)}\n" + " ".join(map(str, nums))
    exp = solve_max_subarray(inp)
    assert int(exp) == brute_max_subarray(nums)
    assert int(exp) == prefix_max_subarray(nums)
    CASES[7].append((inp, exp, note))

for n, lo, hi, seed, note in [
    (1000, -100, 100, 701, "随机 1000 个"),
    (2000, -10000, 10000, 702, "随机 2000 个，值域边界"),
]:
    rnd = random.Random(seed)
    nums = [rnd.randint(lo, hi) for _ in range(n)]
    inp = f"{n}\n" + " ".join(map(str, nums))
    exp = solve_max_subarray(inp)
    assert int(exp) == prefix_max_subarray(nums)
    CASES[7].append((inp, exp, note))

# 规模上限
rnd = random.Random(703)
nums = [rnd.randint(-10000, 10000) for _ in range(100000)]
inp = f"{len(nums)}\n" + " ".join(map(str, nums))
exp = solve_max_subarray(inp)
assert int(exp) == prefix_max_subarray(nums)
CASES[7].append((inp, exp, "规模 100000"))

# 8. 无重复字符的最长子串
longest_inputs = [
    ("abcabcbb", "样例"),
    ("bbbbb", "全重复"),
    ("pwwkew", "重复后重置"),
    (" ", "单个空格"),
    ("au", "两个不同字符"),
    ("dvdf", "重复字符在中间"),
    ("abba", "回文式重复"),
    ("tmmzuxt", "末尾新增字符"),
    ("abcdefg", "全不重复"),
    ("a b c d", "含空格"),
    ("!@#$%^&*()", "全符号"),
    ("1231234567890", "数字串"),
    ("aA", "大小写敏感"),
    ("aa", "两个相同字符"),
]
CASES[8] = []
for s, note in longest_inputs:
    exp = solve_longest_unique(s)
    assert int(exp) == brute_longest_unique(s)
    CASES[8].append((s, exp, note))

rnd = random.Random(801)
alphabet = "abcXYZ019 _-!@"
random_s = "".join(rnd.choice(alphabet) for _ in range(1000))
exp = solve_longest_unique(random_s)
assert int(exp) == brute_longest_unique(random_s)
CASES[8].append((random_s, exp, "随机 1000 字符"))

# 规模上限
rnd = random.Random(802)
alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
large_s = "".join(rnd.choice(alphabet) for _ in range(100000))
exp = solve_longest_unique(large_s)
assert int(exp) == set_window_longest(large_s)
CASES[8].append((large_s, exp, "规模 100000"))

# 9. 二分查找
bs_inputs = [
    (6, 9, [-1, 0, 3, 5, 9, 12], "样例"),
    (1, 5, [5], "单元素命中"),
    (1, 4, [5], "单元素未命中"),
    (6, -1, [-1, 0, 3, 5, 9, 12], "命中首元素"),
    (6, 12, [-1, 0, 3, 5, 9, 12], "命中末元素"),
    (6, -2, [-1, 0, 3, 5, 9, 12], "小于最小值"),
    (6, 13, [-1, 0, 3, 5, 9, 12], "大于最大值"),
    (6, 4, [-1, 0, 3, 5, 9, 12], "落在空隙"),
    (2, 1, [1, 2], "两元素命中首个"),
    (2, 2, [1, 2], "两元素命中末尾"),
    (8, -7, [-10, -9, -8, -7, -6, -5, -4, -3], "全负数数组"),
]
CASES[9] = []
for n, target, nums, note in bs_inputs:
    inp = f"{n} {target}\n" + " ".join(map(str, nums))
    CASES[9].append((inp, solve_binary_search(inp), note))

rnd = random.Random(901)
nums = sorted(rnd.sample(range(-100000, 100000), 1000))
target = nums[617]
inp = f"1000 {target}\n" + " ".join(map(str, nums))
CASES[9].append((inp, solve_binary_search(inp), "随机 1000 个，命中中段"))

# 规模上限
rnd = random.Random(902)
nums = sorted(rnd.sample(range(-100000, 100000), 100000))
target = nums[54321]
inp = f"{len(nums)} {target}\n" + " ".join(map(str, nums))
exp = solve_binary_search(inp)
assert int(exp) == nums.index(target)
CASES[9].append((inp, exp, "规模 100000，命中中段"))

# 10. 岛屿数量
island_cases = [
    ([["1", "1", "0", "0", "0"],
      ["1", "1", "0", "0", "0"],
      ["0", "0", "1", "0", "0"],
      ["0", "0", "0", "1", "1"]], "样例：3 座岛"),
    ([["1"]], "单格陆地"),
    ([["0"]], "单格水"),
    ([["0", "0", "0"], ["0", "0", "0"], ["0", "0", "0"]], "全水"),
    ([["1", "1", "1"], ["1", "1", "1"], ["1", "1", "1"]], "全陆地"),
    ([["1"] * 5], "单行全陆地"),
    ([["1"], ["1"], ["1"], ["1"], ["1"]], "单列全陆地"),
    ([["1", "0", "1", "0"],
      ["0", "1", "0", "1"],
      ["1", "0", "1", "0"],
      ["0", "1", "0", "1"]], "棋盘格：8 座岛"),
    ([["1", "0", "0"], ["0", "1", "0"], ["0", "0", "1"]], "斜向不相连"),
    ([["1", "1", "1", "1"],
      ["1", "0", "0", "1"],
      ["1", "0", "0", "1"],
      ["1", "1", "1", "1"]], "环形陆地"),
    ([["1", "1", "1", "1", "1"],
      ["0", "0", "0", "0", "1"],
      ["1", "1", "1", "1", "1"],
      ["1", "0", "0", "0", "0"],
      ["1", "1", "1", "1", "1"]], "蛇形连通"),
    ([["1", "0"], ["0", "1"]], "2x2 两个对角陆地"),
]
CASES[10] = []
for grid, note in island_cases:
    inp = grid_input(grid)
    exp = solve_islands(inp)
    assert int(exp) == union_find_islands(inp), (note, exp, union_find_islands(inp))
    CASES[10].append((inp, exp, note))

for m, n, p, seed, note in [
    (20, 20, 0.4, 1001, "随机 20x20"),
    (50, 50, 0.3, 1002, "随机 50x50"),
]:
    grid = make_grid(m, n, p, seed)
    inp = grid_input(grid)
    exp = solve_islands(inp)
    assert int(exp) == union_find_islands(inp)
    CASES[10].append((inp, exp, note))

# 规模上限
grid = make_grid(100, 100, 0.35, 1003)
inp = grid_input(grid)
exp = solve_islands(inp)
assert int(exp) == union_find_islands(inp)
CASES[10].append((inp, exp, "规模 100x100"))

# 11. 接雨水
trap_inputs = [
    ([0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1], "样例"),
    ([5], "单柱"),
    ([1, 2], "两柱递增"),
    ([0, 0, 0, 0], "全零"),
    ([1, 2, 3, 4, 5], "单调递增"),
    ([5, 4, 3, 2, 1], "单调递减"),
    ([2, 0, 2], "中间低洼"),
    ([3, 0, 0, 2, 0, 4], "多段低洼"),
    ([4, 2, 0, 3, 2, 5], "典型凹槽"),
    ([0, 1, 0], "两端为 0"),
    ([2, 1, 2], "中间一根矮柱"),
    ([5, 5, 1, 7, 1, 1, 5, 2, 7, 6], "多峰夹谷"),
]
CASES[11] = []
for h, note in trap_inputs:
    inp = f"{len(h)}\n" + " ".join(map(str, h))
    exp = solve_trap(inp)
    assert int(exp) == prefix_trap(h), (note, exp, prefix_trap(h))
    CASES[11].append((inp, exp, note))

for n, hi, seed, note in [
    (1000, 1000, 1101, "随机 1000 根"),
    (5000, 100000, 1102, "随机 5000 根，值域边界"),
]:
    rnd = random.Random(seed)
    h = [rnd.randint(0, hi) for _ in range(n)]
    inp = f"{n}\n" + " ".join(map(str, h))
    exp = solve_trap(inp)
    assert int(exp) == prefix_trap(h)
    CASES[11].append((inp, exp, note))

# 规模上限
rnd = random.Random(1103)
h = [rnd.randint(0, 100000) for _ in range(20000)]
inp = f"{len(h)}\n" + " ".join(map(str, h))
exp = solve_trap(inp)
assert int(exp) == prefix_trap(h)
CASES[11].append((inp, exp, "规模 20000，值域边界"))

# ---------------------------------------------------------------- 题目描述

DESCRIPTIONS = {}

DESCRIPTIONS[1] = """请输出一行固定字符串 hello judge（全小写，中间一个空格），用于熟悉判题流程。本题没有输入。

输入格式：
本题没有输入。

输出格式：
输出一行 hello judge。

样例：
输入：（无）
输出：hello judge

说明：
判题时会忽略行首、行末的空白与多余换行，但字母大小写必须完全一致。"""

DESCRIPTIONS[2] = """读入两个整数 a 和 b，输出它们的和。

输入格式：
一行两个整数 a 和 b，用空格分隔。

输出格式：
输出一个整数，表示 a + b。

数据范围：
-2^31 ≤ a, b ≤ 2^31-1，保证 a + b 在 32 位有符号整数范围内。

样例：
输入：1 2
输出：3

提示：
注意处理 0 和负数。"""

DESCRIPTIONS[3] = """给定一个整数数组 nums 和一个目标值 target，请在数组中找出和为 target 的两个整数，并返回它们的下标（下标从 0 开始）。

同一元素不能重复使用，答案中的下标必须满足 i < j。

输入格式：
第一行两个整数 n 和 target，用空格分隔。
第二行 n 个整数，表示数组 nums。

输出格式：
输出两个下标 i 和 j，用一个空格分隔，且 i < j。

数据范围：
2 ≤ n ≤ 10^5，-10^9 ≤ nums[i], target ≤ 10^9。
保证有且仅有一组下标满足条件。

样例：
输入：
4 9
2 7 11 15
输出：
0 1

说明：
答案唯一，因此按原样输出这组下标即可。"""

DESCRIPTIONS[4] = """给你一个整数 x，如果 x 是回文整数，输出 true；否则输出 false。

回文数是指正序（从左向右）和倒序（从右向左）读都相同的整数。例如 121 是回文数，而 -121 不是。

输入格式：
一行一个整数 x。

输出格式：
输出 true 或 false（全小写）。

数据范围：
-2^31 ≤ x ≤ 2^31-1。

样例：
输入：121
输出：true

提示：
负数一定不是回文数；末位为 0 且本身不为 0 的数也不是回文数。"""

DESCRIPTIONS[5] = """给定一个只包括 '('、')'、'{'、'}'、'['、']' 的字符串 s，判断字符串是否有效。

有效字符串需满足：
1. 左括号必须用相同类型的右括号闭合；
2. 左括号必须以正确的顺序闭合。

输入格式：
一行一个字符串 s。

输出格式：
输出 true 或 false（全小写）。

数据范围：
1 ≤ |s| ≤ 10^4，s 仅由上述六种括号组成。

样例：
输入：()[]{}
输出：true

提示：
只有所有括号都正确配对时才是有效字符串；单个左括号或单个右括号都无效。"""

DESCRIPTIONS[6] = """假设你正在爬楼梯，需要 n 阶才能到达楼顶。每次你可以爬 1 或 2 个台阶，问有多少种不同的方法可以爬到楼顶。

输入格式：
一行一个整数 n。

输出格式：
输出爬到楼顶的方法数。

数据范围：
1 ≤ n ≤ 45，保证结果在 32 位有符号整数范围内。

样例：
输入：5
输出：8

提示：
n = 1 时只有 1 种方法；n = 2 时有 2 种方法。"""

DESCRIPTIONS[7] = """给你一个整数数组 nums，请找出一个具有最大和的连续子数组（子数组最少包含一个元素），返回其最大和。

输入格式：
第一行一个整数 n，表示数组长度。
第二行 n 个整数，表示数组 nums。

输出格式：
输出一个整数，表示连续子数组的最大和。

数据范围：
1 ≤ n ≤ 10^5，-10^4 ≤ nums[i] ≤ 10^4。

样例：
输入：
9
-2 1 -3 4 -1 2 1 -5 4
输出：
6

提示：
子数组至少包含一个元素，因此数组全为负数时，答案是其中最大的那个负数。"""

DESCRIPTIONS[8] = """给定一个字符串 s，请找出其中不含有重复字符的最长子串的长度。

输入格式：
一行一个字符串 s，可能包含英文字母、数字、空格和常见符号。

输出格式：
输出一个整数，表示最长子串的长度。

数据范围：
1 ≤ |s| ≤ 10^5，字符串中不含换行符。

样例：
输入：abcabcbb
输出：3

提示：
区分大小写（'a' 与 'A' 视为不同字符）；空格也计入长度。"""

DESCRIPTIONS[9] = """给定一个严格递增排列的整数数组 nums 和一个目标值 target，请使用二分查找找出 target 在数组中的下标，不存在则返回 -1。

输入格式：
第一行两个整数 n 和 target。
第二行 n 个严格递增排列的整数。

输出格式：
输出 target 的下标（从 0 开始），不存在输出 -1。

数据范围：
1 ≤ n ≤ 10^5，-10^9 ≤ nums[i], target ≤ 10^9。
保证数组中没有重复元素。

样例：
输入：
6 9
-1 0 3 5 9 12
输出：
4

提示：
建议使用 O(log n) 的二分查找；目标不存在时返回 -1。"""

DESCRIPTIONS[10] = """给你一个由 '1'（陆地）和 '0'（水）组成的二维网格，请计算网格中岛屿的数量。

岛屿总是被水包围，并且每座岛屿只能由水平或垂直方向上相邻的陆地连接形成。斜向相邻不算连通。

输入格式：
第一行两个整数 m 和 n，表示网格的行数和列数。
接下来 m 行，每行 n 个用空格分隔的字符（0 或 1）。

输出格式：
输出一个整数，表示岛屿的数量。

数据范围：
1 ≤ m, n ≤ 100。

样例：
输入：
4 5
1 1 0 0 0
1 1 0 0 0
0 0 1 0 0
0 0 0 1 1
输出：
3

提示：
全为水时答案是 0；整个网格连成一片时答案是 1。"""

DESCRIPTIONS[11] = """给定 n 个非负整数表示每个宽度为 1 的柱子的高度图，计算按此排列的柱子在下雨后能接多少雨水。

输入格式：
第一行一个整数 n。
第二行 n 个非负整数，表示柱子的高度。

输出格式：
输出一个整数，表示能接到的雨水总量。

数据范围：
1 ≤ n ≤ 2×10^4，0 ≤ height[i] ≤ 10^5。

样例：
输入：
12
0 1 0 2 1 0 1 3 2 1 2 1
输出：
6

提示：
柱子宽度均为 1；数组两端无法蓄水；所有高度相同时接不到雨水。"""

# 题面中的样例必须与第 1 组测试点、problem.input/expected 三者一致
SAMPLES = {
    1: (None, "hello judge"),
    2: ("1 2", "3"),
    3: ("4 9\n2 7 11 15", "0 1"),
    4: ("121", "true"),
    5: ("()[]{}", "true"),
    6: ("5", "8"),
    7: ("9\n-2 1 -3 4 -1 2 1 -5 4", "6"),
    8: ("abcabcbb", "3"),
    9: ("6 9\n-1 0 3 5 9 12", "4"),
    10: ("4 5\n1 1 0 0 0\n1 1 0 0 0\n0 0 1 0 0\n0 0 0 1 1", "3"),
    11: ("12\n0 1 0 2 1 0 1 3 2 1 2 1", "6"),
}
for pid, (sample_input, sample_output) in SAMPLES.items():
    first_input, first_output, _ = CASES[pid][0]
    assert first_input == sample_input, f"problem {pid} 首组输入与题面样例不一致"
    assert first_output == sample_output, f"problem {pid} 首组输出与题面样例不一致"
    if sample_input is not None:
        assert sample_input in DESCRIPTIONS[pid], f"problem {pid} 题面未包含样例输入"
    assert sample_output in DESCRIPTIONS[pid], f"problem {pid} 题面未包含样例输出"

# ---------------------------------------------------------------- SQL 输出

def sql_str(value):
    if value is None:
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"

lines = []
lines.append("-- ============================================================")
lines.append("-- 测试点数据 + 题目描述/样例")
lines.append("-- 由 sql/gen_testcases.py 生成，可重复执行（会重置 1-11 号题的测试点）")
lines.append("-- 期望输出均由参考实现计算并交叉校验")
lines.append("-- ============================================================")
lines.append("-- 数据构成：")
lines.append("--   1-11 号题，共 147 组测试点；每题 score 合计固定为 100")
lines.append("--   problem.input / problem.expected 只存题面示例（= 第 1 组测试点）")
lines.append("--   真正的判题数据全部在 test_case，判题链路不会读 problem 的样例列")
lines.append("SET NAMES utf8mb4;")
lines.append("SET time_zone = '+08:00';")
lines.append("")
lines.append("-- 1. 测试点可能超过 TEXT 的 64KB 上限，扩宽为 MEDIUMTEXT")
lines.append("--    第 3、7、8、9、11 题的大数组/长字符串用例都超过该上限（最长约 62 万字符），")
lines.append("--    若仍是 TEXT，插入会被截断或直接报错。")
lines.append("ALTER TABLE `test_case`")
lines.append("  MODIFY COLUMN `input` MEDIUMTEXT NULL,")
lines.append("  MODIFY COLUMN `expected` MEDIUMTEXT NOT NULL;")
lines.append("")
lines.append("-- 2. 清理孤儿测试点，并建立 problem_id -> problem.id 外键")
lines.append("--    必须先删孤儿再建外键，否则历史脏数据会让 ALTER 直接失败。")
lines.append("--    ON DELETE CASCADE：删除题目时自动清理其测试点，避免留下孤儿数据。")
lines.append("DELETE FROM `test_case` WHERE `problem_id` NOT IN (SELECT `id` FROM `problem`);")
lines.append("SET @fk_exists := (")
lines.append("  SELECT COUNT(*) FROM information_schema.table_constraints")
lines.append("  WHERE constraint_schema = DATABASE()")
lines.append("    AND table_name = 'test_case'")
lines.append("    AND constraint_type = 'FOREIGN KEY'")
lines.append(");")
lines.append("SET @fk_sql := IF(@fk_exists = 0,")
lines.append("  'ALTER TABLE `test_case` ADD CONSTRAINT `fk_test_case_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE CASCADE ON UPDATE CASCADE',")
lines.append("  'SELECT 1');")
lines.append("PREPARE fk_stmt FROM @fk_sql;")
lines.append("EXECUTE fk_stmt;")
lines.append("DEALLOCATE PREPARE fk_stmt;")
lines.append("")
lines.append("START TRANSACTION;")
lines.append("-- 以下 DELETE + UPDATE + INSERT 放在同一事务：中途失败会整体回滚，")
lines.append("-- 不会留下“测试点删了、题目描述却没更新”的半套状态。")
lines.append("-- 注意上面的 ALTER 属于 DDL，会隐式提交，所以只能放在事务外。")
lines.append("")
lines.append("-- 3. 重置 1-11 号题的测试点")
lines.append("--    先删后插而不是逐条 upsert：保证 sort_order 与当前生成结果完全一致，")
lines.append("--    重跑后不会残留上一版多出来的测试点。")
lines.append("DELETE FROM `test_case` WHERE `problem_id` BETWEEN 1 AND 11;")
lines.append("")
lines.append("-- 4. 题目描述与样例（样例与第 1 个测试点保持一致）")
lines.append("--    这里同时写回 problem.input / expected，是为了解题页的“示例”区块有内容展示；")
lines.append("--    它们与 CASES[pid][0] 同源，并由脚本开头的断言保证不会和测试点漂移。")
for pid in sorted(DESCRIPTIONS):
    first_input, first_expected, _ = CASES[pid][0]
    lines.append(f"UPDATE `problem` SET")
    lines.append(f"  `description` = {sql_str(DESCRIPTIONS[pid])},")
    lines.append(f"  `input` = {sql_str(first_input)},")
    lines.append(f"  `expected` = {sql_str(first_expected)},")
    lines.append(f"  `update_time` = NOW()")
    lines.append(f"WHERE `id` = {pid};")
    lines.append("")
lines.append("-- 5. 测试点")
lines.append("--    分数规则：每题 100 分均分到各组，余数优先给前几组（divmod）。")
lines.append("--    ACM 模式只看通过/失败，不看 score；OI 模式累加 AC 测试点的 score，")
lines.append("--    所以“每题 score 合计 = 100”是 OI 满分等于 100 的前提，改数据时必须保持。")
for pid in sorted(CASES):
    lines.append(f"-- problem {pid}: {len(CASES[pid])} 组，总分 100")
    lines.append("INSERT INTO `test_case` (`problem_id`, `input`, `expected`, `score`, `sort_order`) VALUES")
    rows = []
    count = len(CASES[pid])
    base, rem = divmod(100, count)          # 均分 100 分，余数给前几组
    for order, (inp, exp, note) in enumerate(CASES[pid], start=1):
        score = base + (1 if order <= rem else 0)
        rows.append(f"  ({pid}, {sql_str(inp)}, {sql_str(exp)}, {score}, {order})")
    assert sum(base + (1 if i <= rem else 0) for i in range(1, count + 1)) == 100
    lines.append(",\n".join(rows) + ";")
    lines.append("")
lines.append("COMMIT;")
lines.append("")
lines.append("-- 6. 结果概览（只读，用于确认每题测试点数是否齐全）")
lines.append("SELECT p.id, p.title, COUNT(t.id) AS cases FROM `problem` p")
lines.append("LEFT JOIN `test_case` t ON t.problem_id = p.id")
lines.append("GROUP BY p.id, p.title ORDER BY p.id;")
lines.append("")

with open(OUT_SQL, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

total = sum(len(v) for v in CASES.values())
print(f"written: {OUT_SQL}")
print(f"problems: {len(CASES)}, total cases: {total}")
for pid in sorted(CASES):
    lens = [len(i) for i, _, _ in CASES[pid] if i]
    print(f"  problem {pid:>2}: {len(CASES[pid]):>2} cases, max input {max(lens) if lens else 0} chars")
