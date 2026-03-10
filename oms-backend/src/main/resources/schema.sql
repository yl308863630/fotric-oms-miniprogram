-- 创建数据库
CREATE DATABASE IF NOT EXISTS oms_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE oms_db;

-- 产品表
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '商品名称',
    barcode VARCHAR(100) COMMENT '条码',
    category_id BIGINT COMMENT '分类ID',
    list_price DECIMAL(15, 2) COMMENT '面价',
    dealer_price DECIMAL(15, 2) COMMENT '经销商出货价',
    sub_dealer_price DECIMAL(15, 2) COMMENT '二级经销商价格',
    stock INT DEFAULT 0 COMMENT '库存',
    supply_cycle VARCHAR(100) COMMENT '供货周期',
    is_discontinued BOOLEAN DEFAULT FALSE COMMENT '是否停产',
    ecommerce_link VARCHAR(500) COMMENT '电商链接',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='产品表';

-- 销售订单表
CREATE TABLE IF NOT EXISTS sales_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE COMMENT '订单号',
    buyer_name VARCHAR(255) NOT NULL COMMENT '购方名称',
    amount DECIMAL(15, 2) NOT NULL COMMENT '订单金额',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待处理, SHIPPED-已发货, COMPLETED-已完成',
    shipping_address TEXT COMMENT '收货地址',
    contact_person VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='销售订单表';

-- 对账结算单表
CREATE TABLE IF NOT EXISTS invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_no VARCHAR(50) NOT NULL UNIQUE COMMENT '对账单号',
    amount DECIMAL(15, 2) NOT NULL COMMENT '总金额',
    tax_amount DECIMAL(15, 2) NOT NULL COMMENT '税额',
    pre_tax_amount DECIMAL(15, 2) NOT NULL COMMENT '未税金额',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT-待提交, PENDING-待对账, CONFIRMED-已对账, SETTLED-已结清',
    buyer_name VARCHAR(255) NOT NULL COMMENT '购方名称',
    seller_name VARCHAR(255) NOT NULL COMMENT '销方名称',
    seller_code VARCHAR(50) COMMENT '销方编码',
    period VARCHAR(100) COMMENT '账期',
    invoice_no VARCHAR(100) COMMENT '发票号码',
    confirm_time DATETIME COMMENT '确认时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='对账结算单表';

-- 订单商品明细表
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES sales_orders(id)
) ENGINE=InnoDB COMMENT='订单商品明细表';
