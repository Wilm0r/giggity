/* rm -f db18.sqlite; sqlite3 db18bin.sqlite < db18.sql */
PRAGMA user_version = 18;
CREATE TABLE android_metadata (locale TEXT);
CREATE TABLE schedule (sch_id Integer Primary Key AutoIncrement Not Null, sch_title VarChar(128), sch_url VarChar(256), sch_atime Integer, sch_rtime Integer, sch_itime Integer, sch_refresh_interval Integer, sch_start Integer, sch_end Integer, sch_id_s VarChar(128), sch_metadata VarChar(10240), sch_day Integer);
CREATE TABLE schedule_item (sci_id Integer Primary Key AutoIncrement Not Null, sci_sch_id Integer Not Null, sci_id_s VarChar(128), sci_remind Boolean, sci_hidden Boolean, sci_stars Integer(2) Null);
CREATE VIRTUAL TABLE item_search Using FTS4(sch_id Unindexed, sci_id_s Unindexed, title, subtitle, description, speakers, track)
/* item_search(sch_id,sci_id_s,title,subtitle,description,speakers,track) */;
CREATE TABLE IF NOT EXISTS 'item_search_content'(docid INTEGER PRIMARY KEY, 'c0sch_id', 'c1sci_id_s', 'c2title', 'c3subtitle', 'c4description', 'c5speakers', 'c6track');
CREATE TABLE IF NOT EXISTS 'item_search_segments'(blockid INTEGER PRIMARY KEY, block BLOB);
CREATE TABLE IF NOT EXISTS 'item_search_segdir'(level INTEGER,idx INTEGER,start_block INTEGER,leaves_end_block INTEGER,end_block INTEGER,root BLOB,PRIMARY KEY(level, idx));
CREATE TABLE IF NOT EXISTS 'item_search_docsize'(docid INTEGER PRIMARY KEY, size BLOB);
CREATE TABLE IF NOT EXISTS 'item_search_stat'(id INTEGER PRIMARY KEY, value BLOB);
CREATE TABLE search_history (hst_id Integer Primary Key AutoIncrement Not Null, hst_query VarChar(128), hst_atime Integer);
