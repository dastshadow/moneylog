package com.wuweiqiu.moneylog.controller;

import com.wuweiqiu.moneylog.common.AuthInterceptor;
import com.wuweiqiu.moneylog.common.Result;
import com.wuweiqiu.moneylog.entity.Bill;
import com.wuweiqiu.moneylog.service.BillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账单接口层。
 *
 * 【跟 AccountController 的结构完全一样】
 *   类级 /api/bill  +  方法级 /list、/create
 *   所以完整地址是：
 *     GET  /api/bill/list      查账单列表
 *     POST /api/bill/create    记一笔账
 *
 * 看出来了吧？**加一个新功能模块，就是把同一套骨架再搭一遍。**
 * 这就是为什么前几天要先把 Account 这套走通 ——
 * 现在你只需要关心"账单特有的东西"（连表查询、外键校验），
 * 骨架部分不用再重新思考了。
 */
@RestController
@RequestMapping("/api/bill")
public class BillController {

    @Autowired
    private BillService billService;

    /**
     * 查询账单列表。
     * 访问方式：http://localhost:8080/api/bill/list
     *
     * 返回的每条账单里会多一个 accountName 字段（账户名称），
     * 那是连表查询带回来的，前端可以直接显示，不用再查一次账户表。
     */
    @GetMapping("/list")
    public Result<List<Bill>> list(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId) {
        // 【Day11】加上了"我是谁" —— 现在查到的是"我的账单"
        return Result.ok(billService.findAll(userId));
    }

    /**
     * 记一笔账。
     * 访问方式：POST http://localhost:8080/api/bill/create
     *
     * 请求体示例：
     *   {
     *     "accountId": 1,
     *     "type": 1,
     *     "amount": 35.00,
     *     "category": "餐饮",
     *     "billDate": "2026-10-10",
     *     "remark": "午饭"
     *   }
     *
     * 【注意 billDate 的格式】
     *   前端传的是字符串 "2026-10-10"，而后端要的是 LocalDate 对象。
     *   这中间的转换由 Jackson 自动完成，它默认认的就是这种
     *   "年-月-日"的格式（ISO 标准）。
     *   如果传成 "2026/10/10"（用斜杠），Jackson 认不出来，
     *   会直接报错 —— 请求根本进不到我们的方法里。
     *   到时候你会看到 400 错误，日志里是 JSON 解析失败的提示。
     */
    @PostMapping("/create")
    public Result<Void> create(@RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long userId,
                              @RequestBody Bill bill) {
        // 【Day11】又是这两个来源：
        //    userId ← 服务器验签得到的身份（可信）
        //    bill   ← 客户端发来的数据（不可信，里面可能塞了 userId）
        //   谁说了算？Service 里强制覆盖成 userId。
        billService.create(userId, bill);
        return Result.ok();
    }
}
