-- 为products表添加产品配置和产品保修期字段
USE oms_db;

-- 添加产品配置字段
ALTER TABLE products ADD COLUMN IF NOT EXISTS product_config VARCHAR(500) COMMENT '产品配置' AFTER specs;

-- 添加产品保修期字段
ALTER TABLE products ADD COLUMN IF NOT EXISTS warranty_period VARCHAR(100) COMMENT '产品保修期' AFTER product_config;

-- 为sales_orders_new表添加产品配置和产品保修期字段
ALTER TABLE sales_orders_new ADD COLUMN IF NOT EXISTS product_config VARCHAR(500) COMMENT '产品配置' AFTER model;
ALTER TABLE sales_orders_new ADD COLUMN IF NOT EXISTS warranty_period VARCHAR(100) COMMENT '产品保修期' AFTER product_config;

-- 为contracts表添加产品配置和产品保修期字段（如果还没有的话）
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS product_config VARCHAR(500) COMMENT '产品配置' AFTER product_model;
ALTER TABLE contracts ADD COLUMN IF NOT EXISTS warranty_period VARCHAR(100) COMMENT '产品保修期' AFTER product_config;
