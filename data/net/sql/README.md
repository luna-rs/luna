# SQL Schemas

This folder contains the SQL files used to set up Luna's MySQL persistence.

## Files

### `user.sql`

Optional helper script for creating the initial SQL user.

Before running it, please change the default password and ensure it matches `database.password` in `luna.jsonc`.

### `luna.sql`

Main schema file for Luna's SQL persistence.

This creates the required tables for:

- player and bot account data
- economy prices
- economy price history

## Setup

1. Create or edit your SQL user using `user.sql`.
2. Import the main schema using `luna.sql`.
3. Update the values under `database` in the `luna.jsonc` file so the host, username, and password match your MySQL
   setup.
4. Ensure `game.serializer` is `SqlGameSerializer`in the `luna.jsonc` file.

Example:

```bash
mysql -u root -p < user.sql
mysql -u luna_db -p < luna.sql