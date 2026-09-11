-- tag_id_seq 与存量数据脱节（生产：序列停在 8，表内 max(id) 已到 40——标签曾带外插入未推进序列），
-- 新建标签 nextval 撞主键报 500。将序列对齐到 max(id)，此后从 max+1 起发。
SELECT setval('tag_id_seq', COALESCE((SELECT max(id) FROM tag), 0));
