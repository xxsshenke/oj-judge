package com.yzj.service.impl;

import com.yzj.exception.BusinessException;
import com.yzj.mapper.SolutionCommentMapper;
import com.yzj.mapper.SolutionMapper;
import com.yzj.mapper.UserMapper;
import com.yzj.pojo.CommentCreateRequest;
import com.yzj.pojo.CommentVO;
import com.yzj.pojo.LikeRecord;
import com.yzj.pojo.PageResult;
import com.yzj.pojo.Solution;
import com.yzj.pojo.SolutionComment;
import com.yzj.pojo.User;
import com.yzj.service.CommentService;
import com.yzj.service.LikeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论实现 —— 两层结构（主评论 + 回复，回复可互回但都平铺在第二层）。
 *
 * <h3>rootId 的赋值规则（这一处写错，整棵树就散架）</h3>
 * <pre>
 *   主评论        parentId=null         rootId=null
 *   回复主评论    parentId=主评论id      rootId=主评论id
 *   回复的回复    parentId=被回复者id    rootId=被回复者的rootId  ← 不是被回复者的id！
 * </pre>
 *
 * <h3>为什么列表查询没有 N+1</h3>
 * 固定 5 次查询，与数据量无关：
 * <ol>
 *   <li>count 主评论</li>
 *   <li>分页查主评论</li>
 *   <li>一条 IN 查这批主评论的全部回复</li>
 *   <li>一条 IN 查所有涉及的作者昵称</li>
 *   <li>一条 IN 查当前用户赞过哪些</li>
 * </ol>
 */
@Service
public class CommentServiceImpl implements CommentService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int CONTENT_MAX = 500;

    /**
     * 每条主评论最多内嵌的回复数。
     *
     * 不截断的话，一条有 200 回复的主评论会把页面卡死。
     * 截断在 Java 里做，不在 SQL 里 —— SQL 的 LIMIT 是全局的，
     * 做不到"每个主评论各取 N 条"（除非上窗口函数，一期不值得）。
     */
    private static final int MAX_REPLIES_PER_ROOT = 10;

    private final SolutionCommentMapper commentMapper;
    private final SolutionMapper solutionMapper;
    private final UserMapper userMapper;
    private final LikeService likeService;

    public CommentServiceImpl(SolutionCommentMapper commentMapper,
                              SolutionMapper solutionMapper,
                              UserMapper userMapper,
                              LikeService likeService) {
        this.commentMapper = commentMapper;
        this.solutionMapper = solutionMapper;
        this.userMapper = userMapper;
        this.likeService = likeService;
    }

    @Override
    public PageResult<CommentVO> pageBySolution(Integer solutionId, int pageNum, int pageSize,
                                                Integer currentUserId) {
        if (solutionId == null) {
            throw new BusinessException("题解 ID 不能为空");
        }
        // 题解不存在就不必查评论了；顺便防止对着已删除的题解刷接口
        requireSolution(solutionId);

        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        if (pageSize <= 0) safePageSize = DEFAULT_PAGE_SIZE;
        int safePageNum = Math.max(pageNum, 1);

        long total = commentMapper.countMain(solutionId);
        if (total == 0) {
            return PageResult.empty(safePageNum, safePageSize);
        }

        long pages = (total + safePageSize - 1) / safePageSize;
        if (safePageNum > pages) {
            safePageNum = (int) pages;
        }

        int offset = (safePageNum - 1) * safePageSize;
        List<SolutionComment> roots = commentMapper.findMainPage(solutionId, offset, safePageSize);
        if (roots.isEmpty()) {
            return PageResult.empty(safePageNum, safePageSize);
        }

        // ② 一条 IN 查全部回复（含已软删的 —— 它们的父评论还引用着它们）
        List<Integer> rootIds = roots.stream().map(SolutionComment::getId).toList();
        List<SolutionComment> allReplies = commentMapper.findRepliesByRootIds(rootIds);

        Map<Integer, List<SolutionComment>> repliesByRoot = allReplies.stream()
                .collect(Collectors.groupingBy(SolutionComment::getRootId));

        // ③ 收集所有涉及的 userId（含 replyToUser），一次查昵称
        Set<Integer> userIds = new LinkedHashSet<>();
        roots.forEach(c -> collectUserIds(c, userIds));
        allReplies.forEach(c -> collectUserIds(c, userIds));
        Map<Integer, User> users = loadUsers(userIds);

        // ④ 批量判断 liked
        List<Integer> allIds = new ArrayList<>();
        roots.forEach(c -> allIds.add(c.getId()));
        allReplies.forEach(c -> allIds.add(c.getId()));
        Set<Integer> likedIds = likeService.likedTargetIds(
                LikeRecord.TYPE_COMMENT, allIds, currentUserId);

        // ⑤ 组装
        List<CommentVO> records = roots.stream()
                .map(root -> toRootVO(root, repliesByRoot.getOrDefault(root.getId(), List.of()),
                        users, likedIds, currentUserId))
                .toList();

        return PageResult.of(records, total, safePageNum, safePageSize);
    }

    @Override
    public CommentVO create(Integer solutionId, CommentCreateRequest req, Integer userId) {
        if (solutionId == null) {
            throw new BusinessException("题解 ID 不能为空");
        }
        requireSolution(solutionId);

        String content = validateContent(req.content());

        SolutionComment comment = new SolutionComment();
        comment.setSolutionId(solutionId);
        comment.setUserId(userId);
        comment.setContent(content);

        if (req.parentId() == null) {
            // 主评论
            comment.setParentId(null);
            comment.setRootId(null);
            comment.setReplyToUser(null);
        } else {
            SolutionComment parent = commentMapper.findById(req.parentId());
            if (parent == null) {
                throw new BusinessException("回复的评论不存在");
            }
            // ⚠️ 必须校验父子同属一个题解。
            //    不校验的话，可以 POST /solutions/1/comments {parentId: 属于题解2的评论}
            //    → 题解 1 的评论区里出现一条"回复题解 2 的评论"，数据就串了。
            //    文案和"不存在"保持一致，不泄露别处数据的存在性。
            if (!solutionId.equals(parent.getSolutionId())) {
                throw new BusinessException("回复的评论不存在");
            }

            comment.setParentId(parent.getId());
            // ⭐ rootId：主评论的 rootId 是 null，所以回复主评论时取 parent.getId()；
            //    回复回复时取 parent.getRootId()。两者合一就是「parent.rootId ?? parent.id」。
            comment.setRootId(parent.getRootId() != null ? parent.getRootId() : parent.getId());
            comment.setReplyToUser(parent.getUserId());
        }

        commentMapper.insert(comment);

        // 冗余计数 +1（回复也算，因为用户说"7 条评论"指的是总共 7 条发言）
        solutionMapper.incrementCommentCount(solutionId);

        // ⚠️ 必须回读一次，不能直接用上面的 comment 对象组装 VO：
        //    createTime / update_time 是数据库 DEFAULT CURRENT_TIMESTAMP 生成的，
        //    like_count 也是数据库默认值 0 —— insert 之后实体里这几个字段仍是 null。
        //    直接返回会让前端拿到 createTime=null，时间显示成空白。
        SolutionComment saved = commentMapper.findById(comment.getId());
        if (saved == null) {
            throw new BusinessException("评论发表失败，请重试");
        }

        // 返回单条，前端可直接插到对应位置，不用整页刷新
        Set<Integer> userIds = collectUserIds(saved, new LinkedHashSet<>());
        Map<Integer, User> users = loadUsers(userIds);
        CommentVO vo = toReplyVO(saved, users, Set.of(), userId);
        vo.setReplies(null);
        return vo;
    }

    @Override
    public void delete(Integer id, Integer userId) {
        if (id == null) {
            throw new BusinessException("评论 ID 不能为空");
        }
        SolutionComment comment = commentMapper.findById(id);
        if (comment == null) {
            throw new BusinessException("评论不存在：" + id);
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的评论");
        }

        commentMapper.softDelete(id);
        solutionMapper.decrementCommentCount(comment.getSolutionId());
    }

    /* ==================== 内部方法 ==================== */

    private void requireSolution(Integer solutionId) {
        Solution solution = solutionMapper.findById(solutionId);
        if (solution == null) {
            throw new BusinessException("题解不存在：" + solutionId);
        }
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("评论内容不能为空");
        }
        if (content.length() > CONTENT_MAX) {
            throw new BusinessException("评论不能超过 " + CONTENT_MAX + " 个字符");
        }
        return content.trim();
    }

    private Set<Integer> collectUserIds(SolutionComment c, Set<Integer> into) {
        if (c.getUserId() != null) into.add(c.getUserId());
        if (c.getReplyToUser() != null) into.add(c.getReplyToUser());
        return into;
    }

    private Map<Integer, User> loadUsers(Collection<Integer> ids) {
        List<Integer> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userMapper.findByIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    private CommentVO toRootVO(SolutionComment root,
                               List<SolutionComment> replies,
                               Map<Integer, User> users,
                               Set<Integer> likedIds,
                               Integer currentUserId) {
        CommentVO vo = baseVO(root, users, likedIds, currentUserId);

        // 已软删的回复也要保留（前端显示"该回复已删除"占位），
        // 但 replyCount 只统计未删除的 —— 否则"查看全部 N 条回复"点开少几条
        List<SolutionComment> visible = replies.stream()
                .filter(r -> r.getStatus() == null || r.getStatus() == 0)
                .sorted(Comparator.comparing(SolutionComment::getId))
                .toList();

        List<CommentVO> replyVOs = replies.stream()
                .sorted(Comparator.comparing(SolutionComment::getId))
                .limit(MAX_REPLIES_PER_ROOT)
                .map(r -> toReplyVO(r, users, likedIds, currentUserId))
                .toList();

        vo.setReplies(replyVOs);
        vo.setReplyCount(visible.size());
        vo.setRepliesTruncated(visible.size() > replyVOs.size());
        return vo;
    }

    private CommentVO toReplyVO(SolutionComment c,
                                Map<Integer, User> users,
                                Set<Integer> likedIds,
                                Integer currentUserId) {
        CommentVO vo = baseVO(c, users, likedIds, currentUserId);
        vo.setReplies(null);
        vo.setReplyCount(0);
        vo.setRepliesTruncated(false);

        if (c.getReplyToUser() != null) {
            vo.setReplyToNickname(displayName(users.get(c.getReplyToUser())));
        }
        return vo;
    }

    private CommentVO baseVO(SolutionComment c,
                             Map<Integer, User> users,
                             Set<Integer> likedIds,
                             Integer currentUserId) {
        boolean deleted = c.getStatus() != null && c.getStatus() == 1;
        User author = users.get(c.getUserId());

        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setSolutionId(c.getSolutionId());
        vo.setParentId(c.getParentId());
        vo.setRootId(c.getRootId());

        // ⭐ 已删评论的内容必须在后端置空，绝不能指望前端过滤 ——
        //    前端一旦漏判，删掉的内容就直接显示出来了
        vo.setContent(deleted ? null : c.getContent());

        vo.setUserId(c.getUserId());
        vo.setNickname(displayName(author));
        vo.setAvatar(author == null ? null : author.getAvatar());
        vo.setLikeCount(c.getLikeCount());
        vo.setLiked(likedIds.contains(c.getId()));
        vo.setDeleted(deleted);
        vo.setMine(currentUserId != null && currentUserId.equals(c.getUserId()));
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }

    private String displayName(User user) {
        if (user == null) return "已注销用户";
        return user.getNickname() != null && !user.getNickname().isBlank()
                ? user.getNickname() : user.getUsername();
    }
}
