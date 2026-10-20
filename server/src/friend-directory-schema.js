// Schema setup remains synchronous and executes inside Durable Object blockConcurrencyWhile.
export function initializeFriendDirectorySchema(sql) {
        sql.exec(`
          CREATE TABLE IF NOT EXISTS friend_profiles (
            friend_id TEXT PRIMARY KEY,
            token_hash TEXT NOT NULL UNIQUE,
            registration_request_id TEXT NOT NULL UNIQUE,
            display_name TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            last_seen_at INTEGER NOT NULL
          )
        `);

        sql.exec(`CREATE TABLE IF NOT EXISTS deleted_friend_profiles (friend_id TEXT PRIMARY KEY, deleted_at INTEGER NOT NULL)`);
        sql.exec(`CREATE TABLE IF NOT EXISTS deleted_friend_registration_requests (request_hash TEXT PRIMARY KEY, deleted_at INTEGER NOT NULL)`);

        sql.exec(`
          CREATE TABLE IF NOT EXISTS friend_requests (
            request_id TEXT PRIMARY KEY,
            sender_id TEXT NOT NULL,
            receiver_id TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
          )
        `);

        sql.exec(`
          CREATE INDEX IF NOT EXISTS idx_friend_requests_receiver
          ON friend_requests (
            receiver_id,
            status,
            updated_at DESC
          )
        `);

        sql.exec(`
          CREATE INDEX IF NOT EXISTS idx_friend_requests_sender
          ON friend_requests (
            sender_id,
            status,
            updated_at DESC
          )
        `);

        sql.exec(`
          CREATE TABLE IF NOT EXISTS friendships (
            friend_a TEXT NOT NULL,
            friend_b TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            PRIMARY KEY (
              friend_a,
              friend_b
            )
          )
        `);

        sql.exec(`
          CREATE INDEX IF NOT EXISTS idx_friendships_b
          ON friendships (
            friend_b,
            friend_a
          )
        `);

        sql.exec(`
          CREATE TABLE IF NOT EXISTS friend_invites (
            invite_id TEXT PRIMARY KEY,
            sender_id TEXT NOT NULL,
            receiver_id TEXT NOT NULL,
            match_id TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            expires_at INTEGER NOT NULL
          )
        `);

        sql.exec(`
          CREATE INDEX IF NOT EXISTS idx_friend_invites_receiver
          ON friend_invites (
            receiver_id,
            status,
            created_at DESC
          )
        `);
        sql.exec(`CREATE TABLE IF NOT EXISTS friend_conversation_index (
          friend_id TEXT NOT NULL, other_friend_id TEXT NOT NULL, last_message_at INTEGER NOT NULL,
          PRIMARY KEY(friend_id, other_friend_id)
        )`);
        sql.exec(`CREATE TABLE IF NOT EXISTS friend_recent_index (
          owner_friend_id TEXT NOT NULL, opponent_friend_id TEXT NOT NULL, played_at INTEGER NOT NULL,
          PRIMARY KEY(owner_friend_id, opponent_friend_id)
        )`);
        sql.exec("CREATE INDEX IF NOT EXISTS idx_friend_conversation_index_other ON friend_conversation_index(other_friend_id, friend_id)");
        sql.exec("CREATE INDEX IF NOT EXISTS idx_friend_recent_index_opponent ON friend_recent_index(opponent_friend_id, owner_friend_id)");
        // A block is directional, but protection works in both directions.
        sql.exec(`CREATE TABLE IF NOT EXISTS friend_blocks (
          blocker_id TEXT NOT NULL, blocked_id TEXT NOT NULL, created_at INTEGER NOT NULL,
          PRIMARY KEY (blocker_id, blocked_id)
        )`);
        sql.exec("CREATE INDEX IF NOT EXISTS idx_friend_blocks_target ON friend_blocks(blocked_id)");
        // Metadata-only reports; never store raw chat content in moderation records.
        sql.exec(`CREATE TABLE IF NOT EXISTS friend_reports (
          request_id TEXT PRIMARY KEY, reporter_id TEXT NOT NULL, target_id TEXT NOT NULL,
          reason TEXT NOT NULL, created_at INTEGER NOT NULL,
          status TEXT NOT NULL DEFAULT 'OPEN', resolved_at INTEGER, reviewer_id TEXT
        )`);
        sql.exec("CREATE INDEX IF NOT EXISTS idx_friend_reports_author ON friend_reports(reporter_id, created_at)");
        sql.exec("CREATE INDEX IF NOT EXISTS idx_friend_reports_status ON friend_reports(status, created_at)");
        sql.exec(`CREATE TABLE IF NOT EXISTS friend_suspensions (
          friend_id TEXT PRIMARY KEY, until_at INTEGER NOT NULL,
          report_id TEXT NOT NULL, moderator_id TEXT NOT NULL
        )`);
        sql.exec(`CREATE TABLE IF NOT EXISTS friend_report_messages (
          report_id TEXT PRIMARY KEY, message_id TEXT NOT NULL
        )`);
}
