package com.wuweiqiu.moneylog.mapper;

import com.wuweiqiu.moneylog.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表的数据访问接口。
 *
 * 跟 AccountMapper / BillMapper 一样：**只有方法声明，没有实现**，
 * 实现由 MyBatis 在运行时动态生成（动态代理）。
 *
 * 今天只加两个最必要的方法：
 *   findByUsername —— 注册时查重 + 登录时查用户，都要用它
 *   insert         —— 注册时写入
 */
@Mapper
public interface UserMapper {

    /**
     * 按用户名查用户，查不到返回 null。
     *
     * 这一个方法在今天的代码里被用了两次，用途不同：
     *   · 注册时：判断"用户名是否已被占用"
     *   · 登录时：取出用户，才能比对密码
     *
     * 【注意】这里查出来的 User 对象里，password 是**密文**，
     *   因为数据库那一列存的就是密文。
     */
    User findByUsername(String username);

    /**
     * 按主键 id 查用户，查不到返回 null。
     *
     * 给谁用的？给 /api/user/me 用 —— 那里已经从 token 里拿到了 userId，
     * 需要再回数据库取出用户名、昵称返回给前端。
     *
     * 【这就是"无状态鉴权"的完整链路，今天你会亲眼走一遍】
     *   token（自己身上带着） → 验签名 → 取出 userId → 查数据库 → 得到用户信息
     */
    User findById(Long id);

    /**
     * 新增用户。参数里的 password **必须是已经加密好的密文**，
     * 加密动作在 UserService 里做（绝不能让明文流到这一层）。
     */
    int insert(User user);
}
