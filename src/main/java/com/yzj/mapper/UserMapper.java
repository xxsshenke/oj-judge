package com.yzj.mapper;

import com.yzj.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface UserMapper {

    User findById(@Param("id") Integer id);

    /**
     * 批量查用户，只带出展示用字段（id / nickname / avatar）。
     *
     * 题解列表和评论列表都要"给 N 条内容补作者昵称"。
     * 循环调 findById 就是 N+1 —— 一页 10 条评论 + 10 条回复
     * 就是 20 次往返，必须一次 IN 查完。
     *
     * ⚠️ collection 必须判空：MyBatis 的 foreach 遇到空集合会生成
     * "in ()"，SQL 语法错误。调用方要先 if 判空再调。
     */
    List<User> findByIds(@Param("ids") Collection<Integer> ids);

    /**
     * 登录用：按用户名查，需要带出 password 字段做校验。
     * User.password 上的 @JsonIgnore 只影响返回给前端，
     * 不影响 MyBatis 把列映射进对象。
     */
    User findByUsername(@Param("username") String username);

    /**
     * 按手机号查。手机号注册 / 验证码登录用。
     *
     * 和 findByUsername 分开，不复用：
     * 手机号是唯一索引上的另一个键，混在一个方法里要靠调用方猜传的是哪个。
     */
    User findByPhone(@Param("phone") String phone);

    /**
     * 新增用户，回填自增 id 到入参对象。
     *
     * ⚠️ 调用方必须保证 password 非 null —— 数据库那一列是 NOT NULL，
     *    传 null 会报 ERROR 1048。手机号注册的账号传空串 ""。
     */
    int insert(User user);
}
