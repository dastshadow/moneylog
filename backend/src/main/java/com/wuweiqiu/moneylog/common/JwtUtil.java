package com.wuweiqiu.moneylog.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类 —— 负责"发通行证"和"验通行证"。
 *
 * ============================================================
 *  【先搞懂：JWT 到底是什么？】
 * ============================================================
 *  全称 JSON Web Token。它是一串这样的字符串（长得像三段乱码，用 . 隔开）：
 *
 *      eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJ3dXdlaXFpdSJ9 . 4kQ9...
 *      └─── 第一段 ───┘      └────────── 第二段 ──────────┘      └ 第三段 ┘
 *          头部                    载荷（存的东西）                  签名
 *
 *  三段解开分别是：
 *    ① 头部：用什么算法签的（HS256）
 *    ② 载荷：你要存的内容，比如 {"sub":"1","username":"wuweiqiu","exp":1234567890}
 *    ③ 签名：把①②用一个"密钥"算出来的校验码
 *
 *  ============================================================
 *  【最关键的一句话：载荷是"明文"的，只是 Base64 编码，不是加密！】
 *  ============================================================
 *  任何人拿到 token，都能把中间那段解开看到里面的内容
 *  （浏览器 F12 里就能直接看，或者去 jwt.io 网站粘进去）。
 *
 *  所以：**绝对不要在 JWT 里放密码、手机号、身份证这类敏感信息。**
 *  它的价值不在"藏信息"，而在第三段签名：
 *      "内容一旦被改过一个字，签名就对不上，后端就能立刻发现。"
 *
 *  ============================================================
 *  【它解决什么问题？为什么不用 session？】
 *  ============================================================
 *  传统做法是登录后服务器存一份 session（"我记得你"）。
 *  但服务器要存东西 = 有状态，多台服务器时要共享，麻烦。
 *
 *  JWT 是**无状态**的：服务器不存任何东西。
 *  用户把 token 带在身上，每次请求给服务器看，
 *  服务器只要用密钥验一下签名对不对，就知道"这张证是不是我发的、有没有被改"。
 *
 *  代价是：**签发后无法撤销**（除非等它过期，或者再搞一个黑名单表）。
 *  这也是它最大的缺点，面试常问。
 * ============================================================
 */
@Component   // 交给 Spring 管理。因为它要靠 @Value 读配置，所以不能自己 new
public class JwtUtil {

    /**
     * 签名用的密钥。
     * 它的作用相当于"公章"—— 只有拿着这个密钥，才能签出合法 token，
     * 也只有拿着它才能验出签名对不对。
     */
    private final SecretKey key;

    /** token 有效期（毫秒）。从配置里读，方便调整 */
    private final long expireMillis;

    /**
     * 构造方法上就能用 @Value —— Spring 在创建这个 Bean 时会把配置值传进来。
     *
     * 【为什么密钥要写在配置文件里、而不是直接写死在代码里？】
     *   因为代码要提交到 Git，会被人看到。密钥泄露 = 任何人都能伪造 token =
     *   可以冒充任意用户登录你的系统。
     *   真实项目里它来自**环境变量**，不进代码库，而且每套环境不同。
     *
     * 【为什么不用 @Autowired 而用构造方法注入？】
     *   因为 key 和 expireMillis 是 final 的（创建后不可改），
     *   用构造方法能保证"对象一造出来就是完整的"，不会出现"半边初始化"的状态。
     *   这是 Spring 官方推荐的做法，叫"构造器注入"。
     *
     * 【HS256 对密钥长度有要求】
     *   HMAC-SHA256 要求密钥至少 256 位 = 32 字节。
     *   太短会直接抛 WeakKeyException。所以下面配置里那串密钥故意写得很长。
     */
    public JwtUtil(@Value("${moneylog.jwt.secret}") String secret,
                   @Value("${moneylog.jwt.expire-hours}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 60L * 60L * 1000L;
    }

    /**
     * 签发一张通行证。
     *
     * @param userId   用户 id —— 放进 subject（JWT 标准字段，意思是"这张证是谁的"）
     * @param username  用户名 —— 自定义字段，方便调试时肉眼看清是谁
     * @return 完整的一串 token 字符串
     */
    public String createToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                // subject：标准字段，放"主体标识"。这里放 userId 的字符串形式
                .subject(String.valueOf(userId))
                // claim：自定义字段，想放什么放什么（但别放敏感的）
                .claim("username", username)
                // 签发时间
                .issuedAt(now)
                // 过期时间。超时后 parseSignedClaims 会抛 ExpiredJwtException
                .expiration(new Date(now.getTime() + expireMillis))
                // 用密钥签名。只传 key 不传算法时，jjwt 会根据 key 长度自动选 HS256
                .signWith(key)
                // 打包成最终的字符串
                .compact();
    }

    /**
     * 验证 token 并取出用户 id。
     *
     * 这一行链式调用做了三件事，全都在"验"：
     *   ① 格式对不对（是不是合法的 JWT）
     *   ② 签名对不对（有没有被篡改、是不是我签的）
     *   ③ 过期了没有
     *
     * 任何一条不过都会**抛异常**，所以我们不用写 if 判断 —— 见下面的异常清单。
     *
     * @throws io.jsonwebtoken.ExpiredJwtException    token 过期
     * @throws io.jsonwebtoken.security.SignatureException  签名不对（被篡改，或用别的密钥签的）
     * @throws io.jsonwebtoken.MalformedJwtException  格式不对（乱传的一串字符）
     * @throws IllegalArgumentException               传了 null 或空串
     */
    public Long parseUserId(String token) {
        // Jwts.parser() 是 0.12 版本的新写法。老教程里的 parserBuilder() 已改名。
        Claims claims = Jwts.parser()
                .verifyWith(key)          // 用我们的密钥去验签名
                .build()
                .parseSignedClaims(token) // 解析并校验；校验失败会在这里抛异常
                .getPayload();            // 取出载荷（就是那段"存内容"的部分）
        // subject 里放的是 userId 的字符串形式，转回 Long
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 单独提供一个"尽量解析、失败不抛异常"的版本。
     * 为什么要它？见 UserController./me —— 那里我们想手写"取 token → 换身份"的过程，
     * 需要一个"不炸"的入口来演示。
     */
    public Long parseUserIdQuietly(String token) {
        try {
            return parseUserId(token);
        } catch (Exception e) {
            // 任何验证失败，都归为"这张证无效"
            return null;
        }
    }
}
