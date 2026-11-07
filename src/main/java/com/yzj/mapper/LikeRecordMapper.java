package com.yzj.mapper;

import com.yzj.pojo.LikeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 点赞记录。
 *
 * 写入路径上 Redis 的 SADD 是"快闸"（先去重），这张表是"真相源"（可恢复）。
 * 两者都要写，但顺序不能反 —— 见 LikeServiceImpl。
 */
@Mapper
public interface LikeRecordMapper {

    /**
     * 插入点赞记录，重复则忽略。
     *
     * 用 INSERT IGNORE 而不是先查后插：
     * 先查后插在并发下有窗口（两个请求都查到"没赞过"，然后都插），
     * 虽然 uk_like 会挡住第二个，但会抛 DuplicateKeyException 需要捕获；
     * INSERT IGNORE 直接把这件事交给数据库，一条语句、无异常。
     *
     * ⚠️ 返回受影响行数：1 表示真的插入了，0 表示已存在。
     * 这是"MySQL 侧"的幂等信号，与 Redis SADD 的返回值互为双保险。
     */
    int insertIgnore(@Param("targetType") Integer targetType,
                     @Param("targetId") Integer targetId,
                     @Param("userId") Integer userId);

    int delete(@Param("targetType") Integer targetType,
               @Param("targetId") Integer targetId,
               @Param("userId") Integer userId);

    /** 某目标的真实点赞数 —— 自愈时用它覆盖冗余字段 */
    int countByTarget(@Param("targetType") Integer targetType,
                      @Param("targetId") Integer targetId);

    /**
     * 批量查"当前用户赞过这批目标中的哪些"。
     *
     * 列表页要用：一次 IN 查询拿全，不要循环查。
     */
    List<Integer> findLikedTargetIds(@Param("targetType") Integer targetType,
                                     @Param("targetIds") List<Integer> targetIds,
                                     @Param("userId") Integer userId);

    /** 全部点赞记录（重建 Redis 用，量小才可用） */
    List<LikeRecord> findAllByTargetType(@Param("targetType") Integer targetType);

    /** 某个目标的全部点赞记录 —— 重建该目标的 Redis Set 用 */
    List<LikeRecord> findByTarget(@Param("targetType") Integer targetType,
                                  @Param("targetId") Integer targetId);
}
