-- ============================================================
--  MoneyLog 建库建表脚本
--  MySQL 8.0+
--
--  用法：在命令行或 DataGrip 里执行
--      mysql -uroot -p < schema.sql
--  或在 DataGrip 里全选后 Ctrl+Enter
--
--  【建表顺序很重要】
--    user → account → bill
--    因为 account.user_id 和 bill.user_id 都有外键指向 user(id)，
--    被引用的表必须先存在，否则报 ERROR 1215 / 1452
-- ============================================================

CREATE DATABASE IF NOT EXISTS moneylog
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE moneylog;

-- ------------------------------------------------------------
--  用户表
-- ------------------------------------------------------------
CREATE TABLE `user` (
  `id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`   varchar(30) NOT NULL                COMMENT '登录用户名，全表唯一',
  `password`   varchar(100) NOT NULL               COMMENT '密码（存 BCrypt 密文，绝不存明文）',
  `nickname`   varchar(30) DEFAULT NULL            COMMENT '昵称，仅用于显示',
  `created_at` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '用户表';

-- ------------------------------------------------------------
--  账户表
--    balance 用 DECIMAL 而不是 DOUBLE：
--      浮点数存钱会丢精度（0.1 + 0.2 ≠ 0.3），金额必须用定点数
-- ------------------------------------------------------------
CREATE TABLE `account` (
  `id`         bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `user_id`    bigint        NOT NULL                COMMENT '所属用户ID',
  `name`       varchar(50)   NOT NULL                COMMENT '账户名称，如微信钱包、支付宝',
  `balance`    decimal(12,2) DEFAULT '0.00'          COMMENT '账户余额（元）',
  `created_at` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_account_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '账户表';

-- ------------------------------------------------------------
--  账单表
--    amount 存【正数】，收支方向由 type 决定（1=支出，2=收入）
--    bill_date 是"哪一天的钱"，created_at 是"什么时候录的"——两者必须分开
-- ------------------------------------------------------------
CREATE TABLE `bill` (
  `id`         bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`    bigint        NOT NULL                COMMENT '所属用户ID',
  `account_id` bigint        NOT NULL                COMMENT '所属账户ID，指向 account.id',
  `type`       tinyint       NOT NULL                COMMENT '类型：1=支出，2=收入',
  `amount`     decimal(12,2) NOT NULL                COMMENT '金额（存正数，方向由 type 决定）',
  `category`   varchar(20)   NOT NULL                COMMENT '分类：餐饮/交通/购物/工资等',
  `bill_date`  date          NOT NULL                COMMENT '账单日期（哪一天的钱）',
  `remark`     varchar(200)  DEFAULT NULL            COMMENT '备注',
  `created_at` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_account_id` (`account_id`),
  KEY `idx_bill_date` (`bill_date`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_bill_account` FOREIGN KEY (`account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_bill_user`    FOREIGN KEY (`user_id`)    REFERENCES `user` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '账单表';
