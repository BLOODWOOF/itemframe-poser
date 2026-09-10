-- one json object per minecraft server. json_patch updates a single frame
-- without counting a row write per nearby frame on reads
CREATE TABLE IF NOT EXISTS dumps (
	server TEXT PRIMARY KEY,
	json TEXT NOT NULL DEFAULT '{}'
);
