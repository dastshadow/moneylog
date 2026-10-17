package com.wuweiqiu.moneylog.service;

import com.wuweiqiu.moneylog.common.JwtUtil;
import com.wuweiqiu.moneylog.dto.LoginRequest;
import com.wuweiqiu.moneylog.dto.RegisterRequest;
import com.wuweiqiu.moneylog.entity.User;
import com.wuweiqiu.moneylog.mapper.UserMapper;
import com.wuweiqiu.moneylog.vo.LoginResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户业务层 —— 管"注册"和"登录"的规则。
 *
 * ============================================================
 *  【为什么这两件事必须放在 Service，而不是 Controller？】
 * ============================================================
 *  因为"注册"和"登录"都是**业务规则**，不是"收发包"：
 *    · 用户名不能重复（要查库）
 *    · 密码要加密（要调用加密工具）
 *    · 登录要签发 token（要调用 JwtUtil）
 *  这些东西跟 HTTP 无关。Controller 只负责"接住请求、把结果包起来"。
 *  这是 Day1 那张图里的分层职责，今天你会看到 Service 真正"有内容"了。
 * ============================================================
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    /**
     * 密码加密器。
     *
     * 【为什么这里直接 new，而不是注入？】
     *   因为它**无状态**（没有任何字段会被改），每次 new 出来的都一样能干活，
     *   不涉及"共享资源"，所以不需要交给 Spring 管理。
     *   大项目里更常见的是把它注册成一个 @Bean（好处是可以在一个地方统一配置强度），
     *   今天我们为了少建一个文件，就地 new。
     *
     * 【BCrypt 是什么？】
     *   一种专门为"存密码"设计的哈希算法。它有三个特质：
     *     ① 单向：能算出密文，无法从密文反推明文
     *     ② 加盐：同一个密码，每次加密出来的密文都不一样（盐值藏在密文里）
     *     ③ 故意慢：一次计算约 100ms。对用户来说无感，
     *        但对"暴力破解"来说就是灾难（试 100 万个密码要 27 小时）
     *
     *   第 ② 条很重要：如果不用盐，那么两个人密码相同 → 密文也相同，
     *   攻击者一看就知道"这俩人一个密码"，还能用"彩虹表"批量反推。
     *   加了盐之后，每个密文都是独一无二的。等下一验证你就看到了。
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public UserService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    // ================================================================
    //  注册
    // ================================================================
    public void register(RegisterRequest req) {
        // ---------- 校验1：用户名 ----------
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        //   规则：3-20 位，只能由 字母 / 数字 / 下划线 组成
        //   matches 是 String 自带的方法，参数是"正则表达式"
        //   正则里 ^ 表示开头、$ 表示结尾、[] 表示"这里面任意一个字符"、{3,20} 表示重复 3 到 20 次
        //   加上 ^ $ 是必须的！否则 "hacker!!!" 也能匹配成功（因为它含有一段合法的字符）
        if (!username.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new IllegalArgumentException("用户名必须是 3-20 位的字母、数字或下划线");
        }

        // ---------- 校验2：密码 ----------
        String password = req.getPassword() == null ? "" : req.getPassword();
        if (password.length() < 6 || password.length() > 30) {
            throw new IllegalArgumentException("密码长度必须在 6-30 位之间");
        }
        //   【一个真实项目里会做的事，我们今天先不做】
        //   上面只校验了长度。但 "123456" 长度合格、强度为零。
        //   生产系统通常还会：要求包含字母+数字、对比"弱密码字典"、
        //   检查"是否包含用户名"。这些留给后面的"完善体验"阶段。

        // ---------- 校验3：用户名是否已被占用 ----------
        //   注意：数据库上已经有唯一索引 uk_username 兜底，理论上不会重复。
        //   那为什么还要在这里查一次？—— 跟 Day6 讲"外键 + Service 校验"是同一个道理：
        //     数据库唯一索引 = 最后一道防线（一定会拦住，但报的是 SQL 异常，提示不友好）
        //     Service 校验   = 友好拦截（给出"用户名已被占用"这种人话）
        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已被占用，请换一个");
        }

        // ---------- 校验通过，组装并写入 ----------
        User user = new User();
        user.setUsername(username);
        //  ⭐⭐ 今天最关键的一行 ⭐⭐
        //  加密！从这一刻起，明文密码就"消失"了，程序里再也拿不回来。
        //  写进数据库的是 encode 返回的那串密文。
        user.setPassword(passwordEncoder.encode(password));

        //  昵称没填就用用户名兜底（用户体验：不填也有个显示名）
        String nickname = req.getNickname() == null ? "" : req.getNickname().trim();
        user.setNickname(nickname.isEmpty() ? username : nickname);

        userMapper.insert(user);
    }

    // ================================================================
    //  登录
    // ================================================================
    public LoginResult login(LoginRequest req) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        String password = req.getPassword() == null ? "" : req.getPassword();

        if (username.isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("请输入用户名和密码");
        }

        // ---------- 第1步：按用户名把用户查出来 ----------
        User user = userMapper.findByUsername(username);

        // ---------- 第2步：核对密码 ----------
        //  【超级重点：怎么核对？】
        //   数据库里存的是密文，我们没法"解密"它（BCrypt 是单向的）。
        //   所以做法是：**把用户这次输入的明文，用同样的算法再算一次，看结果对不对得上**。
        //
        //     matches(明文, 密文) = 把明文按密文里带的"盐"重新哈希，再跟密文比对
        //
        //   为什么不用 equals 直接比？因为加了盐 —— 同一个密码每次加密结果都不同，
        //   所以密文之间不能直接比。matches 方法内部会把盐取出来再用。
        //
        //  【安全细节：为什么"用户不存在"和"密码错误"给同一句提示？】
        //   如果分开提示，攻击者就能拿一份"用户名清单"一个个试，
        //   根据提示不同，**判断出哪些用户名真实存在**（这叫"用户名枚举"）。
        //   然后他就只需要猜密码了，成本大大降低。
        //   统一成"用户名或密码错误"，攻击者就什么信息都得不到。
        boolean ok = (user != null) && passwordEncoder.matches(password, user.getPassword());
        if (!ok) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // ---------- 第3步：签发通行证 ----------
        //  注意：这里**没有**往服务器存任何东西。签发完就结束。
        //  身份信息全部写进 token 让用户自己带着 —— 这就是"无状态"。
        String token = jwtUtil.createToken(user.getId(), user.getUsername());

        // ---------- 第4步：返回"只该给前端看"的部分 ----------
        //  注意这里用的是 LoginResult，**不是 User** —— 因为 User 里有 password 字段。
        //  如果直接 return user，密文就会跟着 JSON 一起发出去（见 LoginResult 里的说明）。
        return new LoginResult(token, user.getId(), user.getUsername(), user.getNickname());
    }

    // ================================================================
    //  按 id 查用户（给 /api/user/me 用）
    // ================================================================
    public User findById(Long id) {
        return userMapper.findById(id);
    }
}
