package com.yzj.pojo;

/**
 * 点赞 / 取消点赞的返回。
 *
 * 为什么同时返回 liked 和 likeCount：
 * 前端要做乐观更新，点一下立刻变红；但数字若由前端自己 +1，
 * 别人同时点赞时这个数字就是错的。所以数字以后端返回为准，前端直接覆盖。
 *
 * @param liked     操作后当前用户是否已赞
 * @param likeCount 操作后的权威点赞数
 */
public record LikeResult(boolean liked, int likeCount) {
}
