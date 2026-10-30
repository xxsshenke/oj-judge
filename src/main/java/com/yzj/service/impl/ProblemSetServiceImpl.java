package com.yzj.service.impl;

import com.yzj.mapper.ProblemSetMapper;
import com.yzj.pojo.ProblemSetItem;
import com.yzj.pojo.ProblemSetOverview;
import com.yzj.pojo.TagCount;
import com.yzj.service.ProblemSetService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题库页聚合逻辑。
 *
 * 数据只来自一次 SQL：ProblemSetMapper.findProblems 把题目左连接提交表，
 * 在数据库里算好每题的提交数、通过数、是否通过、是否尝试过。
 * 概览里的各类计数和标签计数都基于这份结果在内存里再聚合，
 * 原因是题库规模很小（十余题、几十条提交），多查几条 count SQL 反而更慢，
 * 也更难保证“概览”和“列表”用的是同一时刻、同一口径的数据。
 */
@Service
public class ProblemSetServiceImpl implements ProblemSetService {

    private final ProblemSetMapper problemSetMapper;

    public ProblemSetServiceImpl(ProblemSetMapper problemSetMapper) {
        this.problemSetMapper = problemSetMapper;
    }

    @Override
    public List<ProblemSetItem> problems(String keyword, Integer difficulty, String tag) {
        // 前端清空输入框时会传空串，这里统一转成 null，
        // 让 Mapper 的 <if> 直接跳过该条件，避免拼出 like '%%' 或 find_in_set('', ...)。
        return problemSetMapper.findProblems(trimToNull(keyword), difficulty, trimToNull(tag));
    }

    @Override
    public ProblemSetOverview overview() {
        // 不带任何筛选查全量：概览统计的是整个题库，不能受页面上当前筛选条件影响。
        List<ProblemSetItem> all = problemSetMapper.findProblems(null, null, null);

        ProblemSetOverview overview = new ProblemSetOverview();
        overview.setTotal(all.size());
        overview.setEasyCount(countByDifficulty(all, 1));
        overview.setMediumCount(countByDifficulty(all, 2));
        overview.setHardCount(countByDifficulty(all, 3));
        // solved 是“历史上有过至少一次 AC”，不是“最后一次提交通过”。
        // 因此同一题重复 AC 只算一道已通过，与列表里的对勾含义一致。
        overview.setSolvedCount((int) all.stream().filter(item -> Boolean.TRUE.equals(item.getSolved())).count());
        overview.setEasySolved(countSolvedByDifficulty(all, 1));
        overview.setMediumSolved(countSolvedByDifficulty(all, 2));
        overview.setHardSolved(countSolvedByDifficulty(all, 3));
        overview.setTags(buildTagCounts(all));
        overview.setDaily(pickDaily(all));
        return overview;
    }

    /** 按难度统计题目总数；difficulty 为空的历史数据不计入任何一档。 */
    private int countByDifficulty(List<ProblemSetItem> problems, int difficulty) {
        return (int) problems.stream()
                .filter(item -> item.getDifficulty() != null && item.getDifficulty() == difficulty)
                .count();
    }

    /** 按难度统计已通过题目数，口径与 countByDifficulty 保持一致。 */
    private int countSolvedByDifficulty(List<ProblemSetItem> problems, int difficulty) {
        return (int) problems.stream()
                .filter(item -> item.getDifficulty() != null && item.getDifficulty() == difficulty)
                .filter(item -> Boolean.TRUE.equals(item.getSolved()))
                .count();
    }

    /**
     * 把每题的 "数组,哈希表" 拆开，统计每个标签覆盖多少题。
     *
     * tags 在库里是英文逗号分隔的字符串（见 sql/problemset.sql），
     * 所以这里只能由应用层拆分；先用 LinkedHashMap 保持首次出现顺序，
     * 再按“题数降序 + 标签名升序”排序，保证返回顺序稳定、前端标签条不会乱跳。
     */
    private List<TagCount> buildTagCounts(List<ProblemSetItem> problems) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (ProblemSetItem problem : problems) {
            if (problem.getTags() == null) continue;
            for (String raw : problem.getTags().split(",")) {
                String name = raw.trim();
                if (!name.isEmpty()) {
                    counter.merge(name, 1, Integer::sum);
                }
            }
        }
        List<TagCount> tags = new ArrayList<>();
        counter.forEach((name, count) -> tags.add(new TagCount(name, count)));
        tags.sort(Comparator.comparingInt(TagCount::getCount).reversed()
                .thenComparing(TagCount::getName));
        return tags;
    }

    /**
     * 每日一题：用“距 1970-01-01 的天数 % 题目数”取下标。
     *
     * 这个算法不落库、不看用户完成状态，只保证：
     *   1. 同一天内多次打开题库页，看到的是同一道题；
     *   2. 相邻两天尽量落在不同题目上（题数大于 1 时一定不同）。
     *
     * 注意：题目增删会改变 all.size()，当天的题可能因此变化；
     * 如果要“优先推荐没做过的题”，需要把用户维度和完成状态传进来，
     * 当前没有登录体系，所以这里刻意保持成纯日期轮换。
     */
    private ProblemSetItem pickDaily(List<ProblemSetItem> all) {
        if (all.isEmpty()) return null;
        int index = (int) (LocalDate.now().toEpochDay() % all.size());
        return all.get(index);
    }

    /** 空白字符串统一视为“没有传这个筛选条件”，避免空串进入 SQL 条件。 */
    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
