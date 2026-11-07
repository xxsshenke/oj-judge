package com.yzj.service.impl;

import com.yzj.exception.BusinessException;
import com.yzj.mapper.ProblemMapper;
import com.yzj.mapper.SolutionCommentMapper;
import com.yzj.mapper.SolutionMapper;
import com.yzj.mapper.UserMapper;
import com.yzj.pojo.LikeRecord;
import com.yzj.pojo.PageResult;
import com.yzj.pojo.Problem;
import com.yzj.pojo.Solution;
import com.yzj.pojo.SolutionDetailVO;
import com.yzj.pojo.SolutionSaveRequest;
import com.yzj.pojo.SolutionVO;
import com.yzj.pojo.User;
import com.yzj.service.LikeService;
import com.yzj.service.SolutionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SolutionServiceImpl implements SolutionService {

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限，与 SubmissionServiceImpl 保持一致 */
    private static final int MAX_PAGE_SIZE = 50;

    /** 列表摘要长度（字符） */
    private static final int EXCERPT_LENGTH = 200;

    private static final int TITLE_MAX = 100;
    private static final int CONTENT_MAX = 10000;

    private final SolutionMapper solutionMapper;
    private final SolutionCommentMapper commentMapper;
    private final ProblemMapper problemMapper;
    private final UserMapper userMapper;
    private final LikeService likeService;

    public SolutionServiceImpl(SolutionMapper solutionMapper,
                               SolutionCommentMapper commentMapper,
                               ProblemMapper problemMapper,
                               UserMapper userMapper,
                               LikeService likeService) {
        this.solutionMapper = solutionMapper;
        this.commentMapper = commentMapper;
        this.problemMapper = problemMapper;
        this.userMapper = userMapper;
        this.likeService = likeService;
    }

    @Override
    public PageResult<SolutionVO> pageByProblem(Integer problemId, String sort,
                                                int pageNum, int pageSize,
                                                Integer currentUserId) {
        if (problemId == null) {
            throw new BusinessException("题目 ID 不能为空");
        }

        int safePageSize = normalizePageSize(pageSize);
        int safePageNum = Math.max(pageNum, 1);

        long total = solutionMapper.countByProblem(problemId);
        if (total == 0) {
            return PageResult.empty(safePageNum, safePageSize);
        }

        long pages = (total + safePageSize - 1) / safePageSize;
        if (safePageNum > pages) {
            safePageNum = (int) pages;   // 回退到最后一页，避免 total>0 但 records 为空的矛盾
        }

        String safeSort = normalizeSort(sort);
        int offset = (safePageNum - 1) * safePageSize;
        List<Solution> rows = solutionMapper.findPageByProblem(problemId, safeSort, offset, safePageSize);

        // 三条批量查询，条数固定 —— 不随列表长度增长
        Map<Integer, User> authors = loadUsers(rows.stream().map(Solution::getUserId).toList());
        Set<Integer> likedIds = likeService.likedTargetIds(
                LikeRecord.TYPE_SOLUTION, rows.stream().map(Solution::getId).toList(), currentUserId);

        List<SolutionVO> records = rows.stream()
                .map(s -> toVO(s, authors.get(s.getUserId()), likedIds.contains(s.getId()), currentUserId))
                .toList();

        return PageResult.of(records, total, safePageNum, safePageSize);
    }

    @Override
    public SolutionDetailVO detail(Integer id, Integer currentUserId) {
        Solution solution = requireSolution(id);

        User author = userMapper.findById(solution.getUserId());
        Problem problem = problemMapper.findById(solution.getProblemId());

        SolutionDetailVO vo = new SolutionDetailVO();
        vo.setId(solution.getId());
        vo.setProblemId(solution.getProblemId());
        vo.setProblemTitle(problem == null ? null : problem.getTitle());
        vo.setTitle(solution.getTitle());
        vo.setContent(solution.getContent());
        vo.setUserId(solution.getUserId());
        vo.setNickname(displayName(author));
        vo.setAvatar(author == null ? null : author.getAvatar());
        vo.setLikeCount(solution.getLikeCount());
        vo.setCommentCount(solution.getCommentCount());
        vo.setLiked(currentUserId != null
                && likeService.likedTargetIds(LikeRecord.TYPE_SOLUTION,
                        List.of(solution.getId()), currentUserId).contains(solution.getId()));
        vo.setMine(currentUserId != null && currentUserId.equals(solution.getUserId()));
        vo.setCreateTime(solution.getCreateTime());
        vo.setUpdateTime(solution.getUpdateTime());
        return vo;
    }

    @Override
    public SolutionDetailVO create(Integer problemId, SolutionSaveRequest req, Integer userId) {
        if (problemId == null) {
            throw new BusinessException("题目 ID 不能为空");
        }
        Problem problem = problemMapper.findById(problemId);
        if (problem == null) {
            throw new BusinessException("题目不存在：" + problemId);
        }

        String title = validateTitle(req.title());
        String content = validateContent(req.content());

        // 一人一题限一篇。
        // ⚠️ 这里查的是"包括已软删的"，因为 uk_solution_problem_user 不区分 status ——
        // 如果只查 status=0 就放行，插入时会撞唯一索引，用户看到的是一句莫名其妙的服务器错误。
        Solution exist = solutionMapper.findByProblemAndUser(problemId, userId);
        if (exist != null) {
            throw new BusinessException(exist.getStatus() != null && exist.getStatus() == 1
                    ? "你在此题下的题解已删除，无法重复发布"
                    : "你在此题下已有题解，请直接编辑");
        }

        Solution solution = new Solution();
        solution.setProblemId(problemId);
        solution.setUserId(userId);
        solution.setTitle(title);
        solution.setContent(content);

        try {
            solutionMapper.insert(solution);
        } catch (DuplicateKeyException e) {
            // 并发下两个人同时提交（或双击），上面的预检查会同时通过，
            // 最后由唯一索引兜住 —— 转成同一句人话，不把 SQL 异常抛给前端
            throw new BusinessException("你在此题下已有题解，请直接编辑");
        }

        return detail(solution.getId(), userId);
    }

    @Override
    public SolutionDetailVO update(Integer id, SolutionSaveRequest req, Integer userId) {
        Solution solution = requireSolution(id);

        if (!solution.getUserId().equals(userId)) {
            throw new BusinessException("只能修改自己的题解");
        }

        String title = validateTitle(req.title());
        String content = validateContent(req.content());

        solutionMapper.updateContent(id, title, content);
        return detail(id, userId);
    }

    @Override
    public void delete(Integer id, Integer userId) {
        Solution solution = requireSolution(id);

        if (!solution.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的题解");
        }

        solutionMapper.softDelete(id);
        // 题解删了，其下评论一并软删：评论区随题解一起不可访问，
        // 留着 status=0 的评论只会在以后做数据统计时制造迷惑
        commentMapper.softDeleteBySolutionId(id);
    }

    /* ==================== 内部方法 ==================== */

    /** 查不到就给统一文案：已删除和不存在对用户是同一种情况，不暴露"曾经存在" */
    private Solution requireSolution(Integer id) {
        if (id == null) {
            throw new BusinessException("题解 ID 不能为空");
        }
        Solution solution = solutionMapper.findById(id);
        if (solution == null) {
            throw new BusinessException("题解不存在：" + id);
        }
        return solution;
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("标题不能为空");
        }
        String trimmed = title.trim();
        if (trimmed.length() > TITLE_MAX) {
            throw new BusinessException("标题不能超过 " + TITLE_MAX + " 个字符");
        }
        return trimmed;
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("内容不能为空");
        }
        if (content.length() > CONTENT_MAX) {
            throw new BusinessException("内容不能超过 " + CONTENT_MAX + " 个字符");
        }
        return content;
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 排序收敛。
     *
     * ⚠️ 外部传进来的 sort 绝不能直接拼进 SQL —— 那是注入入口。
     * 这里映射到两个固定值，非法值一律走 new。
     */
    private String normalizeSort(String sort) {
        return "hot".equalsIgnoreCase(sort) ? "hot" : "new";
    }

    private Map<Integer, User> loadUsers(Collection<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        // 去重：一页里同一个作者可能有多条
        List<Integer> distinct = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userMapper.findByIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    private SolutionVO toVO(Solution s, User author, boolean liked, Integer currentUserId) {
        SolutionVO vo = new SolutionVO();
        vo.setId(s.getId());
        vo.setProblemId(s.getProblemId());
        vo.setTitle(s.getTitle());
        vo.setExcerpt(excerpt(s.getContent()));
        vo.setUserId(s.getUserId());
        vo.setNickname(displayName(author));
        vo.setAvatar(author == null ? null : author.getAvatar());
        vo.setLikeCount(s.getLikeCount());
        vo.setCommentCount(s.getCommentCount());
        vo.setLiked(liked);
        vo.setMine(currentUserId != null && currentUserId.equals(s.getUserId()));
        vo.setCreateTime(s.getCreateTime());
        return vo;
    }

    /** 摘要：超长截断加省略号。按字符截，不按字节 —— 中文按字节切会出乱码 */
    private String excerpt(String content) {
        if (content == null) return null;
        String flat = content.replace("\r\n", "\n").trim();
        return flat.length() <= EXCERPT_LENGTH ? flat : flat.substring(0, EXCERPT_LENGTH) + "…";
    }

    /** 昵称兜底：没设昵称就显示用户名，否则前端会渲染出 "null" */
    private String displayName(User user) {
        if (user == null) return "已注销用户";
        return user.getNickname() != null && !user.getNickname().isBlank()
                ? user.getNickname() : user.getUsername();
    }}
