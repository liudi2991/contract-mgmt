-- ========================================================================
-- 合同回款管理系统 初始化脚本 V1
-- 创建：9 张表 + 初始 sys_param + 默认管理员
-- ========================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------
-- 1. 系统用户
-- ----------------------------------------------------------------
CREATE TABLE `sys_user` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`        VARCHAR(50)  NOT NULL                            COMMENT '登录账号',
  `password_hash`   VARCHAR(100) NOT NULL                            COMMENT 'BCrypt 哈希',
  `name`            VARCHAR(50)  NOT NULL                            COMMENT '姓名',
  `email`           VARCHAR(100) DEFAULT NULL,
  `role`            VARCHAR(20)  NOT NULL                            COMMENT 'SALES / ADMIN',
  `status`          TINYINT      NOT NULL DEFAULT 1                  COMMENT '1=启用 0=停用',
  `must_change_pwd` TINYINT      NOT NULL DEFAULT 1                  COMMENT '首次登录强制改密',
  `last_login_at`   DATETIME     DEFAULT NULL,
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by`      BIGINT       DEFAULT NULL,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by`      BIGINT       DEFAULT NULL,
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- ----------------------------------------------------------------
-- 2. 合同
-- ----------------------------------------------------------------
CREATE TABLE `contract` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `contract_no`      VARCHAR(32)   NOT NULL                          COMMENT '合同编号 HT-YYYY-XXXX',
  `name`             VARCHAR(100)  NOT NULL                          COMMENT '合同名称',
  `customer_name`    VARCHAR(100)  NOT NULL                          COMMENT '客户名称（自由文本）',
  `amount`           DECIMAL(18,2) NOT NULL                          COMMENT '合同总金额',
  `currency`         VARCHAR(8)    NOT NULL DEFAULT 'CNY',
  `sign_date`        DATE          NOT NULL                          COMMENT '签订日期',
  `effective_date`   DATE          NOT NULL                          COMMENT '生效日期',
  `expire_date`      DATE          NOT NULL                          COMMENT '到期日期',
  `owner_id`         BIGINT UNSIGNED NOT NULL                        COMMENT '负责人',
  `status`           VARCHAR(20)   NOT NULL DEFAULT 'EXECUTING'      COMMENT 'EXECUTING/COMPLETED/EXPIRED/VOIDED',
  `void_reason`      VARCHAR(500)  DEFAULT NULL,
  `paid_amount`      DECIMAL(18,2) NOT NULL DEFAULT 0                COMMENT '已回款金额（冗余）',
  `remaining_amount` DECIMAL(18,2) NOT NULL DEFAULT 0                COMMENT '应收余额（冗余）',
  `remark`           VARCHAR(500)  DEFAULT NULL,
  `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by`       BIGINT        NOT NULL,
  `updated_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by`       BIGINT        NOT NULL,
  `deleted`          TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_no` (`contract_no`, `deleted`),
  KEY `idx_owner`     (`owner_id`,     `sign_date`),
  KEY `idx_customer`  (`customer_name`),
  KEY `idx_status`    (`status`,       `expire_date`),
  KEY `idx_sign_date` (`sign_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同主表';

-- ----------------------------------------------------------------
-- 3. 回款计划期次
-- ----------------------------------------------------------------
CREATE TABLE `payment_plan_item` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `contract_id`     BIGINT UNSIGNED NOT NULL,
  `period_no`       INT           NOT NULL                           COMMENT '期次（从 1 起）',
  `planned_date`    DATE          NOT NULL                           COMMENT '计划回款日期',
  `planned_amount`  DECIMAL(18,2) NOT NULL,
  `received_amount` DECIMAL(18,2) NOT NULL DEFAULT 0                 COMMENT '该期已核销回款',
  `status`          VARCHAR(20)   NOT NULL DEFAULT 'UNPAID'          COMMENT 'UNPAID/PARTIAL/SETTLED',
  `remark`          VARCHAR(200)  DEFAULT NULL,
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_period` (`contract_id`, `period_no`, `deleted`),
  KEY `idx_planned_date` (`planned_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回款计划期次';

-- ----------------------------------------------------------------
-- 4. 实际回款流水
-- ----------------------------------------------------------------
CREATE TABLE `payment` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `contract_id`     BIGINT UNSIGNED NOT NULL,
  `plan_item_id`    BIGINT UNSIGNED DEFAULT NULL                     COMMENT '关联期次，为空表示自动核销',
  `received_date`   DATE          NOT NULL                           COMMENT '实际到账日期',
  `amount`          DECIMAL(18,2) NOT NULL,
  `payer`           VARCHAR(100)  DEFAULT NULL,
  `bank_serial`     VARCHAR(64)   DEFAULT NULL                       COMMENT '银行流水号',
  `remark`          VARCHAR(500)  DEFAULT NULL,
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by`      BIGINT        NOT NULL,
  `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by`      BIGINT        NOT NULL,
  `deleted`         TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_contract`      (`contract_id`, `received_date`),
  KEY `idx_received_date` (`received_date`),
  KEY `idx_plan_item`     (`plan_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实际回款流水';

-- ----------------------------------------------------------------
-- 5. 通用附件
-- ----------------------------------------------------------------
CREATE TABLE `attachment` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `owner_type`   VARCHAR(20)   NOT NULL                              COMMENT 'CONTRACT / PAYMENT',
  `owner_id`     BIGINT UNSIGNED NOT NULL,
  `file_name`    VARCHAR(255)  NOT NULL                              COMMENT '原始文件名',
  `object_key`   VARCHAR(500)  NOT NULL                              COMMENT 'MinIO 对象 Key',
  `content_type` VARCHAR(100)  NOT NULL,
  `size`         BIGINT        NOT NULL,
  `md5`          CHAR(32)      DEFAULT NULL,
  `uploaded_by`  BIGINT UNSIGNED NOT NULL,
  `uploaded_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`      TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`owner_type`, `owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件';

-- ----------------------------------------------------------------
-- 6. 审计日志
-- ----------------------------------------------------------------
CREATE TABLE `audit_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED DEFAULT NULL,
  `username`    VARCHAR(50)  DEFAULT NULL                            COMMENT '冗余，便于查询',
  `ip`          VARCHAR(45)  DEFAULT NULL,
  `module`      VARCHAR(30)  NOT NULL                                COMMENT 'CONTRACT / PAYMENT / USER ...',
  `action`      VARCHAR(30)  NOT NULL                                COMMENT 'CREATE / UPDATE / DELETE / LOGIN ...',
  `target_type` VARCHAR(30)  DEFAULT NULL,
  `target_id`   VARCHAR(64)  DEFAULT NULL,
  `before_json` JSON         DEFAULT NULL,
  `after_json`  JSON         DEFAULT NULL,
  `result`      VARCHAR(10)  NOT NULL DEFAULT 'SUCCESS'              COMMENT 'SUCCESS / FAIL',
  `error_msg`   VARCHAR(500) DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time`   (`user_id`, `created_at`),
  KEY `idx_module_time` (`module`,  `created_at`),
  KEY `idx_target`      (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- ----------------------------------------------------------------
-- 7. 系统参数
-- ----------------------------------------------------------------
CREATE TABLE `sys_param` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `param_key`   VARCHAR(50)  NOT NULL,
  `param_value` VARCHAR(500) NOT NULL,
  `description` VARCHAR(200) DEFAULT NULL,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by`  BIGINT UNSIGNED DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_param_key` (`param_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数';

-- ----------------------------------------------------------------
-- 8. 导出记录
-- ----------------------------------------------------------------
CREATE TABLE `export_record` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `ip`           VARCHAR(45)   NOT NULL,
  `export_type`  VARCHAR(30)   NOT NULL                              COMMENT 'CONTRACT / PAYMENT / AUDIT_LOG',
  `filter_json`  JSON          DEFAULT NULL,
  `row_count`    INT           NOT NULL DEFAULT 0,
  `file_md5`     CHAR(32)      DEFAULT NULL,
  `file_name`    VARCHAR(255)  DEFAULT NULL,
  `status`       VARCHAR(20)   NOT NULL                              COMMENT 'RUNNING / SUCCESS / FAIL',
  `error_msg`    VARCHAR(500)  DEFAULT NULL,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at`  DATETIME      DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出记录';

-- ----------------------------------------------------------------
-- 9. 站内消息
-- ----------------------------------------------------------------
CREATE TABLE `notification` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `type`       VARCHAR(30)  NOT NULL                                 COMMENT 'CONTRACT_EXPIRING / PAYMENT_OVERDUE / EXPORT_DONE',
  `title`      VARCHAR(200) NOT NULL,
  `content`    VARCHAR(1000) DEFAULT NULL,
  `target_url` VARCHAR(255) DEFAULT NULL,
  `is_read`    TINYINT      NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_at`    DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_unread` (`user_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

-- ================================================================
-- 初始数据
-- ================================================================

-- 默认管理员：admin / Admin@1234（首次登录需改密）
INSERT INTO `sys_user` (`username`, `password_hash`, `name`, `role`, `status`, `must_change_pwd`)
VALUES ('admin', '$2b$10$wRYGRBS/H3FggkPDoGhYFOxKLDDuLb68G1xzbHpkAWcaUjxuKtt.S', '系统管理员', 'ADMIN', 1, 1);

-- 系统参数
INSERT INTO `sys_param` (`param_key`, `param_value`, `description`) VALUES
('contract.no.prefix',                'HT',        '合同编号前缀'),
('contract.no.serial_length',         '4',         '流水位数'),
('contract.no.reset_yearly',          'true',      '是否按年重置'),
('contract.remind.days',              '30,15,7,1', '到期提醒节点（天）'),
('attachment.max_size_mb',            '50',        '单文件上限（MB）'),
('attachment.max_count_per_contract', '20',        '单合同附件数量上限'),
('export.daily_limit_per_admin',      '5',         '管理员每日导出次数'),
('storage.alert_threshold_percent',   '80',        '存储告警阈值（%）');
