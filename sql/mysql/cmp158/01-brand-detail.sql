DELIMITER //
CREATE PROCEDURE cmp186_brand_switch()
BEGIN
    DECLARE canonical_count INT;
    DECLARE legacy_count INT;
    SELECT COUNT(*) INTO canonical_count FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='product_brand' AND column_name='show_in_detail';
    SELECT COUNT(*) INTO legacy_count FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='product_brand' AND column_name='show_on_detail';
    IF canonical_count=1 AND legacy_count=1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Both brand columns exist: reconcile under write freeze, do not overwrite';
    ELSEIF legacy_count=1 THEN
        ALTER TABLE product_brand RENAME COLUMN show_on_detail TO show_in_detail;
    ELSEIF canonical_count=0 THEN
        ALTER TABLE product_brand ADD COLUMN show_in_detail BIT(1) NOT NULL DEFAULT 0;
    END IF;
END//
DELIMITER ;
CALL cmp186_brand_switch();
DROP PROCEDURE cmp186_brand_switch;
