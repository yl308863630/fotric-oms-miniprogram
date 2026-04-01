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

-- 隐私访问日志表（复制/下载/导出敏感信息时记录）
CREATE TABLE IF NOT EXISTS privacy_access_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(255) NOT NULL COMMENT '操作人姓名',
    client_ip VARCHAR(64) COMMENT '客户端IP',
    action VARCHAR(32) NOT NULL COMMENT 'COPY/DOWNLOAD/EXPORT',
    target_type VARCHAR(64) NOT NULL COMMENT 'SALES_ORDER/PARTNER/CONTRACT/USER/DELIVERY_NOTE/OPPORTUNITY',
    target_id VARCHAR(128) NOT NULL COMMENT '目标ID',
    field_or_description VARCHAR(500) COMMENT '涉及字段或描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_create_time (create_time),
    INDEX idx_operator (operator_name),
    INDEX idx_target (target_type, target_id)
) ENGINE=InnoDB COMMENT='隐私访问日志';

-- 登录验证码 IP 白名单
CREATE TABLE IF NOT EXISTS captcha_ip_allowlist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip_cidr VARCHAR(100) NOT NULL COMMENT '单 IP 或 CIDR',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    remark VARCHAR(255) COMMENT '备注',
    created_by VARCHAR(100) COMMENT '创建人',
    updated_by VARCHAR(100) COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_captcha_ip_allowlist_ip_cidr (ip_cidr),
    INDEX idx_captcha_ip_allowlist_enabled (enabled, update_time)
) ENGINE=InnoDB COMMENT='登录验证码IP白名单';

-- 平台授权主表
CREATE TABLE IF NOT EXISTS authorization_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    authorization_code VARCHAR(64) NOT NULL UNIQUE COMMENT '授权编码',
    platform_name VARCHAR(255) COMMENT '平台名称',
    grantor_name VARCHAR(255) COMMENT '授权方',
    grantee_name VARCHAR(255) COMMENT '被授权方',
    authorized_subject VARCHAR(255) COMMENT '授权主体',
    product_model TEXT COMMENT '具体产品型号',
    project_name TEXT COMMENT '具体项目',
    valid_from DATE COMMENT '生效日期',
    valid_to DATE COMMENT '失效日期',
    template_url VARCHAR(500) COMMENT '模板地址',
    word_url VARCHAR(500) COMMENT '生成Word地址',
    pdf_url VARCHAR(500) COMMENT '中间PDF地址',
    final_pdf_url VARCHAR(500) COMMENT '最终加密PDF地址',
    qr_url VARCHAR(500) COMMENT '二维码图片地址',
    verify_token TEXT COMMENT '验真token',
    document_sha256 VARCHAR(128) COMMENT '文件sha256',
    status VARCHAR(32) DEFAULT '草稿' COMMENT '状态',
    remark TEXT COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_by_name VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_auth_code (authorization_code),
    INDEX idx_auth_status (status),
    INDEX idx_auth_update_time (update_time)
) ENGINE=InnoDB COMMENT='平台授权记录';

-- 平台授权扫码日志
CREATE TABLE IF NOT EXISTS authorization_scan_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    authorization_record_id BIGINT NOT NULL COMMENT '授权记录ID',
    authorization_code VARCHAR(64) COMMENT '授权编码',
    verify_result VARCHAR(32) COMMENT '验真结果',
    token_fingerprint VARCHAR(128) COMMENT 'token指纹',
    channel VARCHAR(64) COMMENT '来源渠道',
    client_ip VARCHAR(64) COMMENT '扫码IP',
    user_agent TEXT COMMENT 'UA',
    geo_country VARCHAR(64) COMMENT '国家',
    geo_region VARCHAR(64) COMMENT '地区',
    geo_city VARCHAR(64) COMMENT '城市',
    operator_user_id BIGINT COMMENT '操作人ID',
    operator_username VARCHAR(64) COMMENT '操作用户名',
    operator_real_name VARCHAR(64) COMMENT '操作人姓名',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scan_auth_id (authorization_record_id),
    INDEX idx_scan_result (verify_result),
    INDEX idx_scan_create_time (create_time)
) ENGINE=InnoDB COMMENT='平台授权扫码日志';

-- 授权导入列映射模板（按用户保存）
CREATE TABLE IF NOT EXISTS authorization_import_mappings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mapping_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    mapping_json TEXT COMMENT '字段到表头的映射JSON',
    is_default BOOLEAN DEFAULT FALSE COMMENT '是否默认模板',
    created_by BIGINT NOT NULL COMMENT '创建人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_mapping_created_by (created_by),
    INDEX idx_mapping_default (created_by, is_default)
) ENGINE=InnoDB COMMENT='授权导入列映射模板';

-- 授权文档模板表
CREATE TABLE IF NOT EXISTS authorization_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(255) NOT NULL COMMENT '模板名称',
    description TEXT COMMENT '描述',
    template_url VARCHAR(500) NOT NULL COMMENT '模板地址',
    file_name VARCHAR(255) COMMENT '文件名',
    file_size BIGINT COMMENT '文件大小',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_authorization_template_name (template_name),
    INDEX idx_authorization_template_update_time (update_time)
) ENGINE=InnoDB COMMENT='平台授权模板';

-- 甲方预计回款规则
CREATE TABLE IF NOT EXISTS party_a_payment_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    party_a_title VARCHAR(255) NOT NULL COMMENT '甲方抬头',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    base_event_type VARCHAR(32) NOT NULL COMMENT '基准事件: DELIVERY_DATE/INVOICE_DATE/RECONCILIATION_DATE',
    base_day_of_month INT NULL COMMENT '基准落点日，如每月25日对账',
    cycle_cutoff_day INT NULL COMMENT '周期截点日，如25日前交货',
    carry_over_to_next_cycle BOOLEAN DEFAULT TRUE COMMENT '超过截点是否顺延下一周期',
    offset_days INT DEFAULT 0 COMMENT '基准后的偏移天数',
    payment_anchor_type VARCHAR(32) NOT NULL COMMENT '付款节点类型: NONE/FIXED_DAY_OF_NEXT_MONTH/INTERVAL_DAY_BUCKET',
    anchor_day_1 INT NULL COMMENT '付款节点日1',
    anchor_day_2 INT NULL COMMENT '付款节点日2',
    anchor_day_3 INT NULL COMMENT '付款节点日3',
    description TEXT COMMENT '规则说明',
    example_rule_text TEXT COMMENT '示例说明',
    created_by BIGINT NULL COMMENT '创建人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_party_a_payment_rule_title (party_a_title)
) ENGINE=InnoDB COMMENT='甲方预计回款规则';

ALTER TABLE sales_orders_new
    ADD COLUMN IF NOT EXISTS erp_entry_status VARCHAR(32) NULL COMMENT '商务ERP录单状态',
    ADD COLUMN IF NOT EXISTS erp_entry_screenshot_url VARCHAR(500) NULL COMMENT '商务ERP录单截图',
    ADD COLUMN IF NOT EXISTS erp_entry_operator VARCHAR(255) NULL COMMENT '商务ERP录单人',
    ADD COLUMN IF NOT EXISTS erp_entry_time DATETIME NULL COMMENT '商务ERP录单时间';

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS erp_entry_status VARCHAR(32) NULL COMMENT '商务ERP录单状态',
    ADD COLUMN IF NOT EXISTS erp_entry_screenshot_url VARCHAR(500) NULL COMMENT '商务ERP录单截图',
    ADD COLUMN IF NOT EXISTS erp_entry_operator VARCHAR(255) NULL COMMENT '商务ERP录单人',
    ADD COLUMN IF NOT EXISTS erp_entry_time DATETIME NULL COMMENT '商务ERP录单时间';

ALTER TABLE sales_orders_new
    ADD COLUMN IF NOT EXISTS settlement_url VARCHAR(500) NULL COMMENT '结算单附件URL',
    ADD COLUMN IF NOT EXISTS platform_reconciliation_url VARCHAR(500) NULL COMMENT '甲方对账单附件URL',
    ADD COLUMN IF NOT EXISTS platform_refund_url VARCHAR(500) NULL COMMENT '甲方回款附件URL';

ALTER TABLE sales_orders_new
    ADD COLUMN IF NOT EXISTS expected_refund_date DATE NULL COMMENT '预计回款日期',
    ADD COLUMN IF NOT EXISTS invoice_issued_date DATE NULL COMMENT '开票日期',
    ADD COLUMN IF NOT EXISTS reconciliation_date DATE NULL COMMENT '对账日期';

-- 客户结算状态流调整：已开票 -> 已开票待结算，已回款 -> 已结算
UPDATE sales_orders_new
SET status = '已开票待结算'
WHERE status = '已开票';

UPDATE sales_orders_new
SET status = '已结算'
WHERE status = '已回款';

-- 销售合并对账/开票/结算
CREATE TABLE IF NOT EXISTS sales_reconciliations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '销售对账单号',
    platform_name VARCHAR(255) COMMENT '甲方抬头',
    party_a_title VARCHAR(255) COMMENT '真实甲方抬头',
    operation_entity_title VARCHAR(255) COMMENT '销方主体',
    period_start DATE COMMENT '对账周期开始',
    period_end DATE COMMENT '对账周期结束',
    reconciliation_date DATE COMMENT '对账日期',
    status VARCHAR(32) NOT NULL DEFAULT '已对账' COMMENT '对账单状态',
    total_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '总金额',
    platform_reconciliation_no VARCHAR(128) COMMENT '甲方对账单号',
    attachment_url VARCHAR(500) COMMENT '附件地址',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sales_recon_platform (platform_name),
    INDEX idx_sales_recon_status (status)
) ENGINE=InnoDB COMMENT='销售对账单';

CREATE TABLE IF NOT EXISTS sales_reconciliation_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reconciliation_id BIGINT NOT NULL COMMENT '销售对账单ID',
    sales_order_id BIGINT NOT NULL COMMENT '销售订单ID',
    oms_order_no VARCHAR(128) COMMENT 'OMS订单号',
    platform_order_no VARCHAR(128) COMMENT '甲方订单号',
    platform_name VARCHAR(255) COMMENT '甲方抬头',
    order_status VARCHAR(64) COMMENT '订单状态快照',
    quantity INT COMMENT '数量',
    model VARCHAR(255) COMMENT '型号',
    product_name VARCHAR(255) COMMENT '商品名称',
    line_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '行金额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sales_recon_item_recon (reconciliation_id),
    INDEX idx_sales_recon_item_order (sales_order_id)
) ENGINE=InnoDB COMMENT='销售对账单明细';

CREATE TABLE IF NOT EXISTS sales_output_invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '销项发票单号',
    invoice_number VARCHAR(128) COMMENT '发票号码',
    platform_name VARCHAR(255) COMMENT '甲方抬头',
    party_a_title VARCHAR(255) COMMENT '真实甲方抬头',
    invoice_date DATE COMMENT '开票日期',
    status VARCHAR(32) NOT NULL DEFAULT '已开票' COMMENT '销项发票状态',
    total_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '总金额',
    attachment_url VARCHAR(500) COMMENT '附件地址',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sales_output_invoice_platform (platform_name),
    INDEX idx_sales_output_invoice_status (status)
) ENGINE=InnoDB COMMENT='销项发票';

CREATE TABLE IF NOT EXISTS sales_output_invoice_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL COMMENT '销项发票ID',
    reconciliation_id BIGINT NOT NULL COMMENT '销售对账单ID',
    reconciliation_bill_no VARCHAR(64) COMMENT '销售对账单号',
    platform_name VARCHAR(255) COMMENT '甲方抬头',
    line_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '金额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sales_output_invoice_item_invoice (invoice_id),
    INDEX idx_sales_output_invoice_item_recon (reconciliation_id)
) ENGINE=InnoDB COMMENT='销项发票明细';

CREATE TABLE IF NOT EXISTS sales_settlements_new (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '销售结算单号',
    platform_name VARCHAR(255) COMMENT '甲方抬头',
    party_a_title VARCHAR(255) COMMENT '真实甲方抬头',
    settlement_date DATE COMMENT '结算日期',
    status VARCHAR(32) NOT NULL DEFAULT '待回款' COMMENT '销售结算状态',
    refund_status VARCHAR(32) DEFAULT '未回款' COMMENT '回款状态',
    refund_date DATE COMMENT '回款日期',
    total_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '总金额',
    attachment_url VARCHAR(500) COMMENT '附件地址',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sales_settlement_platform (platform_name),
    INDEX idx_sales_settlement_status (status)
) ENGINE=InnoDB COMMENT='销售结算单';

CREATE TABLE IF NOT EXISTS sales_settlement_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    settlement_id BIGINT NOT NULL COMMENT '销售结算单ID',
    invoice_id BIGINT COMMENT '销项发票ID',
    invoice_bill_no VARCHAR(64) COMMENT '销项发票单号',
    reconciliation_id BIGINT COMMENT '销售对账单ID',
    reconciliation_bill_no VARCHAR(64) COMMENT '销售对账单号',
    line_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '金额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sales_settlement_item_settlement (settlement_id),
    INDEX idx_sales_settlement_item_invoice (invoice_id),
    INDEX idx_sales_settlement_item_recon (reconciliation_id)
) ENGINE=InnoDB COMMENT='销售结算单明细';

-- 采购合并对账/收票/结算
CREATE TABLE IF NOT EXISTS purchase_reconciliations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '采购对账单号',
    supplier VARCHAR(255) COMMENT '供应商',
    period_start DATE COMMENT '对账周期开始',
    period_end DATE COMMENT '对账周期结束',
    reconciliation_date DATE COMMENT '对账日期',
    status VARCHAR(32) NOT NULL DEFAULT '已对账' COMMENT '采购对账状态',
    total_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '总金额',
    attachment_url VARCHAR(500) COMMENT '附件地址',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_purchase_recon_supplier (supplier),
    INDEX idx_purchase_recon_status (status)
) ENGINE=InnoDB COMMENT='采购对账单';

CREATE TABLE IF NOT EXISTS purchase_reconciliation_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reconciliation_id BIGINT NOT NULL COMMENT '采购对账单ID',
    purchase_order_id BIGINT NOT NULL COMMENT '采购单ID',
    purchase_order_no VARCHAR(128) COMMENT '采购单号',
    oms_order_no VARCHAR(128) COMMENT '关联OMS订单号',
    supplier VARCHAR(255) COMMENT '供应商',
    order_status VARCHAR(64) COMMENT '采购单状态快照',
    quantity INT COMMENT '数量',
    model VARCHAR(255) COMMENT '型号',
    line_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '行金额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_purchase_recon_item_recon (reconciliation_id),
    INDEX idx_purchase_recon_item_order (purchase_order_id)
) ENGINE=InnoDB COMMENT='采购对账单明细';

CREATE TABLE IF NOT EXISTS purchase_input_invoices_new (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '进项发票单号',
    invoice_number VARCHAR(128) COMMENT '发票号码',
    supplier VARCHAR(255) COMMENT '供应商',
    invoice_date DATE COMMENT '收票日期',
    status VARCHAR(32) NOT NULL DEFAULT '已收票' COMMENT '进项发票状态',
    total_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '总金额',
    attachment_url VARCHAR(500) COMMENT '附件地址',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_purchase_input_invoice_supplier (supplier),
    INDEX idx_purchase_input_invoice_status (status)
) ENGINE=InnoDB COMMENT='进项发票';

CREATE TABLE IF NOT EXISTS purchase_input_invoice_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL COMMENT '进项发票ID',
    reconciliation_id BIGINT NOT NULL COMMENT '采购对账单ID',
    reconciliation_bill_no VARCHAR(64) COMMENT '采购对账单号',
    supplier VARCHAR(255) COMMENT '供应商',
    line_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '金额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_purchase_input_invoice_item_invoice (invoice_id),
    INDEX idx_purchase_input_invoice_item_recon (reconciliation_id)
) ENGINE=InnoDB COMMENT='进项发票明细';

CREATE TABLE IF NOT EXISTS purchase_settlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '采购结算单号',
    supplier VARCHAR(255) COMMENT '供应商',
    settlement_date DATE COMMENT '结算日期',
    status VARCHAR(32) NOT NULL DEFAULT '待付款' COMMENT '采购结算状态',
    payment_status VARCHAR(32) DEFAULT '待付款' COMMENT '付款状态',
    payment_date DATE COMMENT '付款日期',
    total_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '总金额',
    attachment_url VARCHAR(500) COMMENT '附件地址',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_purchase_settlement_supplier (supplier),
    INDEX idx_purchase_settlement_status (status)
) ENGINE=InnoDB COMMENT='采购结算单';

CREATE TABLE IF NOT EXISTS purchase_settlement_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    settlement_id BIGINT NOT NULL COMMENT '采购结算单ID',
    invoice_id BIGINT COMMENT '进项发票ID',
    invoice_bill_no VARCHAR(64) COMMENT '进项发票单号',
    reconciliation_id BIGINT COMMENT '采购对账单ID',
    reconciliation_bill_no VARCHAR(64) COMMENT '采购对账单号',
    line_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00 COMMENT '金额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_purchase_settlement_item_settlement (settlement_id),
    INDEX idx_purchase_settlement_item_invoice (invoice_id),
    INDEX idx_purchase_settlement_item_recon (reconciliation_id)
) ENGINE=InnoDB COMMENT='采购结算单明细';

-- 第一阶段：整单主单 / 分配 / SN 明细锚点
CREATE TABLE IF NOT EXISTS sales_order_masters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_no VARCHAR(64) NOT NULL UNIQUE COMMENT '整单主单号',
    root_oms_order_no VARCHAR(128) COMMENT '根OMS订单号',
    platform_order_no VARCHAR(128) COMMENT '平台订单号',
    party_a_title VARCHAR(255) COMMENT '真实甲方抬头',
    platform_name VARCHAR(255) COMMENT '平台/甲方显示名',
    master_status VARCHAR(32) DEFAULT '待执行' COMMENT '主单状态',
    assign_status VARCHAR(32) DEFAULT '未指派' COMMENT '指派状态',
    contract_status VARCHAR(32) DEFAULT '未签约' COMMENT '合同状态',
    finance_status VARCHAR(32) DEFAULT '未开始' COMMENT '财务状态',
    total_line_count INT DEFAULT 0 COMMENT '总商品行数',
    total_quantity INT DEFAULT 0 COMMENT '总数量',
    created_by BIGINT COMMENT '创建人ID',
    creator VARCHAR(255) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sales_order_master_root_oms (root_oms_order_no),
    INDEX idx_sales_order_master_platform_order (platform_order_no),
    INDEX idx_sales_order_master_party_a (party_a_title),
    INDEX idx_sales_order_master_status (master_status)
) ENGINE=InnoDB COMMENT='销售整单主单';

CREATE TABLE IF NOT EXISTS sales_order_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL COMMENT '主单ID',
    sales_order_id BIGINT NOT NULL COMMENT '销售订单行ID',
    allocation_no VARCHAR(64) NOT NULL UNIQUE COMMENT '分配单号',
    root_allocation_id BIGINT COMMENT '根分配ID',
    parent_allocation_id BIGINT COMMENT '父分配ID',
    hop_no INT DEFAULT 1 COMMENT '跳数',
    assigned_company_title VARCHAR(255) COMMENT '被分配主体抬头',
    assigned_user_id BIGINT COMMENT '被分配用户ID',
    assigned_username VARCHAR(255) COMMENT '被分配用户名',
    allocated_qty INT DEFAULT 0 COMMENT '分配数量',
    planned_delivery_date DATE COMMENT '计划交期',
    allocation_status VARCHAR(32) DEFAULT '待指派' COMMENT '分配状态',
    is_chain_tail BOOLEAN DEFAULT TRUE COMMENT '是否链尾',
    source_type VARCHAR(64) COMMENT '来源类型',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sales_order_allocation_master (master_id),
    INDEX idx_sales_order_allocation_order (sales_order_id),
    INDEX idx_sales_order_allocation_root (root_allocation_id),
    INDEX idx_sales_order_allocation_parent (parent_allocation_id),
    INDEX idx_sales_order_allocation_assigned_user (assigned_user_id),
    INDEX idx_sales_order_allocation_status (allocation_status)
) ENGINE=InnoDB COMMENT='销售订单执行分配';

CREATE TABLE IF NOT EXISTS sales_serial_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT COMMENT '主单ID',
    sales_order_id BIGINT NOT NULL COMMENT '销售订单行ID',
    allocation_id BIGINT COMMENT '分配ID',
    batch_id BIGINT COMMENT '发货批次ID',
    sn_code VARCHAR(255) NOT NULL UNIQUE COMMENT '序列号',
    product_model VARCHAR(255) COMMENT '商品型号',
    serial_status VARCHAR(32) DEFAULT 'CREATED' COMMENT '序列号状态',
    bind_time DATETIME COMMENT '绑定时间',
    unbind_time DATETIME COMMENT '解绑时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sales_serial_item_master (master_id),
    INDEX idx_sales_serial_item_order (sales_order_id),
    INDEX idx_sales_serial_item_allocation (allocation_id),
    INDEX idx_sales_serial_item_batch (batch_id),
    INDEX idx_sales_serial_item_status (serial_status)
) ENGINE=InnoDB COMMENT='销售序列号明细';

ALTER TABLE sales_orders_new
    ADD COLUMN IF NOT EXISTS master_id BIGINT NULL COMMENT '整单主单ID',
    ADD COLUMN IF NOT EXISTS allocation_id BIGINT NULL COMMENT '执行分配ID',
    ADD COLUMN IF NOT EXISTS line_no INT NULL COMMENT '行号',
    ADD COLUMN IF NOT EXISTS line_status VARCHAR(32) NULL COMMENT '行状态',
    ADD COLUMN IF NOT EXISTS is_master_primary_line BOOLEAN NULL COMMENT '是否主单主行';

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS master_id BIGINT NULL COMMENT '整单主单ID',
    ADD COLUMN IF NOT EXISTS allocation_id BIGINT NULL COMMENT '执行分配ID',
    ADD COLUMN IF NOT EXISTS source_sales_order_id BIGINT NULL COMMENT '来源销售订单ID',
    ADD COLUMN IF NOT EXISTS order_flow_hop_id BIGINT NULL COMMENT '订单流转跳点ID',
    ADD COLUMN IF NOT EXISTS finance_flow_hop_id BIGINT NULL COMMENT '财务流转跳点ID',
    ADD COLUMN IF NOT EXISTS merge_selection_key VARCHAR(64) NULL COMMENT '合并指派选择键',
    ADD COLUMN IF NOT EXISTS merged_sales_order_ids TEXT NULL COMMENT '合并指派销售订单ID集合';

ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS master_id BIGINT NULL COMMENT '整单主单ID',
    ADD COLUMN IF NOT EXISTS allocation_id BIGINT NULL COMMENT '执行分配ID',
    ADD COLUMN IF NOT EXISTS contract_scope VARCHAR(32) NULL COMMENT '合同范围 MASTER/ALLOCATION',
    ADD COLUMN IF NOT EXISTS merge_selection_key VARCHAR(64) NULL COMMENT '合并指派选择键',
    ADD COLUMN IF NOT EXISTS merged_sales_order_ids TEXT NULL COMMENT '合并指派销售订单ID集合';
