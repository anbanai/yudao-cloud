ALTER TABLE member_user ADD COLUMN recovered_floor_level_id bigint DEFAULT NULL
    COMMENT '已恢复的等级下限，普通成长值重算使用';
