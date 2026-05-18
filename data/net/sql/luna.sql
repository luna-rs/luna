-- --------------------------------------------------------
-- Host:                         127.0.0.1
-- Server version:               8.0.40 - MySQL Community Server - GPL
-- Server OS:                    Win64
-- HeidiSQL Version:             12.17.0.7270
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- Dumping database structure for luna
CREATE DATABASE IF NOT EXISTS `luna` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `luna`;

-- Dumping structure for table luna.main
CREATE TABLE IF NOT EXISTS `main` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `password` varchar(72) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `username` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `bot` bit(1) NOT NULL,
  `rights` varchar(50) NOT NULL,
  `json_data` text NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `bot` (`bot`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='The main persisted data for all players and bots.';

-- Data exporting was unselected.

-- Dumping structure for table luna.price_history
CREATE TABLE IF NOT EXISTS `price_history` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `item_id` int unsigned NOT NULL,
  `price` double unsigned NOT NULL,
  `last_price` double unsigned NOT NULL,
  `samples` bigint unsigned NOT NULL,
  `last_samples` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `item_id` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='The history of price changes for all economy items.';

-- Data exporting was unselected.

-- Dumping structure for table luna.prices
CREATE TABLE IF NOT EXISTS `prices` (
  `id` int unsigned NOT NULL,
  `price` double unsigned NOT NULL,
  `last_price` double unsigned NOT NULL,
  `samples` bigint unsigned NOT NULL,
  `last_samples` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='The economy prices that act as the ''street'' price for tradeable items. Buying and selling items in-game influences their prices and activity (samples).';

-- Data exporting was unselected.

-- Dumping structure for table luna.skills
CREATE TABLE IF NOT EXISTS `skills` (
  `id` int unsigned NOT NULL,
  `attack_xp` double unsigned NOT NULL,
  `attack_level` tinyint unsigned NOT NULL,
  `defence_xp` double unsigned NOT NULL,
  `defence_level` tinyint unsigned NOT NULL,
  `strength_xp` double unsigned NOT NULL,
  `strength_level` tinyint unsigned NOT NULL,
  `hitpoints_xp` double unsigned NOT NULL,
  `hitpoints_level` tinyint unsigned NOT NULL,
  `ranged_xp` double unsigned NOT NULL,
  `ranged_level` tinyint unsigned NOT NULL,
  `prayer_xp` double unsigned NOT NULL,
  `prayer_level` tinyint unsigned NOT NULL,
  `magic_xp` double unsigned NOT NULL,
  `magic_level` tinyint unsigned NOT NULL,
  `cooking_xp` double unsigned NOT NULL,
  `cooking_level` tinyint unsigned NOT NULL,
  `woodcutting_xp` double unsigned NOT NULL,
  `woodcutting_level` tinyint unsigned NOT NULL,
  `fletching_xp` double unsigned NOT NULL,
  `fletching_level` tinyint unsigned NOT NULL,
  `fishing_xp` double unsigned NOT NULL,
  `fishing_level` tinyint unsigned NOT NULL,
  `firemaking_xp` double unsigned NOT NULL,
  `firemaking_level` tinyint unsigned NOT NULL,
  `crafting_xp` double unsigned NOT NULL,
  `crafting_level` tinyint unsigned NOT NULL,
  `smithing_xp` double unsigned NOT NULL,
  `smithing_level` tinyint unsigned NOT NULL,
  `mining_xp` double unsigned NOT NULL,
  `mining_level` tinyint unsigned NOT NULL,
  `herblore_xp` double unsigned NOT NULL,
  `herblore_level` tinyint unsigned NOT NULL,
  `agility_xp` double unsigned NOT NULL,
  `agility_level` tinyint unsigned NOT NULL,
  `thieving_xp` double unsigned NOT NULL,
  `thieving_level` tinyint unsigned NOT NULL,
  `slayer_xp` double unsigned NOT NULL,
  `slayer_level` tinyint unsigned NOT NULL,
  `farming_xp` double unsigned NOT NULL,
  `farming_level` tinyint unsigned NOT NULL,
  `runecrafting_xp` double unsigned NOT NULL,
  `runecrafting_level` tinyint unsigned NOT NULL,
  `total_level` smallint unsigned NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='The skill XP and level data for all players and bots.';

-- Data exporting was unselected.

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
