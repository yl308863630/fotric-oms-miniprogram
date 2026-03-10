USE oms_db;

ALTER TABLE contracts 
ADD COLUMN IF NOT EXISTS party_b_representative VARCHAR(255) 
COMMENT '乙方代表姓名';
