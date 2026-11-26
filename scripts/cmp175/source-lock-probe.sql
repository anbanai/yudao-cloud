SELECT VERSION(), @@transaction_isolation;
EXPLAIN SELECT id FROM member_point_record WHERE tenant_id=1 AND youzan_recovery_source='701';
EXPLAIN SELECT id FROM member_point_record WHERE tenant_id=1 AND youzan_recovery_source='702';
START TRANSACTION;
SELECT id FROM member_youzan_point_snapshot WHERE id=701 FOR UPDATE;
SELECT 'CAPTURED_SOURCE', COUNT(*) FROM performance_schema.data_locks
WHERE THREAD_ID=PS_CURRENT_THREAD_ID() AND OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME='member_youzan_point_snapshot' AND LOCK_TYPE='RECORD';
SELECT id FROM member_point_record WHERE tenant_id=1 AND youzan_recovery_source='701';
SELECT 'RECORD_LOCKS_AFTER_HIT', COUNT(*) FROM performance_schema.data_locks
WHERE THREAD_ID=PS_CURRENT_THREAD_ID() AND OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME='member_point_record' AND LOCK_TYPE='RECORD';
SELECT id FROM member_point_record WHERE tenant_id=1 AND youzan_recovery_source='702';
SELECT 'RECORD_LOCKS_AFTER_MISS', COUNT(*) FROM performance_schema.data_locks
WHERE THREAD_ID=PS_CURRENT_THREAD_ID() AND OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME='member_point_record' AND LOCK_TYPE='RECORD';
SELECT OBJECT_NAME, INDEX_NAME, LOCK_TYPE, LOCK_MODE, LOCK_DATA FROM performance_schema.data_locks
WHERE THREAD_ID=PS_CURRENT_THREAD_ID() AND OBJECT_SCHEMA=DATABASE() ORDER BY OBJECT_NAME, INDEX_NAME;
ROLLBACK;
