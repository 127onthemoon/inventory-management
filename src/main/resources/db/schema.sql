-- =====================================================================
-- 高并发库存管理系统 建表脚本（MySQL 8.x / InnoDB / utf8mb4）
-- 应用启动时由 Spring Boot (spring.sql.init) 自动执行；全部使用
-- CREATE TABLE IF NOT EXISTS，可重复执行，不会清空已有数据。
-- =====================================================================

-- 用户表：管理员 ADMIN / 操作员 OPERATOR
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    real_name   VARCHAR(50)  NOT NULL COMMENT '姓名',
    role        VARCHAR(20)  NOT NULL COMMENT 'ADMIN / OPERATOR',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- 商品分类
CREATE TABLE IF NOT EXISTS category (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

-- 供应商
CREATE TABLE IF NOT EXISTS supplier (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL,
    contact_person VARCHAR(50)  NULL,
    phone          VARCHAR(30)  NULL,
    email          VARCHAR(100) NULL,
    address        VARCHAR(255) NULL,
    remark         VARCHAR(255) NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商';

-- 商品：stock 列是并发控制的核心，CHECK 约束作为“最后一道防线”保证库存永不为负
CREATE TABLE IF NOT EXISTS product (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    code              VARCHAR(50)    NOT NULL COMMENT '商品编码（唯一）',
    name              VARCHAR(100)   NOT NULL,
    category_id       BIGINT         NOT NULL,
    supplier_id       BIGINT         NULL COMMENT '默认供应商',
    unit              VARCHAR(20)    NOT NULL DEFAULT '件',
    price             DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    stock             INT            NOT NULL DEFAULT 0 COMMENT '当前库存，只能经入库/出库变更',
    warning_threshold INT            NOT NULL DEFAULT 10 COMMENT '低库存预警阈值',
    status            INT            NOT NULL DEFAULT 1 COMMENT '1在售 0停用',
    created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_code (code),
    KEY idx_product_category (category_id),
    KEY idx_product_supplier (supplier_id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_product_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT ck_product_stock_non_negative CHECK (stock >= 0),
    CONSTRAINT ck_product_threshold_non_negative CHECK (warning_threshold >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品';

-- 入库单：order_no 与 request_id 均唯一；request_id 用于防重复提交（幂等）
CREATE TABLE IF NOT EXISTS stock_in_order (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(40)    NOT NULL COMMENT '入库单号',
    request_id   VARCHAR(64)    NOT NULL COMMENT '请求幂等号',
    product_id   BIGINT         NOT NULL,
    supplier_id  BIGINT         NULL,
    quantity     INT            NOT NULL,
    unit_price   DECIMAL(12, 2) NULL,
    before_stock INT            NOT NULL,
    after_stock  INT            NOT NULL,
    operator     VARCHAR(50)    NOT NULL,
    remark       VARCHAR(255)   NULL,
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_in_order_no (order_no),
    UNIQUE KEY uk_in_request_id (request_id),
    KEY idx_in_product (product_id),
    KEY idx_in_created (created_at),
    CONSTRAINT fk_in_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_in_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT ck_in_quantity_positive CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库单';

-- 出库单
CREATE TABLE IF NOT EXISTS stock_out_order (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(40)  NOT NULL COMMENT '出库单号',
    request_id   VARCHAR(64)  NOT NULL COMMENT '请求幂等号',
    product_id   BIGINT       NOT NULL,
    quantity     INT          NOT NULL,
    customer     VARCHAR(100) NULL COMMENT '去向/客户',
    before_stock INT          NOT NULL,
    after_stock  INT          NOT NULL,
    operator     VARCHAR(50)  NOT NULL,
    remark       VARCHAR(255) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_out_order_no (order_no),
    UNIQUE KEY uk_out_request_id (request_id),
    KEY idx_out_product (product_id),
    KEY idx_out_created (created_at),
    CONSTRAINT fk_out_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_out_quantity_positive CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库单';

-- 库存流水：每一次库存变动一条，记录变动前后库存与关联单据
CREATE TABLE IF NOT EXISTS stock_flow (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    product_id   BIGINT      NOT NULL,
    change_type  VARCHAR(10) NOT NULL COMMENT 'IN 入库 / OUT 出库',
    quantity     INT         NOT NULL COMMENT '变动数量（正数）',
    before_stock INT         NOT NULL,
    after_stock  INT         NOT NULL,
    ref_order_no VARCHAR(40) NOT NULL COMMENT '关联单据号',
    operator     VARCHAR(50) NOT NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_ref_order (ref_order_no),
    KEY idx_flow_product (product_id, id),
    KEY idx_flow_created (created_at),
    CONSTRAINT fk_flow_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_flow_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_flow_after_non_negative CHECK (after_stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水';
