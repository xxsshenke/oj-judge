package com.yzj.pojo;

/**
 * 发表评论的请求体。
 *
 * @param parentId 回复哪条评论；发表主评论时为 null。
 *                 服务端会校验它属于路径里的那道题解 —— 否则可以把
 *                 别的题解的评论"搬"过来，数据就串了。
 */
public record CommentCreateRequest(String content, Integer parentId) {
}
